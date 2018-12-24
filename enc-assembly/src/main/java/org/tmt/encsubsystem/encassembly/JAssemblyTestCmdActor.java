package org.tmt.encsubsystem.encassembly;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import csw.command.api.javadsl.ICommandService;
import csw.framework.models.JCswContext;
import csw.logging.javadsl.ILogger;
import csw.params.commands.CommandResponse;
import csw.params.commands.ControlCommand;
import csw.params.core.models.Prefix;

import java.util.Optional;

/**
 * This is a CommandWorkerActor for AssemblyTestCommand.
 * AssemblyTestCommand is a dummy command create for generating performance measures
 */
public class JAssemblyTestCmdActor extends AbstractBehavior<ControlCommand> {
    private Prefix templateHcdPrefix = new Prefix("tcs.encA");
    private ActorContext<ControlCommand> actorContext;
    JCswContext cswCtx;
    private ILogger log;

    private Optional<ICommandService> hcdCommandService;
    private ActorRef<JMonitorActor.MonitorMessage> monitorActor;


    private JAssemblyTestCmdActor(ActorContext<ControlCommand> actorContext, JCswContext cswCtx, Optional<ICommandService> hcdCommandService, ActorRef<JMonitorActor.MonitorMessage> monitorActor) {
        this.actorContext = actorContext;
        this.cswCtx = cswCtx;
        this.log = cswCtx.loggerFactory().getLogger(JAssemblyTestCmdActor.class);

        this.hcdCommandService = hcdCommandService;
        this.monitorActor = monitorActor;

    }

    public static <ControlCommand> Behavior<ControlCommand> behavior(JCswContext cswCtx, Optional<ICommandService> hcdCommandService,   ActorRef<JMonitorActor.MonitorMessage> monitorActor) {
        return Behaviors.setup(ctx -> {
            return (AbstractBehavior<ControlCommand>) new JAssemblyTestCmdActor((ActorContext<csw.params.commands.ControlCommand>) ctx, cswCtx, hcdCommandService, monitorActor);
        });
    }

    /**
     * This method receives messages sent to this worker actor.
     * @return
     */
    @Override
    public Receive<ControlCommand> createReceive() {

        ReceiveBuilder<ControlCommand> builder = receiveBuilder()
                .onMessage(ControlCommand.class,
                        command -> {
                            log.debug(() -> "AssemblyTestCommand Received");
                            handleSubmitCommand(command);
                            return Behaviors.stopped();// actor stops itself, it is meant to only process one command.
                        });
        return builder.build();
    }

    /**
     * This method process the command.
     * As this actor receives AssemblyTestCommand which is just a dummy command for performance testing, there is no processing required.
     * @param controlCommand
     */
    private void handleSubmitCommand(ControlCommand controlCommand) {
                this.cswCtx.commandResponseManager().addOrUpdateCommand(new CommandResponse.Completed(controlCommand.runId()));
    }


}
