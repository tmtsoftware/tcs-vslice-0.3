package org.tmt.encsubsystem.encassembly;

import akka.actor.testkit.typed.javadsl.BehaviorTestKit;
import akka.actor.testkit.typed.javadsl.TestInbox;
import csw.command.api.javadsl.ICommandService;
import csw.command.client.CommandResponseManager;
import csw.framework.models.JCswContext;
import csw.logging.javadsl.ILogger;
import csw.params.commands.CommandName;
import csw.params.commands.ControlCommand;
import csw.params.commands.Setup;
import csw.params.core.models.Prefix;
import csw.params.javadsl.JKeyType;
import csw.params.javadsl.JUnits;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Optional;

public class JCommandHandlerActorTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();
    @Mock
    CommandResponseManager commandResponseManager;

    @Mock
    JCswContext cswCtx;
    @Mock
    ILogger logger;

    @Mock
    ICommandService hcdCommandService;

    TestInbox<JMonitorActor.MonitorMessage> monitorActor;


    BehaviorTestKit<JCommandHandlerActor.CommandMessage> commandHandlerBehaviourKit;

    TestInbox<JCommandHandlerActor.ImmediateResponseMessage> replyTo;

    @Before
    public void setUp() throws Exception {
      //  when(jLoggerFactory.getLogger(isA(ActorContext.class), any())).thenReturn(logger);
        replyTo = TestInbox.create();
        monitorActor= TestInbox.create();
        commandHandlerBehaviourKit = BehaviorTestKit.create(JCommandHandlerActor.behavior(cswCtx, Optional.of(hcdCommandService), true, Optional.empty(), monitorActor.getRef()));
    }

    @After
    public void tearDown() throws Exception {
    }

    /**
     * given assembly is initialized,
     * when startup command as message is send to CommandHandlerActor,
     * then one Command Worker Actor (JStartUpCmdActor) should be created
     * and command should be send to newly created actor to process.
     */
    @Test
    public void handleStartupCommandTest() {
        Setup startupCmd = new Setup(new Prefix("enc.enc-test"), new CommandName("startup"), Optional.empty());
        commandHandlerBehaviourKit.run(new JCommandHandlerActor.SubmitCommandMessage(startupCmd));
        TestInbox<ControlCommand> commandWorkerActorInbox = commandHandlerBehaviourKit.childInbox("$a");
        TestInbox<ControlCommand> controlCommandTestInbox = commandWorkerActorInbox.expectMessage(startupCmd);


    }

    /**
     * given assembly is initialized,
     * when shutdown command as message is send to CommandHandlerActor,
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
     * given Assembly is running,
     * when move command as message is send to CommandHandlerActor,
     * then one Command Worker Actor (MoveCmdActor) should be created
     * and command should be send to newly created actor to process.
     */
    @Test
    public void handleMoveCommandTest() {
        Long[] timeValue = new Long[1];
        timeValue[0] = 10L;
        Setup moveSetupCmd = new Setup(new Prefix("enc.enc-test"), new CommandName("move"), Optional.empty())
                .add(JKeyType.DoubleKey().make("az").set(2.64))
                .add(JKeyType.DoubleKey().make("el").set(5.34))
                .add(JKeyType.StringKey().make("mode").set("fast"))
                .add(JKeyType.StringKey().make("operation").set("On"))
                .add(JKeyType.LongKey().make("timeDuration").set(timeValue, JUnits.second));
        commandHandlerBehaviourKit.run(new JCommandHandlerActor.SubmitCommandMessage(moveSetupCmd));
        //   commandHandlerBehaviourKit.expectEffect(Effects.spawnedAnonymous(JFastMoveCmdActor.behavior(commandResponseManager,jLoggerFactory, statePublisherActorInbox.getRef()),Props.empty()));
        TestInbox<ControlCommand> commandWorkerActorInbox = commandHandlerBehaviourKit.childInbox("$a");
        TestInbox<ControlCommand> controlCommandTestInbox = commandWorkerActorInbox.expectMessage(moveSetupCmd);

    }


    /**
     * given Assembly is running,
     * when follow command as message is send to CommandHandlerActor,
     * then one Command Worker Actor (JFollowCmdActor) should be created
     * and command should be send to newly created actor to process.
     */
    @Test
    public void handleFollowCommandTest() {
        Setup followCommand = new Setup(new Prefix("enc.enc-test"), new CommandName("follow"), Optional.empty());
        JCommandHandlerActor.ImmediateCommandMessage message = new JCommandHandlerActor.ImmediateCommandMessage(followCommand, replyTo.getRef());
        commandHandlerBehaviourKit.run(message);
        TestInbox<JFollowCmdActor.FollowMessage> commandWorkerActorInbox = commandHandlerBehaviourKit.childInbox("$a");
        TestInbox<JFollowCmdActor.FollowMessage> controlCommandTestInbox = commandWorkerActorInbox.expectMessage(new JFollowCmdActor.FollowCommandMessage(message.controlCommand, message.replyTo));

    }
}