package org.tmt.tcs.pk.pkassembly;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import csw.event.api.javadsl.IEventService;
import csw.logging.javadsl.ILogger;
import csw.logging.javadsl.JLoggerFactory;
import csw.params.core.generics.Key;
import csw.params.core.models.Prefix;
import csw.params.events.Event;
import csw.params.events.EventName;
import csw.params.events.SystemEvent;
import csw.params.javadsl.JKeyType;
import java.time.Instant;

public class JPkEventHandlerActor extends AbstractBehavior<JPkEventHandlerActor.EventMessage> {

    private ActorContext<EventMessage> actorContext;
    private IEventService eventService;
    private JLoggerFactory loggerFactory;
    private ILogger log;

    private static final Prefix prefix = new Prefix("tcs.pk");

    private int counterEnc = 0 ;
    private int counterMcs = 0 ;
    private int counterM3 = 0 ;

    private static final int LIMIT = 100000;


    private JPkEventHandlerActor(ActorContext<EventMessage> actorContext, IEventService eventService, JLoggerFactory loggerFactory) {
        this.actorContext = actorContext;
        this.eventService = eventService;
        this.loggerFactory = loggerFactory;
        this.log = loggerFactory.getLogger(actorContext, getClass());

    }

    public static <EventMessage> Behavior<EventMessage> behavior(IEventService eventService, JLoggerFactory loggerFactory) {
        return Behaviors.setup(ctx -> {
            return (AbstractBehavior<EventMessage>) new JPkEventHandlerActor((ActorContext<JPkEventHandlerActor.EventMessage>) ctx, eventService, loggerFactory);
        });
    }



    @Override
    public Receive<EventMessage> createReceive() {

        ReceiveBuilder<EventMessage> builder = receiveBuilder()
                .onMessage(McsDemandMessage.class,
                        message -> {
                            if(this.counterMcs<LIMIT)
                            {
                                log.info("Inside JPkEventHandlerActor: McsDemandMessage Received");
                                publishMcsDemand(message);
                                this.counterMcs++;
                            }
                            return Behaviors.same();
                        })
                .onMessage(EncDemandMessage.class,
                        message -> {
                          if(this.counterEnc<LIMIT)
                          {
                              publishEncDemand(message);
                              log.info("Inside JPkEventHandlerActor: EncDemandMessage Received");
                              this.counterEnc++;
                          }

                            return Behaviors.same();
                        })
                .onMessage(M3DemandMessage.class,
                        message -> {
                            if(this.counterM3<LIMIT)
                            {
                                log.info("Inside JPkEventHandlerActor: M3DemandMessage Received");
                                publishM3Demand(message);
                                this.counterM3++;
                            }


                            return Behaviors.same();
                        });
        return builder.build();
    }

    private void publishMcsDemand(McsDemandMessage message) {

        log.info("Inside JPkEventHandlerActor: Publishing Mcs Demand ");
        Key<Double> azDoubleKey = JKeyType.DoubleKey().make("mcs.az");
        Key<Double> elDoubleKey = JKeyType.DoubleKey().make("mcs.el");
        Key<Instant>  publishTimeKey             = JKeyType.TimestampKey().make("timeStamp");
        Event event = new SystemEvent(prefix, new EventName("mcsdemandpositions"))
                .add(azDoubleKey.set(message.getAz()))
                .add(elDoubleKey.set(message.getEl()))
                .add(publishTimeKey.set(Instant.now()));
        eventService.defaultPublisher().publish(event);
    }

    private void publishEncDemand(EncDemandMessage message) {

        log.info("Inside JPkEventHandlerActor: Publishing Enc Demand ");
        Key<Double> baseDoubleKey = JKeyType.DoubleKey().make("ecs.base");
        Key<Double> capDoubleKey = JKeyType.DoubleKey().make("ecs.cap");

        Event event = new SystemEvent(prefix, new EventName("encdemandpositions")).add(baseDoubleKey.set(message.getBase())).add(capDoubleKey.set(message.getCap()));

        eventService.defaultPublisher().publish(event);

    }

    private void publishM3Demand(M3DemandMessage message) {

        log.info("Inside JPkEventHandlerActor: Publishing M3 Demand ");
        Key<Double> rotationDoubleKey = JKeyType.DoubleKey().make("m3.rotation");
        Key<Double> tiltDoubleKey = JKeyType.DoubleKey().make("m3.tilt");

        Event event = new SystemEvent(prefix, new EventName("m3demandpositions")).add(rotationDoubleKey.set(message.getRotation())).add(tiltDoubleKey.set(message.getTilt()));;
        eventService.defaultPublisher().publish(event);
    }

    // add messages here
    public interface EventMessage {}

    public static final class McsDemandMessage implements EventMessage {
        private final double az;
        private final double el;

        public McsDemandMessage(double az, double el){
            this.az = az;
            this.el = el;
        }

        public double getAz() {
            return az;
        }

        public double getEl() {
            return el;
        }
    }

    public static final class EncDemandMessage implements EventMessage {
        private final double base;
        private final double cap;

        public EncDemandMessage(double base, double cap){
            this.base = base;
            this.cap = cap;
        }

        public double getBase() {
            return base;
        }

        public double getCap() {
            return cap;
        }
    }

    public static final class M3DemandMessage implements EventMessage {
        private final double rotation;
        private final double tilt;

        public M3DemandMessage(double rotation, double tilt){
            this.rotation = rotation;
            this.tilt = tilt;
        }

        public double getRotation() {
            return rotation;
        }

        public double getTilt() {
            return tilt;
        }
    }

}
