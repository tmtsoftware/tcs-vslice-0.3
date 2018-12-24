package org.tmt.encsubsystem.encassembly;

import akka.actor.typed.ActorSystem;
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
import csw.params.commands.CommandIssue;
import csw.params.commands.CommandResponse;
import csw.params.commands.ControlCommand;
import csw.params.commands.Setup;
import csw.params.javadsl.JKeyType;
import org.tmt.encsubsystem.encassembly.model.AssemblyState;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * Domain specific logic should be written in below handlers.
 * This handlers gets invoked when component receives messages/commands from other component/entity.
 * For example, if one component sends Submit(Setup(args)) command to HelloHcd,
 * This will be first validated in the supervisor and then forwarded to Component TLA which first invokes validateCommand hook
 * and if validation is successful, then onSubmit hook gets invoked.
 * You can find more information on this here : https://tmtsoftware.github.io/csw/commons/framework.html
 */
public class JEncAssemblyHandlers extends JComponentHandlers {

    private ILogger log;
    //
   // private CurrentStatePublisher currentStatePublisher;
    private ActorContext<TopLevelActorMessage> actorContext;
    JCswContext cswCtx;
    //private ILocationService locationService;
   // private ComponentInfo componentInfo;

    //private IConfigClientService configClientApi;

    private ActorRef<JCommandHandlerActor.CommandMessage> commandHandlerActor;
    private ActorRef<JEventHandlerActor.EventMessage> eventHandlerActor;
    private ActorRef<JLifecycleActor.LifecycleMessage> lifecycleActor;
    private ActorRef<JMonitorActor.MonitorMessage> monitorActor;


    private Optional<ICommandService> hcdCommandService = Optional.empty();
    private Optional<CurrentStateSubscription> subscription = Optional.empty();

    JEncAssemblyHandlers(ActorContext<TopLevelActorMessage> ctx, JCswContext cswCtx) {
        super(ctx, cswCtx);
       // this.currentStatePublisher = currentStatePublisher;
        this.log = cswCtx.loggerFactory().getLogger(JEncAssemblyHandlers.class);
        this.cswCtx = cswCtx;
        //
        this.actorContext = ctx;
       // this.locationService = locationService;
      //  this.componentInfo = componentInfo;
        //configClientApi = cswCtx.configClientService();
        //configClientApi = JConfigClientFactory.clientApi(Adapter.toUntyped(actorContext.getSystem()), cswCtx.locationService());
        AssemblyState initialAssemblyState = new AssemblyState(AssemblyState.LifecycleState.Initialized, AssemblyState.OperationalState.Idle);
        log.debug(() -> "Spawning Handler Actors in assembly");
        eventHandlerActor = ctx.spawnAnonymous(JEventHandlerActor.behavior(cswCtx,initialAssemblyState));
        monitorActor = ctx.spawnAnonymous(JMonitorActor.behavior(cswCtx,initialAssemblyState, eventHandlerActor));
        commandHandlerActor = ctx.spawnAnonymous(JCommandHandlerActor.behavior(cswCtx, hcdCommandService, Boolean.TRUE, Optional.empty(), monitorActor));
        lifecycleActor = ctx.spawnAnonymous(JLifecycleActor.behavior(cswCtx, hcdCommandService, commandHandlerActor, eventHandlerActor));



    }

    /**
     * This is a CSW Hook to initialize assembly.
     * This will get executed as part of assembly initialization after deployment.
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
     * This will get executed as part of assembly shutdown.
     * @return
     */
    @Override
    public CompletableFuture<Void> jOnShutdown() {
        CompletableFuture<Void> cf = new CompletableFuture<>();
        log.debug(() -> "shutdown enc assembly");
        subscription.ifPresent(subscription -> subscription.unsubscribe());
            lifecycleActor.tell(new JLifecycleActor.ShutdownMessage(cf));
            return cf;
    }

    /**
     * This is a callback method
     * When CSW detect HCD, HCD connection is obtained by assembly in this method.
     * CSW will notify assembly in case hcd connection is lost through this method.
     * @param trackingEvent
     */
    @Override
    public void onLocationTrackingEvent(TrackingEvent trackingEvent) {
        log.debug(() -> "assembly getting notified - location changed ");
        if (trackingEvent instanceof LocationUpdated) {
            AkkaLocation hcdAkkaLocation = (AkkaLocation) ((LocationUpdated) trackingEvent).location();
            hcdCommandService= Optional.of(CommandServiceFactory.jMake(hcdAkkaLocation, actorContext.getSystem()));
            // set up Hcd CurrentState subscription to be handled by the monitor actor
            subscription = Optional.of(hcdCommandService.get().subscribeCurrentState(currentState -> {
                        monitorActor.tell(new JMonitorActor.CurrentStateMessage(currentState));
                    }
            ));

            log.debug(() -> "connection to hcd from assembly received");

        } else if (trackingEvent instanceof LocationRemoved) {
            // do something for the tracked location when it is no longer available
            hcdCommandService = Optional.empty();
            // FIXME: not sure if this is necessary
            subscription.ifPresent(subscription -> subscription.unsubscribe());
        }

        // send messages to command handler and monitor actors
        commandHandlerActor.tell(new JCommandHandlerActor.UpdateTemplateHcdMessage(hcdCommandService));
        lifecycleActor.tell(new JLifecycleActor.UpdateHcdCommandServiceMessage(hcdCommandService));
        monitorActor.tell(new JMonitorActor.LocationEventMessage(hcdCommandService));
    }

    /**
     * This is a CSW Validation hook. When command is submitted to this component
     * then first validation hook is called to validate command like parameter, value range operational state etc
     * @param controlCommand
     * @return
     */
    @Override
    public CommandResponse.ValidateCommandResponse validateCommand(ControlCommand controlCommand) {
        log.debug(() -> "validating command enc assembly " + controlCommand.commandName().name());
        CommandResponse.Accepted accepted = new CommandResponse.Accepted(controlCommand.runId());
        switch (controlCommand.commandName().name()) {
            case "move":
                log.debug(() -> "Validating command parameters and operational state");
                //Parameter based validation
                if (((Setup) controlCommand).get("mode", JKeyType.StringKey()).isEmpty()) {

                    return new CommandResponse.Invalid(controlCommand.runId(), new CommandIssue.MissingKeyIssue("Move command is missing mode parameter"));
                }
                //State based validation
                if (!isStateValid(askOperationalStateFromMonitor(monitorActor, actorContext.getSystem()))) {
                    return new CommandResponse.Invalid(controlCommand.runId(), new CommandIssue.WrongInternalStateIssue("Assembly is not in valid operational state"));
                }
                return accepted;

            case "follow":
                //State based validation
                if (!isStateValid(askOperationalStateFromMonitor(monitorActor, actorContext.getSystem()))) {
                    return new CommandResponse.Invalid(controlCommand.runId(), new CommandIssue.WrongInternalStateIssue("Assembly is not in valid operational state"));
                }
                //Immediate command implementation, on submit hook will not be called.
                return accepted;
                //return executeFollowCommandAndReturnResponse(controlCommand);
            case "startup":
                return accepted;
            case "shutdown":
                return accepted;
            case "assemblyTestCommand":
                return accepted;
            case "hcdTestCommand":
                return accepted;

            default:
                log.debug(() -> "invalid command");
                CommandResponse.Invalid invalid = new CommandResponse.Invalid(controlCommand.runId(), new CommandIssue.UnsupportedCommandIssue("Command is not supported"));
                return invalid;

        }

    }

    /**
     * Getting state from monitor actor to perform state related validation etc.
     * This is a blocking call to actor
     *
     * @return
     */

    public static AssemblyState.OperationalState askOperationalStateFromMonitor(ActorRef<JMonitorActor.MonitorMessage> actorRef, ActorSystem sys) {
        final JMonitorActor.AssemblyStatesResponseMessage assemblyStatesResponse;
        try {
            assemblyStatesResponse = AskPattern.ask(actorRef, (ActorRef<JMonitorActor.AssemblyStatesResponseMessage> replyTo) ->
                            new JMonitorActor.AssemblyStatesAskMessage(replyTo)
                    , new Timeout(10, TimeUnit.SECONDS), sys.scheduler()).toCompletableFuture().get();
            //  log.debug(() -> "Got Assembly state from monitor actor - " + assemblyStates.assemblyOperationalState + " ,  " + assemblyStates.assemblyLifecycleState);
            return assemblyStatesResponse.assemblyState.getOperationalState();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }

    /**
     * Performing state based validation
     *
     * @param operationalState
     * @return
     */
    private boolean isStateValid(AssemblyState.OperationalState operationalState) {
        return operationalState == AssemblyState.OperationalState.Ready ||
                operationalState == AssemblyState.OperationalState.Slewing ||
                operationalState == AssemblyState.OperationalState.Tracking ||
                operationalState == AssemblyState.OperationalState.InPosition;
    }


    /**
     * This CSW hook is called after command is validated in validate hook.
     * Command is forwarded to CommandHandlerActor or LifecycleActor for processing.
     * @param controlCommand
     */
    @Override
    public CommandResponse.SubmitResponse onSubmit(ControlCommand controlCommand) {
        log.debug(() -> "Assembly received command - " + controlCommand);
        switch (controlCommand.commandName().name()) {
            case "follow":
               return  executeFollowCommandAndReturnResponse(controlCommand);
            default:
                commandHandlerActor.tell(new JCommandHandlerActor.SubmitCommandMessage(controlCommand));
                return new CommandResponse.Started(controlCommand.runId());
    }}
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


    @Override
    public void onOneway(ControlCommand controlCommand) {
        log.debug(() -> "processing oneway command to enc assembly");
    }

    @Override
    public void onGoOffline() {
        log.debug(() -> "in onGoOffline()");
        commandHandlerActor.tell(new JCommandHandlerActor.GoOfflineMessage());
    }

    @Override
    public void onGoOnline() {
        log.debug(() -> "in onGoOnline()");

        commandHandlerActor.tell(new JCommandHandlerActor.GoOnlineMessage());
    }





    public ActorRef<JMonitorActor.MonitorMessage> getMonitorActor(){
        return monitorActor;
    }

    public ActorRef<JCommandHandlerActor.CommandMessage> getCommandHandlerActor(){
        return commandHandlerActor;
    }

}
