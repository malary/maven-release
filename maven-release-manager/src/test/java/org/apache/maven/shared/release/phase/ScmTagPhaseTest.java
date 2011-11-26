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
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import java.util.Map;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.model.Scm;
import org.apache.maven.project.MavenProject;
import org.apache.maven.scm.ScmException;
import org.apache.maven.scm.ScmFile;
import org.apache.maven.scm.ScmFileSet;
import org.apache.maven.scm.ScmFileStatus;
import org.apache.maven.scm.ScmTagParameters;
import org.apache.maven.scm.command.tag.TagScmResult;
import org.apache.maven.scm.manager.NoSuchScmProviderException;
import org.apache.maven.scm.manager.ScmManager;
import org.apache.maven.scm.provider.ScmProvider;
import org.apache.maven.scm.provider.ScmProviderStub;
import org.apache.maven.scm.provider.svn.repository.SvnScmProviderRepository;
import org.apache.maven.scm.repository.ScmRepository;
import org.apache.maven.scm.repository.ScmRepositoryException;
import org.apache.maven.shared.release.ReleaseExecutionException;
import org.apache.maven.shared.release.ReleaseFailureException;
import org.apache.maven.shared.release.config.ReleaseDescriptor;
import org.apache.maven.shared.release.env.DefaultReleaseEnvironment;
import org.apache.maven.shared.release.scm.DefaultScmRepositoryConfigurator;
import org.apache.maven.shared.release.scm.ReleaseScmCommandException;
import org.apache.maven.shared.release.scm.ReleaseScmRepositoryException;
import org.apache.maven.shared.release.scm.ScmRepositoryConfigurator;
import org.apache.maven.shared.release.stubs.ScmManagerStub;
import org.apache.maven.shared.release.util.ReleaseUtil;
import org.jmock.Mock;
import org.jmock.core.Constraint;
import org.jmock.core.constraint.IsAnything;
import org.jmock.core.constraint.IsEqual;
import org.jmock.core.matcher.InvokeOnceMatcher;
import org.jmock.core.matcher.TestFailureMatcher;
import org.jmock.core.stub.ReturnStub;
import org.jmock.core.stub.ThrowStub;

/**
 * Test the SCM tag phase.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 */
public class ScmTagPhaseTest
    extends AbstractReleaseTestCase
{
    protected void setUp()
        throws Exception
    {
        super.setUp();

        phase = (ReleasePhase) lookup( ReleasePhase.ROLE, "scm-tag" );
    }

    public static String getPath(File file)
        throws IOException
    {
        return ReleaseUtil.isSymlink( file ) ? file.getCanonicalPath() : file.getAbsolutePath();
    }

    public void testTag()
        throws Exception
    {
        ReleaseDescriptor descriptor = new ReleaseDescriptor();
        List<MavenProject> reactorProjects = createReactorProjects();
        descriptor.setScmSourceUrl( "scm-url" );
        MavenProject rootProject = ReleaseUtil.getRootProject( reactorProjects );
        descriptor.setWorkingDirectory( getPath(rootProject.getFile().getParentFile() ) );
        descriptor.setScmReleaseLabel( "release-label" );
        descriptor.setScmCommentPrefix( "[my prefix]" );

        ScmFileSet fileSet = new ScmFileSet( rootProject.getFile().getParentFile() );

        Mock scmProviderMock = new Mock( ScmProvider.class );
        Constraint[] arguments =
            new Constraint[]{new IsAnything(), new IsScmFileSetEquals( fileSet ), new IsEqual( "release-label" ),
                new IsScmTagParamtersEquals( new ScmTagParameters( "[my prefix] copy for tag release-label" ) )};
        scmProviderMock
            .expects( new InvokeOnceMatcher() )
            .method( "tag" )
            .with( arguments )
            .will( new ReturnStub( new TagScmResult( "...", Collections.singletonList( new ScmFile( getPath (rootProject
                       .getFile() ), ScmFileStatus.TAGGED ) ) ) ) );

        ScmManagerStub stub = (ScmManagerStub) lookup( ScmManager.ROLE );
        stub.setScmProvider( (ScmProvider) scmProviderMock.proxy() );

        phase.execute( descriptor, new DefaultReleaseEnvironment(), reactorProjects );

        assertTrue( true );
    }

    public void testCommitMultiModuleDeepFolders()
        throws Exception
    {
        List<MavenProject> reactorProjects = createReactorProjects( "scm-commit/", "multimodule-with-deep-subprojects" );
        String sourceUrl = "http://svn.example.com/repos/project/trunk/";
        String scmUrl = "scm:svn:" + sourceUrl;
        ReleaseDescriptor descriptor = new ReleaseDescriptor();
        descriptor.setScmSourceUrl( scmUrl );
        MavenProject rootProject = ReleaseUtil.getRootProject( reactorProjects );
        descriptor.setWorkingDirectory( getPath( rootProject.getFile().getParentFile() ) );
        descriptor.setScmReleaseLabel( "release-label" );
        descriptor.setScmCommentPrefix( "[my prefix]" );
        descriptor.setScmTagBase( "http://svn.example.com/repos/project/releases/" );

        ScmFileSet fileSet = new ScmFileSet( rootProject.getFile().getParentFile() );

        Mock scmProviderMock = new Mock( ScmProvider.class );
        SvnScmProviderRepository scmProviderRepository = new SvnScmProviderRepository( sourceUrl );
        scmProviderRepository.setTagBase( "http://svn.example.com/repos/project/releases/" );
        ScmRepository repository = new ScmRepository( "svn", scmProviderRepository );
        Constraint[] arguments = new Constraint[]{new IsEqual( repository ), new IsScmFileSetEquals( fileSet ),
            new IsEqual( "release-label" ),
            new IsScmTagParamtersEquals( new ScmTagParameters( "[my prefix] copy for tag release-label" ) )};

        scmProviderMock
            .expects( new InvokeOnceMatcher() )
            .method( "tag" )
            .with( arguments )
            .will( new ReturnStub( new TagScmResult( "...", Collections.singletonList( new ScmFile( getPath (rootProject
                       .getFile() ), ScmFileStatus.TAGGED ) ) ) ) );

        ScmManagerStub stub = (ScmManagerStub) lookup( ScmManager.ROLE );
        stub.setScmProvider( (ScmProvider) scmProviderMock.proxy() );
        stub.addScmRepositoryForUrl( scmUrl, repository );

        phase.execute( descriptor, new DefaultReleaseEnvironment(), reactorProjects );
    }

    public void testCommitForFlatMultiModule()
        throws Exception
    {
        List<MavenProject> reactorProjects =
            createReactorProjects( "rewrite-for-release/pom-with-parent-flat", "/root-project" );
        MavenProject rootProject = ReleaseUtil.getRootProject( reactorProjects );
        ReleaseDescriptor descriptor = new ReleaseDescriptor();
        descriptor.setScmSourceUrl( rootProject.getScm().getConnection() );
        descriptor.setWorkingDirectory( getPath( rootProject.getFile().getParentFile() ) );
        descriptor.setScmReleaseLabel( "release-label" );
        descriptor.setScmCommentPrefix( "[my prefix]" );

        // one directory up from root project
        ScmFileSet fileSet = new ScmFileSet( rootProject.getFile().getParentFile().getParentFile() );

        Mock scmProviderMock = new Mock( ScmProvider.class );
        String scmUrl = "file://localhost/tmp/scm-repo/trunk";
        SvnScmProviderRepository scmProviderRepository = new SvnScmProviderRepository( scmUrl );
        ScmRepository repository = new ScmRepository( "svn", scmProviderRepository );
        Constraint[] arguments = new Constraint[]{new IsEqual( repository ), new IsScmFileSetEquals( fileSet ),
            new IsEqual( "release-label" ),
            new IsScmTagParamtersEquals( new ScmTagParameters( "[my prefix] copy for tag release-label" ) )};
        scmProviderMock
            .expects( new InvokeOnceMatcher() )
            .method( "tag" )
            .with( arguments )
            .will( new ReturnStub( new TagScmResult( "...", Collections.singletonList( new ScmFile( getPath (rootProject
                       .getFile() ), ScmFileStatus.TAGGED ) ) ) ) );

        ScmManagerStub stub = (ScmManagerStub) lookup( ScmManager.ROLE );
        stub.setScmProvider( (ScmProvider) scmProviderMock.proxy() );
        stub.addScmRepositoryForUrl( "scm:svn:" + scmUrl, repository );

        phase.execute( descriptor, new DefaultReleaseEnvironment(), reactorProjects );
    }

    public void testCommitForFlatMultiModuleLeafTrunk()
        throws Exception
    {
        List<MavenProject> reactorProjects =
            createReactorProjects( "rewrite-for-release/pom-with-parent-flat-leaf-trunk", "/root-project" );
        MavenProject rootProject = ReleaseUtil.getRootProject( reactorProjects );
        ReleaseDescriptor descriptor = new ReleaseDescriptor();
        descriptor.setScmSourceUrl( rootProject.getScm().getConnection() );
        descriptor.setWorkingDirectory( getPath( rootProject.getFile().getParentFile() ) );
        Map releaseLabels = new HashMap();
        releaseLabels.put("groupId:artifactId", "release-label");
        releaseLabels.put("groupId:subproject1", "release-label");
        descriptor.setScmReleaseLabels(releaseLabels);

        descriptor.setScmCommentPrefix( "[my prefix]" );
        descriptor.setCommitByProject(true);

        Map originalScmInfo = new HashMap();
        for (MavenProject mavenProject : reactorProjects)
        {
            String projectKey = ArtifactUtils.versionlessKey( mavenProject.getGroupId(), mavenProject.getArtifactId() );
            originalScmInfo.put(projectKey, mavenProject.getScm());
        }
        descriptor.setOriginalScmInfo(originalScmInfo);

        Mock scmProviderMock = new Mock( ScmProvider.class );
        ScmManagerStub stub = (ScmManagerStub) lookup( ScmManager.ROLE );
        stub.setScmProvider( (ScmProvider) scmProviderMock.proxy() );

        // commit by project
        for (MavenProject project : reactorProjects)
        {
            String scmUrl = project.getScm().getDeveloperConnection().substring(8);
            SvnScmProviderRepository scmProviderRepository = new SvnScmProviderRepository( scmUrl );
            ScmRepository repository = new ScmRepository( "svn", scmProviderRepository );

            ScmFileSet fileSet = new ScmFileSet( project.getFile().getParentFile() );

            Constraint[] arguments = new Constraint[]{
                new IsEqual( repository ),
                new IsScmFileSetEquals( fileSet ),
                new IsEqual( "release-label" ),
                new IsScmTagParamtersEquals( new ScmTagParameters( "[my prefix] copy for tag release-label" ) )};
            scmProviderMock
                .expects( new InvokeOnceMatcher() )
                .method( "tag" )
                .with( arguments )
                .will( new ReturnStub( new TagScmResult( "...", Collections.singletonList( new ScmFile( getPath (project
                           .getFile() ), ScmFileStatus.TAGGED ) ) ) ) );
            stub.addScmRepositoryForUrl( "scm:svn:" + scmUrl, repository );
        }


        phase.execute( descriptor, new DefaultReleaseEnvironment(), reactorProjects );
    }

    public void testCommitMultiModule()
        throws Exception
    {
        ReleaseDescriptor descriptor = new ReleaseDescriptor();
        List<MavenProject> reactorProjects = createReactorProjects( "scm-commit/", "multiple-poms" );
        descriptor.setScmSourceUrl( "scm-url" );
        MavenProject rootProject = ReleaseUtil.getRootProject( reactorProjects );
        descriptor.setWorkingDirectory( getPath (rootProject.getFile().getParentFile() ) );
        descriptor.setScmReleaseLabel( "release-label" );
        descriptor.setScmCommentPrefix( "[my prefix]" );

        ScmFileSet fileSet = new ScmFileSet( rootProject.getFile().getParentFile() );

        Mock scmProviderMock = new Mock( ScmProvider.class );
        Constraint[] arguments =
            new Constraint[]{new IsAnything(), new IsScmFileSetEquals( fileSet ), new IsEqual( "release-label" ),
                new IsScmTagParamtersEquals( new ScmTagParameters( "[my prefix] copy for tag release-label" ) )};
        scmProviderMock
            .expects( new InvokeOnceMatcher() )
            .method( "tag" )
            .with( arguments )
            .will( new ReturnStub( new TagScmResult( "...", Collections.singletonList( new ScmFile( getPath( rootProject
                       .getFile() ), ScmFileStatus.TAGGED ) ) ) ) );

        ScmManagerStub stub = (ScmManagerStub) lookup( ScmManager.ROLE );
        stub.setScmProvider( (ScmProvider) scmProviderMock.proxy() );

        phase.execute( descriptor, new DefaultReleaseEnvironment(), reactorProjects );

        assertTrue( true );
    }

    public void testTagNoReleaseLabel()
        throws Exception
    {
        ReleaseDescriptor descriptor = new ReleaseDescriptor();
        List<MavenProject> reactorProjects = createReactorProjects();

        try
        {
            phase.execute( descriptor, new DefaultReleaseEnvironment(), reactorProjects );
            fail( "Should have thrown an exception" );
        }
        catch ( ReleaseFailureException e )
        {
            assertTrue( true );
        }
    }

    public void testSimulateTag()
        throws Exception
    {
        ReleaseDescriptor descriptor = new ReleaseDescriptor();
        List<MavenProject> reactorProjects = createReactorProjects();
        descriptor.setScmSourceUrl( "scm-url" );
        MavenProject rootProject = ReleaseUtil.getRootProject( reactorProjects );
        descriptor.setWorkingDirectory( getPath ( rootProject.getFile().getParentFile() ) );
        descriptor.setScmReleaseLabel( "release-label" );

        Mock scmProviderMock = new Mock( ScmProvider.class );
        scmProviderMock.expects( new TestFailureMatcher( "Shouldn't have called tag" ) ).method( "tag" );

        ScmManagerStub stub = (ScmManagerStub) lookup( ScmManager.ROLE );
        stub.setScmProvider( (ScmProvider) scmProviderMock.proxy() );

        phase.simulate( descriptor, new DefaultReleaseEnvironment(), reactorProjects );

        assertTrue( true );
    }

    public void testSimulateTagNoReleaseLabel()
        throws Exception
    {
        ReleaseDescriptor descriptor = new ReleaseDescriptor();
        List<MavenProject> reactorProjects = createReactorProjects();

        try
        {
            phase.simulate( descriptor, new DefaultReleaseEnvironment(), reactorProjects );
            fail( "Should have thrown an exception" );
        }
        catch ( ReleaseFailureException e )
        {
            assertTrue( true );
        }
    }

    public void testNoSuchScmProviderExceptionThrown()
        throws Exception
    {
        List<MavenProject> reactorProjects = createReactorProjects();
        ReleaseDescriptor releaseDescriptor = createReleaseDescriptor();

        Mock scmManagerMock = new Mock( ScmManager.class );
        scmManagerMock.expects( new InvokeOnceMatcher() ).method( "makeScmRepository" ).with(
            new IsEqual( "scm-url" ) ).will( new ThrowStub( new NoSuchScmProviderException( "..." ) ) );

        ScmManager scmManager = (ScmManager) scmManagerMock.proxy();
        DefaultScmRepositoryConfigurator configurator =
            (DefaultScmRepositoryConfigurator) lookup( ScmRepositoryConfigurator.ROLE );
        configurator.setScmManager( scmManager );

        try
        {
            phase.execute( releaseDescriptor, new DefaultReleaseEnvironment(), reactorProjects );

            fail( "Status check should have failed" );
        }
        catch ( ReleaseExecutionException e )
        {
            assertEquals( "check cause", NoSuchScmProviderException.class, e.getCause().getClass() );
        }
    }

    public void testScmRepositoryExceptionThrown()
        throws Exception
    {
        List<MavenProject> reactorProjects = createReactorProjects();
        ReleaseDescriptor releaseDescriptor = createReleaseDescriptor();

        Mock scmManagerMock = new Mock( ScmManager.class );
        scmManagerMock.expects( new InvokeOnceMatcher() ).method( "makeScmRepository" ).with(
            new IsEqual( "scm-url" ) ).will( new ThrowStub( new ScmRepositoryException( "..." ) ) );

        ScmManager scmManager = (ScmManager) scmManagerMock.proxy();
        DefaultScmRepositoryConfigurator configurator =
            (DefaultScmRepositoryConfigurator) lookup( ScmRepositoryConfigurator.ROLE );
        configurator.setScmManager( scmManager );

        try
        {
            phase.execute( releaseDescriptor, new DefaultReleaseEnvironment(), reactorProjects );

            fail( "Status check should have failed" );
        }
        catch ( ReleaseScmRepositoryException e )
        {
            assertNull( "Check no additional cause", e.getCause() );
        }
    }

    public void testScmExceptionThrown()
        throws Exception
    {
        List<MavenProject> reactorProjects = createReactorProjects();
        ReleaseDescriptor releaseDescriptor = createReleaseDescriptor();

        Mock scmProviderMock = new Mock( ScmProvider.class );
        scmProviderMock.expects( new InvokeOnceMatcher() ).method( "tag" ).will(
            new ThrowStub( new ScmException( "..." ) ) );

        ScmManagerStub stub = (ScmManagerStub) lookup( ScmManager.ROLE );
        stub.setScmProvider( (ScmProvider) scmProviderMock.proxy() );

        try
        {
            phase.execute( releaseDescriptor, new DefaultReleaseEnvironment(), reactorProjects );

            fail( "Status check should have failed" );
        }
        catch ( ReleaseExecutionException e )
        {
            assertEquals( "check cause", ScmException.class, e.getCause().getClass() );
        }
    }

    public void testScmResultFailure()
        throws Exception
    {
        List<MavenProject> reactorProjects = createReactorProjects();
        ReleaseDescriptor releaseDescriptor = createReleaseDescriptor();

        ScmManager scmManager = (ScmManager) lookup( ScmManager.ROLE );
        ScmProviderStub providerStub =
            (ScmProviderStub) scmManager.getProviderByUrl( releaseDescriptor.getScmSourceUrl() );

        providerStub.setTagScmResult( new TagScmResult( "", "", "", false ) );

        try
        {
            phase.execute( releaseDescriptor, new DefaultReleaseEnvironment(), reactorProjects );

            fail( "Commit should have failed" );
        }
        catch ( ReleaseScmCommandException e )
        {
            assertNull( "check no other cause", e.getCause() );
        }
    }

    private List<MavenProject> createReactorProjects()
        throws Exception
    {
        return createReactorProjects( "scm-commit/", "single-pom" );
    }

    private static ReleaseDescriptor createReleaseDescriptor()
        throws IOException
    {
        ReleaseDescriptor descriptor = new ReleaseDescriptor();
        descriptor.setScmSourceUrl( "scm-url" );
        descriptor.setScmReleaseLabel( "release-label" );
        descriptor.setWorkingDirectory( getPath(getTestFile( "target/test/checkout" ) ) );
        return descriptor;
    }

}
