package org.tmt.encsubsystem.encassembly;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import csw.command.api.javadsl.ICommandService;
import csw.framework.models.JCswContext;
import csw.logging.javadsl.ILogger;
import csw.params.core.generics.Parameter;
import csw.params.core.models.ArrayData;
import csw.params.core.states.CurrentState;
import org.tmt.encsubsystem.encassembly.model.AssemblyState;
import org.tmt.encsubsystem.encassembly.model.HCDState;

import java.time.Instant;
import java.util.Optional;

import static org.tmt.encsubsystem.encassembly.Constants.*;

/**
 * Monitor actor track hcd connection, events coming from hcd.
 * based on provided data it determined assembly state and health
 */
public class JMonitorActor extends AbstractBehavior<JMonitorActor.MonitorMessage> {
    private ActorContext<MonitorMessage> actorContext;
    JCswContext cswCtx;
    private ILogger log;

    ActorRef<JEventHandlerActor.EventMessage> eventHandlerActor;

    private AssemblyState assemblyState;
    private Optional<HCDState> hcdState = Optional.empty();

    private JMonitorActor(ActorContext<MonitorMessage> actorContext, JCswContext cswCtx, AssemblyState assemblyState, ActorRef<JEventHandlerActor.EventMessage> eventHandlerActor) {
        this.actorContext = actorContext;this.cswCtx = cswCtx;
        this.cswCtx = cswCtx;
        this.log = cswCtx.loggerFactory().getLogger(JMonitorActor.class);
        this.assemblyState = assemblyState;
        this.eventHandlerActor = eventHandlerActor;

    }

    public static <MonitorMessage> Behavior<MonitorMessage> behavior(JCswContext cswCtx,AssemblyState assemblyState, ActorRef<JEventHandlerActor.EventMessage> eventHandlerActor) {
        return Behaviors.setup(ctx -> {
            return (AbstractBehavior<MonitorMessage>) new JMonitorActor((ActorContext<JMonitorActor.MonitorMessage>) ctx, cswCtx, assemblyState, eventHandlerActor);
        });
    }

    /**
     * This method receives messages sent to actor.
     * based on message type it forward message to its dedicated handler method.
     * @return
     */
    @Override
    public Receive<MonitorMessage> createReceive() {

        ReceiveBuilder<MonitorMessage> builder = receiveBuilder()
                .onMessage(InitializedMessage.class,
                        message -> {
                            log.debug(() -> "InitializedMessage Received");
                            return handleInitializedMessage(message);
                        })
                .onMessage(UnInitializedMessage.class,
                        message -> {
                            log.debug(() -> "UnInitializedMessage Received");
                            return handleUnInitializedMessage(message);
                        })
                .onMessage(LocationEventMessage.class,
                        message -> {
                            log.debug(() -> "LocationEventMessage Received");
                            return onLocationEventMessage(message);
                        })
                .onMessage(CurrentStateMessage.class,
                        message -> {
                            log.debug(() -> "CurrentStateMessage Received");
                            return onCurrentStateEventMessage(message);
                        })
                .onMessage(AssemblyStatesAskMessage.class,
                        message -> {
                            log.debug(() -> "AssemblyStatesAskMessage Received");
                            // providing current lifecycle and operation state to sender for it's use such as validation.
                            message.replyTo.tell(new AssemblyStatesResponseMessage(assemblyState));
                            return Behaviors.same();
                        });
        return builder.build();
    }

    /**
     * This method will change assembly state to running
     * @param message
     * @return
     */
    private Behavior<MonitorMessage> handleInitializedMessage(InitializedMessage message) {
        this.assemblyState.setLifecycleState(AssemblyState.LifecycleState.Running);
        this.assemblyState.setOperationalState(AssemblyState.OperationalState.Ready);
        return JMonitorActor.behavior(cswCtx,assemblyState, eventHandlerActor);
    }

    /**
     * This method will change assembly state to initialized
     * @param message
     * @return
     */
    private Behavior<MonitorMessage> handleUnInitializedMessage(UnInitializedMessage message) {
        this.assemblyState.setLifecycleState(AssemblyState.LifecycleState.Initialized);
        this.assemblyState.setOperationalState(AssemblyState.OperationalState.Idle);
        return JMonitorActor.behavior(cswCtx,assemblyState,  eventHandlerActor);
    }

    /**
     * This method derives assembly operational state based on connectivity to hcd.
     *
     * @param message
     * @return
     */
    private Behavior<MonitorMessage> onLocationEventMessage(LocationEventMessage message) {
        if (message.hcdCommandService.isPresent()) {
            // HCD is connected, marking operational states of assembly idle.
            assemblyState.setOperationalState(AssemblyState.OperationalState.Idle);
        } else {
            // as assembly is disconnected to hcd, monitor actor will not be receiving current states from hcd.
            // assembly is disconnected to hcd, then change state to disconnected/faulted
            assemblyState.setOperationalState(AssemblyState.OperationalState.Faulted);
        }
        forwardToEventHandlerActor(new JEventHandlerActor.AssemblyStateMessage(assemblyState, ASSEMBLY_STATE_TIME_KEY.set(Instant.now())));
        return JMonitorActor.behavior(cswCtx,assemblyState,  eventHandlerActor);

    }

    private Behavior<MonitorMessage> onCurrentStateEventMessage(CurrentStateMessage message) {
        CurrentState currentState = message.currentState;
        switch (currentState.stateName().name()) {
            case HCD_STATE:
                log.debug(() -> "HCD lifecycle,operational states received - "+currentState);
                hcdState = Optional.of(getHcdState(currentState));
                forwardToEventHandlerActor(new JEventHandlerActor.AssemblyStateMessage(DeriveAssemblyState(), ASSEMBLY_STATE_TIME_KEY.set(Instant.now())));//assembly state derivation can be scheduled using timer.
                return JMonitorActor.behavior(cswCtx,assemblyState,  eventHandlerActor);
            case CURRENT_POSITION:
                log.debug(() -> "Current position received - " + currentState);
                //Compare Current position and demand position to determine if assembly is slewing or tracking or in position.
                forwardToEventHandlerActor(getCurrentPosition(currentState));
                return JMonitorActor.behavior(cswCtx,assemblyState,  eventHandlerActor);
            case HEALTH:
                log.debug(() -> "Health received from HCD- " + currentState);
                forwardToEventHandlerActor(getHealth(currentState));
                return Behaviors.same();

            case DIAGNOSTIC:
                log.debug(() -> "Diagnostic received from HCD- " + currentState);
                forwardToEventHandlerActor(getDiagnostic(currentState));
                return Behaviors.same();
            default:
                log.error("This current state is not handled");
                return JMonitorActor.behavior(cswCtx,assemblyState,  eventHandlerActor);
        }

    }

    /**
     * Extracting current position parameters from current state into current position message for EventHandlerActor
     * & Adding assembly processing timestamp to current position.
     * @param currentState
     * @return
     */
    private JEventHandlerActor.CurrentPositionMessage getCurrentPosition(CurrentState currentState) {
        Parameter<Double> basePosParam  = currentState.jGet(BASE_POS_KEY).get();
        Parameter<Double> capPosParam  = currentState.jGet(CAP_POS_KEY).get();
        Parameter<Instant> subsystemTimestampKey  = currentState.jGet(SUBSYSTEM_TIMESTAMP_KEY).get();
        Parameter<Instant> hcdTimestampKey  = currentState.jGet(HCD_TIMESTAMP_KEY).get();
        Parameter<Instant> assemblyTimestampKey  = ASSEMBLY_TIMESTAMP_KEY.set(Instant.now());
        return new JEventHandlerActor.CurrentPositionMessage(basePosParam,capPosParam,subsystemTimestampKey, hcdTimestampKey, assemblyTimestampKey);
    }

    /**
     * Extracting health parameters from current state into health message for EventHandlerActor
     * & Adding assembly processing timestamp to current position.
     * @param currentState
     * @return
     */
    private JEventHandlerActor.HealthMessage getHealth(CurrentState currentState) {
        Parameter<String> healthParam  = currentState.jGet(HEALTH_KEY).get();
        Parameter<String> healthReasonParam = currentState.jGet(HEALTH_REASON_KEY).get();
        Parameter<Instant> healthTimeParam = currentState.jGet(HEALTH_TIME_KEY).get();
        Parameter<Instant> assemblyTimestampKey  = ASSEMBLY_TIMESTAMP_KEY.set(Instant.now());
        return new JEventHandlerActor.HealthMessage(healthParam, healthReasonParam, healthTimeParam, assemblyTimestampKey);
    }
    /**
     * Extracting diagnostic parameters from current state into diagnostic message for EventHandlerActor
     * @param currentState
     * @return
     */
    private JEventHandlerActor.DiagnosticMessage getDiagnostic(CurrentState currentState) {
        Parameter<ArrayData<Byte>> diagnosticParam  = currentState.jGet(DIAGNOSTIC_KEY).get();
        Parameter<Instant> diagnosticTimeParam = currentState.jGet(DIAGNOSTIC_TIME_KEY).get();
        return new JEventHandlerActor.DiagnosticMessage(diagnosticParam, diagnosticTimeParam);
    }

    /**
     * Extracting HCD state from current state
     *
     * @param currentState
     * @return
     */
    private HCDState getHcdState(CurrentState currentState) {
        Parameter<String> lifecycleStateParam = currentState.jGet(LIFECYCLE_KEY).get();
        Parameter<String> operationalStateParam = currentState.jGet(OPERATIONAL_KEY).get();
        return new HCDState(HCDState.LifecycleState.valueOf(lifecycleStateParam.value(0)), HCDState.OperationalState.valueOf(operationalStateParam.value(0)));

    }

    /**
     * This method derive assembly state based on last assembly state and other current state monitor actor receiving from other component.
     * Logic will be improved.
     */
    private AssemblyState DeriveAssemblyState() {
        hcdState.ifPresent(hcdState->{
        switch (hcdState.getOperationalState()) {
            case Degraded:
                assemblyState.setOperationalState(AssemblyState.OperationalState.Degraded);
                break;
            case Faulted:
                assemblyState.setOperationalState(AssemblyState.OperationalState.Faulted);
                break;
            default:
        }});
        return assemblyState;
         }

    /**
     * This method forwards messages to EventHandlerActor for publishing them as event.
     */
    private void forwardToEventHandlerActor(JEventHandlerActor.EventMessage eventMessage) {
        eventHandlerActor.tell(eventMessage);
    }

    // add messages here
    interface MonitorMessage {
    }

    public static final class InitializedMessage implements MonitorMessage {
    }
    public static final class UnInitializedMessage implements MonitorMessage {
    }
    public static final class LocationEventMessage implements MonitorMessage {

        public final Optional<ICommandService> hcdCommandService;

        public LocationEventMessage(Optional<ICommandService> hcdCommandService) {
            this.hcdCommandService = hcdCommandService;
        }
    }

    public static final class CurrentStateMessage implements MonitorMessage {

        public final CurrentState currentState;

        public CurrentStateMessage(CurrentState currentState) {
            this.currentState = currentState;
        }
    }


    public static final class AssemblyStatesAskMessage implements MonitorMessage {

        public final ActorRef<JMonitorActor.AssemblyStatesResponseMessage> replyTo;

        public AssemblyStatesAskMessage(ActorRef<JMonitorActor.AssemblyStatesResponseMessage> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public static final class AssemblyStatesResponseMessage implements MonitorMessage {
        public final AssemblyState assemblyState;

        public AssemblyStatesResponseMessage(AssemblyState assemblyState) {
            this.assemblyState = assemblyState;
        }
    }


}
