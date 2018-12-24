package org.tmt.tcs.mcs.MCSassembly

import akka.actor
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.ActorContext
import csw.services.command.CommandResponseManager

import csw.services.config.api.scaladsl.ConfigClientService
import csw.services.location.scaladsl.LocationService
import csw.services.logging.scaladsl.{Logger, LoggerFactory}
import org.scalatest.mockito.MockitoSugar

class FrameworkTestMocks1(implicit untypedSystem: actor.ActorSystem, system: ActorSystem[Nothing]) extends MockitoSugar {

  val commandResponseManager: CommandResponseManager = mock[CommandResponseManager]

  val locationService: LocationService = mock[LocationService]

  val context: ActorContext[LifeCycleMessage] = mock[ActorContext[LifeCycleMessage]]
  val configClient: ConfigClientService       = mock[ConfigClientService]

  val loggerFactory: LoggerFactory = mock[LoggerFactory]

  val logger: Logger = mock[Logger]

}
