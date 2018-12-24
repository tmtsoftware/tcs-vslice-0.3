package org.tmt.tcs;


import akka.actor.ActorSystem;
import akka.actor.typed.javadsl.Adapter;
import akka.util.Timeout;
import csw.command.api.javadsl.ICommandService;
import csw.command.client.CommandServiceFactory;
import csw.location.api.javadsl.ILocationService;
import csw.location.api.models.AkkaLocation;
import csw.location.api.models.ComponentId;
import csw.location.api.models.Connection;
import csw.params.commands.CommandName;
import csw.params.commands.CommandResponse;
import csw.params.commands.Setup;
import csw.params.core.generics.Key;
import csw.params.core.models.Id;
import csw.params.core.models.ObsId;
import csw.params.core.models.Prefix;
import csw.params.javadsl.JKeyType;
import scala.concurrent.duration.FiniteDuration;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static csw.location.api.javadsl.JComponentType.Assembly;

public class PkClient {

    Prefix source;
    ActorSystem system;
    ILocationService locationService;

    public PkClient(Prefix source, ActorSystem system, ILocationService locationService) throws Exception {

        this.source = source;
        this.system = system;
        this.locationService = locationService;

        commandServiceOptional = getAssemblyBlocking();
    }
    Optional<ICommandService> commandServiceOptional = Optional.empty();
    private Connection.AkkaConnection assemblyConnection = new Connection.AkkaConnection(new ComponentId("PkAssembly", Assembly));
    private Key<Double> raKey = JKeyType.DoubleKey().make("ra");
    private Key<Double> decKey = JKeyType.DoubleKey().make("dec");


    /**
     * Gets a reference to the running assembly from the location service, if found.
     */

    private Optional<ICommandService> getAssemblyBlocking() throws Exception {
        Duration waitForResolveLimit = Duration.ofSeconds(30);
        Optional<AkkaLocation> resolveResult = locationService.resolve(assemblyConnection, waitForResolveLimit).get();
        if (resolveResult.isPresent()) {
            AkkaLocation akkaLocation = resolveResult.get();
            return Optional.of(CommandServiceFactory.jMake(akkaLocation,Adapter.toTyped(system)));
        }
        return Optional.empty();
    }

    /**
     * Sends a setTarget message to the Assembly and returns the response
     */
    public CompletableFuture<CommandResponse.SubmitResponse> setTarget(Optional<ObsId> obsId, Double ra, Double dec) throws Exception {
        if (commandServiceOptional.isPresent()) {
            ICommandService commandService = commandServiceOptional.get();
            Setup setup = new Setup(source, new CommandName("setTarget"), obsId)
                    .add(raKey.set(ra))
                    .add(decKey.set(dec));
            return commandService.submit(setup, Timeout.durationToTimeout(FiniteDuration.apply(5, TimeUnit.SECONDS)));
        }
        return CompletableFuture.completedFuture(new CommandResponse.Error(new Id(""), "Can't locate Assembly"));
    }
}






