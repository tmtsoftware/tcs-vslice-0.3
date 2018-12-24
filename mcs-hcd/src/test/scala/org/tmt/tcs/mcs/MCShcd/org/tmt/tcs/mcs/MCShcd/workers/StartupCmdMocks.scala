package org.tmt.tcs.mcs.MCShcd.org.tmt.tcs.mcs.MCShcd.workers
import akka.actor
import akka.actor.typed.ActorSystem
import csw.services.command.CommandResponseManager
import csw.services.logging.scaladsl.{Logger, LoggerFactory}
import org.scalatest.mockito.MockitoSugar

class StartupCmdMocks(implicit untypedSystem: actor.ActorSystem, system: ActorSystem[Nothing]) extends MockitoSugar {

  val commandResponseManager: CommandResponseManager = mock[CommandResponseManager]
  val loggerFactory                                  = mock[LoggerFactory]
  val log: Logger                                    = mock[Logger]
}
