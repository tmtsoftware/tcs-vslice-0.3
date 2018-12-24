package org.tmt.encsubsystem.enchcd;

import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.AskPattern;
import akka.util.Timeout;
import csw.command.api.CurrentStateSubscription;
import csw.command.api.javadsl.ICommandService;
import csw.command.client.CommandServiceFactory;
import csw.command.client.messages.TopLevelActorMessage;
import csw.framework.javadsl.JComponentHandlers;
import csw.framework.models.JCswContext;
import csw.location.api.models.AkkaLocation;
import csw.location.api.models.LocationRemoved;
import csw.location.api.models.LocationUpdated;
import csw.location.api.models.TrackingEvent;
import csw.logging.javadsl.ILogger;
import csw.params.commands.CommandResponse;
import csw.params.commands.ControlCommand;
import org.tmt.encsubsystem.enchcd.models.HCDState;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * Domain specific logic should be written in below handlers.
 * This handlers gets invoked when component receives messages/commands from other component/entity.
 * For example, if one component sends Submit(Setup(args)) command to EncHcd,
 * This will be first validated in the supervisor and then forwarded to Component TLA which first invokes validateCommand hook
 * and if validation is successful, then onSubmit hook gets invoked.
 * You can find more information on this here : https://tmtsoftware.github.io/csw-prod/framework.html
 */
public class JEncHcdHandlers extends JComponentHandlers {
    private ILogger log;
    private ActorContext<TopLevelActorMessage> actorContext;
    JCswContext cswCtx;

    ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor;
    ActorRef<JCommandHandlerActor.CommandMessage> commandHandlerActor;
    ActorRef<JLifecycleActor.LifecycleMessage> lifecycleActor;
    private Optional<CurrentStateSubscription> subscription = Optional.empty();

    JEncHcdHandlers(ActorContext<TopLevelActorMessage> ctx, JCswContext cswCtx) {
        super(ctx, cswCtx);
        this.cswCtx = cswCtx;
        this.log = cswCtx.loggerFactory().getLogger(getClass());

        this.actorContext = ctx;

        HCDState initialHcdState = new HCDState(HCDState.LifecycleState.Initialized, HCDState.OperationalState.Idle);

        statePublisherActor = ctx.spawnAnonymous(JStatePublisherActor.behavior(cswCtx, initialHcdState));

        commandHandlerActor = ctx.spawnAnonymous(JCommandHandlerActor.behavior(cswCtx, statePublisherActor));
        lifecycleActor = ctx.spawnAnonymous(JLifecycleActor.behavior(cswCtx, statePublisherActor));


    }
    /**
     * This is a CSW Hook to initialize assembly.
     * This will get executed as part of hcd initialization after deployment.
     * @return
     */
    @Override
    public CompletableFuture<Void> jInitialize() {
        CompletableFuture<Void> cf = new CompletableFuture<>();
        log.debug(() -> "initializing enc assembly");
        lifecycleActor.tell(new JLifecycleActor.InitializeMessage(cf));
        return cf;
    }
    /**
     * This is a CSW Hook to shutdown assembly.
     * This will get executed as part of hcd shutdown.
     * @return
     */
    @Override
    public CompletableFuture<Void> jOnShutdown() {
        return CompletableFuture.runAsync(() -> {
            log.debug(() -> "shutdown enc hcd");
            lifecycleActor.tell(new JLifecycleActor.ShutdownMessage());
        });
    }

    @Override
    public void onLocationTrackingEvent(TrackingEvent trackingEvent) {
        log.debug(() -> "LocationEvent - " + trackingEvent);
        if (trackingEvent instanceof LocationUpdated) {
            AkkaLocation assemblyAkkaLocation = (AkkaLocation) ((LocationUpdated) trackingEvent).location();
            ICommandService jCommandService= CommandServiceFactory.jMake(assemblyAkkaLocation, actorContext.getSystem());
            // set up Hcd CurrentState subscription to be handled by the monitor actor
            subscription = Optional.of(jCommandService.subscribeCurrentState(reverseCurrentState -> {
                        statePublisherActor.tell(new JStatePublisherActor.ReverseCurrentStateMessage(reverseCurrentState));
                    }
            ));

            log.debug(() -> "connection to assembly received");

        } else if (trackingEvent instanceof LocationRemoved) {
            // FIXME: not sure if this is necessary
            subscription.ifPresent(subscription -> subscription.unsubscribe());
        }

    }
    /**
     * This is a CSW Validation hook. When command is submitted to this component
     * then first validation hook is called to validate command like parameter, value range.
     * @param controlCommand
     * @return
     */
    @Override
    public CommandResponse.ValidateCommandResponse validateCommand(ControlCommand controlCommand) {
        log.info(() -> "validating command in enc hcd");
                //return executeFollowCommandAndReturnResponse(controlCommand);
                return new CommandResponse.Accepted(controlCommand.runId());
    }
    /**
     * This CSW hook is called after command is validated in validate hook.
     * Command is forwarded to Command Handler Actor or Lifecycle Actor for processing.
     * @param controlCommand
     */
    @Override
    public CommandResponse.SubmitResponse onSubmit(ControlCommand controlCommand) {
        log.info(() -> "HCD , Command received - " + controlCommand);
        switch (controlCommand.commandName().name()){
            case "follow":
                return  executeFollowCommandAndReturnResponse(controlCommand);
                default:
                    commandHandlerActor.tell(new JCommandHandlerActor.SubmitCommandMessage(controlCommand));
                    return new CommandResponse.Started(controlCommand.runId());
        }
    }

    @Override
    public void onOneway(ControlCommand controlCommand) {
        log.debug(() -> "processing one way command to enc hcd");
    }

    @Override
    public void onGoOffline() {
        log.info(() -> "HCD Go Offline hook");
    }

    @Override
    public void onGoOnline() {
        log.info(() -> "HCD Go Online hook");
    }

    /**
     * This method send command to command handler and return response of execution.
     * The command execution is blocking, response is not return until command processing is completed
     *
     * @param controlCommand
     * @return
     */
    private CommandResponse.SubmitResponse executeFollowCommandAndReturnResponse(ControlCommand controlCommand) {
        //submitting command to commandHandler actor and waiting for completion.
        try {
            CompletionStage<JCommandHandlerActor.ImmediateResponseMessage> reply = AskPattern.ask(commandHandlerActor, (ActorRef<JCommandHandlerActor.ImmediateResponseMessage> replyTo) ->
                    new JCommandHandlerActor.ImmediateCommandMessage(controlCommand, replyTo), new Timeout(10, TimeUnit.SECONDS), actorContext.getSystem().scheduler());

            return reply.toCompletableFuture().get().commandResponse;
        } catch (Exception e) {
            return new CommandResponse.Error(controlCommand.runId(), "Error occurred while executing follow command");
        }
    }

}
