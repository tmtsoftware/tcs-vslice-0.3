package org.tmt.tcs.mcs.MCSassembly

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}

import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.concurrent.duration._
import akka.util.Timeout
import csw.command.api.scaladsl.CommandService
import csw.command.client.CommandResponseManager
import csw.logging.scaladsl.LoggerFactory
import csw.params.commands.CommandResponse.Error
import csw.params.commands.ControlCommand

import csw.params.core.models.Id
object DatumCommandActor {
  def createObject(commandResponseManager: CommandResponseManager,
                   hcdLocation: Option[CommandService],
                   loggerFactory: LoggerFactory): Behavior[ControlCommand] =
    Behaviors.setup(ctx => DatumCommandActor(ctx, commandResponseManager, hcdLocation, loggerFactory))
}
/*
This actor is responsible for processing of datum command.
 */
case class DatumCommandActor(ctx: ActorContext[ControlCommand],
                             commandResponseManager: CommandResponseManager,
                             hcdLocation: Option[CommandService],
                             loggerFactory: LoggerFactory)
    extends AbstractBehavior[ControlCommand] {
  private val log = loggerFactory.getLogger

  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  implicit val duration: Timeout            = 60 seconds
  override def onMessage(controlCommand: ControlCommand): Behavior[ControlCommand] = {
    // val axes: Parameter[_] = controlCommand.paramSet.find(msg => msg.keyName == "axes").get
    hcdLocation match {
      case Some(commandService) =>
        val response = Await.result(commandService.submit(controlCommand), 50.seconds)
        log.info(s"Response for Datum command in Assembly is : $response")
        commandResponseManager.addOrUpdateCommand(response)
        Behavior.stopped
      case None =>
        Future.successful(Error(Id(), s"Can't locate mcs hcd location : $hcdLocation in DatumCommandActor "))
        Behavior.unhandled
    }
  }
}
