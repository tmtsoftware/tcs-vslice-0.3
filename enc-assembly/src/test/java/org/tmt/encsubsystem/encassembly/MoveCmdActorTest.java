package org.tmt.encsubsystem.encassembly;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.typed.ActorRef;
import csw.command.api.javadsl.ICommandService;
import csw.command.client.CommandResponseManager;
import csw.framework.models.JCswContext;
import csw.logging.javadsl.JLoggerFactory;
import csw.params.commands.CommandResponse;
import csw.params.commands.ControlCommand;
import csw.params.commands.Setup;
import csw.params.core.models.Id;
import org.junit.*;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MoveCmdActorTest {
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
    ActorRef<ControlCommand> moveCmdActor;

    @Before
    public void setUp() throws Exception {
        jLoggerFactory = new JLoggerFactory("enc-test-logger");
        moveCmdActor = testKit.spawn(MoveCmdActor.behavior(cswCtx, Optional.of(hcdCommandService)));
    }

    @After
    public void tearDown() throws Exception {
    }

    /**
     * given the Assembly is running,
     * when valid move command is send to command worker actor
     * then worker actor create and submit sub command to HCD,
     * join sub command with actual command
     * and update command response in command response manager.
     */

    @Test
    public void startupCommandCompletion() throws InterruptedException {
        Id responseId = new Id("");
        Setup moveCommand = TestConstants.moveCommand();
        when(hcdCommandService.submit(any(), any())).thenReturn(CompletableFuture.completedFuture(new CommandResponse.Completed(responseId)));
        moveCmdActor.tell(moveCommand);
        Thread.sleep(TestConstants.ACTOR_MESSAGE_PROCESSING_DELAY);
        verify(commandResponseManager).addSubCommand(moveCommand.runId(), responseId);
        verify(commandResponseManager).updateSubCommand(new CommandResponse.Completed(responseId));
    }
}