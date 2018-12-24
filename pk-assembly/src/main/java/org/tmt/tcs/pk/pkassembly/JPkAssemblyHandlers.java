package org.tmt.tcs.pk.pkassembly;

import akka.actor.ActorRefFactory;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Adapter;
import akka.stream.Materializer;
import com.typesafe.config.Config;
import csw.command.client.CommandResponseManager;
import csw.command.client.messages.TopLevelActorMessage;
import csw.command.client.models.framework.ComponentInfo;
import csw.config.api.javadsl.IConfigClientService;
import csw.config.api.models.ConfigData;
import csw.config.client.internal.ActorRuntime;
import csw.config.client.javadsl.JConfigClientFactory;
import csw.framework.exceptions.FailureStop;
import csw.framework.javadsl.JComponentHandlers;
import csw.framework.CurrentStatePublisher;
import csw.framework.models.JCswContext;
import csw.location.api.javadsl.ILocationService;
import csw.location.api.models.TrackingEvent;
import csw.logging.javadsl.ILogger;
import csw.params.commands.CommandResponse;
import csw.params.commands.CommandResponse.ValidateCommandResponse;
import csw.params.commands.CommandResponse.SubmitResponse;
import csw.params.commands.ControlCommand;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Domain specific logic should be written in below handlers.
 * This handlers gets invoked when component receives messages/commands from other component/entity.
 * For example, if one component sends Submit(Setup(args)) command to PkHcd,
 * This will be first validated in the supervisor and then forwarded to Component TLA which first invokes validateCommand hook
 * and if validation is successful, then onSubmit hook gets invoked.
 * You can find more information on this here : https://tmtsoftware.github.io/csw-prod/framework.html
 */
public class JPkAssemblyHandlers extends JComponentHandlers {

    private ILogger log;
    private CommandResponseManager commandResponseManager;
    private CurrentStatePublisher currentStatePublisher;
    private ActorContext<TopLevelActorMessage> actorContext;
    private ILocationService locationService;
    private ComponentInfo componentInfo;
    private IConfigClientService clientApi;

    private ActorRef<JPkCommandHandlerActor.CommandMessage> commandHandlerActor;
    private ActorRef<JPkLifecycleActor.LifecycleMessage> lifecycleActor;
    private ActorRef<JPkEventHandlerActor.EventMessage> eventHandlerActor;

    JPkAssemblyHandlers(ActorContext<TopLevelActorMessage> ctx, JCswContext cswContext) {
        super(ctx, cswContext);
        this.currentStatePublisher = cswContext.currentStatePublisher();
        this.log = cswContext.loggerFactory().getLogger(getClass());
        this.commandResponseManager = cswContext.commandResponseManager();
        this.actorContext = ctx;
        this.locationService = cswContext.locationService();
        this.componentInfo = cswContext.componentInfo();
        // Handle to the config client service
        clientApi = JConfigClientFactory.clientApi(Adapter.toUntyped(actorContext.getSystem()), locationService);
        // Load the configuration from the configuration service
        //Config assemblyConfig = getAssemblyConfig();
        lifecycleActor = ctx.spawnAnonymous(JPkLifecycleActor.behavior(cswContext.loggerFactory()));
        eventHandlerActor = ctx.spawnAnonymous(JPkEventHandlerActor.behavior(cswContext.eventService(), cswContext.loggerFactory()));
        commandHandlerActor = ctx.spawnAnonymous(JPkCommandHandlerActor.behavior(commandResponseManager, Boolean.TRUE, cswContext.loggerFactory(), eventHandlerActor));
    }

    @Override
    public CompletableFuture<Void> jInitialize() {
        return CompletableFuture.runAsync(() -> log.debug("Inside JPkAssemblyHandlers: initialize()"));
    }

    @Override
    public CompletableFuture<Void> jOnShutdown() {
        return CompletableFuture.runAsync(() -> log.debug("Inside JPkAssemblyHandlers: onShutdown()"));
    }

    @Override
    public void onLocationTrackingEvent(TrackingEvent trackingEvent) {
        log.debug("Inside JPkAssemblyHandlers: onLocationTrackingEvent()");
    }

    @Override
    public ValidateCommandResponse validateCommand(ControlCommand controlCommand) {
        log.debug("Inside JPkAssemblyHandlers: validateCommand()");
        return new CommandResponse.Accepted(controlCommand.runId());
    }

    @Override
    public SubmitResponse onSubmit(ControlCommand controlCommand) {
        log.debug("Inside JPkAssemblyHandlers: onSubmit()");

        commandHandlerActor.tell(new JPkCommandHandlerActor.SubmitCommandMessage(controlCommand));
        return new CommandResponse.Started(controlCommand.runId());
    }

    @Override
    public void onOneway(ControlCommand controlCommand) {
        log.debug("Inside JPkAssemblyHandlers: onOneway()");
    }

    @Override
    public void onGoOffline() {
        log.debug("Inside JPkAssemblyHandlers: onGoOffline()");

        commandHandlerActor.tell(new JPkCommandHandlerActor.GoOfflineMessage());
    }

    @Override
    public void onGoOnline() {
        log.debug("Inside JPkAssemblyHandlers: onGoOnline()");

        commandHandlerActor.tell(new JPkCommandHandlerActor.GoOnlineMessage());
    }

    public class ConfigNotAvailableException extends FailureStop {

        public ConfigNotAvailableException() {
            super("Inside JPkAssemblyHandlers: Configuration not available. Initialization failure.");
        }
    }

    private Config getAssemblyConfig() {

        try {
            ActorRefFactory actorRefFactory = Adapter.toUntyped(actorContext.getSystem());

            ActorRuntime actorRuntime = new ActorRuntime(Adapter.toUntyped(actorContext.getSystem()));

            Materializer mat = actorRuntime.mat();

            ConfigData configData = getAssemblyConfigData();

            return configData.toJConfigObject(mat).get();

        } catch (Exception e) {
            throw new ConfigNotAvailableException();
        }

    }

    private ConfigData getAssemblyConfigData() throws ExecutionException, InterruptedException {

        log.info("Inside JPkAssemblyHandlers: loading assembly configuration");

        // construct the path
        Path filePath = Paths.get("/org/tmt/tcs/tcs_test.conf");

        ConfigData activeFile = clientApi.getActive(filePath).get().get();

        return activeFile;
    }
}
