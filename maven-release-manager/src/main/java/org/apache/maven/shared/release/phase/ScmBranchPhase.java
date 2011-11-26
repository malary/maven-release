package org.apache.maven.shared.release.phase;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.project.MavenProject;
import org.apache.maven.scm.ScmBranchParameters;
import org.apache.maven.scm.ScmException;
import org.apache.maven.scm.ScmFileSet;
import org.apache.maven.scm.command.branch.BranchScmResult;
import org.apache.maven.scm.manager.NoSuchScmProviderException;
import org.apache.maven.scm.provider.ScmProvider;
import org.apache.maven.scm.repository.ScmRepository;
import org.apache.maven.scm.repository.ScmRepositoryException;
import org.apache.maven.shared.release.ReleaseExecutionException;
import org.apache.maven.shared.release.ReleaseFailureException;
import org.apache.maven.shared.release.ReleaseResult;
import org.apache.maven.shared.release.config.ReleaseDescriptor;
import org.apache.maven.shared.release.env.ReleaseEnvironment;
import org.apache.maven.shared.release.scm.ReleaseScmCommandException;
import org.apache.maven.shared.release.scm.ReleaseScmRepositoryException;
import org.apache.maven.shared.release.scm.ScmRepositoryConfigurator;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.model.Scm;
import org.apache.maven.shared.release.util.ReleaseUtil;

/**
 * Branch the SCM repository.
 *
 * @author <a href="mailto:evenisse@apache.org">Emmanuel Venisse</a>
 * @plexus.component role="org.apache.maven.shared.release.phase.ReleasePhase" role-hint="scm-branch"
 */
public class ScmBranchPhase
    extends AbstractReleasePhase
{
    /**
     * Tool that gets a configured SCM repository from release configuration.
     *
     * @plexus.requirement
     */
    private ScmRepositoryConfigurator scmRepositoryConfigurator;

    public ReleaseResult execute( ReleaseDescriptor releaseDescriptor, ReleaseEnvironment releaseEnvironment, List<MavenProject> reactorProjects )
        throws ReleaseExecutionException, ReleaseFailureException
    {
        ReleaseResult relResult = new ReleaseResult();

        validateConfiguration( releaseDescriptor );

        // MRELEASE-613
        if ( releaseDescriptor.getWaitBeforeTagging() > 0 )
        {
            logInfo( relResult, "Waiting for " + releaseDescriptor.getWaitBeforeTagging() + " seconds before branching the release." );
            try
            {
                Thread.sleep( releaseDescriptor.getWaitBeforeTagging() * 1000 );
            }
            catch( InterruptedException e ) {
                // Ignore
            }
        }

        logInfo( relResult, "Branching release with the label " + releaseDescriptor.getScmReleaseLabel() + "..." );

        BranchScmResult result = new BranchScmResult(null, null, null, true);
        try
        {

            if (releaseDescriptor.isCommitByProject()) {
                Iterator reactorProjectsIter = reactorProjects.iterator();
                while (reactorProjectsIter.hasNext() && result.isSuccess()) {
                    // Get project key
                    MavenProject mavenProject = (MavenProject) reactorProjectsIter.next();
                    String projectKey = ArtifactUtils.versionlessKey( mavenProject.getGroupId(), mavenProject.getArtifactId() );

                    // Prepare parameters
                    String branchName = releaseDescriptor.getScmReleaseLabel(projectKey);
                    ScmBranchParameters scmBranchParameters = prepareScmBranchParameters(releaseDescriptor, branchName);

                    // Prepare workdir, and source url
                    ReleaseDescriptor projectReleaseDescriptor = new ReleaseDescriptor();
                    projectReleaseDescriptor.setWorkingDirectory( mavenProject.getBasedir().getAbsolutePath() );
                    projectReleaseDescriptor.setScmSourceUrl(
                            ((Scm)releaseDescriptor.getOriginalScmInfo().get(projectKey)).getDeveloperConnection()
                    );

                    // Do the tag
                    result = doBranch(projectReleaseDescriptor, releaseDescriptor, releaseEnvironment, branchName, scmBranchParameters);
                }
            } else {
                // Prepare parameters
                String branchName = releaseDescriptor.getScmReleaseLabel();
                ScmBranchParameters scmBranchParameters = prepareScmBranchParameters(releaseDescriptor, branchName);

                // Prepare workdir, and source url
                ReleaseDescriptor basedirAlignedReleaseDescriptor =
                    ReleaseUtil.createBasedirAlignedReleaseDescriptor( releaseDescriptor, reactorProjects );

                // Do the tag
                result = doBranch(basedirAlignedReleaseDescriptor, releaseDescriptor, releaseEnvironment, branchName, scmBranchParameters);
            }
        }
        catch ( ScmException e )
        {
            throw new ReleaseExecutionException( "An error is occurred in the branch process: " + e.getMessage(), e );
        }

        if ( !result.isSuccess() )
        {
            throw new ReleaseScmCommandException( "Unable to branch SCM", result );
        }

        relResult.setResultCode( ReleaseResult.SUCCESS );

        return relResult;
    }

    private ScmBranchParameters prepareScmBranchParameters(ReleaseDescriptor releaseDescriptor, String branchName)
    {
        ScmBranchParameters scmBranchParameters = new ScmBranchParameters();
        scmBranchParameters.setMessage( releaseDescriptor.getScmCommentPrefix() + " copy for branch " + branchName );
        scmBranchParameters.setRemoteBranching( releaseDescriptor.isRemoteTagging() );
        scmBranchParameters.setScmRevision( releaseDescriptor.getScmReleasedPomRevision() );

        if ( getLogger().isDebugEnabled() )
        {
            getLogger().debug(
                "ScmBranchPhase :: scmBranchParameters remotingBranch " + releaseDescriptor.isRemoteTagging() );
            getLogger().debug(
                "ScmBranchPhase :: scmBranchParameters scmRevision " + releaseDescriptor.getScmReleasedPomRevision() );
        }
        return scmBranchParameters;
    }

    private BranchScmResult doBranch(ReleaseDescriptor basedirAlignedReleaseDescriptor, ReleaseDescriptor releaseDescriptor, ReleaseEnvironment releaseEnvironment, String branchName, ScmBranchParameters scmBranchParameters)
            throws ReleaseScmRepositoryException, ReleaseExecutionException, ScmException
    {
        ScmRepository repository;
        ScmProvider provider;
        try
        {
            repository = scmRepositoryConfigurator.getConfiguredRepository( basedirAlignedReleaseDescriptor.getScmSourceUrl(), releaseDescriptor, releaseEnvironment.getSettings() );

            repository.getProviderRepository().setPushChanges( releaseDescriptor.isPushChanges() );

            provider = scmRepositoryConfigurator.getRepositoryProvider( repository );
        }
        catch ( ScmRepositoryException e )
        {
            throw new ReleaseScmRepositoryException( e.getMessage(), e.getValidationMessages() );
        }
        catch ( NoSuchScmProviderException e )
        {
            throw new ReleaseExecutionException( "Unable to configure SCM repository: " + e.getMessage(), e );
        }

        // TODO: want includes/excludes?
        ScmFileSet fileSet = new ScmFileSet( new File( basedirAlignedReleaseDescriptor.getWorkingDirectory() ) );
        if ( getLogger().isDebugEnabled() )
        {
            getLogger().debug( "ScmBranchPhase :: fileSet  " + fileSet );
        }

        return provider.branch( repository, fileSet, branchName, scmBranchParameters);
    }

    public ReleaseResult simulate( ReleaseDescriptor releaseDescriptor, ReleaseEnvironment releaseEnvironment, List<MavenProject> reactorProjects )
        throws ReleaseExecutionException, ReleaseFailureException
    {
        ReleaseResult result = new ReleaseResult();

        validateConfiguration( releaseDescriptor );

        ReleaseDescriptor basedirAlignedReleaseDescriptor =
            ReleaseUtil.createBasedirAlignedReleaseDescriptor( releaseDescriptor, reactorProjects );

        logInfo( result, "Full run would be branching " + basedirAlignedReleaseDescriptor.getWorkingDirectory() );
        if ( releaseDescriptor.isRemoteTagging() )
        {
            logInfo( result, "  To SCM URL: " + basedirAlignedReleaseDescriptor.getScmBranchBase() );
        }
        logInfo( result, "  with label: '" + releaseDescriptor.getScmReleaseLabel() + "'" );

        result.setResultCode( ReleaseResult.SUCCESS );

        return result;
    }

    private static void validateConfiguration( ReleaseDescriptor releaseDescriptor )
        throws ReleaseFailureException
    {
        if ( releaseDescriptor.getScmReleaseLabel() == null )
        {
            throw new ReleaseFailureException( "A release label is required for committing" );
        }
    }
}
