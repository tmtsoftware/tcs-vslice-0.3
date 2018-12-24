package org.tmt.encsubsystem.encassembly;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import akka.util.Timeout;
import csw.command.api.javadsl.ICommandService;
import csw.framework.models.JCswContext;
import csw.logging.javadsl.ILogger;
import csw.params.commands.CommandResponse;
import csw.params.commands.ControlCommand;
import csw.params.core.models.Prefix;
import scala.concurrent.duration.FiniteDuration;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * This is a CommandWorkerActor for HcdTestCommand.
 * HcdTestCommand is a dummy command created for generating performance measures
 */
public class JHcdTestCmdActor extends AbstractBehavior<ControlCommand> {
    private Prefix templateHcdPrefix = new Prefix("tcs.encA");
    private ActorContext<ControlCommand> actorContext;JCswContext cswCtx;
    ;
    private ILogger log;

    private Optional<ICommandService> hcdCommandService;
    private ActorRef<JMonitorActor.MonitorMessage> monitorActor;


    private JHcdTestCmdActor(ActorContext<ControlCommand> actorContext, JCswContext cswCtx,  Optional<ICommandService> hcdCommandService,   ActorRef<JMonitorActor.MonitorMessage> monitorActor) {
        this.actorContext = actorContext;this.cswCtx = cswCtx;

          this.log = cswCtx.loggerFactory().getLogger(JHcdTestCmdActor.class);

        this.hcdCommandService = hcdCommandService;
        this.monitorActor = monitorActor;

    }

    public static <ControlCommand> Behavior<ControlCommand> behavior(JCswContext cswCtx, Optional<ICommandService> hcdCommandService, ActorRef<JMonitorActor.MonitorMessage> monitorActor) {
        return Behaviors.setup(ctx -> {
            return (AbstractBehavior<ControlCommand>) new JHcdTestCmdActor((ActorContext<csw.params.commands.ControlCommand>) ctx, cswCtx,  hcdCommandService,
                     monitorActor);
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
                            log.debug(() -> "HcdTestCommand Received");
                            handleSubmitCommand(command);
                            return Behaviors.stopped();// actor stops itself, it is meant to only process one command.
                        });
        return builder.build();
    }

    /**
     * This method process the command.
     * As this actor receives HcdTestCommand which is just a dummy command for performance testing, It will just forward command to HCD
     * @param controlCommand
     */
    private void handleSubmitCommand(ControlCommand controlCommand) {
        if (hcdCommandService.isPresent()) {
            log.debug(() -> "Submitting HcdTestCommand command from assembly to hcd");
            hcdCommandService.get()
                    .submit(
                            controlCommand,
                            Timeout.durationToTimeout(FiniteDuration.apply(5, TimeUnit.SECONDS))
                    ).thenAccept(response -> {
                log.debug(() -> "received response from hcd");
                this.cswCtx.commandResponseManager().addOrUpdateCommand( response);
                    });
        } else {
            this.cswCtx.commandResponseManager().addOrUpdateCommand( new CommandResponse.Error(controlCommand.runId(), "Can't locate HCD"));

        }
    }


}
