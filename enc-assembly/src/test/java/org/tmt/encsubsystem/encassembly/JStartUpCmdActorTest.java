package org.tmt.encsubsystem.encassembly;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import csw.command.api.javadsl.ICommandService;
import csw.command.client.CommandResponseManager;
import csw.framework.models.JCswContext;
import csw.logging.javadsl.JLoggerFactory;
import csw.params.commands.CommandName;
import csw.params.commands.CommandResponse;
import csw.params.commands.ControlCommand;
import csw.params.commands.Setup;
import csw.params.core.models.Prefix;
import org.junit.*;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JStartUpCmdActorTest {

    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource();

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    CommandResponseManager commandResponseManager;
    @Mock
    JCswContext cswCtx;
    @Mock
    ICommandService hcdCommandService;

    JLoggerFactory jLoggerFactory;
    ActorRef<ControlCommand> startUpCmdActor;
    TestProbe<JMonitorActor.MonitorMessage> monitorActor;

    @Before
    public void setUp() throws Exception {
        jLoggerFactory = new JLoggerFactory("enc-test-logger");
        monitorActor = testKit.createTestProbe();
        startUpCmdActor = testKit.spawn(JStartUpCmdActor.behavior(cswCtx, Optional.of(hcdCommandService),monitorActor.getRef()));
    }

    @After
    public void tearDown() throws Exception {
    }

    /**
     * given the Assembly is initialized, subsystem is initialized
     * when valid startup command is send to command worker actor
     * then worker actor submit command to HCD and update command response in command response manager.
     */

    @Test
    public void startupCommandCompletion() throws InterruptedException {

        Setup startupCmd = new Setup(new Prefix("enc.enc-test"), new CommandName("startup"), Optional.empty());
        when(hcdCommandService.submit(any(), any())).thenReturn(CompletableFuture.completedFuture(new CommandResponse.Completed(startupCmd.runId())));
        startUpCmdActor.tell(startupCmd);
        Thread.sleep(TestConstants.ACTOR_MESSAGE_PROCESSING_DELAY);
        verify(commandResponseManager).addOrUpdateCommand( new CommandResponse.Completed(startupCmd.runId()));
    }
}