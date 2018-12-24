package org.tmt.encsubsystem.encassembly;

import akka.actor.ActorRefFactory;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import akka.stream.Materializer;
import akka.util.Timeout;
import com.typesafe.config.Config;
import csw.command.api.javadsl.ICommandService;
import csw.config.api.models.ConfigData;
import csw.config.client.internal.ActorRuntime;
import csw.framework.exceptions.FailureStop;
import csw.framework.models.JCswContext;
import csw.logging.javadsl.ILogger;
import csw.params.commands.ControlCommand;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
//import akka.actor.typed.javadsl.AbstractBehavior;

/**
 * Lifecycle Actor receive lifecycle messages and perform initialization, config loading, shutdown operations.
 */
public class JLifecycleActor extends AbstractBehavior<JLifecycleActor.LifecycleMessage> {


    // add messages here
    interface LifecycleMessage {
    }

    public static final class InitializeMessage implements LifecycleMessage {
        public final CompletableFuture<Void> cf;

        public InitializeMessage(CompletableFuture<Void> cf) {
            this.cf = cf;
        }
    }

    public static final class ShutdownMessage implements LifecycleMessage {
        public final CompletableFuture<Void> cf;
        public ShutdownMessage(CompletableFuture<Void> cf) {
            this.cf =cf;
        }
    }

    public static final class SubmitCommandMessage implements LifecycleMessage {

        public final ControlCommand controlCommand;


        public SubmitCommandMessage(ControlCommand controlCommand) {
            this.controlCommand = controlCommand;
        }
    }

    public static final class UpdateHcdCommandServiceMessage implements LifecycleMessage {

        public final Optional<ICommandService> commandServiceOptional;

        public UpdateHcdCommandServiceMessage(Optional<ICommandService> commandServiceOptional) {
            this.commandServiceOptional = commandServiceOptional;
        }
    }


    private ActorContext<LifecycleMessage> actorContext;
    JCswContext cswCtx;
    //private Config assemblyConfig;
    private ILogger log;
    private Optional<ICommandService> hcdCommandService;
    ActorRef<JCommandHandlerActor.CommandMessage> commandHandlerActor;
    ActorRef<JEventHandlerActor.EventMessage> eventHandlerActor;


    private JLifecycleActor(ActorContext<LifecycleMessage> actorContext, JCswContext cswCtx, Optional<ICommandService> hcdCommandService, ActorRef<JCommandHandlerActor.CommandMessage> commandHandlerActor, ActorRef<JEventHandlerActor.EventMessage> eventHandlerActor) {
        this.actorContext = actorContext;this.cswCtx = cswCtx;
        this.cswCtx = cswCtx;
        this.log = cswCtx.loggerFactory().getLogger(JEventHandlerActor.class);
        this.hcdCommandService = hcdCommandService;
        this.commandHandlerActor = commandHandlerActor;
        this.eventHandlerActor = eventHandlerActor;

    }

    public static <LifecycleMessage> Behavior<LifecycleMessage> behavior(JCswContext cswCtx, Optional<ICommandService> hcdCommandService,ActorRef<JCommandHandlerActor.CommandMessage> commandHandlerActor, ActorRef<JEventHandlerActor.EventMessage> eventHandlerActor) {
        return Behaviors.setup(ctx -> {
            return (AbstractBehavior<LifecycleMessage>) new JLifecycleActor((ActorContext<JLifecycleActor.LifecycleMessage>) ctx, cswCtx, hcdCommandService, commandHandlerActor, eventHandlerActor);
        });
    }

    /**
     * This method receives messages sent to actor.
     * based on message type it forward message to its dedicated handler method.
     * @return
     */
    @Override
    public Receive<LifecycleMessage> createReceive() {

        ReceiveBuilder<LifecycleMessage> builder = receiveBuilder()
                .onMessage(InitializeMessage.class,
                        command -> {
                            log.debug(() -> "InitializeMessage Received");
                            onInitialize(command);
                            return Behaviors.same();
                        })
                .onMessage(ShutdownMessage.class,
                        shutdownMessage -> {
                            log.debug(() -> "ShutdownMessage Received");
                            onShutdown(shutdownMessage);
                            return Behaviors.same();
                        })
                .onMessage(UpdateHcdCommandServiceMessage.class,
                        command -> {
                            log.debug(() -> "UpdateTemplateHcdMessage Received");
                            // update the template hcd
                            return behavior(cswCtx, hcdCommandService, commandHandlerActor, eventHandlerActor);
                        });
        return builder.build();
    }

    /**
     * This is called as part of csw component initialization
     * lifecycle actor will perform initialization activities like config loading inside it.
     * @param message
     */
    private void onInitialize(InitializeMessage message) {
        log.debug(() -> "Initialize Message Received ");
        eventHandlerActor.tell(new JEventHandlerActor.PublishAssemblyStateMessage());//change to ask pattern?
        eventHandlerActor.tell(new JEventHandlerActor.SubscribeEventMessage());
        Config assemblyConfig = getAssemblyConfig();
        // example of working with Config
        Double ventopenpercentage = assemblyConfig.getDouble("ventopenpercentage");
        log.debug(() -> "ventopenpercentage element value is: " + ventopenpercentage);
        //providing configuration to command actor for use in command.
        commandHandlerActor.tell(new JCommandHandlerActor.UpdateConfigMessage(Optional.of(assemblyConfig)));
        message.cf.complete(null);

    }

    /**
     * This is called as part of csw component shutdown.
     * Lifecycle actor will perform shutdown activities like releasing occupied resources if any, stop publishing events
     * @param message
     */
    private void onShutdown(ShutdownMessage message) {
        log.debug(() -> "Shutdown Message Received ");
        try {
            String cfString= AskPattern.ask(eventHandlerActor, (ActorRef<String> replyTo)->
                    new JEventHandlerActor.StopEventsMessage(replyTo), new Timeout(10, TimeUnit.SECONDS) , actorContext.getSystem().scheduler()
            ).toCompletableFuture().get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        message.cf.complete(null);

    }

    /**
     * This method load assembly configuration.
     *
     * @return
     */
    private Config getAssemblyConfig() {

        try {
            ActorRefFactory actorRefFactory = Adapter.toUntyped(actorContext.getSystem());

            ActorRuntime actorRuntime = new ActorRuntime(Adapter.toUntyped(actorContext.getSystem()));

            Materializer mat = actorRuntime.mat();

            ConfigData configData = getAssemblyConfigData();

            return configData.toJConfigObject(mat).get();

        } catch (Exception e) {
            throw new JLifecycleActor.ConfigNotAvailableException();
        }

    }

    private ConfigData getAssemblyConfigData() throws ExecutionException, InterruptedException {

        log.debug(() -> "loading assembly configuration");

        // construct the path
        Path filePath = Paths.get("/org/tmt/tcs/enc/enc_assembly.conf");

        ConfigData activeFile = cswCtx.configClientService().getActive(filePath).get().get();

        return activeFile;
    }

    public class ConfigNotAvailableException extends FailureStop {

        public ConfigNotAvailableException() {
            super("Configuration not available. Initialization failure.");
        }
    }


}
