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

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Assembly's Follow Command Actor.
 * This Actor submit follow command from assembly to hcd.
 */
public class JFollowCmdActor extends AbstractBehavior<JFollowCmdActor.FollowMessage> {


    // Add messages here
    // No sealed trait/interface or messages for this actor.  Always accepts the Submit command message.
    interface FollowMessage {
    }

    public static final class FollowCommandMessage implements FollowMessage {

        public final ControlCommand controlCommand;
        public final ActorRef<JCommandHandlerActor.ImmediateResponseMessage> replyTo;


        public FollowCommandMessage(ControlCommand controlCommand, ActorRef<JCommandHandlerActor.ImmediateResponseMessage> replyTo) {
            this.controlCommand = controlCommand;
            this.replyTo = replyTo;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FollowCommandMessage that = (FollowCommandMessage) o;
            return Objects.equals(controlCommand, that.controlCommand) &&
                    Objects.equals(replyTo, that.replyTo);
        }
    }

    private ActorContext<FollowMessage> actorContext;
    JCswContext cswCtx;
    ;
    private ILogger log;

    private Optional<ICommandService> hcdCommandService;

    private Prefix encAssemblyPrefix = new Prefix("tcs.encA");


    private JFollowCmdActor(ActorContext<FollowMessage> actorContext, JCswContext cswCtx,  Optional<ICommandService> hcdCommandService ) {
        this.actorContext = actorContext;this.cswCtx = cswCtx;

          this.log = cswCtx.loggerFactory().getLogger(JFollowCmdActor.class);

        this.hcdCommandService = hcdCommandService;


    }

    public static <FollowMessage> Behavior<FollowMessage> behavior(JCswContext cswCtx, Optional<ICommandService> hcdCommandService ) {
        return Behaviors.setup(ctx -> {
            return (AbstractBehavior<FollowMessage>) new JFollowCmdActor((ActorContext<JFollowCmdActor.FollowMessage>) ctx, cswCtx,  hcdCommandService );
        });
    }


    @Override
    public Receive<FollowMessage> createReceive() {

        ReceiveBuilder<FollowMessage> builder = receiveBuilder()
                .onMessage(FollowCommandMessage.class,
                        followCommandMessage -> {
                            log.debug(() -> "Follow Command Message Received by FollowCmdActor in Assembly");
                            handleSubmitCommand(followCommandMessage);
                            return Behaviors.stopped();// actor stops itself, it is meant to only process one command.
                        });
        return builder.build();
    }

    /**
     * This method handle follow command.
     * It forwards the command to hcd using hcd command service, receive response and
     * update the response back to 'replyTo' actor
     *
     * @param followCommandMessage
     */
    private void handleSubmitCommand(FollowCommandMessage followCommandMessage) {
        // NOTE: we use get instead of getOrElse because we assume the command has been validated
        ControlCommand command = followCommandMessage.controlCommand;

        if (hcdCommandService.isPresent()) {
            hcdCommandService.get()
                    .submit(command, Timeout.durationToTimeout(FiniteDuration.apply(10, TimeUnit.SECONDS))).thenAccept(response -> {
                followCommandMessage.replyTo.tell(new JCommandHandlerActor.ImmediateResponseMessage(response));
            });
        } else {
            followCommandMessage.replyTo.tell(new JCommandHandlerActor.ImmediateResponseMessage(new CommandResponse.Error(command.runId(), "Can't locate TcsEncHcd")));
        }
    }


}
