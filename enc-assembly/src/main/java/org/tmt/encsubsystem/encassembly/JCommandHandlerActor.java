package org.tmt.encsubsystem.encassembly;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import com.typesafe.config.Config;
import csw.command.api.javadsl.ICommandService;
import csw.framework.models.JCswContext;
import csw.logging.javadsl.ILogger;
import csw.params.commands.CommandResponse;
import csw.params.commands.ControlCommand;

import java.util.Optional;

/**
 * This is a typed mutable actor class
 * This class acts as a router for commands it routes each command to individual
 * CommandWorkerActor
 */
public class JCommandHandlerActor extends AbstractBehavior<JCommandHandlerActor.CommandMessage> {


    // add messages here
    interface CommandMessage {
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

    public static final class ImmediateCommandMessage implements CommandMessage {

        public final ControlCommand controlCommand;
        public final ActorRef<ImmediateResponseMessage> replyTo;


        public ImmediateCommandMessage(ControlCommand controlCommand, ActorRef<ImmediateResponseMessage> replyTo) {
            this.controlCommand = controlCommand;
            this.replyTo = replyTo;
        }
    }

    public static final class SubmitCommandMessage implements CommandMessage {

        public final ControlCommand controlCommand;


        public SubmitCommandMessage(ControlCommand controlCommand) {
            this.controlCommand = controlCommand;
        }
    }

    public static final class GoOnlineMessage implements CommandMessage {
    }

    public static final class GoOfflineMessage implements CommandMessage {
    }

    public static final class UpdateTemplateHcdMessage implements CommandMessage {

        public final Optional<ICommandService> commandServiceOptional;

        public UpdateTemplateHcdMessage(Optional<ICommandService> commandServiceOptional) {
            this.commandServiceOptional = commandServiceOptional;
        }
    }

    public static final class UpdateConfigMessage implements CommandMessage {
        public final Optional<Config> assemblyConfig;

        public UpdateConfigMessage(Optional<Config> assemblyConfig) {
            this.assemblyConfig = assemblyConfig;
        }
    }

    private ActorContext<CommandMessage> actorContext;
    JCswContext cswCtx;
    private ILogger log;
    private Boolean online;
    private Optional<ICommandService> hcdCommandService;
    // assembly configuration recevied from lifecycle actor for use in command, event etc.
    private Optional<Config> assemblyConfig;
    ActorRef<JMonitorActor.MonitorMessage> monitorActor;

    private JCommandHandlerActor(ActorContext<CommandMessage> actorContext,JCswContext cswCtx, Optional<ICommandService> hcdCommandService, Boolean online, Optional<Config> assemblyConfig, ActorRef<JMonitorActor.MonitorMessage> monitorActor) {
        this.actorContext = actorContext;
        this.cswCtx = cswCtx;
        this.log = cswCtx.loggerFactory().getLogger(JCommandHandlerActor.class);
        this.online = online;
        this.hcdCommandService = hcdCommandService;
        this.assemblyConfig = assemblyConfig;
        this.monitorActor = monitorActor;
    }

    public static <CommandMessage> Behavior<CommandMessage> behavior(JCswContext cswCtx, Optional<ICommandService> hcdCommandService, Boolean online,  Optional<Config> assemblyConfig, ActorRef<JMonitorActor.MonitorMessage> monitorActor) {
        return Behaviors.setup(ctx -> {
            return (AbstractBehavior<CommandMessage>) new JCommandHandlerActor((ActorContext<JCommandHandlerActor.CommandMessage>) ctx,cswCtx, hcdCommandService, online, assemblyConfig, monitorActor);
        });
    }

    /**
      * This function creates individual command worker actor when command is received and delegates
      * working to it
     **/
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
                            log.debug(() -> "shutdown Received");
                            handleShutdownCommand(command.controlCommand);
                            return Behaviors.same();
                        })
                .onMessage(SubmitCommandMessage.class,
                        command -> command.controlCommand.commandName().name().equals("move"),
                        command -> {
                            log.debug(() -> "MoveMessage Received");
                            handleMoveCommand(command.controlCommand);
                            return Behaviors.same();
                        })
                .onMessage(ImmediateCommandMessage.class,
                        message -> message.controlCommand.commandName().name().equals("follow"),
                        message -> {
                            log.debug(() -> "Follow Message Received");
                            handleFollowCommand(message);
                            return Behaviors.same();
                        })
                .onMessage(SubmitCommandMessage.class,
                        message -> message.controlCommand.commandName().name().equals("assemblyTestCommand"),
                        message -> {
                            log.debug(() -> "assemblyTestCommand Received");
                            handleAssemblyTestCommand(message);
                            return Behaviors.same();
                        })
                .onMessage(SubmitCommandMessage.class,
                        message -> message.controlCommand.commandName().name().equals("hcdTestCommand"),
                        message -> {
                            log.debug(() -> "hcdTestCommand Received");
                            handleHcdTestCommand(message);
                            return Behaviors.same();
                        })
                .onMessage(UpdateTemplateHcdMessage.class,
                        command -> {
                            log.debug(() -> "UpdateTemplateHcdMessage Received");
                            // update the template hcd
                            return behavior(cswCtx, command.commandServiceOptional, online, assemblyConfig, monitorActor);
                        })
                .onMessage(UpdateConfigMessage.class,
                        updateConfigMessage -> {
                            log.debug(() -> "UpdateConfigMessage Received");

                            return behavior(cswCtx, hcdCommandService, online, updateConfigMessage.assemblyConfig, monitorActor);
                        })
                .onMessage(GoOnlineMessage.class,
                        command -> {
                            log.debug(() -> "GoOnlineMessage Received");
                            // change the behavior to online
                            return behavior(cswCtx, hcdCommandService, Boolean.TRUE, assemblyConfig, monitorActor);
                        })
                .onMessage(GoOfflineMessage.class,
                        command -> {
                            log.debug(() -> "GoOfflineMessage Received");
                            // change the behavior to online
                            return behavior(cswCtx, hcdCommandService, Boolean.FALSE, assemblyConfig, monitorActor);
                        });

        return builder.build();
    }

    /**
     * hcdTestCommand is dummy command created for performance testing of command which are processed and result is returned from hcd.
     * These kind of commands are not forwarded to subsystem.
     * @param message
     */
    private void handleHcdTestCommand(SubmitCommandMessage message) {

        log.debug(() -> "handle HcdTestCommand = " + message.controlCommand);
        ActorRef<ControlCommand> hcdTestCmdActor =
                actorContext.spawnAnonymous(JHcdTestCmdActor.behavior(cswCtx, hcdCommandService, monitorActor));

        hcdTestCmdActor.tell(message.controlCommand);
    }

    /**
     * assemblyTestCommand is dummy command created for performance testing of command which are processed and result is returned from assembly itself.
     * @param message
     */
    private void handleAssemblyTestCommand(SubmitCommandMessage message) {
        log.debug(() -> "handle AssemblyTestCommand = " + message.controlCommand);
        ActorRef<ControlCommand> assemblyTestCmdActor =
                actorContext.spawnAnonymous(JAssemblyTestCmdActor.behavior(cswCtx, hcdCommandService, monitorActor));

        assemblyTestCmdActor.tell(message.controlCommand);
    }

    /**
     * This method will received startup command and create worker actor to handle it.
     * command will be forwarded to worker actor.
     * @param controlCommand
     */
    private void handleStartupCommand(ControlCommand controlCommand) {

        log.debug(() -> "handle Startup Command = " + controlCommand);
        ActorRef<ControlCommand> startupCmdActor =
                actorContext.spawnAnonymous(JStartUpCmdActor.behavior(cswCtx, hcdCommandService, monitorActor));

        startupCmdActor.tell(controlCommand);
    }
    /**
     * This method will received shutdown command and create worker actor to handle it.
     * command will be forwarded to worker actor.
     * @param controlCommand
     */
    private void handleShutdownCommand(ControlCommand controlCommand) {

        log.debug(() -> "handle shutdown Command = " + controlCommand);
        ActorRef<ControlCommand> shutdownCmdActor =
                actorContext.spawnAnonymous(JShutdownCmdActor.behavior(cswCtx, hcdCommandService, monitorActor));

        shutdownCmdActor.tell(controlCommand);
    }
    /**
     * This method create worker actor and submit command to it.
     * @param controlCommand
     */
    private void handleMoveCommand(ControlCommand controlCommand) {

        log.debug(() -> "handleMoveCommand = " + controlCommand);

        if (online) {

            ActorRef<ControlCommand> moveCmdActor =
                    actorContext.spawnAnonymous(MoveCmdActor.behavior(cswCtx, hcdCommandService));

            moveCmdActor.tell(controlCommand);

        }
    }
    /**
     * This method create worker actor and submit command to it.
     * @param message
     */
    private void handleFollowCommand(ImmediateCommandMessage message) {
        log.debug(() -> "handleFollowCommand = " + message.controlCommand);
        if (online) {
            ActorRef<JFollowCmdActor.FollowMessage> followCmdActor = actorContext.spawnAnonymous(JFollowCmdActor.behavior(cswCtx, hcdCommandService));
            followCmdActor.tell(new JFollowCmdActor.FollowCommandMessage(message.controlCommand, message.replyTo));

        }
    }

}
