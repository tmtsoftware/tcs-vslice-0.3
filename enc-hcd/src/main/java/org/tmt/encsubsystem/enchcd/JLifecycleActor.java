package org.tmt.encsubsystem.enchcd;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import akka.stream.Materializer;
import com.typesafe.config.Config;
import csw.config.api.javadsl.IConfigClientService;
import csw.config.api.models.ConfigData;
import csw.config.client.internal.ActorRuntime;
import csw.framework.exceptions.FailureStop;
import csw.framework.models.JCswContext;
import csw.logging.javadsl.ILogger;
import csw.params.commands.ControlCommand;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
//import akka.actor.typed.javadsl.AbstractBehavior;

public class JLifecycleActor extends AbstractBehavior<JLifecycleActor.LifecycleMessage> {
    interface LifecycleMessage {
    }

    public static final class InitializeMessage implements LifecycleMessage {
        public final CompletableFuture<Void> cf;

        public InitializeMessage(CompletableFuture<Void> cf) {
            this.cf = cf;
        }
    }

    public static final class ShutdownMessage implements LifecycleMessage {
    }

    public static final class SubmitCommandMessage implements LifecycleMessage {

        public final ControlCommand controlCommand;


        public SubmitCommandMessage(ControlCommand controlCommand) {
            this.controlCommand = controlCommand;
        }
    }


    private ActorContext<LifecycleMessage> actorContext;
    JCswContext cswCtx;
    ;
    //private Config assemblyConfig;
    private ILogger log;
    private IConfigClientService configClientApi;

    ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor;


    private JLifecycleActor(ActorContext<LifecycleMessage> actorContext, JCswContext cswCtx,  ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor ) {
        this.actorContext = actorContext;this.cswCtx = cswCtx;

          this.log = cswCtx.loggerFactory().getLogger(JLifecycleActor.class);
        this.configClientApi = cswCtx.configClientService();

        this.statePublisherActor = statePublisherActor;
    }

    public static <LifecycleMessage> Behavior<LifecycleMessage> behavior(JCswContext cswCtx, ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor ) {
        return Behaviors.setup(ctx -> {
            return (AbstractBehavior<LifecycleMessage>) new JLifecycleActor((ActorContext<JLifecycleActor.LifecycleMessage>) ctx, cswCtx,  statePublisherActor);
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
                        command -> {
                            log.debug(() -> "ShutdownMessage Received");
                            onShutdown(command);
                            return Behaviors.same();
                        });

        return builder.build();
    }

    private void onInitialize(InitializeMessage message) {

        log.debug(() -> "Initialize Message Received ");
        Config assemblyConfig = getHCDConfig();

        // example of working with Config
        String ethernetaddress = assemblyConfig.getString("ethernetaddress");

        log.debug(() -> "ethernetaddress config element value is: " + ethernetaddress);
        statePublisherActor.tell(new JStatePublisherActor.StartMessage());
        message.cf.complete(null);

    }

    private void onShutdown(ShutdownMessage message) {
        log.debug(() -> "Shutdown Message Received ");
        JStatePublisherActor.StopMessage stopMessage = new JStatePublisherActor.StopMessage();
        statePublisherActor.tell(stopMessage);
    }

    /**
     * This method load assembly configuration.
     *
     * @return
     */
    private Config getHCDConfig() {

        try {
            //ActorRefFactory actorRefFactory = Adapter.toUntyped(actorContext.getSystem());

            ActorRuntime actorRuntime = new ActorRuntime(Adapter.toUntyped(actorContext.getSystem()));

            Materializer mat = actorRuntime.mat();

            ConfigData configData = getHCDConfigData();

            return configData.toJConfigObject(mat).get();

        } catch (Exception e) {
            throw new JLifecycleActor.ConfigNotAvailableException();
        }

    }

    private ConfigData getHCDConfigData() throws ExecutionException, InterruptedException {

        log.debug(() -> "loading hcd configuration");

        // construct the path
        Path filePath = Paths.get("/org/tmt/tcs/enc/enc_hcd.conf");

        ConfigData activeFile = configClientApi.getActive(filePath).get().get();

        return activeFile;
    }

    public class ConfigNotAvailableException extends FailureStop {

        public ConfigNotAvailableException() {
            super("Configuration not available. Initialization failure.");
        }
    }


}
