package org.tmt.tcs;

import akka.actor.ActorSystem;
import akka.actor.typed.javadsl.Adapter;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import csw.config.server.ActorRuntime;
import csw.location.api.javadsl.ILocationService;
import csw.location.client.javadsl.JHttpLocationServiceFactory;
import csw.location.server.commons.ClusterAwareSettings;
import csw.logging.javadsl.JLoggingSystemFactory;
import csw.params.commands.CommandResponse;
import csw.params.core.models.ObsId;
import csw.params.core.models.Prefix;
import java.net.InetAddress;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class PkClientApp {

    public static void main(String[] args) throws Exception {

        ActorSystem system = ClusterAwareSettings.system();
        Materializer mat = ActorMaterializer.create(system);
        ILocationService locationService = JHttpLocationServiceFactory.makeLocalClient(system, mat);

        PkClient pkClient   = new PkClient(new Prefix("tcs.pk"), system, locationService);
        Optional<ObsId> maybeObsId          = Optional.empty();
        String hostName                = InetAddress.getLocalHost().getHostName();
        JLoggingSystemFactory.start("PkClientApp", "0.1", hostName, system);

        CompletableFuture<CommandResponse.SubmitResponse> cf1 = pkClient.setTarget(maybeObsId, 185.79, 6.753333);
        CommandResponse resp1 = cf1.get();
        System.out.println("Inside PkClientApp: setTarget response is: " + resp1);

    }
}
