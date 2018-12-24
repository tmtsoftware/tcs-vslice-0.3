package org.tmt.encsubsystem.encassembly;


import akka.actor.Cancellable;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import csw.event.api.javadsl.IEventService;
import csw.event.api.javadsl.IEventSubscriber;
import csw.event.api.javadsl.IEventSubscription;
import csw.framework.CurrentStatePublisher;
import csw.framework.models.JCswContext;
import csw.logging.javadsl.ILogger;
import csw.params.core.generics.Parameter;
import csw.params.core.models.ArrayData;
import csw.params.core.models.Prefix;
import csw.params.core.states.CurrentState;
import csw.params.core.states.StateName;
import csw.params.events.Event;
import csw.params.events.EventKey;
import csw.params.events.EventName;
import csw.params.events.SystemEvent;
import org.tmt.encsubsystem.encassembly.model.AssemblyState;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.tmt.encsubsystem.encassembly.Constants.*;

/*** EventHandlerActor handles event processing.
 * it takes data from other actors like monitor actor and publish event using event service.
 * it also subscribes to events from other assemblies and provide data to other actors.
 */
public class JEventHandlerActor extends AbstractBehavior<JEventHandlerActor.EventMessage> {

    private ActorContext<EventMessage> actorContext;JCswContext cswCtx;
    ;
    private IEventService eventService;
    private CurrentStatePublisher currentStatePublisher;

    private ILogger log;
    private IEventSubscription positionDemandsSubscription;
    private Optional<Cancellable> cancellable = Optional.empty();

    /**
     * This hold latest assembly state
     */
    private AssemblyStateMessage assemblyState;


    private JEventHandlerActor(ActorContext<EventMessage> actorContext, JCswContext cswCtx, AssemblyState assemblyState) {
        this.actorContext = actorContext;this.cswCtx = cswCtx;

        this.eventService = cswCtx.eventService();
        this.currentStatePublisher = cswCtx.currentStatePublisher();
        this.log = cswCtx.loggerFactory().getLogger(JEventHandlerActor.class);// how expensive is this operation?
        this.assemblyState = new AssemblyStateMessage(assemblyState, ASSEMBLY_STATE_TIME_KEY.set(Instant.now()));
    }

    public static <EventMessage> Behavior<EventMessage> behavior(JCswContext cswCtx, AssemblyState assemblyState) {
        return Behaviors.setup(ctx -> {
            return (AbstractBehavior<EventMessage>) new JEventHandlerActor((ActorContext<JEventHandlerActor.EventMessage>) ctx, cswCtx, assemblyState);
        });
    }

    /**
     * This method receives messages sent to actor
     * and handle them based on their type.
     * @return
     */
    @Override
    public Receive<EventMessage> createReceive() {

        ReceiveBuilder<EventMessage> builder = receiveBuilder()
                .onMessage(CurrentPositionMessage.class,
                        currentPositionMessage -> {
                            log.debug(() -> "CurrentPositionMessage Received");
                            publishCurrentPosition(currentPositionMessage);
                            return Behaviors.same();
                        })
                .onMessage(AssemblyStateMessage.class,
                        assemblyStateMessage -> {
                            log.debug(() -> "AssemblyStateMessage received" + assemblyStateMessage.assemblyState);
                            this.assemblyState = assemblyStateMessage; // updating assembly state in event handler actor
                            return Behaviors.same();
                        })
                .onMessage(HealthMessage.class,
                        healthMessage -> {
                            log.debug(() -> "HealthMessage received");
                            publishHealth(healthMessage);
                            return Behaviors.same();
                        })
                .onMessage(DiagnosticMessage.class,
                        diagnosticMessage -> {
                            log.debug(() -> "DiagnosticMessage received");
                            publishDiagnostic(diagnosticMessage);
                            return Behaviors.same();
                        })
                .onMessage(PublishAssemblyStateMessage.class,
                        assemblyStateMessage -> {
                            log.debug(() -> "PublishAssemblyStateMessage received");
                            Cancellable c= startPublishingAssemblyState();
                            cancellable = Optional.of(c);
                            return Behaviors.same();
                        })
                .onMessage(StopEventsMessage.class,
                        stopEventsMessage -> {
                            log.debug(() -> "StopEventsMessage received");
                            stopPublishingAssemblyState(stopEventsMessage);
                            return Behaviors.same();
                        })
                .onMessage(SubscribeEventMessage.class,
                        subscribeEventMessage -> {
                            log.debug(() -> "PublishAssemblyStateMessage received");
                            subscribeToEvents(subscribeEventMessage);
                            return Behaviors.same();
                        });
        return builder.build();
    }

    private void stopPublishingAssemblyState(StopEventsMessage message) {
        cancellable.ifPresent(c-> c.cancel());
        message.replyTo.tell("Done");
    }

    /**
     * This method subscribe to events from other assemblies.
     * And forward events to other components and hcd.
     * @param subscribeEventMessage
     */
    private void subscribeToEvents(SubscribeEventMessage subscribeEventMessage) {
        positionDemandsSubscription  = subscribeEncDemandsPositions();
    }

    private IEventSubscription subscribeEncDemandsPositions(){
        IEventSubscriber subscriber = eventService.defaultSubscriber();
        EventKey eventKey = new EventKey(new Prefix(DEMAND_POSITIONS_PUBLISHER_PREFIX), new EventName(DEMAND_POSITIONS));
        return subscriber.subscribeAsync(Collections.singleton(eventKey), this::demandPositionsCallback);
    }


    private CompletableFuture<String> demandPositionsCallback(Event event){

        Parameter baseParam = event.paramSet().find(x -> x.keyName().equals(DEMAND_POSITIONS_BASE_KEY)).get();
        Parameter capParam = event.paramSet().find(x -> x.keyName().equals(DEMAND_POSITIONS_CAP_KEY)).get();
        Parameter<Instant> clientTimeParam = CLIENT_TIMESTAMP_KEY.set(event.eventTime().time());
        Parameter<Instant> assemblyTimeParam = ASSEMBLY_TIMESTAMP_KEY.set(Instant.now());
        CurrentState demandPosition = new CurrentState(this.cswCtx.componentInfo().prefix(), new StateName(DEMAND_POSITIONS))
                .add(baseParam)
                .add(capParam)
                .add(clientTimeParam)
                .add(assemblyTimeParam);
        currentStatePublisher.publish(demandPosition);
        return CompletableFuture.completedFuture("Ok");
    }

    /**
     * This method publish current position event.
     * @param message
     */
    private void publishCurrentPosition(CurrentPositionMessage message) {
        SystemEvent currentPositionEvent = new SystemEvent(this.cswCtx.componentInfo().prefix(), new EventName(Constants.CURRENT_POSITION))
                .madd(message.basePosParam, message.capPosParam, message.subsystemTimestamp, message.hcdTimestamp, message.assemblyTimestamp);
        eventService.defaultPublisher().publish(currentPositionEvent);
    }

    /**
     * This method publish health event.
     * @param message
     */
    private void publishHealth(HealthMessage message) {
        SystemEvent healthEvent = new SystemEvent(this.cswCtx.componentInfo().prefix(), new EventName(Constants.HEALTH))
                .madd(message.healthParam, message.assemblyTimestamp, message.healthReasonParam, message.healthTimeParam);
        eventService.defaultPublisher().publish(healthEvent);
    }

    /**
     * This method publish diagnostic event.
     * @param message
     */
    private void publishDiagnostic(DiagnosticMessage message) {
        SystemEvent diagnosticEvent = new SystemEvent(this.cswCtx.componentInfo().prefix(), new EventName(Constants.DIAGNOSTIC))
                .madd(message.diagnosticByteParam, message.diagnosticTimeParam);
        eventService.defaultPublisher().publish(diagnosticEvent);
    }

    /**
     * This method create event generator to publish assembly state.
     * This method keep publishing latest assembly state over and over unless any update is received from monitor actor.
     * @return
     */
    private Cancellable startPublishingAssemblyState(){
        Event baseEvent = new SystemEvent(this.cswCtx.componentInfo().prefix(), new EventName(Constants.ASSEMBLY_STATE));
        return eventService.defaultPublisher().publish(()->
             ((SystemEvent) baseEvent)
                     .add(LIFECYCLE_KEY.set(this.assemblyState.assemblyState.getLifecycleState().name()))
                     .add(OPERATIONAL_KEY.set(this.assemblyState.assemblyState.getOperationalState().name()))
                    .add(this.assemblyState.time)
        , Duration.ofMillis(1000/Constants.ASSEMBLY_STATE_EVENT_FREQUENCY_IN_HERTZ),error->{
            log.error("Assembly state publish error");
                });
    }

    interface EventMessage {
    }

    /**
     * Send this message to EventHandlerActor to start publishing assembly events to
     */
    public static final class AssemblyStateMessage implements EventMessage {
        public final AssemblyState assemblyState;
        public final Parameter<Instant> time;

        public  AssemblyStateMessage(AssemblyState assemblyState, Parameter<Instant> time){
            this.assemblyState = assemblyState;
            this.time = time;
        }

    }

    /**
     * EventHandlerActor message for current position event.
     */
    public static final class CurrentPositionMessage implements  EventMessage{
        public final Parameter<Double> basePosParam;
        public final Parameter<Double> capPosParam;
        public final Parameter<Instant> subsystemTimestamp;
        public final Parameter<Instant> hcdTimestamp;
        public final Parameter<Instant> assemblyTimestamp;


        public CurrentPositionMessage(Parameter<Double> basePosParam, Parameter<Double> capPosParam, Parameter<Instant> subsystemTimestamp, Parameter<Instant> hcdTimestamp, Parameter<Instant> assemblyTimestamp) {
            this.basePosParam = basePosParam;
            this.capPosParam = capPosParam;
            this.subsystemTimestamp = subsystemTimestamp;
            this.hcdTimestamp = hcdTimestamp;
            this.assemblyTimestamp = assemblyTimestamp;
        }
    }

    public static final class HealthMessage implements  EventMessage {
        public final Parameter<String> healthParam;
        public final Parameter<String> healthReasonParam;
        public final Parameter<Instant> healthTimeParam;
        public final Parameter<Instant> assemblyTimestamp;

        public HealthMessage(Parameter<String> healthParam, Parameter<String> healthReasonParam, Parameter<Instant> healthTimeParam, Parameter<Instant> assemblyTimestamp) {
            this.healthParam = healthParam;
            this.healthReasonParam = healthReasonParam;
            this.healthTimeParam = healthTimeParam;
            this.assemblyTimestamp = assemblyTimestamp;
        }
    }

    public  static final class DiagnosticMessage implements  EventMessage {
        public final Parameter<ArrayData<Byte>> diagnosticByteParam;
        public final Parameter<Instant> diagnosticTimeParam;

        public DiagnosticMessage(Parameter<ArrayData<Byte>> diagnosticByteParam, Parameter<Instant> diagnosticTimeParam) {
            this.diagnosticByteParam = diagnosticByteParam;
            this.diagnosticTimeParam = diagnosticTimeParam;
        }
    }

    /**
     * Upon receiving this message, EventHandlerActor will start publishing assembly state at defined frequency.
     */
    public static final class PublishAssemblyStateMessage implements  EventMessage{
    }

    /**
     * Upon receiving this message, EventHandlerActor will stop publishing assembly state at defined frequency.
     */
    public static final class StopEventsMessage implements  EventMessage{
        public final ActorRef<String> replyTo;

        public StopEventsMessage(ActorRef<String> replyTo) {
            this.replyTo = replyTo;
        }
    }


    /**
     * This will start events subscription
     */
    public static final class SubscribeEventMessage implements  EventMessage{
    }

}
