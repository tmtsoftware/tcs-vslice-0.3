package org.tmt.encsubsystem.enchcd;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import csw.framework.models.JCswContext;
import csw.logging.javadsl.ILogger;
import csw.params.commands.CommandResponse;
import csw.params.commands.ControlCommand;

public class JTrackOffCmdActor extends AbstractBehavior<ControlCommand> {


    // Add messages here
    // No sealed trait/interface or messages for this actor.  Always accepts the Submit command message.


    private ActorContext<ControlCommand> actorContext;JCswContext cswCtx;
    ;
    private ILogger log;



    private JTrackOffCmdActor(ActorContext<ControlCommand> actorContext, JCswContext cswCtx) {
        this.actorContext = actorContext;this.cswCtx = cswCtx;

          this.log = cswCtx.loggerFactory().getLogger(JTrackOffCmdActor.class);



    }

    public static <ControlCommand> Behavior<ControlCommand> behavior(JCswContext cswCtx ) {
        return Behaviors.setup(ctx -> {
            return (AbstractBehavior<ControlCommand>) new JTrackOffCmdActor((ActorContext<csw.params.commands.ControlCommand>) ctx, cswCtx );
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
                            handleSubmitCommand(command);
                            return Behaviors.stopped();
                        });
        return builder.build();
    }

    /**
     * This method process trackOff command.
     * We assume all the validation have been done at ComponentHandler and CommandHandler.
     *
     * @param message
     */
    private void handleSubmitCommand(ControlCommand message) {
        try {
            log.debug(() -> "TrackOff Command Message Received by TrackOffCmdActor in HCD " + message);
            Thread.sleep(500);
            //Serialize command data, submit to subsystem using ethernet ip connection
            log.debug(() -> "Got response from enc susbystem for trackOff command");
            this.cswCtx.commandResponseManager().addOrUpdateCommand(new CommandResponse.Completed(message.runId()));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


    }


}
