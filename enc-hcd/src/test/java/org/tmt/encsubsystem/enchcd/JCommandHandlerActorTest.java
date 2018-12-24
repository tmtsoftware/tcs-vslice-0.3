package org.tmt.encsubsystem.enchcd;

import akka.actor.testkit.typed.javadsl.BehaviorTestKit;
import akka.actor.testkit.typed.javadsl.TestInbox;
import akka.actor.typed.javadsl.ActorContext;
import csw.command.client.CommandResponseManager;
import csw.framework.models.JCswContext;
import csw.logging.javadsl.ILogger;
import csw.logging.javadsl.JLoggerFactory;
import csw.params.commands.CommandName;
import csw.params.commands.ControlCommand;
import csw.params.commands.Setup;
import csw.params.core.models.Prefix;
import csw.params.javadsl.JKeyType;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Optional;

import static org.mockito.Mockito.*;

public class JCommandHandlerActorTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();
    @Mock
    CommandResponseManager commandResponseManager;
    @Mock
    JCswContext cswCtx;
    @Mock
    JLoggerFactory jLoggerFactory;
    @Mock
    ILogger logger;


    TestInbox<JStatePublisherActor.StatePublisherMessage> statePublisherActorInbox;
    BehaviorTestKit<JCommandHandlerActor.CommandMessage> commandHandlerBehaviourKit;

    TestInbox<JCommandHandlerActor.ImmediateResponseMessage> replyTo;

    @Before
    public void setUp() throws Exception {
        when(jLoggerFactory.getLogger(isA(ActorContext.class), any())).thenReturn(logger);
        statePublisherActorInbox = TestInbox.create();
        replyTo = TestInbox.create();
        commandHandlerBehaviourKit = BehaviorTestKit.create(JCommandHandlerActor.behavior(cswCtx, statePublisherActorInbox.getRef()));
    }

    @After
    public void tearDown() throws Exception {
    }

    /**
     * given HCD is initialized,
     * when startup command as message is send to LifecycleActor,
     * then one Command Worker Actor (JStartUpCmdActor) should be created
     * and command should be send to newly created actor to process.
     */
    @Test
    public void handleStartupCommandTest() throws InterruptedException {
        Setup startupCmd = new Setup(new Prefix("enc.enc-test"), new CommandName("startup"), Optional.empty());
        commandHandlerBehaviourKit.run(new JCommandHandlerActor.SubmitCommandMessage(startupCmd));
        TestInbox<ControlCommand> commandWorkerActorInbox = commandHandlerBehaviourKit.childInbox("$a");
        TestInbox<ControlCommand> controlCommandTestInbox = commandWorkerActorInbox.expectMessage(startupCmd);
    }

    /**
     * given HCD is initialized,
     * when shutdown command as message is send to LifecycleActor,
     * then one Command Worker Actor (JShutdownCmdActor) should be created
     * and command should be send to newly created actor to process.
     */
    @Test
    public void handleShutdownCommandTest() {
        Setup shutdownCmd = new Setup(new Prefix("enc.enc-test"), new CommandName("shutdown"), Optional.empty());
        commandHandlerBehaviourKit.run(new JCommandHandlerActor.SubmitCommandMessage(shutdownCmd));
        TestInbox<ControlCommand> commandWorkerActorInbox = commandHandlerBehaviourKit.childInbox("$a");
        TestInbox<ControlCommand> controlCommandTestInbox = commandWorkerActorInbox.expectMessage(shutdownCmd);
    }

    /**
     * given HCD is running,
     * when fastMove command as message is send to CommandHandlerActor,
     * then one Command Worker Actor (JFastMoveCmdActor) should be created
     * and command should be send to newly created actor to process.
     */
    @Test
    public void handleFastMoveCommandTest() throws InterruptedException {

        Setup fastMoveSetupCmd = new Setup(new Prefix("enc.enc-test"), new CommandName("fastMove"), Optional.empty())
                .add(JKeyType.DoubleKey().make("az").set(2.64))
                .add(JKeyType.DoubleKey().make("el").set(5.34))
                .add(JKeyType.StringKey().make("mode").set("fast"))
                .add(JKeyType.StringKey().make("operation").set("On"));
        commandHandlerBehaviourKit.run(new JCommandHandlerActor.SubmitCommandMessage(fastMoveSetupCmd));
        //   commandHandlerBehaviourKit.expectEffect(Effects.spawnedAnonymous(JFastMoveCmdActor.behavior(jLoggerFactory, statePublisherActorInbox.getRef()),Props.empty()));
        TestInbox<ControlCommand> commandWorkerActorInbox = commandHandlerBehaviourKit.childInbox("$a");
        TestInbox<ControlCommand> controlCommandTestInbox = commandWorkerActorInbox.expectMessage(fastMoveSetupCmd);

    }

    /**
     * given HCD is running,
     * when trackOff command as message is send to CommandHandlerActor,
     * then one Command Worker Actor (JTrackOffCmdActor) should be created
     * and command should be send to newly created actor to process.
     */
    @Test
    public void handleTrackOffCommandTest() throws InterruptedException {

        Setup trackOffCommand = new Setup(new Prefix("enc.enc-test"), new CommandName("trackOff"), Optional.empty())
                .add(JKeyType.StringKey().make("operation").set("Off"));
        commandHandlerBehaviourKit.run(new JCommandHandlerActor.SubmitCommandMessage(trackOffCommand));
        TestInbox<ControlCommand> commandWorkerActorInbox = commandHandlerBehaviourKit.childInbox("$a");
        TestInbox<ControlCommand> controlCommandTestInbox = commandWorkerActorInbox.expectMessage(trackOffCommand);

    }

    /**
     * given HCD is running,
     * when follow command as message is send to CommandHandlerActor,
     * then one Command Worker Actor (JFollowCmdActor) should be created
     * and command should be send to newly created actor to process.
     */
    @Test
    public void handleFollowCommandTest() throws InterruptedException {
        Setup followCommand = new Setup(new Prefix("enc.enc-test"), new CommandName("follow"), Optional.empty());
        JCommandHandlerActor.ImmediateCommandMessage message = new JCommandHandlerActor.ImmediateCommandMessage(followCommand, replyTo.getRef());
        commandHandlerBehaviourKit.run(message);
        TestInbox<JFollowCmdActor.FollowMessage> commandWorkerActorInbox = commandHandlerBehaviourKit.childInbox("$a");
        TestInbox<JFollowCmdActor.FollowMessage> controlCommandTestInbox = commandWorkerActorInbox.expectMessage(new JFollowCmdActor.FollowCommandMessage(message.controlCommand, message.replyTo));

    }
}