package org.tmt.encsubsystem.enchcd;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import csw.framework.models.JCswContext;
import csw.logging.javadsl.ILogger;
import csw.params.commands.CommandResponse;
import csw.params.commands.ControlCommand;
import csw.params.core.generics.Parameter;
import org.tmt.encsubsystem.enchcd.models.FastMoveCommand;
import org.tmt.encsubsystem.enchcd.simplesimulator.SimpleSimulator;

public class JFastMoveCmdActor extends AbstractBehavior<ControlCommand> {

    private ActorContext<ControlCommand> actorContext;JCswContext cswCtx;
    ;
    private ILogger log;

    ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor;


    private JFastMoveCmdActor(ActorContext<ControlCommand> actorContext, JCswContext cswCtx, ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor) {
        this.actorContext = actorContext;this.cswCtx = cswCtx;

          this.log = cswCtx.loggerFactory().getLogger(JFastMoveCmdActor.class);

        this.statePublisherActor = statePublisherActor;
    }

    public static <ControlCommand> Behavior<ControlCommand> behavior(JCswContext cswCtx,   ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor) {
        return Behaviors.setup(ctx -> {
            return (AbstractBehavior<ControlCommand>) new JFastMoveCmdActor((ActorContext<csw.params.commands.ControlCommand>) ctx, cswCtx,   statePublisherActor);
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
                            log.debug(() -> "FastMove Command Message Received");
                            handleSubmitCommand(command);
                            return Behaviors.stopped();
                        });
        return builder.build();
    }

    /**
     * Submitting command to ENC Control system, once subsystem respond then sending command response on command response manager.
     * Updating Operational state to state publisher actor.
     *
     * @param message
     */
    private void handleSubmitCommand(ControlCommand message) {
        System.out.println("worker actor handling command fast move");
        Parameter baseParam = message.paramSet().find(x -> x.keyName().equals("base")).get();
        Parameter capParam = message.paramSet().find(x -> x.keyName().equals("cap")).get();
           log.debug(() -> "Submitting fastMove command to ENC Subsystem");
           FastMoveCommand.Response response = SimpleSimulator.getInstance().sendCommand(new FastMoveCommand((double)baseParam.value(0), (double)capParam.value(0)));
           switch (response.getStatus()){
               case OK:
                   this.cswCtx.commandResponseManager().addOrUpdateCommand( new CommandResponse.Completed(message.runId()));
                   break;
               case ERROR:
                   this.cswCtx.commandResponseManager().addOrUpdateCommand( new CommandResponse.Error(message.runId(), response.getDesc()));
           }
    }


}
