package org.tmt.tcs.mcs.MCSassembly

import akka.actor.Status.Success
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import org.tmt.tcs.mcs.MCSassembly.Constants.Commands

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import akka.util.Timeout
import csw.command.api.scaladsl.CommandService
import csw.command.client.CommandResponseManager
import csw.logging.scaladsl.LoggerFactory
import csw.params.commands.CommandResponse._
import csw.params.commands.{CommandName, CommandResponse, ControlCommand, Setup}
import csw.params.core.models.{Id, Prefix, Subsystem}

object ReadCmdActor {
  def createObject(commandResponseManager: CommandResponseManager,
                   hcdLocation: Option[CommandService],
                   loggerFactory: LoggerFactory): Behavior[ControlCommand] =
    Behaviors.setup(ctx => ReadCmdActor(ctx, commandResponseManager, hcdLocation, loggerFactory))
}
/*
This actor is responsible for handling move command
 */
case class ReadCmdActor(ctx: ActorContext[ControlCommand],
                        commandResponseManager: CommandResponseManager,
                        hcdLocation: Option[CommandService],
                        loggerFactory: LoggerFactory)
    extends AbstractBehavior[ControlCommand] {
  private val log                = loggerFactory.getLogger
  implicit val duration: Timeout = 10 seconds

  override def onMessage(controlCommand: ControlCommand): Behavior[ControlCommand] = {
    //log.info(msg = s"Executing ReadConf command $controlCommand")
    hcdLocation match {
      case Some(commandService) =>
        val response = Await.result(commandService.submit(controlCommand), 10.seconds)
        // log.info(s"Response for ReadConf command in Assembly is : $response")
        commandResponseManager.addOrUpdateCommand(response)
        Behavior.stopped
      case None =>
        Future.successful(Error(Id(), s"Can't locate mcs hcd location : $hcdLocation in ReadCmdActor "))
        Behavior.unhandled
    }

  }
}
