package org.tmt.encsubsystem.enchcd;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import csw.framework.models.JCswContext;
import csw.logging.javadsl.ILogger;
import csw.params.commands.CommandResponse;
import csw.params.commands.ControlCommand;
import csw.params.core.models.Prefix;
import org.tmt.encsubsystem.enchcd.models.ShutdownCommand;
import org.tmt.encsubsystem.enchcd.simplesimulator.SimpleSimulator;


public class JShutdownCmdActor extends AbstractBehavior<ControlCommand> {


    private Prefix templateHcdPrefix = new Prefix("tcs.encA");

    private ActorContext<ControlCommand> actorContext;JCswContext cswCtx;
    ;
    private ILogger log;

    ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor;


    private JShutdownCmdActor(ActorContext<ControlCommand> actorContext, JCswContext cswCtx, ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor ) {
        this.actorContext = actorContext;this.cswCtx = cswCtx;

          this.log = cswCtx.loggerFactory().getLogger(JShutdownCmdActor.class);

        this.statePublisherActor = statePublisherActor;

    }

    public static <ControlCommand> Behavior<ControlCommand> behavior(JCswContext cswCtx, ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor ) {
        return Behaviors.setup(ctx -> {
            return (AbstractBehavior<ControlCommand>) new JShutdownCmdActor((ActorContext<csw.params.commands.ControlCommand>) ctx, cswCtx,  statePublisherActor
                    );
        });
    }

    /**
     * This method receives messages sent to actor.
     * based on message type it forward message to its dedicated handler method.
     * @return
     */
    @Override
    public Receive<ControlCommand> createReceive() {

        ReceiveBuilder<ControlCommand> builder = receiveBuilder()
                .onMessage(ControlCommand.class,
                        command -> {
                            log.debug(() -> "Shutdown Received");
                            handleShutdownCommand(command);
                            return Behaviors.stopped();
                        });
        return builder.build();
    }

    private void handleShutdownCommand(ControlCommand controlCommand) {
        log.debug(() -> "HCD handling shutdown command = " + controlCommand);
        ShutdownCommand.Response response = SimpleSimulator.getInstance().sendCommand(new ShutdownCommand());
        switch (response.getStatus()){
            case OK:
                this.cswCtx.commandResponseManager().addOrUpdateCommand( new CommandResponse.Completed(controlCommand.runId()));
                statePublisherActor.tell(new JStatePublisherActor.UnInitializedMessage());
                break;
            case ERROR:
                this.cswCtx.commandResponseManager().addOrUpdateCommand( new CommandResponse.Error(controlCommand.runId(), response.getDesc()));
        }
    }


}
