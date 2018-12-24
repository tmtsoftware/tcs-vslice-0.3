package org.tmt.encsubsystem.enchcd;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import csw.framework.CurrentStatePublisher;
import csw.framework.models.JCswContext;
import csw.logging.javadsl.ILogger;
import csw.params.core.generics.Key;
import csw.params.core.generics.Parameter;
import csw.params.core.models.ArrayData;
import csw.params.core.states.CurrentState;
import csw.params.core.states.StateName;
import csw.params.javadsl.JKeyType;
import csw.params.javadsl.JUnits;
import org.tmt.encsubsystem.enchcd.models.*;
import org.tmt.encsubsystem.enchcd.simplesimulator.SimpleSimulator;

import java.time.Duration;
import java.time.Instant;

import static org.tmt.encsubsystem.enchcd.Constants.*;

/**
 * This actor publishes various current states to assembly.
 * This actor have timers for each current state which define schedule/frequency at which current states are published.
 * Each current state has it's own frequency and timer.
 */
public class JStatePublisherActor extends AbstractBehavior<JStatePublisherActor.StatePublisherMessage> {
    //name, keys, frequency for assembly and hcd state
    public static final String ASSEMBLY_STATE = "assemblyState";
    public static final  String HCD_STATE = "HcdState";
    public static final Key<String> LIFECYCLE_KEY = JKeyType.StringKey().make("LifecycleState");
    public static final Key<String> OPERATIONAL_KEY = JKeyType.StringKey().make("OperationalState");
    public static final Key<Instant> TIME_OF_STATE_DERIVATION = JKeyType.TimestampKey().make("TimeOfStateDerivation");
    public  static final int ASSEMBLY_STATE_EVENT_FREQUENCY_IN_HERTZ = 20;

    //name, keys for current position
    public static final  String CURRENT_POSITION = "currentPosition";
    public static final Key<Double> BASE_POS_KEY = JKeyType.DoubleKey().make("basePosKey");
    public static final Key<Double> CAP_POS_KEY = JKeyType.DoubleKey().make("capPosKey");


    //name, keys for health
    public static final  String HEALTH = "health";
    public static final Key<String> HEALTH_KEY = JKeyType.StringKey().make("healthKey");
    public static final Key<String> HEALTH_REASON_KEY = JKeyType.StringKey().make("healthReasonKey");
    public static final Key<Instant> HEALTH_TIME_KEY = JKeyType.TimestampKey().make("healthTimeKey");

    //name, keys for diagnostic
    public static final  String DIAGNOSTIC = "diagnostic";
    public static final  Key<ArrayData<Byte>> DIAGNOSTIC_KEY = JKeyType.ByteArrayKey().make("diagnosticBytesKey");
    public static final  Key<Instant> DIAGNOSTIC_TIME_KEY = JKeyType.TimestampKey().make("diagnosticTimeKey");

    //name, keys for demand positions
    public static final String DEMAND_POSITIONS = "encdemandpositions";
    public static final Key<Double> DEMAND_POSITIONS_BASE_KEY = JKeyType.DoubleKey().make("ecs.base");
    public static final Key<Double> DEMAND_POSITIONS_CAP_KEY = JKeyType.DoubleKey().make("ecs.cap");


    //keys to hold timestamps. this will hold timestamp when was the event processed by any component.
    //this is the time when ENC Subsystem generated/sampled given information
    public static final Key<Instant> SUBSYSTEM_TIMESTAMP_KEY = JKeyType.TimestampKey().make("subsystemTimestampKey");
    //this is the time when ENC HCD processed any event
    public static final Key<Instant> HCD_TIMESTAMP_KEY = JKeyType.TimestampKey().make("hcdTimestampKey");
    //this is the time when Assembly processed any event
    public static final Key<Instant> ASSEMBLY_TIMESTAMP_KEY = JKeyType.TimestampKey().make("assemblyTimestampKey");
    //this is the time when client processed any event
    public static final Key<Instant> CLIENT_TIMESTAMP_KEY = JKeyType.TimestampKey().make("clientTimestampKey");


    //Unique keys for timers
    private static final Object TIMER_KEY_HCD_STATE = new Object();
    private static final Object TIMER_KEY_CURRENT_POSITION = new Object();
    private static final Object TIMER_KEY_HEALTH = new Object();
    private static final Object TIMER_KEY_DIAGNOSTIC = new Object();


    ;
    private CurrentStatePublisher currentStatePublisher;
    private ILogger log;
    private TimerScheduler<StatePublisherMessage> timer;
    JCswContext cswCtx;

    private HCDState hcdState;


    private JStatePublisherActor(TimerScheduler<StatePublisherMessage> timer, JCswContext cswCtx, HCDState hcdState) {
        this.timer = timer;
        this.cswCtx = cswCtx;
        this.log = cswCtx.loggerFactory().getLogger(JStatePublisherActor.class);
        this.currentStatePublisher = cswCtx.currentStatePublisher();
        this.hcdState = hcdState;

    }

    public static <StatePublisherMessage> Behavior<StatePublisherMessage> behavior(JCswContext cswCtx, HCDState hcdState) {
        return Behaviors.withTimers(timers -> {
            return (AbstractBehavior<StatePublisherMessage>) new JStatePublisherActor((TimerScheduler<JStatePublisherActor.StatePublisherMessage>) timers, cswCtx, hcdState);
        });
    }

    /**
     * This method receives messages sent to actor.
     * based on message type it forward message to its dedicated handler method.
     * @return
     */
    @Override
    public Receive<StatePublisherMessage> createReceive() {

        ReceiveBuilder<StatePublisherMessage> builder = receiveBuilder()
                .onMessage(StartMessage.class,
                        command -> {
                            log.debug(() -> "StartMessage Received");
                            onStart(command);
                            return Behaviors.same();
                        })
                .onMessage(StopMessage.class,
                        command -> {
                            log.debug(() -> "StopMessage Received");
                            onStop(command);
                            return Behaviors.same();
                        })
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
                .onMessage(FollowCommandCompletedMessage.class,
                        message -> {
                            log.debug(() -> "FollowCommandCompletedMessage Received");
                            return handleFollowCommandCompletedMessage(message);
                        })
                .onMessage(PublishHcdStateMessage.class,
                        publishHcdStateMessage -> {
                            log.debug(() -> "PublishHcdStateMessage Received");
                            publishHcdState();
                            return Behaviors.same();
                        })
                .onMessage(PublishCurrentPositionMessage.class,
                        publishCurrentPositionMessage -> {
                            log.debug(() -> "PublishCurrentPositionMessage Received");
                            publishCurrentPosition();
                            return Behaviors.same();
                        })
                .onMessage(PublishHealthMessage.class,
                        publishHealthMessage -> {
                            log.debug(() -> "PublishHealthMessage Received");
                            publishHealth();
                            return Behaviors.same();
                        })
                .onMessage(PublishDiagnosticMessage.class,
                        publishDiagnosticMessage -> {
                            log.debug(() -> "PublishDiagnosticMessage Received");
                            publishDiagnostic();
                            return Behaviors.same();
                        })
                .onMessage(ReverseCurrentStateMessage.class,
                        reverseCurrentStateMessage -> {
                            log.debug(() -> "ReverseCurrentStateMessage Received");
                            onReverseCurrentStateMessage(reverseCurrentStateMessage);
                            return Behaviors.same();
                        });
        return builder.build();
    }

    /**
     * This method will change assembly state to running
     * @param message
     * @return
     */
    private Behavior<StatePublisherMessage> handleInitializedMessage(InitializedMessage message) {
        this.hcdState.setLifecycleState(HCDState.LifecycleState.Running);
        this.hcdState.setOperationalState(HCDState.OperationalState.Ready);
        return Behaviors.same();
    }

    /**
     * This method will change assembly state to initialized
     * @param message
     * @return
     */
    private Behavior<StatePublisherMessage> handleUnInitializedMessage(UnInitializedMessage message) {
        this.hcdState.setLifecycleState(HCDState.LifecycleState.Initialized);
        this.hcdState.setOperationalState(HCDState.OperationalState.Idle);
        return Behaviors.same();
    }
    /**
     * This method will change assembly state to following
     * @param message
     * @return
     */
    private Behavior<StatePublisherMessage> handleFollowCommandCompletedMessage(FollowCommandCompletedMessage message) {
        this.hcdState.setOperationalState(HCDState.OperationalState.Following);
        return Behaviors.same();
    }


    /**
     * This method handles reverse current state(Demand state) received from assembly.
     * It will forward states to subsystem
     * @param reverseCurrentStateMessage
     */
    private void onReverseCurrentStateMessage(ReverseCurrentStateMessage reverseCurrentStateMessage) {
            CurrentState reverseCurrentState = reverseCurrentStateMessage.reverseCurrentState;
            switch (reverseCurrentState.stateName().name()) {
                case DEMAND_POSITIONS:
                    log.debug(() -> "encdemandpositions - "+reverseCurrentState);
                    DemandPosition demandPosition = extractDemandPosition(reverseCurrentState);
                    forwardToSubsystem(demandPosition);
                    break;
                default:
                    log.error("This current state is not handled");
            }
    }

    /**
     * This method forwards demand position to subsystem
     * @param demandPosition
     */
    private void forwardToSubsystem(DemandPosition demandPosition) {
        SimpleSimulator.getInstance().setDemandPosition(demandPosition);
    }

    /**
     * This method create DemandPostion object from CurrentState received from assembly
     * @param reverseCurrentState
     * @return
     */
    private DemandPosition extractDemandPosition(CurrentState reverseCurrentState) {
        Parameter<Double> basePosParam  = reverseCurrentState.jGet(DEMAND_POSITIONS_BASE_KEY).get();
        Parameter<Double> capPosParam  = reverseCurrentState.jGet(DEMAND_POSITIONS_CAP_KEY).get();
        Parameter<Instant> clientTimestampKey  = reverseCurrentState.jGet(CLIENT_TIMESTAMP_KEY).get();
        Parameter<Instant> assemblyTimestampKey  = reverseCurrentState.jGet(ASSEMBLY_TIMESTAMP_KEY).get();
        DemandPosition demandPosition = new DemandPosition(basePosParam.value(0), capPosParam.value(0), clientTimestampKey.value(0), assemblyTimestampKey.value(0), Instant.now());
        return  demandPosition;
    }

    private void onStart(StartMessage message) {
        log.debug(() -> "Start Message Received ");
        timer.startPeriodicTimer(TIMER_KEY_HCD_STATE, new PublishHcdStateMessage(), Duration.ofMillis(1000/HCD_STATE_PUBLISH_FREQUENCY));
        timer.startPeriodicTimer(TIMER_KEY_CURRENT_POSITION, new PublishCurrentPositionMessage(), Duration.ofMillis(1000/CURRENT_POSITION_PUBLISH_FREQUENCY));
        timer.startPeriodicTimer(TIMER_KEY_HEALTH, new PublishHealthMessage(), Duration.ofMillis(1000/HEALTH_PUBLISH_FREQUENCY));
        timer.startPeriodicTimer(TIMER_KEY_DIAGNOSTIC, new PublishDiagnosticMessage(), Duration.ofMillis(1000/DIAGNOSTIC_PUBLISH_FREQUENCY));
        log.debug(() -> "start message completed");
    }

    /**
     * This method will stop all timers i.e. it will stop publishing all current states from HCD.
     * @param message
     */
    private void onStop(StopMessage message) {

        log.debug(() -> "Stop Message Received ");
        timer.cancel(TIMER_KEY_HCD_STATE);
        timer.cancel(TIMER_KEY_CURRENT_POSITION);
        timer.cancel(TIMER_KEY_HEALTH);
        timer.cancel(TIMER_KEY_DIAGNOSTIC);
    }

    /**
     * This method
     * publish Hcd lifecycle and operational state as per timer frequency.
     */
    private void publishHcdState() {
        CurrentState currentState = new CurrentState(this.cswCtx.componentInfo().prefix(), new StateName(HCD_STATE))
                .madd(LIFECYCLE_KEY.set(hcdState.getLifecycleState().name()),OPERATIONAL_KEY.set(hcdState.getOperationalState().name()));
        currentStatePublisher.publish(currentState);
    }
    /**
     * This method get current position from subsystem and
     * publish it using current state publisher as per timer frequency.
     */
    private void publishCurrentPosition() {
        // example parameters for a current state
        CurrentPosition currentPosition = SimpleSimulator.getInstance().getCurrentPosition();

        Parameter<Double> basePosParam = BASE_POS_KEY.set(currentPosition.getBase()).withUnits(JUnits.degree);
        Parameter<Double> capPosParam = CAP_POS_KEY.set(currentPosition.getCap()).withUnits(JUnits.degree);
        //this is the time when subsystem published current position.
        Parameter<Instant> ecsSubsystemTimestampParam = SUBSYSTEM_TIMESTAMP_KEY.set(Instant.ofEpochMilli(currentPosition.getTime()));
        //this is the time when ENC HCD processed current position
        Parameter<Instant> encHcdTimestampParam = HCD_TIMESTAMP_KEY.set(Instant.now());

        CurrentState currentStatePosition = new CurrentState(this.cswCtx.componentInfo().prefix(), new StateName(CURRENT_POSITION))
                .add(basePosParam)
                .add(capPosParam)
                .add(ecsSubsystemTimestampParam)
                .add(encHcdTimestampParam);

        currentStatePublisher.publish(currentStatePosition);
     }

    /**
     * This method get current health from subsystem and
     * publish it using current state publisher as per timer frequency.
     */
    private void publishHealth() {
        Health health = SimpleSimulator.getInstance().getHealth();
        Parameter<String> healthParam = HEALTH_KEY.set(health.getHealth().name());
        Parameter<String> healthReasonParam = HEALTH_REASON_KEY.set(health.getReason());
        Parameter<Instant> healthTimeParam = HEALTH_TIME_KEY.set(Instant.ofEpochMilli(health.getTime()));

        CurrentState currentStateHealth = new CurrentState(this.cswCtx.componentInfo().prefix(), new StateName(HEALTH))
                .add(healthParam)
                .add(healthReasonParam)
                .add(healthTimeParam);
        currentStatePublisher.publish(currentStateHealth);
    }


    /**
     * This method get diagnostic from subsystem and
     * publish it using current state publisher as per timer frequency.
     */
    private void publishDiagnostic() {
        Diagnostic diagnostic = SimpleSimulator.getInstance().getDiagnostic();
        Parameter<ArrayData<Byte>> diagnosticByteParam = DIAGNOSTIC_KEY.set(ArrayData.fromJavaArray(diagnostic.getDummyDiagnostic()));
        Parameter<Instant> diagnosticTimeParam = DIAGNOSTIC_TIME_KEY.set(Instant.ofEpochMilli(diagnostic.getTime()));

        CurrentState currentStateDiagnostic = new CurrentState(this.cswCtx.componentInfo().prefix(), new StateName(DIAGNOSTIC))
                .add(diagnosticByteParam)
                .add(diagnosticTimeParam);

        currentStatePublisher.publish(currentStateDiagnostic);
    }

    //Messages which are accepted by JStatePublisherActor

    interface StatePublisherMessage {
    }

    public static final class StartMessage implements StatePublisherMessage {
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof StartMessage)) {
                return false;
            }
            return true;

        }
    }

    public static final class StopMessage implements StatePublisherMessage {
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof StopMessage)) {
                return false;
            }
            return true;

        }
    }
    public static final class PublishHcdStateMessage implements StatePublisherMessage {
    }
    public static final class PublishCurrentPositionMessage implements StatePublisherMessage {
    }

    public static final class PublishHealthMessage implements StatePublisherMessage {
    }
    public static final class PublishDiagnosticMessage implements StatePublisherMessage {
    }

    public static final class InitializedMessage implements StatePublisherMessage {
        @Override
        public boolean equals(Object obj) {

            if (!(obj instanceof InitializedMessage)) {
                return false;
            }
            return true;
        }
    }
    public static final class UnInitializedMessage implements StatePublisherMessage {
        @Override
        public boolean equals(Object obj) {

            if (!(obj instanceof UnInitializedMessage)) {
                return false;
            }
            return true;
        }
    }
    public static final class FollowCommandCompletedMessage implements StatePublisherMessage {
        @Override
        public boolean equals(Object obj) {

            if (!(obj instanceof FollowCommandCompletedMessage)) {
                return false;
            }
            return true;
        }
    }

    /**
     * HCD's JStatePublisherActor receives ReverseCurrentState like demandPositions from assembly.
     */
    public static final class ReverseCurrentStateMessage implements StatePublisherMessage {
        public final CurrentState reverseCurrentState;
        public ReverseCurrentStateMessage(CurrentState reverseCurrentState) {
            this.reverseCurrentState = reverseCurrentState;
        }
    }

}

