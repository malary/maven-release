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

import java.io.File;
import java.util.Iterator;
import java.util.List;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.model.Scm;
import org.apache.maven.project.MavenProject;
import org.apache.maven.scm.ScmException;
import org.apache.maven.scm.ScmFileSet;
import org.apache.maven.scm.ScmTagParameters;
import org.apache.maven.scm.command.tag.TagScmResult;
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
import org.apache.maven.shared.release.util.ReleaseUtil;

/**
 * Tag the SCM repository after committing the release.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @plexus.component role="org.apache.maven.shared.release.phase.ReleasePhase" role-hint="scm-tag"
 */
public class ScmTagPhase
    extends AbstractReleasePhase
{
    /**
     * Tool that gets a configured SCM repository from release configuration.
     *
     * @plexus.requirement
     */
    private ScmRepositoryConfigurator scmRepositoryConfigurator;

    public ReleaseResult execute( ReleaseDescriptor releaseDescriptor, ReleaseEnvironment releaseEnvironment, List reactorProjects )
        throws ReleaseExecutionException, ReleaseFailureException
    {
        ReleaseResult relResult = new ReleaseResult();

        validateConfiguration( releaseDescriptor );

        // MRELEASE-613
        if ( releaseDescriptor.getWaitBeforeTagging() > 0 )
        {
            logInfo( relResult, "Waiting for " + releaseDescriptor.getWaitBeforeTagging() + " seconds before tagging the release." );
            try
            {
                Thread.sleep( releaseDescriptor.getWaitBeforeTagging() * 1000 );
            }
            catch( InterruptedException e ) {
                // Ignore
            }
        }


        TagScmResult result = new TagScmResult(null, null, null, true);
        try
        {

            if (releaseDescriptor.isCommitByProject()) {
                Iterator reactorProjectsIter = reactorProjects.iterator();
                while (reactorProjectsIter.hasNext() && result.isSuccess()) {
                    // Get project key
                    MavenProject mavenProject = (MavenProject) reactorProjectsIter.next();
                    String projectKey = ArtifactUtils.versionlessKey( mavenProject.getGroupId(), mavenProject.getArtifactId() );

                    logInfo( relResult, "Tagging release with the label " + releaseDescriptor.getScmReleaseLabel(projectKey) + "..." );

                    // Prepare parameters
                    String tagName = releaseDescriptor.getScmReleaseLabel(projectKey);
                    ScmTagParameters scmTagParameters = prepareScmTagParameters(releaseDescriptor, tagName);

                    // Prepare workdir, and source url
                    ReleaseDescriptor projectReleaseDescriptor = new ReleaseDescriptor();
                    projectReleaseDescriptor.setWorkingDirectory( mavenProject.getBasedir().getAbsolutePath() );
                    projectReleaseDescriptor.setScmSourceUrl(
                            ((Scm)releaseDescriptor.getOriginalScmInfo().get(projectKey)).getDeveloperConnection()
                    );

                    // Do the tag
                    result = doTag(projectReleaseDescriptor, releaseDescriptor, releaseEnvironment, tagName, scmTagParameters);
                }
            } else {
                logInfo( relResult, "Tagging release with the label " + releaseDescriptor.getScmReleaseLabel() + "..." );

                // Prepare parameters
                String tagName = releaseDescriptor.getScmReleaseLabel();
                ScmTagParameters scmTagParameters = prepareScmTagParameters(releaseDescriptor, tagName);

                // Prepare workdir, and source url
                ReleaseDescriptor basedirAlignedReleaseDescriptor =
                    ReleaseUtil.createBasedirAlignedReleaseDescriptor( releaseDescriptor, reactorProjects );

                // Do the tag
                result = doTag(basedirAlignedReleaseDescriptor, releaseDescriptor, releaseEnvironment, tagName, scmTagParameters);
            }
        }
        catch ( ScmException e )
        {
            throw new ReleaseExecutionException( "An error is occurred in the tag process: " + e.getMessage(), e );
        }

        if ( !result.isSuccess() )
        {
            throw new ReleaseScmCommandException( "Unable to tag SCM", result );
        }

        relResult.setResultCode( ReleaseResult.SUCCESS );

        return relResult;
    }

    private ScmTagParameters prepareScmTagParameters(ReleaseDescriptor releaseDescriptor, String tagName)
    {
        ScmTagParameters scmTagParameters = new ScmTagParameters();
        scmTagParameters.setMessage( releaseDescriptor.getScmCommentPrefix() + " copy for tag " + tagName );
        scmTagParameters.setRemoteTagging( releaseDescriptor.isRemoteTagging() );
        scmTagParameters.setScmRevision( releaseDescriptor.getScmReleasedPomRevision() );

        if ( getLogger().isDebugEnabled() )
        {
            getLogger().debug(
                "ScmTagPhase :: scmTagParameters remotingTag " + releaseDescriptor.isRemoteTagging() );
            getLogger().debug(
                "ScmTagPhase :: scmTagParameters scmRevision " + releaseDescriptor.getScmReleasedPomRevision() );
        }
        return scmTagParameters;
    }

    private TagScmResult doTag(ReleaseDescriptor basedirAlignedReleaseDescriptor, ReleaseDescriptor releaseDescriptor, ReleaseEnvironment releaseEnvironment, String tagName, ScmTagParameters scmTagParameters)
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
            getLogger().debug( "ScmTagPhase :: fileSet  " + fileSet );
        }

        return provider.tag( repository, fileSet, tagName, scmTagParameters );
    }

    public ReleaseResult simulate( ReleaseDescriptor releaseDescriptor, ReleaseEnvironment releaseEnvironment, List<MavenProject> reactorProjects )
        throws ReleaseExecutionException, ReleaseFailureException
    {
        ReleaseResult result = new ReleaseResult();

        validateConfiguration( releaseDescriptor );

        if (releaseDescriptor.isCommitByProject())
        {
            for (MavenProject mavenProject : reactorProjects) {
                // Get project key
                String projectKey = ArtifactUtils.versionlessKey( mavenProject.getGroupId(), mavenProject.getArtifactId() );

                // Prepare workdir, and source url
                ReleaseDescriptor projectReleaseDescriptor = new ReleaseDescriptor();
                projectReleaseDescriptor.setWorkingDirectory( mavenProject.getBasedir().getAbsolutePath() );
                projectReleaseDescriptor.setScmSourceUrl(
                        ((Scm)releaseDescriptor.getOriginalScmInfo().get(projectKey)).getDeveloperConnection()
                );

                logSimulate(result,
                    projectReleaseDescriptor.getWorkingDirectory(),
                    releaseDescriptor.isRemoteTagging(),
                    projectReleaseDescriptor.getScmSourceUrl(),
                    releaseDescriptor.getScmReleaseLabel(projectKey));
            }
        }
        else
        {
            ReleaseDescriptor basedirAlignedReleaseDescriptor =
                ReleaseUtil.createBasedirAlignedReleaseDescriptor( releaseDescriptor, reactorProjects );

            logSimulate(result, basedirAlignedReleaseDescriptor.getWorkingDirectory(),
                    releaseDescriptor.isRemoteTagging(),
                    basedirAlignedReleaseDescriptor.getScmSourceUrl(),
                    releaseDescriptor.getScmReleaseLabel());
        }

        result.setResultCode( ReleaseResult.SUCCESS );

        return result;
    }

    private void logSimulate(ReleaseResult result, String workdir, boolean remoteTagging, String scmSourceUrl, String label)
    {
        logInfo( result, "Full run would be tagging " + workdir );

        if ( remoteTagging )
        {
            logInfo( result, "  To SCM URL: " + scmSourceUrl );
        }

        logInfo( result, "  with label: '" + label + "'" );
    }

    private static void validateConfiguration( ReleaseDescriptor releaseDescriptor )
        throws ReleaseFailureException
    {
        if ( releaseDescriptor.getScmReleaseLabels().isEmpty() )
        {
            throw new ReleaseFailureException( "A release label is required for committing" );
        }
    }
}
