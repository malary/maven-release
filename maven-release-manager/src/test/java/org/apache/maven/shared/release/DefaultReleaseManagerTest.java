package org.apache.maven.shared.release;

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
import java.util.List;
import java.util.Map;

import org.apache.maven.project.MavenProject;
import org.apache.maven.scm.ScmException;
import org.apache.maven.scm.ScmFile;
import org.apache.maven.scm.ScmFileSet;
import org.apache.maven.scm.ScmTag;
import org.apache.maven.scm.command.checkout.CheckOutScmResult;
import org.apache.maven.scm.manager.NoSuchScmProviderException;
import org.apache.maven.scm.manager.ScmManager;
import org.apache.maven.scm.manager.ScmManagerStub;
import org.apache.maven.scm.provider.ScmProvider;
import org.apache.maven.scm.provider.ScmProviderStub;
import org.apache.maven.scm.repository.ScmRepositoryException;
import org.apache.maven.shared.release.config.ReleaseDescriptor;
import org.apache.maven.shared.release.config.ReleaseDescriptorStore;
import org.apache.maven.shared.release.config.ReleaseDescriptorStoreException;
import org.apache.maven.shared.release.config.ReleaseDescriptorStoreStub;
import org.apache.maven.shared.release.env.DefaultReleaseEnvironment;
import org.apache.maven.shared.release.exec.MavenExecutor;
import org.apache.maven.shared.release.phase.IsScmFileSetEquals;
import org.apache.maven.shared.release.phase.ReleasePhase;
import org.apache.maven.shared.release.phase.ReleasePhaseStub;
import org.apache.maven.shared.release.phase.RunPerformGoalsPhase;
import org.apache.maven.shared.release.scm.DefaultScmRepositoryConfigurator;
import org.apache.maven.shared.release.scm.ReleaseScmCommandException;
import org.apache.maven.shared.release.scm.ReleaseScmRepositoryException;
import org.apache.maven.shared.release.scm.ScmRepositoryConfigurator;
import org.codehaus.plexus.PlexusTestCase;
import org.codehaus.plexus.util.FileUtils;
import org.jmock.Mock;
import org.jmock.core.Constraint;
import org.jmock.core.constraint.IsAnything;
import org.jmock.core.constraint.IsEqual;
import org.jmock.core.constraint.IsInstanceOf;
import org.jmock.core.constraint.IsNull;
import org.jmock.core.constraint.IsSame;
import org.jmock.core.matcher.InvokeOnceMatcher;
import org.jmock.core.stub.ReturnStub;
import org.jmock.core.stub.ThrowStub;

/**
 * Test the default release manager.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 */
public class DefaultReleaseManagerTest
    extends PlexusTestCase
{
    private ReleaseDescriptorStoreStub configStore;


    protected void setUp()
        throws Exception
    {
        super.setUp();

        configStore = (ReleaseDescriptorStoreStub) lookup( ReleaseDescriptorStore.ROLE, "stub" );
    }

    public void testPrepareNoCompletedPhase()
        throws Exception
    {
        ReleaseManager releaseManager = (ReleaseManager) lookup( ReleaseManager.ROLE, "test" );

        ReleaseDescriptor releaseDescriptor = configStore.getReleaseConfiguration();
        releaseDescriptor.setCompletedPhase( null );

        releaseManager.prepare( new ReleaseDescriptor(), new DefaultReleaseEnvironment(), null );

        @SuppressWarnings("unchecked")
        Map<String,ReleasePhaseStub> phases = container.lookupMap( ReleasePhase.ROLE );

        ReleasePhaseStub phase = (ReleasePhaseStub) phases.get( "step1" );
        assertTrue( "step1 executed", phase.isExecuted() );
        assertFalse( "step1 not simulated", phase.isSimulated() );
        phase = (ReleasePhaseStub) phases.get( "step2" );
        assertTrue( "step2 executed", phase.isExecuted() );
        assertFalse( "step2 not simulated", phase.isSimulated() );
        phase = (ReleasePhaseStub) phases.get( "step3" );
        assertTrue( "step3 executed", phase.isExecuted() );
        assertFalse( "step3 not simulated", phase.isSimulated() );
    }

    public void testPrepareCompletedPhase()
        throws Exception
    {
        ReleaseManager releaseManager = (ReleaseManager) lookup( ReleaseManager.ROLE, "test" );

        ReleaseDescriptor releaseDescriptor = configStore.getReleaseConfiguration();
        releaseDescriptor.setCompletedPhase( "step1" );

        releaseManager.prepare( new ReleaseDescriptor(), new DefaultReleaseEnvironment(), null );

        @SuppressWarnings("unchecked")
        Map<String,ReleasePhaseStub> phases = container.lookupMap( ReleasePhase.ROLE );

        ReleasePhaseStub phase = (ReleasePhaseStub) phases.get( "step1" );
        assertFalse( "step1 not executed", phase.isExecuted() );
        assertFalse( "step1 not simulated", phase.isSimulated() );
        phase = (ReleasePhaseStub) phases.get( "step2" );
        assertTrue( "step2 executed", phase.isExecuted() );
        assertFalse( "step2 not simulated", phase.isSimulated() );
        phase = (ReleasePhaseStub) phases.get( "step3" );
        assertTrue( "step3 executed", phase.isExecuted() );
        assertFalse( "step3 not simulated", phase.isSimulated() );
    }

    public void testPrepareCompletedPhaseNoResume()
        throws Exception
    {
        ReleaseManager releaseManager = (ReleaseManager) lookup( ReleaseManager.ROLE, "test" );

        ReleaseDescriptor releaseDescriptor = configStore.getReleaseConfiguration();
        releaseDescriptor.setCompletedPhase( "step1" );

        releaseManager.prepare( new ReleaseDescriptor(), new DefaultReleaseEnvironment(), null, false, false );

        @SuppressWarnings("unchecked")
        Map<String,ReleasePhaseStub> phases = container.lookupMap( ReleasePhase.ROLE );

        ReleasePhaseStub phase = (ReleasePhaseStub) phases.get( "step1" );
        assertTrue( "step1 executed", phase.isExecuted() );
        assertFalse( "step1 not simulated", phase.isSimulated() );
        phase = (ReleasePhaseStub) phases.get( "step2" );
        assertTrue( "step2 executed", phase.isExecuted() );
        assertFalse( "step2 not simulated", phase.isSimulated() );
        phase = (ReleasePhaseStub) phases.get( "step3" );
        assertTrue( "step3 executed", phase.isExecuted() );
        assertFalse( "step3 not simulated", phase.isSimulated() );
    }

    public void testPrepareCompletedAllPhases()
        throws Exception
    {
        ReleaseManager releaseManager = (ReleaseManager) lookup( ReleaseManager.ROLE, "test" );

        ReleaseDescriptor releaseDescriptor = configStore.getReleaseConfiguration();
        releaseDescriptor.setCompletedPhase( "step3" );

        releaseManager.prepare( new ReleaseDescriptor(), new DefaultReleaseEnvironment(), null );

        @SuppressWarnings("unchecked")
        Map<String,ReleasePhaseStub> phases = container.lookupMap( ReleasePhase.ROLE );

        ReleasePhaseStub phase = (ReleasePhaseStub) phases.get( "step1" );
        assertFalse( "step1 not executed", phase.isExecuted() );
        assertFalse( "step1 not simulated", phase.isSimulated() );
        phase = (ReleasePhaseStub) phases.get( "step2" );
        assertFalse( "step2 not executed", phase.isExecuted() );
        assertFalse( "step2 not simulated", phase.isSimulated() );
        phase = (ReleasePhaseStub) phases.get( "step3" );
        assertFalse( "step3 not executed", phase.isExecuted() );
        assertFalse( "step3 not simulated", phase.isSimulated() );
    }

    public void testPrepareInvalidCompletedPhase()
        throws Exception
    {
        ReleaseManager releaseManager = (ReleaseManager) lookup( ReleaseManager.ROLE, "test" );

        ReleaseDescriptor releaseDescriptor = configStore.getReleaseConfiguration();
        releaseDescriptor.setCompletedPhase( "foo" );

        releaseManager.prepare( new ReleaseDescriptor(), new DefaultReleaseEnvironment(), null );

        @SuppressWarnings("unchecked")
        Map<String,ReleasePhaseStub> phases = container.lookupMap( ReleasePhase.ROLE );

        ReleasePhaseStub phase = phases.get( "step1" );
        assertTrue( "step1 executed", phase.isExecuted() );
        assertFalse( "step1 not simulated", phase.isSimulated() );
        phase = phases.get( "step2" );
        assertTrue( "step2 executed", phase.isExecuted() );
        assertFalse( "step2 not simulated", phase.isSimulated() );
        phase = phases.get( "step3" );
        assertTrue( "step3 executed", phase.isExecuted() );
        assertFalse( "step3 not simulated", phase.isSimulated() );
    }

    public void testPrepareSimulateNoCompletedPhase()
        throws Exception
    {
        ReleaseManager releaseManager = (ReleaseManager) lookup( ReleaseManager.ROLE, "test" );

        ReleaseDescriptor releaseDescriptor = configStore.getReleaseConfiguration();
        releaseDescriptor.setCompletedPhase( null );

        releaseManager.prepare( new ReleaseDescriptor(), new DefaultReleaseEnvironment(), null, true, true );

        @SuppressWarnings("unchecked")
        Map<String,ReleasePhaseStub> phases = container.lookupMap( ReleasePhase.ROLE );

        ReleasePhaseStub phase = (ReleasePhaseStub) phases.get( "step1" );
        assertTrue( "step1 simulated", phase.isSimulated() );
        assertFalse( "step1 not executed", phase.isExecuted() );
        phase = (ReleasePhaseStub) phases.get( "step2" );
        assertTrue( "step2 simulated", phase.isSimulated() );
        assertFalse( "step2 not executed", phase.isExecuted() );
        phase = (ReleasePhaseStub) phases.get( "step3" );
        assertTrue( "step3 simulated", phase.isSimulated() );
        assertFalse( "step3 not executed", phase.isExecuted() );
    }

    public void testPrepareSimulateCompletedPhase()
        throws Exception
    {
        ReleaseManager releaseManager = (ReleaseManager) lookup( ReleaseManager.ROLE, "test" );

        ReleaseDescriptor releaseDescriptor = configStore.getReleaseConfiguration();
        releaseDescriptor.setCompletedPhase( "step1" );

        releaseManager.prepare( new ReleaseDescriptor(), new DefaultReleaseEnvironment(), null, true, true );

        @SuppressWarnings("unchecked")
        Map<String,ReleasePhaseStub> phases = container.lookupMap( ReleasePhase.ROLE );

        ReleasePhaseStub phase = (ReleasePhaseStub) phases.get( "step1" );
        assertFalse( "step1 not simulated", phase.isSimulated() );
        assertFalse( "step1 not executed", phase.isExecuted() );
        phase = (ReleasePhaseStub) phases.get( "step2" );
        assertTrue( "step2 simulated", phase.isSimulated() );
        assertFalse( "step2 not executed", phase.isExecuted() );
        phase = (ReleasePhaseStub) phases.get( "step3" );
        assertTrue( "step3 simulated", phase.isSimulated() );
        assertFalse( "step3 not executed", phase.isExecuted() );
    }

    public void testPrepareSimulateCompletedAllPhases()
        throws Exception
    {
        ReleaseManager releaseManager = (ReleaseManager) lookup( ReleaseManager.ROLE, "test" );

        ReleaseDescriptor releaseDescriptor = configStore.getReleaseConfiguration();
        releaseDescriptor.setCompletedPhase( "step3" );

        releaseManager.prepare( new ReleaseDescriptor(), new DefaultReleaseEnvironment(), null, true, true );

        @SuppressWarnings("unchecked")
        Map<String,ReleasePhaseStub> phases = container.lookupMap( ReleasePhase.ROLE );

        ReleasePhaseStub phase = (ReleasePhaseStub) phases.get( "step1" );
        assertFalse( "step1 not simulated", phase.isSimulated() );
        assertFalse( "step1 not executed", phase.isExecuted() );
        phase = (ReleasePhaseStub) phases.get( "step2" );
        assertFalse( "step2 not simulated", phase.isSimulated() );
        assertFalse( "step2 not executed", phase.isExecuted() );
        phase = (ReleasePhaseStub) phases.get( "step3" );
        assertFalse( "step3 not simulated", phase.isSimulated() );
        assertFalse( "step3 not executed", phase.isExecuted() );
    }

    public void testPrepareSimulateInvalidCompletedPhase()
        throws Exception
    {
        ReleaseManager releaseManager = (ReleaseManager) lookup( ReleaseManager.ROLE, "test" );

        ReleaseDescriptor releaseDescriptor = configStore.getReleaseConfiguration();
        releaseDescriptor.setCompletedPhase( "foo" );

        releaseManager.prepare( new ReleaseDescriptor(), new DefaultReleaseEnvironment(), null, true, true );

        @SuppressWarnings("unchecked")
        Map<String,ReleasePhaseStub> phases = container.lookupMap( ReleasePhase.ROLE );

        ReleasePhaseStub phase = (ReleasePhaseStub) phases.get( "step1" );
        assertTrue( "step1 simulated", phase.isSimulated() );
        assertFalse( "step1 not executed", phase.isExecuted() );
        phase = (ReleasePhaseStub) phases.get( "step2" );
        assertTrue( "step2 simulated", phase.isSimulated() );
        assertFalse( "step2 not executed", phase.isExecuted() );
        phase = (ReleasePhaseStub) phases.get( "step3" );
        assertTrue( "step3 simulated", phase.isSimulated() );
        assertFalse( "step3 not executed", phase.isExecuted() );
    }

    public void testPrepareUnknownPhaseConfigured()
        throws Exception
    {
        ReleaseManager releaseManager = (ReleaseManager) lookup( ReleaseManager.ROLE, "bad-phase-configured" );

        try
        {
            releaseManager.prepare( new ReleaseDescriptor(), new DefaultReleaseEnvironment(), null );
            fail( "Should have failed to find a phase" );
        }
        catch ( ReleaseExecutionException e )
        {
            // good
        }
    }

    public void testReleaseConfigurationStoreReadFailure()
        throws Exception
    {
        ReleaseDescriptor releaseDescriptor = new ReleaseDescriptor();
        releaseDescriptor.setScmSourceUrl( "scm-url" );
        releaseDescriptor.setWorkingDirectory( getTestFile( "target/working-directory" ).getAbsolutePath() );

        DefaultReleaseManager releaseManager = (DefaultReleaseManager) lookup( ReleaseManager.ROLE, "test" );

        Mock configStoreMock = new Mock( ReleaseDescriptorStore.class );
        configStoreMock.expects( new InvokeOnceMatcher() ).method( "read" ).with(
            new IsSame( releaseDescriptor ) ).will(
            new ThrowStub( new ReleaseDescriptorStoreException( "message", new IOException( "ioExceptionMsg" ) ) ) );

        releaseManager.setConfigStore( (ReleaseDescriptorStore) configStoreMock.proxy() );

        try
        {
            releaseManager.prepare( releaseDescriptor, new DefaultReleaseEnvironment(), null );
            fail( "Should have failed to read configuration" );
        }
        catch ( ReleaseExecutionException e )
        {
            // good
            assertEquals( "check cause", ReleaseDescriptorStoreException.class, e.getCause().getClass() );
        }
    }

    public void testReleaseConfigurationStoreWriteFailure()
        throws Exception
    {
        ReleaseDescriptor releaseDescriptor = new ReleaseDescriptor();
        releaseDescriptor.setScmSourceUrl( "scm-url" );
        releaseDescriptor.setWorkingDirectory( getTestFile( "target/working-directory" ).getAbsolutePath() );

        DefaultReleaseManager releaseManager = (DefaultReleaseManager) lookup( ReleaseManager.ROLE, "test" );

        Mock configStoreMock = new Mock( ReleaseDescriptorStore.class );
        configStoreMock.expects( new InvokeOnceMatcher() ).method( "write" ).with(
            new IsSame( releaseDescriptor ) ).will(
            new ThrowStub( new ReleaseDescriptorStoreException( "message", new IOException( "ioExceptionMsg" ) ) ) );

        releaseManager.setConfigStore( (ReleaseDescriptorStore) configStoreMock.proxy() );

        try
        {
            releaseManager.prepare( releaseDescriptor, new DefaultReleaseEnvironment(), null, false, false );
            fail( "Should have failed to read configuration" );
        }
        catch ( ReleaseExecutionException e )
        {
            // good
            assertEquals( "check cause", ReleaseDescriptorStoreException.class, e.getCause().getClass() );
        }
    }

    public void testReleaseConfigurationStoreClean()
        throws Exception
    {
        ReleaseDescriptor releaseDescriptor = new ReleaseDescriptor();
        releaseDescriptor.setScmSourceUrl( "scm-url" );
        releaseDescriptor.setWorkingDirectory( getTestFile( "target/working-directory" ).getAbsolutePath() );

        DefaultReleaseManager releaseManager = (DefaultReleaseManager) lookup( ReleaseManager.ROLE, "test" );

        Mock configStoreMock = new Mock( ReleaseDescriptorStore.class );
        configStoreMock.expects( new InvokeOnceMatcher() ).method( "delete" );

        releaseManager.setConfigStore( (ReleaseDescriptorStore) configStoreMock.proxy() );

        releaseManager.clean( releaseDescriptor, null, null );

        @SuppressWarnings("unchecked")
        Map<String,ReleasePhaseStub> phases = container.lookupMap( ReleasePhase.ROLE );

        ReleasePhaseStub phase = (ReleasePhaseStub) phases.get( "step1" );
        assertTrue( "step1 not cleaned", phase.isCleaned() );

        phase = (ReleasePhaseStub) phases.get( "step2" );
        assertTrue( "step2 not cleaned", phase.isCleaned() );

        phase = (ReleasePhaseStub) phases.get( "step3" );
        assertTrue( "step3 not cleaned", phase.isCleaned() );

        phase = (ReleasePhaseStub) phases.get( "branch1" );
        assertTrue( "branch1 not cleaned", phase.isCleaned() );
    }

    public void testReleasePerform()
        throws Exception
    {
        DefaultReleaseManager releaseManager = (DefaultReleaseManager) lookup( ReleaseManager.ROLE, "test" );

        ReleaseDescriptor releaseDescriptor = new ReleaseDescriptor();
        releaseDescriptor.setScmSourceUrl( "scm-url" );
        File checkoutDirectory = getTestFile( "target/checkout-directory" );
        releaseDescriptor.setCheckoutDirectory( checkoutDirectory.getAbsolutePath() );

        Mock mock = new Mock( MavenExecutor.class );
        Constraint[] constraints = new Constraint[]{new IsSame( checkoutDirectory ), new IsEqual( "goal1 goal2" ),
            new IsEqual( Boolean.TRUE ), new IsEqual( "-DperformRelease=true" ), new IsNull(), new IsAnything()};
        mock.expects( new InvokeOnceMatcher() ).method( "executeGoals" ).with( constraints );
        RunPerformGoalsPhase phase = (RunPerformGoalsPhase) lookup( ReleasePhase.ROLE, "run-perform-goals" );
        phase.setMavenExecutor( (MavenExecutor) mock.proxy() );

        Mock scmProviderMock = new Mock( ScmProvider.class );
        constraints = new Constraint[]{new IsAnything(), new IsScmFileSetEquals( new ScmFileSet( checkoutDirectory ) ),
            new IsInstanceOf( ScmTag.class )};
        scmProviderMock.expects( new InvokeOnceMatcher() ).method( "checkOut" ).with( constraints ).will(
            new ReturnStub( new CheckOutScmResult( "...", Collections.<ScmFile>emptyList() ) ) );

        ScmManagerStub stub = (ScmManagerStub) lookup( ScmManager.ROLE );
        stub.setScmProvider( (ScmProvider) scmProviderMock.proxy() );

        releaseManager.perform( releaseDescriptor, new DefaultReleaseEnvironment(), createReactorProjects() );

        assertTrue( true );
    }

    private static List<MavenProject> createReactorProjects()
    {
        MavenProject project = new MavenProject();
        project.setFile( getTestFile( "target/dummy-project/pom.xml" ) );
        return Collections.singletonList( project );
    }

    public void testReleasePerformWithResult() 
        throws Exception
    {
        DefaultReleaseManager releaseManager = (DefaultReleaseManager) lookup( ReleaseManager.ROLE, "test" );
        
        ReleaseDescriptor releaseDescriptor = new ReleaseDescriptor();
        releaseDescriptor.setScmSourceUrl( "scm-url" );
        File checkoutDirectory = getTestFile( "target/checkout-directory" );
        releaseDescriptor.setCheckoutDirectory( checkoutDirectory.getAbsolutePath() );

        ReleaseResult result = releaseManager.performWithResult( releaseDescriptor, new DefaultReleaseEnvironment(),
                                                                 createReactorProjects(), null );
        assert( result.getOutput().length() > 0 );
    }  

    public void testReleasePerformNoReleaseProfile()
        throws Exception
    {
        DefaultReleaseManager releaseManager = (DefaultReleaseManager) lookup( ReleaseManager.ROLE, "test" );

        ReleaseDescriptor releaseDescriptor = new ReleaseDescriptor();
        releaseDescriptor.setScmSourceUrl( "scm-url" );
        File checkoutDirectory = getTestFile( "target/checkout-directory" );
        releaseDescriptor.setCheckoutDirectory( checkoutDirectory.getAbsolutePath() );

        Mock mock = new Mock( MavenExecutor.class );
        Constraint[] constraints = new Constraint[]{new IsSame( checkoutDirectory ), new IsEqual( "goal1 goal2" ),
            new IsEqual( Boolean.TRUE ), new IsNull(), new IsNull(), new IsAnything()};
        mock.expects( new InvokeOnceMatcher() ).method( "executeGoals" ).with( constraints );
        RunPerformGoalsPhase phase = (RunPerformGoalsPhase) lookup( ReleasePhase.ROLE, "run-perform-goals" );
        phase.setMavenExecutor( (MavenExecutor) mock.proxy() );

        Mock scmProviderMock = new Mock( ScmProvider.class );
        constraints = new Constraint[]{new IsAnything(), new IsScmFileSetEquals( new ScmFileSet( checkoutDirectory ) ),
            new IsInstanceOf( ScmTag.class )};
        scmProviderMock.expects( new InvokeOnceMatcher() ).method( "checkOut" ).with( constraints ).will(
            new ReturnStub( new CheckOutScmResult( "...", Collections.<ScmFile>emptyList() ) ) );

        ScmManagerStub stub = (ScmManagerStub) lookup( ScmManager.ROLE );
        stub.setScmProvider( (ScmProvider) scmProviderMock.proxy() );

        releaseDescriptor.setUseReleaseProfile( false );

        releaseManager.perform( releaseDescriptor, new DefaultReleaseEnvironment(), createReactorProjects() );

        assertTrue( true );
    }

    public void testReleasePerformWithArguments()
        throws Exception
    {
        DefaultReleaseManager releaseManager = (DefaultReleaseManager) lookup( ReleaseManager.ROLE, "test" );

        ReleaseDescriptor releaseDescriptor = new ReleaseDescriptor();
        releaseDescriptor.setScmSourceUrl( "scm-url" );
        releaseDescriptor.setAdditionalArguments( "-Dmaven.test.skip=true" );
        File checkoutDirectory = getTestFile( "target/checkout-directory" );
        releaseDescriptor.setCheckoutDirectory( checkoutDirectory.getAbsolutePath() );

        Mock mock = new Mock( MavenExecutor.class );
        Constraint[] constraints = new Constraint[]{new IsSame( checkoutDirectory ), new IsEqual( "goal1 goal2" ),
            new IsEqual( Boolean.TRUE ), new IsEqual( "-Dmaven.test.skip=true -DperformRelease=true" ), new IsNull(),
            new IsAnything()};
        mock.expects( new InvokeOnceMatcher() ).method( "executeGoals" ).with( constraints );
        RunPerformGoalsPhase phase = (RunPerformGoalsPhase) lookup( ReleasePhase.ROLE, "run-perform-goals" );
        phase.setMavenExecutor( (MavenExecutor) mock.proxy() );

        Mock scmProviderMock = new Mock( ScmProvider.class );
        constraints = new Constraint[]{new IsAnything(), new IsScmFileSetEquals( new ScmFileSet( checkoutDirectory ) ),
            new IsInstanceOf( ScmTag.class )};
        scmProviderMock.expects( new InvokeOnceMatcher() ).method( "checkOut" ).with( constraints ).will(
            new ReturnStub( new CheckOutScmResult( "...", Collections.<ScmFile>emptyList() ) ) );

        ScmManagerStub stub = (ScmManagerStub) lookup( ScmManager.ROLE );
        stub.setScmProvider( (ScmProvider) scmProviderMock.proxy() );

        releaseManager.perform( releaseDescriptor, new DefaultReleaseEnvironment(), createReactorProjects() );

        assertTrue( true );
    }

    public void testReleasePerformWithArgumentsNoReleaseProfile()
        throws Exception
    {
        DefaultReleaseManager releaseManager = (DefaultReleaseManager) lookup( ReleaseManager.ROLE, "test" );

        ReleaseDescriptor releaseDescriptor = new ReleaseDescriptor();
        releaseDescriptor.setScmSourceUrl( "scm-url" );
        releaseDescriptor.setAdditionalArguments( "-Dmaven.test.skip=true" );
        File checkoutDirectory = getTestFile( "target/checkout-directory" );
        releaseDescriptor.setCheckoutDirectory( checkoutDirectory.getAbsolutePath() );

        Mock mock = new Mock( MavenExecutor.class );
        Constraint[] constraints = new Constraint[]{new IsSame( checkoutDirectory ), new IsEqual( "goal1 goal2" ),
            new IsEqual( Boolean.TRUE ), new IsEqual( "-Dmaven.test.skip=true" ), new IsNull(), new IsAnything()};
        mock.expects( new InvokeOnceMatcher() ).method( "executeGoals" ).with( constraints );
        RunPerformGoalsPhase phase = (RunPerformGoalsPhase) lookup( ReleasePhase.ROLE, "run-perform-goals" );
        phase.setMavenExecutor( (MavenExecutor) mock.proxy() );

        Mock scmProviderMock = new Mock( ScmProvider.class );
        constraints = new Constraint[]{new IsAnything(), new IsScmFileSetEquals( new ScmFileSet( checkoutDirectory ) ),
            new IsInstanceOf( ScmTag.class )};
        scmProviderMock.expects( new InvokeOnceMatcher() ).method( "checkOut" ).with( constraints ).will(
            new ReturnStub( new CheckOutScmResult( "...", Collections.<ScmFile>emptyList() ) ) );

        ScmManagerStub stub = (ScmManagerStub) lookup( ScmManager.ROLE );
        stub.setScmProvider( (ScmProvider) scmProviderMock.proxy() );

        releaseDescriptor.setUseReleaseProfile( false );

        releaseManager.perform( releaseDescriptor, new DefaultReleaseEnvironment(), createReactorProjects() );

        assertTrue( true );
    }

    public void testReleasePerformWithReleasePropertiesCompleted()
        throws Exception
    {
        DefaultReleaseManager releaseManager = (DefaultReleaseManager) lookup( ReleaseManager.ROLE, "test" );

        ReleaseDescriptor releaseDescriptor = new ReleaseDescriptor();
        releaseDescriptor.setScmSourceUrl( "scm-url" );
        File checkoutDirectory = getTestFile( "target/checkout-directory" );
        releaseDescriptor.setCheckoutDirectory( checkoutDirectory.getAbsolutePath() );

        Mock mock = new Mock( MavenExecutor.class );
        Constraint[] constraints = new Constraint[]{new IsSame( checkoutDirectory ), new IsEqual( "goal1 goal2" ),
            new IsEqual( Boolean.TRUE ), new IsEqual( "-DperformRelease=true" ), new IsNull(), new IsAnything()};
        mock.expects( new InvokeOnceMatcher() ).method( "executeGoals" ).with( constraints );
        RunPerformGoalsPhase phase = (RunPerformGoalsPhase) lookup( ReleasePhase.ROLE, "run-perform-goals" );
        phase.setMavenExecutor( (MavenExecutor) mock.proxy() );

        Mock scmProviderMock = new Mock( ScmProvider.class );
        constraints = new Constraint[]{new IsAnything(), new IsScmFileSetEquals( new ScmFileSet( checkoutDirectory ) ),
            new IsInstanceOf( ScmTag.class )};
        scmProviderMock.expects( new InvokeOnceMatcher() ).method( "checkOut" ).with( constraints ).will(
            new ReturnStub( new CheckOutScmResult( "...", Collections.<ScmFile>emptyList() ) ) );

        ScmManagerStub stub = (ScmManagerStub) lookup( ScmManager.ROLE );
        stub.setScmProvider( (ScmProvider) scmProviderMock.proxy() );

        ReleaseDescriptorStoreStub configStore = new ReleaseDescriptorStoreStub();
        configStore.getReleaseConfiguration().setCompletedPhase( "end-release" );
        releaseManager.setConfigStore( configStore );

        releaseManager.perform( releaseDescriptor, new DefaultReleaseEnvironment(), createReactorProjects() );

        assertTrue( true );
    }

    public void testReleaseConfigurationStoreReadFailureOnPerform()
        throws Exception
    {
        ReleaseDescriptor releaseDescriptor = new ReleaseDescriptor();
        releaseDescriptor.setScmSourceUrl( "scm-url" );
        releaseDescriptor.setWorkingDirectory( getTestFile( "target/working-directory" ).getAbsolutePath() );

        DefaultReleaseManager releaseManager = (DefaultReleaseManager) lookup( ReleaseManager.ROLE, "test" );

        Mock configStoreMock = new Mock( ReleaseDescriptorStore.class );
        configStoreMock.expects( new InvokeOnceMatcher() ).method( "read" ).with(
            new IsSame( releaseDescriptor ) ).will(
            new ThrowStub( new ReleaseDescriptorStoreException( "message", new IOException( "ioExceptionMsg" ) ) ) );

        releaseManager.setConfigStore( (ReleaseDescriptorStore) configStoreMock.proxy() );

        try
        {
            releaseDescriptor.setUseReleaseProfile( false );

            releaseManager.perform( releaseDescriptor, new DefaultReleaseEnvironment(), null );
            fail( "Should have failed to read configuration" );
        }
        catch ( ReleaseExecutionException e )
        {
            // good
            assertEquals( "check cause", ReleaseDescriptorStoreException.class, e.getCause().getClass() );
        }
    }

    public void testReleasePerformWithIncompletePrepare()
        throws Exception
    {
        ReleaseDescriptor releaseDescriptor = new ReleaseDescriptor();
        releaseDescriptor.setScmSourceUrl( "scm-url" );
        releaseDescriptor.setWorkingDirectory( getTestFile( "target/working-directory" ).getAbsolutePath() );

        DefaultReleaseManager releaseManager = (DefaultReleaseManager) lookup( ReleaseManager.ROLE, "test" );

        ReleaseDescriptorStoreStub configStore = new ReleaseDescriptorStoreStub();
        releaseDescriptor.setCompletedPhase( "scm-tag" );
        releaseManager.setConfigStore( configStore );

        try
        {
            releaseDescriptor.setUseReleaseProfile( false );

            releaseManager.perform( releaseDescriptor, new DefaultReleaseEnvironment(), null );
            fail( "Should have failed to perform" );
        }
        catch ( ReleaseFailureException e )
        {
            // good
            assertTrue( true );
        }
    }

    public void testNoScmUrlPerform()
        throws Exception
    {
        ReleaseDescriptor releaseDescriptor = new ReleaseDescriptor();
        releaseDescriptor.setWorkingDirectory( getTestFile( "target/test/checkout" ).getAbsolutePath() );

        DefaultReleaseManager releaseManager = (DefaultReleaseManager) lookup( ReleaseManager.ROLE, "test" );

        try
        {
            releaseDescriptor.setUseReleaseProfile( false );

            releaseManager.perform( releaseDescriptor, new DefaultReleaseEnvironment(), null );

            fail( "perform should have failed" );
        }
        catch ( ReleaseFailureException e )
        {
            assertNull( "check no cause", e.getCause() );
        }
    }

    public void testNoSuchScmProviderExceptionThrown()
        throws Exception
    {
        ReleaseDescriptor releaseDescriptor = new ReleaseDescriptor();
        releaseDescriptor.setScmSourceUrl( "scm-url" );
        releaseDescriptor.setWorkingDirectory( getTestFile( "target/test/checkout" ).getAbsolutePath() );

        Mock scmManagerMock = new Mock( ScmManager.class );
        scmManagerMock.expects( new InvokeOnceMatcher() ).method( "makeScmRepository" ).with(
            new IsEqual( "scm-url" ) ).will( new ThrowStub( new NoSuchScmProviderException( "..." ) ) );

        ScmManager scmManager = (ScmManager) scmManagerMock.proxy();
        DefaultScmRepositoryConfigurator configurator =
            (DefaultScmRepositoryConfigurator) lookup( ScmRepositoryConfigurator.ROLE );
        configurator.setScmManager( scmManager );

        DefaultReleaseManager releaseManager = (DefaultReleaseManager) lookup( ReleaseManager.ROLE, "test" );

        try
        {
            releaseDescriptor.setUseReleaseProfile( false );

            releaseManager.perform( releaseDescriptor, new DefaultReleaseEnvironment(), createReactorProjects() );

            fail( "commit should have failed" );
        }
        catch ( ReleaseExecutionException e )
        {
            assertEquals( "check cause", NoSuchScmProviderException.class, e.getCause().getClass() );
        }
    }

    public void testScmRepositoryExceptionThrown()
        throws Exception
    {
        ReleaseDescriptor releaseDescriptor = new ReleaseDescriptor();
        releaseDescriptor.setScmSourceUrl( "scm-url" );
        releaseDescriptor.setWorkingDirectory( getTestFile( "target/test/checkout" ).getAbsolutePath() );

        Mock scmManagerMock = new Mock( ScmManager.class );
        scmManagerMock.expects( new InvokeOnceMatcher() ).method( "makeScmRepository" ).with(
            new IsEqual( "scm-url" ) ).will( new ThrowStub( new ScmRepositoryException( "..." ) ) );

        ScmManager scmManager = (ScmManager) scmManagerMock.proxy();
        DefaultScmRepositoryConfigurator configurator =
            (DefaultScmRepositoryConfigurator) lookup( ScmRepositoryConfigurator.ROLE );
        configurator.setScmManager( scmManager );

        DefaultReleaseManager releaseManager = (DefaultReleaseManager) lookup( ReleaseManager.ROLE, "test" );

        try
        {
            releaseDescriptor.setUseReleaseProfile( false );

            releaseManager.perform( releaseDescriptor, new DefaultReleaseEnvironment(), createReactorProjects() );

            fail( "commit should have failed" );
        }
        catch ( ReleaseScmRepositoryException e )
        {
            assertNull( "Check no additional cause", e.getCause() );
        }
    }

    public void testScmExceptionThrown()
        throws Exception
    {
        DefaultReleaseManager releaseManager = (DefaultReleaseManager) lookup( ReleaseManager.ROLE, "test" );

        ReleaseDescriptor releaseDescriptor = new ReleaseDescriptor();
        releaseDescriptor.setScmSourceUrl( "scm-url" );
        File checkoutDirectory = getTestFile( "target/checkout-directory" );
        releaseDescriptor.setCheckoutDirectory( checkoutDirectory.getAbsolutePath() );

        Mock scmProviderMock = new Mock( ScmProvider.class );
        scmProviderMock.expects( new InvokeOnceMatcher() ).method( "checkOut" ).will(
            new ThrowStub( new ScmException( "..." ) ) );

        ScmManagerStub stub = (ScmManagerStub) lookup( ScmManager.ROLE );
        stub.setScmProvider( (ScmProvider) scmProviderMock.proxy() );

        try
        {
            releaseManager.perform( releaseDescriptor, new DefaultReleaseEnvironment(), createReactorProjects() );

            fail( "commit should have failed" );
        }
        catch ( ReleaseExecutionException e )
        {
            assertEquals( "check cause", ScmException.class, e.getCause().getClass() );
        }
    }

    public void testScmResultFailure()
        throws Exception
    {
        DefaultReleaseManager releaseManager = (DefaultReleaseManager) lookup( ReleaseManager.ROLE, "test" );

        ReleaseDescriptor releaseDescriptor = new ReleaseDescriptor();
        releaseDescriptor.setScmSourceUrl( "scm-url" );
        File checkoutDirectory = getTestFile( "target/checkout-directory" );
        releaseDescriptor.setCheckoutDirectory( checkoutDirectory.getAbsolutePath() );

        ScmManager scmManager = (ScmManager) lookup( ScmManager.ROLE );
        ScmProviderStub providerStub =
            (ScmProviderStub) scmManager.getProviderByUrl( releaseDescriptor.getScmSourceUrl() );

        providerStub.setCheckOutScmResult( new CheckOutScmResult( "", "", "", false ) );

        try
        {
            releaseManager.perform( releaseDescriptor, new DefaultReleaseEnvironment(), createReactorProjects() );

            fail( "commit should have failed" );
        }
        catch ( ReleaseScmCommandException e )
        {
            assertNull( "check no other cause", e.getCause() );
        }
    }

    public void testDetermineWorkingDirectory()
        throws Exception
    {
        DefaultReleaseManager defaultReleaseManager = new DefaultReleaseManager();

        File checkoutDir = getTestFile( "target/checkout" );
        FileUtils.forceDelete( checkoutDir );
        checkoutDir.mkdirs();

        File projectDir = getTestFile( "target/checkout/my/project" );
        projectDir.mkdirs();

        // only checkout dir
        assertEquals( checkoutDir, defaultReleaseManager.determineWorkingDirectory( checkoutDir, "" ) );
        assertEquals( checkoutDir, defaultReleaseManager.determineWorkingDirectory( checkoutDir, null ) );

        // checkout dir and relative path project dir
        assertEquals( projectDir, defaultReleaseManager.determineWorkingDirectory( checkoutDir, "my/project" ) );
        assertEquals( projectDir, defaultReleaseManager.determineWorkingDirectory( checkoutDir, "my/project/" ) );
        assertEquals( projectDir, defaultReleaseManager.determineWorkingDirectory( checkoutDir, "my" + File.separator +
            "project" ) );

        FileUtils.forceDelete( checkoutDir );
    }
}
