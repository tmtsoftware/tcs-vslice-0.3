package org.tmt.tcs;


import akka.Done;
import akka.actor.ActorSystem;
import akka.actor.typed.javadsl.Adapter;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.util.Timeout;
import csw.command.api.javadsl.ICommandService;
import csw.command.client.CommandServiceFactory;
import csw.event.api.javadsl.IEventService;
import csw.location.api.javadsl.ILocationService;
import csw.location.api.models.AkkaLocation;
import csw.location.api.models.ComponentId;
import csw.location.api.models.Connection;
import csw.location.client.javadsl.JHttpLocationServiceFactory;
import csw.location.server.commons.ClusterAwareSettings;
import csw.logging.internal.LoggingSystem;
import csw.logging.javadsl.ILogger;
import csw.logging.javadsl.JLoggerFactory;
import csw.logging.javadsl.JLoggingSystemFactory;
import csw.params.commands.CommandName;
import csw.params.commands.CommandResponse;
import csw.params.commands.Setup;
import csw.params.core.generics.Key;
import csw.params.core.models.Id;
import csw.params.core.models.ObsId;
import csw.params.core.models.Prefix;
import csw.params.javadsl.JKeyType;
import csw.params.javadsl.JUnits;
import scala.concurrent.duration.FiniteDuration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static csw.location.api.javadsl.JComponentType.Assembly;


public class ENCCommandsClient {

    Prefix source;
    ActorSystem system;
    ILocationService locationService;
    public static ILogger log;
    Optional<ICommandService> commandServiceOptional;
    IEventService eventService;
    PkClient pkClient;

    PrintStream printStream;

    public ENCCommandsClient(Prefix source, ActorSystem system, ILocationService locationService) throws Exception {
        this.source = source;
        this.system = system;
        this.locationService = locationService;
        commandServiceOptional = getAssemblyBlocking();
        pkClient   = new PkClient(new Prefix("tcs.pk"), system, locationService);
        File file = new File("Commmands_SimpleSimulator_Logs_"+Instant.now().toString()+"__.txt");
        file.createNewFile();
        this.printStream = new PrintStream(new FileOutputStream(file));
    }


    private Connection.AkkaConnection assemblyConnection = new Connection.AkkaConnection(new ComponentId("EncAssembly", Assembly));


    private Key<String> targetTypeKey = JKeyType.StringKey().make("targetType");
    private Key<Double> wavelengthKey = JKeyType.DoubleKey().make("wavelength");
    private Key<String> axesKey = JKeyType.StringKey().make("axes");

    private Key<Double> baseKey = JKeyType.DoubleKey().make("base");
    private Key<Double> capKey = JKeyType.DoubleKey().make("cap");
    private Key<String> mode = JKeyType.StringKey().make("mode");
    //private Key<Long>  time = JKeyType.LongKey().make("time");
    private Key<String> operation = JKeyType.StringKey().make("operation");
    private Key<Long> timeDuration = JKeyType.LongKey().make("timeDuration");


    /**
     * Gets a reference to the running assembly from the location service, if found.
     */

    private Optional<ICommandService> getAssemblyBlocking() throws Exception {

        Duration waitForResolveLimit = Duration.ofSeconds(30);

        Optional<AkkaLocation> resolveResult = locationService.resolve(assemblyConnection, waitForResolveLimit).get();

        if (resolveResult.isPresent()) {

            AkkaLocation akkaLocation = resolveResult.get();

            return Optional.of(CommandServiceFactory.jMake(resolveResult.get(),Adapter.toTyped(system)));

        } else {
            return Optional.empty();
        }
    }

    /**
     * Sends a datum message to the Assembly and returns the response
     */
    public CompletableFuture<CommandResponse.SubmitResponse> datum(Optional<ObsId> obsId) {

        //Optional<ICommandService> commandServiceOptional = getAssemblyBlocking();

        if (commandServiceOptional.isPresent()) {

            ICommandService commandService = commandServiceOptional.get();

            Setup setup = new Setup(source, new CommandName("datum"), obsId);


            return commandService.submit(setup, Timeout.durationToTimeout(FiniteDuration.apply(20, TimeUnit.SECONDS)));

        } else {

            return CompletableFuture.completedFuture(new CommandResponse.Error(new Id(""), "Can't locate Assembly"));
        }


    }

    /**
     * Sends a move message to the Assembly and returns the response
     */
    public CompletableFuture<CommandResponse.SubmitResponse> move(Optional<ObsId> obsId, Double base, Double cap, String operationValue, String modeValue) {

        if (commandServiceOptional.isPresent()) {

            ICommandService commandService = commandServiceOptional.get();
            Long[] timeDurationValue = new Long[1];
            timeDurationValue[0] = 10L;

            Setup setup = new Setup(source, new CommandName("move"), obsId)
                    .add(operation.set(operationValue))
                    .add(baseKey.set(base))
                    .add(capKey.set(cap))
                    .add(mode.set(modeValue))
                    .add(timeDuration.set(timeDurationValue, JUnits.second));
            log.debug("Submitting move command to assembly...");

            return commandService.submit(setup, Timeout.durationToTimeout(FiniteDuration.apply(20, TimeUnit.SECONDS)));

        } else {

            return CompletableFuture.completedFuture(new CommandResponse.Error(new Id(""), "Can't locate Assembly"));
        }


    }

    /**
     * Sends a invalid move message to the Assembly and returns the response
     * this move command does not have "mode" parameter.
     */
    public CompletableFuture<CommandResponse.SubmitResponse> moveInvalid(Optional<ObsId> obsId, Double base, Double cap, String operationValue, String modeValue) {

        if (commandServiceOptional.isPresent()) {

            ICommandService commandService = commandServiceOptional.get();
            Long[] timeValue = new Long[1];
            timeValue[0] = 10L;

            Setup setup = new Setup(source, new CommandName("move"), obsId)
                    .add(operation.set(operationValue))
                    .add(baseKey.set(base))
                    .add(capKey.set(cap))
                    .add(timeDuration.set(timeValue, JUnits.second));
            log.debug("Submitting invalid move command to assembly...");
            return commandService.submit(setup, Timeout.durationToTimeout(FiniteDuration.apply(20, TimeUnit.SECONDS)));

        } else {

            return CompletableFuture.completedFuture(new CommandResponse.Error(new Id(""), "Can't locate Assembly"));
        }


    }

    /**
     * Sends a follow message to the Assembly and returns the response.
     * This command execute as immediate command.
     */
    public CompletableFuture<CommandResponse.SubmitResponse> follow(Optional<ObsId> obsId) {

        if (commandServiceOptional.isPresent()) {

            ICommandService commandService = commandServiceOptional.get();
            Long[] timeDurationValue = new Long[1];
            timeDurationValue[0] = 10L;

            Setup setup = new Setup(source, new CommandName("follow"), obsId);
            log.debug("Submitting follow command to assembly...");
            return commandService.submit(setup, Timeout.durationToTimeout(FiniteDuration.apply(20, TimeUnit.SECONDS)));

        } else {

            return CompletableFuture.completedFuture(new CommandResponse.Error(new Id(""), "Can't locate Assembly"));
        }


    }

    /**
     * Sends StartUp command to assembly to transit from initialization state to running state.
     */
    public CompletableFuture<CommandResponse.SubmitResponse> startup(Optional<ObsId> obsId) {

        if (commandServiceOptional.isPresent()) {

            ICommandService commandService = commandServiceOptional.get();
            Long[] timeDurationValue = new Long[1];
            timeDurationValue[0] = 10L;

            Setup setup = new Setup(source, new CommandName("startup"), obsId);
            log.debug("Submitting startup command to assembly...");
            return commandService.submit(setup, Timeout.durationToTimeout(FiniteDuration.apply(20, TimeUnit.SECONDS)));

        } else {

            return CompletableFuture.completedFuture(new CommandResponse.Error(new Id(""), "Can't locate Assembly"));
        }


    }

    /**
     * Sends StartUp command to assembly to transit from initialization state to running state.
     */
    public CompletableFuture<CommandResponse.SubmitResponse> shutdown(Optional<ObsId> obsId) {
        if (commandServiceOptional.isPresent()) {
            ICommandService commandService = commandServiceOptional.get();
            Setup setup = new Setup(source, new CommandName("shutdown"), obsId);
            log.debug("Submitting shutdown command to assembly...");
            return commandService.submit(setup, Timeout.durationToTimeout(FiniteDuration.apply(20, TimeUnit.SECONDS)));

        } else {

            return CompletableFuture.completedFuture(new CommandResponse.Error(new Id(""), "Can't locate Assembly"));
        }


    }

    /**
     * Sends AssemblyTestCommand to assembly.
     */
    public CompletableFuture<CommandResponse.SubmitResponse> submitAssemblyTestCommand(Optional<ObsId> obsId) {
        if (commandServiceOptional.isPresent()) {
            ICommandService commandService = commandServiceOptional.get();
            Setup setup = new Setup(source, new CommandName("assemblyTestCommand"), obsId);
            return commandService.submit(setup, Timeout.durationToTimeout(FiniteDuration.apply(20, TimeUnit.SECONDS)));
        } else {
            return CompletableFuture.completedFuture(new CommandResponse.Error(new Id(""), "Can't locate Assembly"));
        }
    }
    /**
     * Sends HcdTestCommand to assembly.
     */
    public CompletableFuture<CommandResponse.SubmitResponse> submitHcdTestCommand(Optional<ObsId> obsId) {
        if (commandServiceOptional.isPresent()) {
            ICommandService commandService = commandServiceOptional.get();
            Setup setup = new Setup(source, new CommandName("hcdTestCommand"), obsId);
            return commandService.submit(setup, Timeout.durationToTimeout(FiniteDuration.apply(20, TimeUnit.SECONDS)));
        } else {
            return CompletableFuture.completedFuture(new CommandResponse.Error(new Id(""), "Can't locate Assembly"));
        }
    }

    private static Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) throws Exception {
        ActorSystem system = ClusterAwareSettings.system();
        Materializer mat = ActorMaterializer.create(system);
        ILocationService locationService = JHttpLocationServiceFactory.makeLocalClient(system, mat);

        ENCCommandsClient encClient = new ENCCommandsClient(new Prefix("enc.enc-client"), system, locationService);

        Optional<ObsId> maybeObsId = Optional.empty();
        String hostName = InetAddress.getLocalHost().getHostName();
        LoggingSystem loggingSystem = JLoggingSystemFactory.start("ENCCommandsClient", "0.1", hostName, system);
        log = new JLoggerFactory("client-app").getLogger(ENCCommandsClient.class);

       // JLoggingSystemFactory.start("client-app", "0.1", hostName, system);
        log.info(() -> "TCS Client Starting..");

        boolean keepRunning = true;
        while (keepRunning) {
            log.info(() -> "Type command name [startup, invalidMove, move, follow, shutdown, takeCommandMeasures] or type 'exit' to stop client");

            String commandName = scanner.nextLine();
            switch (commandName) {
                case "startup":
                    log.info(() -> "Sending startup command to enclosure assembly.. ");
                    CompletableFuture<CommandResponse.SubmitResponse> startUpCmdResponse = encClient.startup(maybeObsId);
                    log.info("Response on  startup command: " + startUpCmdResponse.get()+ ", Time Taken - ");
                    break;
                case "shutdown":
                    log.info(() -> "Sending shutdown command to enclosure assembly.. ");
                    CompletableFuture<CommandResponse.SubmitResponse> shutdownCmdResponse = encClient.shutdown(maybeObsId);
                    log.info("Response on  shutdown command: " + shutdownCmdResponse.get());
                    break;
                case "move":
                    log.info(() -> "Commanding enclosure to move  fast: ");
                    CompletableFuture<CommandResponse.SubmitResponse> moveCmdResponse = encClient.move(maybeObsId, 2.34, 5.67, "On", "fast");
                    CommandResponse respMoveCmd = moveCmdResponse.get();
                    log.info(() -> "Enclosure moved: " + respMoveCmd);
                    break;
                case "follow":
                    log.info(() -> "Commanding enclosure with Follow Command: ");
                    CompletableFuture<CommandResponse.SubmitResponse> followCmdResponse = encClient.follow(maybeObsId);
                    CommandResponse respFollowCmd = followCmdResponse.get();
                    log.info(() -> "Enclosure Follow: " + respFollowCmd);
                    break;
                case "invalidMove":
                    log.info(() -> "Commanding enclosure to move with invalid param: ");
                    CompletableFuture<CommandResponse.SubmitResponse> invalidMoveCmdResponse = encClient.moveInvalid(maybeObsId, 2.34, 5.67, "On", "fast");
                    log.info("Response on invalid move command: " + invalidMoveCmdResponse.get());
                    break;
                case "assemblyTestCommand":
                    log.info(() -> "Sending AssemblyTestCommand");
                    CompletableFuture<CommandResponse.SubmitResponse> assemblyCmdResponse = encClient.submitAssemblyTestCommand(maybeObsId);
                    log.info("CommandResponse: " + assemblyCmdResponse.get());
                    break;
                case "hcdTestCommand":
                    log.info(() -> "Sending HcdTestCommand");
                    CompletableFuture<CommandResponse.SubmitResponse> hcdCmdResponse = encClient.submitHcdTestCommand(maybeObsId);
                    log.info("CommandResponse: " + hcdCmdResponse.get());
                    break;
                case "takeCommandMeasures":
                    log.info(() -> "Starting command performance test");
                    encClient.takeCommandMeasures();
                    log.info("Performance measure test completed");
                    break;
                case "takeEventMeasures":
                    log.info(() -> "Starting command performance test");
                    encClient.takeEventMeasures();
                    log.info("Performance measure test completed");
                    break;
                case "exit":
                    keepRunning = false;
                    break;
                default:
                    log.info(commandName + "   - Is not a valid choice");
            }
        }

        Done done = loggingSystem.javaStop().get();
        system.terminate();

    }

    private void takeCommandMeasures() throws ExecutionException, InterruptedException {
        for (int i =0;i<5000;i++){
            Instant startTime = Instant.now();
            CommandResponse startUpCmdResponse = this.startup(Optional.empty()).get();
            Duration startupCommandDuration = Duration.between(startTime, Instant.now());
            Thread.sleep(10);

            Instant step1Time = Instant.now();
            CommandResponse assemblyCmdResponse = this.submitAssemblyTestCommand(Optional.empty()).get();
            Duration assemblyCommandDuration = Duration.between(step1Time,  Instant.now());
            Thread.sleep(10);

            Instant step2Time = Instant.now();
            CommandResponse hcdCmdResponse = this.submitHcdTestCommand(Optional.empty()).get();
            Duration hcdCommandDuration = Duration.between(step2Time, Instant.now());
            Thread.sleep(10);

            Instant step3Time = Instant.now();
            CommandResponse shutdownCmdResponse = this.shutdown(Optional.empty()).get();
            Duration shutdownCommandDuration = Duration.between(step3Time, Instant.now());
            Thread.sleep(10);

            log.info(()->"Time taken by startup command(ms)="+ startupCommandDuration.toMillis()
                    + ", Time taken by assembly command(ms)=" + assemblyCommandDuration.toMillis()
                    + ", Time taken by HCD command(ms)=" + hcdCommandDuration.toMillis()
                    + ", Time taken by shutdown command(ms)=" + shutdownCommandDuration.toMillis());

            this.printStream.println("Time taken by startup command(ms)="+ startupCommandDuration.toMillis()
                    + ", Time taken by assembly command(ms)=" + assemblyCommandDuration.toMillis()
                    + ", Time taken by HCD command(ms)=" + hcdCommandDuration.toMillis()
                    + ", Time taken by shutdown command(ms)=" + shutdownCommandDuration.toMillis());
        }
    }

    private CommandResponse startPkPositionDemands() throws Exception {
        Optional<ObsId> maybeObsId          = Optional.empty();
        CompletableFuture<CommandResponse.SubmitResponse> cf1 = pkClient.setTarget(maybeObsId, 185.79, 6.753333);
        CommandResponse resp1 = cf1.get();
        System.out.println("Inside PkClientApp: setTarget response is: " + resp1);
        return resp1;
    }
    private void takeEventMeasures() throws Exception {
            CommandResponse startUpCmdResponse = this.startup(Optional.empty()).get();
            CommandResponse pkSetTargetResponse = this.startPkPositionDemands();

    }
}






