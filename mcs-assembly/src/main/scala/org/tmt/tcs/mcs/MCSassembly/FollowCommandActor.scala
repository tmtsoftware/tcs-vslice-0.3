package org.tmt.tcs.mcs.MCSassembly

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import akka.util.Timeout
import csw.command.api.scaladsl.CommandService
import csw.logging.scaladsl.LoggerFactory
import csw.params.commands.CommandResponse.Error
import csw.params.core.models.Id
import org.tmt.tcs.mcs.MCSassembly.CommandMessage.{ImmediateCommand, ImmediateCommandResponse}

object FollowCommandActor {
  def createObject(hcdLocation: Option[CommandService], loggerFactory: LoggerFactory): Behavior[ImmediateCommand] =
    Behaviors.setup(ctx => FollowCommandActor(ctx, hcdLocation, loggerFactory))
}
/*
This actor is responsible for processing of Follow command. It sends follow command to
hcd and sends the response from HCD to the caller in ImmediateCommand message
 */
case class FollowCommandActor(ctx: ActorContext[ImmediateCommand],
                              hcdLocation: Option[CommandService],
                              loggerFactory: LoggerFactory)
    extends AbstractBehavior[ImmediateCommand] {
  private val log                = loggerFactory.getLogger
  implicit val duration: Timeout = 1 seconds
  /*
      This method sends follow command to HCD if commandService instance is available and sends HCD response
      to the caller else if command service instance is not available then
      it will send error message to caller
   */
  override def onMessage(command: ImmediateCommand): Behavior[ImmediateCommand] = {
    log.info(msg = s"Starting execution of Follow command id : ${command.controlCommand.runId} in Assembly worker actor")
    hcdLocation match {
      case Some(commandService) =>
        val response = Await.result(commandService.submit(command.controlCommand), 1.seconds)
        log.info(msg = s" Updating follow command : ${command.controlCommand.runId} with response : $response")
        command.sender ! ImmediateCommandResponse(response)
        Behavior.stopped
      case None =>
        Future.successful(Error(Id(), s"Can't locate mcs hcd location : $hcdLocation"))
        Behavior.unhandled
    }
  }
}
