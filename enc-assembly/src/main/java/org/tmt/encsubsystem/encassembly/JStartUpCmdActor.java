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


public class JStartUpCmdActor extends AbstractBehavior<ControlCommand> {


    private Prefix templateHcdPrefix = new Prefix("tcs.encA");

    private ActorContext<ControlCommand> actorContext;
    JCswContext cswCtx;

    private ILogger log;

    private Optional<ICommandService> hcdCommandService;
    private ActorRef<JMonitorActor.MonitorMessage> monitorActor;


    private JStartUpCmdActor(ActorContext<ControlCommand> actorContext, JCswContext cswCtx, Optional<ICommandService> hcdCommandService, ActorRef<JMonitorActor.MonitorMessage> monitorActor) {
        this.actorContext = actorContext;this.cswCtx = cswCtx;

          this.log = cswCtx.loggerFactory().getLogger(JStartUpCmdActor.class);

        this.hcdCommandService = hcdCommandService;
        this.monitorActor = monitorActor;

    }

    public static <ControlCommand> Behavior<ControlCommand> behavior(JCswContext cswCtx, Optional<ICommandService> hcdCommandService,   ActorRef<JMonitorActor.MonitorMessage> monitorActor) {
        return Behaviors.setup(ctx -> {
            return (AbstractBehavior<ControlCommand>) new JStartUpCmdActor((ActorContext<csw.params.commands.ControlCommand>) ctx, cswCtx,  hcdCommandService,
                     monitorActor);
        });
    }

    /**
     * This method receives messages sent to actor.
     * based on message type it forward message to its dedicated handler method.
     *
     * @return
     */
    @Override
    public Receive<ControlCommand> createReceive() {

        ReceiveBuilder<ControlCommand> builder = receiveBuilder()
                .onMessage(ControlCommand.class,
                        command -> {
                            log.debug(() -> "Startup Received");
                            handleSubmitCommand(command);
                            return Behaviors.stopped();// actor stops itself, it is meant to only process one command.
                        });
        return builder.build();
    }

    private void handleSubmitCommand(ControlCommand message) {

        if (hcdCommandService.isPresent()) {
            log.debug(() -> "Submitting startup command from assembly to hcd");
            hcdCommandService.get()
                    .submit(
                            message,
                            Timeout.durationToTimeout(FiniteDuration.apply(5, TimeUnit.SECONDS))
                    ).thenAccept(response -> {
                log.debug(() -> "received response from hcd");
                this.cswCtx.commandResponseManager().addOrUpdateCommand(response);
                monitorActor.tell(new JMonitorActor.InitializedMessage());
            });

        } else {
            //
            this.cswCtx.commandResponseManager().addOrUpdateCommand(new CommandResponse.Error(message.runId(), "Can't locate HCD"));

        }
    }


}
