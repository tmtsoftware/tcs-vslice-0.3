package org.tmt.encsubsystem.encassembly;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import akka.util.Timeout;
import csw.command.api.javadsl.ICommandService;
import csw.framework.models.JCswContext;
import csw.logging.javadsl.ILogger;
import csw.params.commands.CommandName;
import csw.params.commands.CommandResponse;
import csw.params.commands.ControlCommand;
import csw.params.commands.Setup;
import csw.params.core.generics.Parameter;
import csw.params.core.models.Id;
import csw.params.core.models.ObsId;
import csw.params.core.models.Prefix;
import scala.Option;
import scala.concurrent.duration.FiniteDuration;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


public class MoveCmdActor extends AbstractBehavior<ControlCommand> {


    // Add messages here
    // No sealed trait/interface or messages for this actor.  Always accepts the Submit command message.


    private ActorContext<ControlCommand> actorContext;JCswContext cswCtx;
    ;
    private ILogger log;

    private Optional<ICommandService> hcdCommandService;


    private MoveCmdActor(ActorContext<ControlCommand> actorContext, JCswContext cswCtx, Optional<ICommandService> hcdCommandService ) {
        this.actorContext = actorContext;this.cswCtx = cswCtx;

          this.log = cswCtx.loggerFactory().getLogger(MoveCmdActor.class);

        this.hcdCommandService = hcdCommandService;

    }

    public static <ControlCommand> Behavior<ControlCommand> behavior(JCswContext cswCtx, Optional<ICommandService> hcdCommandService ) {
        return Behaviors.setup(ctx -> {
            return (AbstractBehavior<ControlCommand>) new MoveCmdActor((ActorContext<csw.params.commands.ControlCommand>) ctx, cswCtx,  hcdCommandService
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
                            log.debug(() -> "Move Received");
                            handleSubmitCommand(command);
                            return Behaviors.stopped();// actor stops itself, it is meant to only process one command.
                        });
        return builder.build();
    }

    private void handleSubmitCommand(ControlCommand message) {

        // NOTE: we use get instead of getOrElse because we assume the command has been validated
        //Parameter axesParam = message.paramSet().find(x -> x.keyName().equals("axes")).get();
        Parameter operation = message.paramSet().find(x -> x.keyName().equals("operation")).get();
        Parameter baseParam = message.paramSet().find(x -> x.keyName().equals("base")).get();
        Parameter capParam = message.paramSet().find(x -> x.keyName().equals("cap")).get();
        Parameter mode = message.paramSet().find(x -> x.keyName().equals("mode")).get();
        Parameter timeDuration = message.paramSet().find(x -> x.keyName().equals("timeDuration")).get();

        CompletableFuture<CommandResponse.SubmitResponse> moveFuture = move(message.maybeObsId(), operation, baseParam, capParam, mode, timeDuration);

        moveFuture.thenAccept((response) -> {

            log.debug(() -> "response = " + response);
            log.debug(() -> "runId = " + message.runId());

            cswCtx.commandResponseManager().addSubCommand(message.runId(), response.runId());

            cswCtx.commandResponseManager().updateSubCommand(response);

            log.debug(() -> "move command message handled");


        });


    }

    private Prefix templateHcdPrefix = new Prefix("tcs.encA");

    CompletableFuture<CommandResponse.SubmitResponse> move(Option<ObsId> obsId,
                                            Parameter operation,
                                            Parameter baseParam,
                                            Parameter capParam,
                                            Parameter mode,
                                            Parameter timeDuration) {
        String modeValue = (String) mode.get(0).get();
        if (hcdCommandService.isPresent()) {
            log.debug(() -> "Mode - " + modeValue);
            if ("fast".equals(modeValue)) {
                log.debug(() -> "Submitting fastMove command to HCD");
                Setup fastMoveSetupCmd = new Setup(templateHcdPrefix, new CommandName("fastMove"), Optional.empty())
                        .add(baseParam)
                        .add(capParam)
                        .add(mode)
                        .add(operation);


                CompletableFuture<CommandResponse.SubmitResponse> commandResponse = hcdCommandService.get()
                        .submit(
                                fastMoveSetupCmd,
                                Timeout.durationToTimeout(FiniteDuration.apply(5, TimeUnit.SECONDS))
                        );

                return commandResponse;

            } else {

                return CompletableFuture.completedFuture(new CommandResponse.Error(new Id(""), "Invalid mode value"));
            }

        } else {
            return CompletableFuture.completedFuture(new CommandResponse.Error(new Id(""), "Can't locate HCD"));

        }
    }
}
