package org.tmt.tcs;

import akka.Done;
import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import csw.event.api.javadsl.IEventService;
import csw.event.api.javadsl.IEventSubscriber;
import csw.event.api.javadsl.IEventSubscription;
import csw.event.client.EventServiceFactory;
import csw.location.api.javadsl.ILocationService;
import csw.location.client.javadsl.JHttpLocationServiceFactory;
import csw.location.server.commons.ClusterAwareSettings;
import csw.logging.internal.LoggingSystem;
import csw.logging.javadsl.ILogger;
import csw.logging.javadsl.JLoggerFactory;
import csw.logging.javadsl.JLoggingSystemFactory;
import csw.params.core.generics.Parameter;
import csw.params.core.models.Prefix;
import csw.params.events.Event;
import csw.params.events.EventKey;
import csw.params.events.EventName;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;

/**
 * This class provide subscription to various events from ENC Assembly.
 * It provides performance measurements of event propagation in logs.
 */
public class ENCEventsClient {

        Prefix source;
        ActorSystem system;
        ILocationService locationService;
        public  static ILogger log;
        public static Scanner scanner = new Scanner(System.in);

        IEventService eventService;

        String assemblyState = "Unknown";
        String health="Unknown";
        String diagnostic="Unknown";
        PrintStream  printStream;

        public ENCEventsClient(ActorSystem system, ILocationService locationService) throws Exception {
            this.source = new Prefix("enc.enc-event-client");
            this.system = system;
            this.locationService = locationService;
            this.eventService = getEventServiceInstance(locationService, system);

            File file = new File("CurrentPosition_SimpleSimulator_Logs_"+Instant.now().toString()+"__.txt");
            file.createNewFile();
            this.printStream = new PrintStream(new FileOutputStream(file));
        }
        /**
         * Use this method to get an instance of EventService
         * @param locationService
         * @param system
         * @return
         */
        private static IEventService getEventServiceInstance(ILocationService locationService, ActorSystem system){
            return new EventServiceFactory().jMake(locationService, system);
        }

    /**
     * This method subscribe to current position event and register a callback function.
     * @return
     */
        private IEventSubscription subscribeCurrentPosition(){
            IEventSubscriber subscriber = eventService.defaultSubscriber();
            EventKey currentPositionEventKey = new EventKey(new Prefix("tmt.tcs.ecs"), new EventName("currentPosition"));
            return subscriber.subscribeAsync(Collections.singleton(currentPositionEventKey), this::currentPositionCallback);
        }

    /**
     * This method gets called for each current position event.
     * @param event
     * @return
     */
        private CompletableFuture<String> currentPositionCallback(Event event){
            Instant clientInstantTime = Instant.now();
            Parameter basePosParam = event.paramSet().find(x -> x.keyName().equals("basePosKey")).get();
            Parameter capPosParam = event.paramSet().find(x -> x.keyName().equals("capPosKey")).get();
            Parameter subsystemTimestampParam = event.paramSet().find(x -> x.keyName().equals("subsystemTimestampKey")).get();
            Parameter hcdTimestampParam = event.paramSet().find(x -> x.keyName().equals("hcdTimestampKey")).get();
            Parameter assemblyTimestampParam = event.paramSet().find(x -> x.keyName().equals("assemblyTimestampKey")).get();

            Instant subsystemInstantTime = (Instant) subsystemTimestampParam.value(0);
            Instant hcdInstantTime = (Instant) hcdTimestampParam.value(0);
            Instant assemblyInstantTime = (Instant) assemblyTimestampParam.value(0);

            long hcdToClientDuration = Duration.between(hcdInstantTime, clientInstantTime).toNanos();
            long subsystemToClientDuration = Duration.between(subsystemInstantTime, clientInstantTime).toNanos();
            long hcdToAssemblyDuration = Duration.between(hcdInstantTime, assemblyInstantTime).toNanos();

          //  System.out.print("\r"+event.eventName().name()+", base="+ basePosParam.value(0) + ", cap="+ capPosParam.value(0) + ", subsystem timestamp - " + subsystemInstantTime + ", HCD timestamp- " + hcdInstantTime+ ", Assembly timestamp- " + assemblyInstantTime + ", Client timestamp- " + clientInstantTime + ", Time taken(HCD to Client) - " + hcdToClientDuration + "ms, Time taken(Subsystem to Client) - " + subsystemToClientDuration + "ms, Time taken(HCD to Assembly) - " + hcdToAssemblyDuration+"ms" + " , "+assemblyState+ ", " + health+" , "+ diagnostic);
            log.info(()->"Event="+event.eventName().name()+", base="+ basePosParam.value(0) + ", cap="+ capPosParam.value(0) + ", subsystem time=" + subsystemInstantTime + ", hcd time=" + hcdInstantTime+ ", assembly time=" + assemblyInstantTime + ", subscriber time=" + clientInstantTime + ", Duration(hcd to subscriber in ms)=" + hcdToClientDuration + ", Duration(subsystem to subscriber in ms)=" + subsystemToClientDuration + ", Duration(hcd to assembly in ms)=" + hcdToAssemblyDuration);
           // this.printStream.println("Event="+event.eventName().name()+", base="+ basePosParam.value(0) + ", cap="+ capPosParam.value(0) + ", subsystem time=" + subsystemInstantTime + ", hcd time=" + hcdInstantTime+ ", assembly time=" + assemblyInstantTime + ", subscriber time=" + clientInstantTime + ", Duration(hcd to subscriber in ms)=" + hcdToClientDuration + ", Duration(subsystem to subscriber in ms)=" + subsystemToClientDuration + ", Duration(hcd to assembly in ms)=" + hcdToAssemblyDuration);
            this.printStream.println(subsystemInstantTime + ", " + hcdInstantTime+ ", " + assemblyInstantTime + ", " + clientInstantTime);
            return CompletableFuture.completedFuture("Ok");
        }


    /**
     * This method subscribe to assembly state event and register a callback function.
     * @return
     */
    private IEventSubscription subscribeAssemblyState(){
        IEventSubscriber subscriber = eventService.defaultSubscriber();
        EventKey assemblyStateEventKey = new EventKey(new Prefix("tmt.tcs.ecs"), new EventName("assemblyState"));
        return subscriber.subscribeAsync(Collections.singleton(assemblyStateEventKey), this::currentAssemblyStateCallback);
    }

    /**
     * This method gets called for each assembly state event.
     * @param event
     * @return
     */
    private CompletableFuture<String> currentAssemblyStateCallback(Event event){
        Parameter lifecycleStateParam = event.paramSet().find(x -> x.keyName().equals("LifecycleState")).get();
        Parameter operationalStateParam = event.paramSet().find(x -> x.keyName().equals("OperationalState")).get();
        Parameter assemblyStateTimeParam = event.paramSet().find(x -> x.keyName().equals("assemblyStateTimeKey")).get();
        Instant assemblyStateTime = (Instant) assemblyStateTimeParam.value(0);

        //log.info(()->event.eventName().name()+", "+ lifecycleStateParam.value(0) + ", "+ operationalStateParam.value(0) + ", " + assemblyStateTime + ", " + "-"+ ", " + "-" + ", " + "-" + ", " + "-" + ", " + "-" + ", " + "-");
        log.info(()->"Event="+event.eventName().name()+", Lifecycle state="+ lifecycleStateParam.value(0) + ", operational state="+ operationalStateParam.value(0) + ", subsystem time=" + "NA" + ", hcd time=" + "NA"+ ", assembly time=" + assemblyStateTime + ", subscriber time=" + "NA" + ", Duration(hcd to subscriber in ms)=" + "NA" + ", Duration(subsystem to subscriber in ms)=" + "NA" + ", Duration(hcd to assembly in ms)=" + "NA");
        //assemblyState = "Lifecycle state="+ lifecycleStateParam.value(0) + ", Operational state="+ operationalStateParam.value(0) + ", State event time=" + event.eventTime().time();
        return CompletableFuture.completedFuture("Ok");
    }

    /**
     * This method subscribe to health event and register a callback function.
     * @return
     */
    private IEventSubscription subscribeHealth(){
        IEventSubscriber subscriber = eventService.defaultSubscriber();
        EventKey healthEventKey = new EventKey(new Prefix("tmt.tcs.ecs"), new EventName("health"));
        return subscriber.subscribeAsync(Collections.singleton(healthEventKey), this::healthCallback);
    }

    /**
     * This method gets called for each health event.
     * @param event
     * @return
     */
    private CompletableFuture<String> healthCallback(Event event){
        //log.info("health event received - " + event);
        Parameter healthParam = event.paramSet().find(x -> x.keyName().equals("healthKey")).get();
        Parameter healthReasonParam = event.paramSet().find(x -> x.keyName().equals("healthReasonKey")).get();
        Parameter healthTimeParam = event.paramSet().find(x -> x.keyName().equals("healthTimeKey")).get();
        Parameter assemblyTimestampParam = event.paramSet().find(x -> x.keyName().equals("assemblyTimestampKey")).get();

        Instant assemblyInstantTime = (Instant) assemblyTimestampParam.value(0);
        Instant healthTime = (Instant) healthTimeParam.value(0);

        //log.info(()->event.eventName().name()+", "+ healthParam.value(0) + ", "+ healthReasonParam.value(0) + ", " + healthTime + ", " + "-"+ ", " + assemblyInstantTime + ", " + "-" + ", " + "-" + ", " + "-" + ", " + "-");
        log.info(()->"Event="+event.eventName().name()+", health="+ healthParam.value(0) + ", reason="+ healthReasonParam.value(0) + ", subsystem time=" + healthTime + ", hcd time=" + "NA"+ ", assembly time=" + assemblyInstantTime + ", subscriber time=" + "NA" + ", Duration(hcd to subscriber in ms)=" + "NA" + ", Duration(subsystem to subscriber in ms)=" + "NA" + ", Duration(hcd to assembly in ms)=" + "NA");
        //health = "Health="+ healthParam.value(0) + ", Health Reason="+ healthReasonParam.value(0) + ", health event time=" + event.eventTime().time();
        return CompletableFuture.completedFuture("Ok");
    }

    /**
     * This method subscribe to diagnostic event and register a callback function.
     * @return
     */
    private IEventSubscription subscribeDiagnostic(){
        IEventSubscriber subscriber = eventService.defaultSubscriber();
        EventKey healthEventKey = new EventKey(new Prefix("tmt.tcs.ecs"), new EventName("diagnostic"));
        return subscriber.subscribeAsync(Collections.singleton(healthEventKey), this::diagnosticCallback);
    }

    /**
     * This method gets called for each diagnostic event.
     * @param event
     * @return
     */
    private CompletableFuture<String> diagnosticCallback(Event event){
        //log.info("diagnostic event received - " + event);
        Parameter diagnosticBytesParam = event.paramSet().find(x -> x.keyName().equals("diagnosticBytesKey")).get();
        Parameter diagnosticTimeParam = event.paramSet().find(x -> x.keyName().equals("diagnosticTimeKey")).get();

        Instant diagnosticTime = (Instant) diagnosticTimeParam.value(0);

        //log.info(()->event.eventName().name()+", "+ diagnosticBytesParam.value(0) + ", "+ "-" + ", " + diagnosticTime + ", " + "-"+ ", " + "-" + ", " + "-" + ", " + "-" + ", " + "-" + ", " + "-");
        log.info(()->"Event="+event.eventName().name()+", diagnostics="+ diagnosticBytesParam.value(0) + ", param2="+ "NA" + ", subsystem time=" + diagnosticTime + ", hcd time=" + "NA"+ ", assembly time=" + "NA" + ", subscriber time=" + "NA" + ", Duration(hcd to subscriber in ms)=" + "NA" + ", Duration(subsystem to subscriber in ms)=" + "NA" + ", Duration(hcd to assembly in ms)=" + "NA");
        //diagnostic = "Diagnostic Bytes="+ diagnosticBytesParam.value(0) + ", diagnostic event time="+event.eventTime().time();
        return CompletableFuture.completedFuture("Ok");
    }


    private IEventSubscription subscribeEncDemandsPositions(){
        IEventSubscriber subscriber = eventService.defaultSubscriber();
        EventKey eventKey = new EventKey(new Prefix("tcs.pk"), new EventName("encdemandpositions"));
        return subscriber.subscribeAsync(Collections.singleton(eventKey), this::demandPositionsCallback);
    }


    private CompletableFuture<String> demandPositionsCallback(Event event){
        Parameter baseParam = event.paramSet().find(x -> x.keyName().equals("ecs.base")).get();
        Parameter capParam = event.paramSet().find(x -> x.keyName().equals("ecs.cap")).get();
        log.info(()->event.eventName().name()+", "+ baseParam.value(0) + ", "+ capParam.value(0) + ", " + "-" + ", " + "-"+ ", " + "-" + ", " + "-" + ", " + "-" + ", " + "-" + ", " + "-");
        return CompletableFuture.completedFuture("Ok");
    }


        public static void main(String[] args) throws Exception {
            ActorSystem system = ClusterAwareSettings.system();
            Materializer mat = ActorMaterializer.create(system);
            ILocationService locationService = JHttpLocationServiceFactory.makeLocalClient(system, mat);

            //Optional<ObsId> maybeObsId = Optional.empty();
            String hostName = InetAddress.getLocalHost().getHostName();
            LoggingSystem loggingSystem = JLoggingSystemFactory.start("ENCEventClient", "0.1", hostName, system);
            log = new JLoggerFactory("enc-event-client-app").getLogger(ENCEventsClient.class);

            ENCEventsClient client = new ENCEventsClient(system, locationService);
            IEventSubscription currentPositionSubscription = client.subscribeCurrentPosition();
            IEventSubscription assemblyStateSubscription = client.subscribeAssemblyState();
            IEventSubscription healthSubscription = client.subscribeHealth();
            //IEventSubscription positionDemandsSubscription = client.subscribeEncDemandsPositions();
            IEventSubscription diagnosticSubscription = client.subscribeDiagnostic();

            log.info(() -> "Press any key to terminate");

            String s = scanner.nextLine();
            currentPositionSubscription.unsubscribe();
            assemblyStateSubscription.unsubscribe();
            healthSubscription.unsubscribe();
            //positionDemandsSubscription.unsubscribe();
            diagnosticSubscription.unsubscribe();
            Done done = loggingSystem.javaStop().get();
            system.terminate();

        }


    }
