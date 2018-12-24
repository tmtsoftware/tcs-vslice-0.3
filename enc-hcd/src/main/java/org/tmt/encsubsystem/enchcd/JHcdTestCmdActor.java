package org.tmt.encsubsystem.enchcd;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import csw.framework.models.JCswContext;
import csw.logging.javadsl.ILogger;
import csw.params.commands.CommandResponse;
import csw.params.commands.ControlCommand;

/**
 * This is a CommandWorkerActor for HcdTestCommand.
 * HcdTestCommand is a dummy command created for generating performance measures
 */
public class JHcdTestCmdActor extends AbstractBehavior<ControlCommand> {
    private ActorContext<ControlCommand> actorContext;JCswContext cswCtx;
    ;
    private ILogger log;


    private JHcdTestCmdActor(ActorContext<ControlCommand> actorContext, JCswContext cswCtx) {
        this.actorContext = actorContext;this.cswCtx = cswCtx;

          this.log = cswCtx.loggerFactory().getLogger(JHcdTestCmdActor.class);


    }

    public static <ControlCommand> Behavior<ControlCommand> behavior(JCswContext cswCtx ) {
        return Behaviors.setup(ctx -> {
            return (AbstractBehavior<ControlCommand>) new JHcdTestCmdActor((ActorContext<csw.params.commands.ControlCommand>) ctx, cswCtx );
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
     * this actor receives HcdTestCommand which is just a dummy command for performance testing,
     * This method will send CommandResponse to assembly using CommandResponseManager
     * @param controlCommand
     */
    private void handleSubmitCommand(ControlCommand controlCommand) {
        this.cswCtx.commandResponseManager().addOrUpdateCommand(  new CommandResponse.Completed(controlCommand.runId()));
    }


}
