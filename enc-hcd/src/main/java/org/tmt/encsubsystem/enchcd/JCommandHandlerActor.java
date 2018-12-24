package org.tmt.encsubsystem.enchcd;


import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import csw.framework.models.JCswContext;
import csw.logging.javadsl.ILogger;
import csw.params.commands.CommandResponse;
import csw.params.commands.ControlCommand;

/**
 * This is a typed mutable actor class
 * This class acts as a router for commands it routes each command to individual
 * CommandWorkerActor,
 */
public class JCommandHandlerActor extends AbstractBehavior<JCommandHandlerActor.CommandMessage> {


    // add messages here
    interface CommandMessage {
    }

    public static final class SubmitCommandMessage implements CommandMessage {

        public final ControlCommand controlCommand;


        public SubmitCommandMessage(ControlCommand controlCommand) {
            this.controlCommand = controlCommand;
        }


    }

    public static final class ImmediateCommandMessage implements CommandMessage {

        public final ControlCommand controlCommand;
        public final ActorRef<ImmediateResponseMessage> replyTo;


        public ImmediateCommandMessage(ControlCommand controlCommand, ActorRef<ImmediateResponseMessage> replyTo) {
            this.controlCommand = controlCommand;
            this.replyTo = replyTo;
        }
    }

    public static final class ImmediateResponseMessage implements CommandMessage {
        public final CommandResponse.SubmitResponse commandResponse;

        public ImmediateResponseMessage(CommandResponse.SubmitResponse commandResponse) {
            this.commandResponse = commandResponse;
        }

        @Override
        public boolean equals(Object obj) {

            if (!(obj instanceof ImmediateResponseMessage)) {
                return false;
            }
            boolean isSame = commandResponse.equals(((ImmediateResponseMessage) obj).commandResponse);
            return isSame;
        }
    }


    private ActorContext<CommandMessage> actorContext;
    JCswContext cswCtx;
    ;
    private ILogger log;


    ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor;


    private JCommandHandlerActor(ActorContext<CommandMessage> actorContext, JCswContext cswCtx,    ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor) {
        this.actorContext = actorContext;
        this.cswCtx = cswCtx;

          this.log = cswCtx.loggerFactory().getLogger(JCommandHandlerActor.class);


        this.statePublisherActor = statePublisherActor;

    }

    public static <CommandMessage> Behavior<CommandMessage> behavior(JCswContext cswCtx,   ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor) {
        return Behaviors.setup(ctx -> {
            return (AbstractBehavior<CommandMessage>) new JCommandHandlerActor((ActorContext<JCommandHandlerActor.CommandMessage>) ctx, cswCtx,   statePublisherActor);
        });
    }

    /**
     * This method receives messages sent to actor.
     * based on message type it forward message to its dedicated handler method.
     * @return
     */
    @Override
    public Receive<CommandMessage> createReceive() {

        ReceiveBuilder<CommandMessage> builder = receiveBuilder()
                .onMessage(SubmitCommandMessage.class,
                        command -> command.controlCommand.commandName().name().equals("startup"),
                        command -> {
                            log.debug(() -> "StartUp Received");
                            handleStartupCommand(command.controlCommand);
                            return Behaviors.same();
                        })
                .onMessage(SubmitCommandMessage.class,
                        command -> command.controlCommand.commandName().name().equals("shutdown"),
                        command -> {
                            log.debug(() -> "Shutdown Received");
                            handleShutdownCommand(command.controlCommand);
                            return Behaviors.same();
                        })
                .onMessage(SubmitCommandMessage.class,
                        command -> command.controlCommand.commandName().name().equals("fastMove"),
                        command -> {
                            log.debug(() -> "FastMove Message Received");
                            handleFastMoveCommand(command.controlCommand);
                            return Behaviors.same();
                        })
                .onMessage(SubmitCommandMessage.class,
                        command -> command.controlCommand.commandName().name().equals("trackOff"),
                        command -> {
                            log.debug(() -> "trackOff Received");
                            handleTrackOffCommand(command.controlCommand);
                            return Behaviors.same();
                        })
                .onMessage(SubmitCommandMessage.class,
                        command -> command.controlCommand.commandName().name().equals("hcdTestCommand"),
                        command -> {
                            log.debug(() -> "hcdTestCommand Received");
                            handleHcdTestCommand(command.controlCommand);
                            return Behaviors.same();
                        })
                .onMessage(ImmediateCommandMessage.class,
                        message -> message.controlCommand.commandName().name().equals("follow"),
                        message -> {
                            log.debug(() -> "follow command received");
                            handleFollowCommand(message);
                            return Behaviors.same();
                        });
        return builder.build();
    }

    /**
     * This method create CommandWorkerActor for incoming command
     * @param controlCommand
     */
    private void handleHcdTestCommand(ControlCommand controlCommand) {
        log.debug(() -> "handleHcdTestCommand = " + controlCommand);
        ActorRef<ControlCommand> hcdTestCmdActor =
                actorContext.spawnAnonymous(JHcdTestCmdActor.behavior(cswCtx ));
        hcdTestCmdActor.tell(controlCommand);
    }
    /**
     * This method create CommandWorkerActor for incoming command
     * @param controlCommand
     */
    private void handleStartupCommand(ControlCommand controlCommand) {
        log.debug(() -> "handle Startup Command = " + controlCommand);
        ActorRef<ControlCommand> startupCmdActor =
                actorContext.spawnAnonymous(JStartUpCmdActor.behavior(cswCtx, statePublisherActor ));
        startupCmdActor.tell(controlCommand);
    }
    /**
     * This method create CommandWorkerActor for incoming command
     * @param controlCommand
     */
    private void handleShutdownCommand(ControlCommand controlCommand) {

        log.debug(() -> "handle shutdown Command = " + controlCommand);
        ActorRef<ControlCommand> shutdownCmdActor =
                actorContext.spawnAnonymous(JShutdownCmdActor.behavior(cswCtx, statePublisherActor ));

        shutdownCmdActor.tell(controlCommand);
    }
    /**
     * This method create CommandWorkerActor for incoming command
     * @param controlCommand
     */
    private void handleFastMoveCommand(ControlCommand controlCommand) {
        log.debug(() -> "HCD handling fastMove command = " + controlCommand);
        ActorRef<ControlCommand> fastMoveCmdActor =
                actorContext.spawnAnonymous(JFastMoveCmdActor.behavior(cswCtx,  statePublisherActor));
        fastMoveCmdActor.tell(controlCommand);


    }

    /**
     * This method create CommandWorkerActor for incoming command
     * @param controlCommand
     */
    private void handleTrackOffCommand(ControlCommand controlCommand) {
        log.debug(() -> "HCD handling trackOff command = " + controlCommand);
        ActorRef<ControlCommand> trackOffCmdActor =
                actorContext.spawnAnonymous(JTrackOffCmdActor.behavior(cswCtx ));
        trackOffCmdActor.tell(controlCommand);
    }
    /**
     * This method create CommandWorkerActor for incoming command
     * @param message
     */
    private void handleFollowCommand(ImmediateCommandMessage message) {
        log.debug(() -> "HCD handling follow command = " + message.controlCommand);
        ActorRef<JFollowCmdActor.FollowMessage> followCmdActor =
                actorContext.spawnAnonymous(JFollowCmdActor.behavior(cswCtx,  statePublisherActor));
        followCmdActor.tell(new JFollowCmdActor.FollowCommandMessage(message.controlCommand, message.replyTo));
    }


}
