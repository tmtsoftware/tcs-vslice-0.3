package org.tmt.encsubsystem.enchcd;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import csw.framework.models.JCswContext;
import csw.logging.javadsl.ILogger;
import csw.params.commands.CommandResponse;
import csw.params.commands.ControlCommand;
import csw.params.core.models.Prefix;
import org.tmt.encsubsystem.enchcd.models.StartupCommand;
import org.tmt.encsubsystem.enchcd.simplesimulator.SimpleSimulator;

public class JStartUpCmdActor extends AbstractBehavior<ControlCommand> {


    private Prefix templateHcdPrefix = new Prefix("tcs.encA");

    private ActorContext<ControlCommand> actorContext;
    JCswContext cswCtx;
    private ILogger log;

    ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor;


    private JStartUpCmdActor(ActorContext<ControlCommand> actorContext, JCswContext cswCtx,  ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor ) {
        this.actorContext = actorContext;this.cswCtx = cswCtx;

          this.log = cswCtx.loggerFactory().getLogger(JStartUpCmdActor.class);

        this.statePublisherActor = statePublisherActor;

    }

    public static <ControlCommand> Behavior<ControlCommand> behavior(JCswContext cswCtx, ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor ) {
        return Behaviors.setup(ctx -> {
            return (AbstractBehavior<ControlCommand>) new JStartUpCmdActor((ActorContext<csw.params.commands.ControlCommand>) ctx, cswCtx,  statePublisherActor
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
                            log.debug(() -> "Starup Received");
                            handleStartupCommand(command);
                            return Behaviors.stopped();
                        });
        return builder.build();
    }

    private void handleStartupCommand(ControlCommand controlCommand) {
        log.debug(() -> "HCD handling startup command = " + controlCommand);
            StartupCommand.Response response = SimpleSimulator.getInstance().sendCommand(new StartupCommand());
            switch (response.getStatus()){
                case OK:
                    this.cswCtx.commandResponseManager().addOrUpdateCommand( new CommandResponse.Completed(controlCommand.runId()));
                    statePublisherActor.tell(new JStatePublisherActor.InitializedMessage());
                    break;
                case ERROR:
                    this.cswCtx.commandResponseManager().addOrUpdateCommand( new CommandResponse.Error(controlCommand.runId(), response.getDesc()));
            }
    }


}
