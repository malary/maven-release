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

import org.apache.commons.lang.StringUtils;
import org.apache.maven.project.MavenProject;
import org.apache.maven.scm.ScmException;
import org.apache.maven.scm.ScmFile;
import org.apache.maven.scm.ScmFileSet;
import org.apache.maven.scm.command.status.StatusScmResult;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * See if there are any local modifications to the files before proceeding with SCM operations and the release.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @plexus.component role="org.apache.maven.shared.release.phase.ReleasePhase" role-hint="scm-check-modifications"
 */
public class ScmCheckModificationsPhase
    extends AbstractReleasePhase
{
    /**
     * Tool that gets a configured SCM repository from release configuration.
     *
     * @plexus.requirement
     */
    private ScmRepositoryConfigurator scmRepositoryConfigurator;

    /**
     * The files to exclude from the status check.
     *
     * @todo proper construction of filenames, especially release properties
     */
    private Set<String> excludedFiles = new HashSet<String>( Arrays.asList( new String[] { "pom.xml.backup",
        "pom.xml.tag", "pom.xml.next", "pom.xml.branch", "release.properties", "pom.xml.releaseBackup" } ) );

    public ReleaseResult execute( ReleaseDescriptor releaseDescriptor, ReleaseEnvironment releaseEnvironment,
                                  List<MavenProject> reactorProjects )
        throws ReleaseExecutionException, ReleaseFailureException
    {
        ReleaseResult relResult = new ReleaseResult();

        List<String> additionalExcludes = releaseDescriptor.getCheckModificationExcludes();

        if ( additionalExcludes != null )
        {
            for ( int i1 = 0, additionalExcludesSize = additionalExcludes.size(); i1 < additionalExcludesSize; i1++ )
            {
                excludedFiles.add( additionalExcludes.get( i1 ) );
            }
        }

        logInfo( relResult, "Verifying that there are no local modifications..." );
        logInfo( relResult, "  ignoring changes on: " + StringUtils.join( excludedFiles, ", " ) );

        ScmRepository repository;
        ScmProvider provider;
        try
        {
            repository =
                scmRepositoryConfigurator.getConfiguredRepository( releaseDescriptor, releaseEnvironment.getSettings() );

            provider = scmRepositoryConfigurator.getRepositoryProvider( repository );
        }
        catch ( ScmRepositoryException e )
        {
            throw new ReleaseScmRepositoryException(
                e.getMessage() + " for URL: " + releaseDescriptor.getScmSourceUrl(), e.getValidationMessages() );
        }
        catch ( NoSuchScmProviderException e )
        {
            throw new ReleaseExecutionException( "Unable to configure SCM repository: " + e.getMessage(), e );
        }

        StatusScmResult result;
        try
        {
            result =
                provider.status( repository, new ScmFileSet( new File( releaseDescriptor.getWorkingDirectory() ) ) );
        }
        catch ( ScmException e )
        {
            throw new ReleaseExecutionException( "An error occurred during the status check process: " + e.getMessage(),
                                                 e );
        }

        if ( !result.isSuccess() )
        {
            throw new ReleaseScmCommandException( "Unable to check for local modifications", result );
        }

        List<ScmFile> changedFiles = result.getChangedFiles();

        // TODO: would be nice for SCM status command to do this for me.
        for ( Iterator<ScmFile> i = changedFiles.iterator(); i.hasNext(); )
        {
            ScmFile f = i.next();

            String fileName = f.getPath().replace( '\\', '/' );
            fileName = fileName.substring( fileName.lastIndexOf( '/' ) + 1, fileName.length() );

            if ( excludedFiles.contains( fileName ) )
            {
                i.remove();
            }
        }

        if ( !changedFiles.isEmpty() )
        {
            StringBuffer message = new StringBuffer();

            for ( ScmFile file : changedFiles )
            {
                message.append( file.toString() );
                message.append( "\n" );
            }

            throw new ReleaseFailureException(
                "Cannot prepare the release because you have local modifications : \n" + message );
        }

        relResult.setResultCode( ReleaseResult.SUCCESS );

        return relResult;
    }

    public ReleaseResult simulate( ReleaseDescriptor releaseDescriptor, ReleaseEnvironment releaseEnvironment,
                                   List<MavenProject> reactorProjects )
        throws ReleaseExecutionException, ReleaseFailureException
    {
        // It makes no modifications, so simulate is the same as execute
        return execute( releaseDescriptor, releaseEnvironment, reactorProjects );
    }
}
