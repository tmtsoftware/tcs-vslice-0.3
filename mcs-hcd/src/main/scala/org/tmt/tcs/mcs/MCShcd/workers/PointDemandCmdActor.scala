package org.tmt.tcs.mcs.MCShcd.workers
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.util.Timeout
import org.tmt.tcs.mcs.MCShcd.Protocol.{SimpleSimMsg, ZeroMQMessage}

import scala.concurrent.duration._
import akka.actor.typed.scaladsl.AskPattern._
import csw.command.client.CommandResponseManager
import csw.logging.scaladsl.{Logger, LoggerFactory}
import csw.params.commands.{CommandResponse, ControlCommand}

import scala.concurrent.Await

object PointDemandCmdActor {
  def create(commandResponseManager: CommandResponseManager,
             zeroMQProtoActor: ActorRef[ZeroMQMessage],
             simpleSimActor: ActorRef[SimpleSimMsg],
             simulatorMode: String,
             loggerFactory: LoggerFactory): Behavior[ControlCommand] =
    Behaviors.setup(
      ctx => PointDemandCmdActor(ctx, commandResponseManager, zeroMQProtoActor, simpleSimActor, simulatorMode, loggerFactory)
    )
}
case class PointDemandCmdActor(ctx: ActorContext[ControlCommand],
                               commandResponseManager: CommandResponseManager,
                               zeroMQProtoActor: ActorRef[ZeroMQMessage],
                               simpleSimActor: ActorRef[SimpleSimMsg],
                               simulatorMode: String,
                               loggerFactory: LoggerFactory)
    extends AbstractBehavior[ControlCommand] {
  private val log: Logger = loggerFactory.getLogger
  override def onMessage(msg: ControlCommand): Behavior[ControlCommand] = {
    // log.info(s"Submitting point demand command with id : ${msg.runId} to Protocol")
    //val commandResponse: CommandResponse = subsystemManager.sendCommand(msg)
    implicit val duration: Timeout = 20 seconds
    implicit val scheduler         = ctx.system.scheduler
    val response: ZeroMQMessage = Await.result(zeroMQProtoActor ? { ref: ActorRef[ZeroMQMessage] =>
      ZeroMQMessage.SubmitCommand(ref, msg)
    }, 10.seconds)
    response match {
      case x: ZeroMQMessage.MCSResponse =>
        commandResponseManager.addOrUpdateCommand(x.commandResponse)

      case _ =>
        commandResponseManager.addOrUpdateCommand(
          CommandResponse.Error(msg.runId, "Unable to submit command data to MCS subsystem from worker actor.")
        )

    }
    Behavior.stopped
  }

}
