package org.tmt.tcs.mcs.MCShcd.workers
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.util.Timeout
import org.tmt.tcs.mcs.MCShcd.Protocol.{SimpleSimMsg, ZeroMQMessage}

import scala.concurrent.duration._
import scala.concurrent.Await
import akka.actor.typed.scaladsl.AskPattern._
import csw.command.client.CommandResponseManager
import csw.logging.scaladsl.{Logger, LoggerFactory}
import csw.params.commands.{CommandResponse, ControlCommand}
object PointCmdActor {
  def create(commandResponseManager: CommandResponseManager,
             zeroMQProtoActor: ActorRef[ZeroMQMessage],
             simpleSimActor: ActorRef[SimpleSimMsg],
             simulatorMode: String,
             loggerFactory: LoggerFactory): Behavior[ControlCommand] =
    Behaviors.setup(
      ctx => PointCmdActor(ctx, commandResponseManager, zeroMQProtoActor, simpleSimActor, simulatorMode, loggerFactory)
    )
}
case class PointCmdActor(ctx: ActorContext[ControlCommand],
                         commandResponseManager: CommandResponseManager,
                         zeroMQProtoActor: ActorRef[ZeroMQMessage],
                         simpleSimActor: ActorRef[SimpleSimMsg],
                         simulatorMode: String,
                         loggerFactory: LoggerFactory)
    extends AbstractBehavior[ControlCommand] {
  private val log: Logger = loggerFactory.getLogger
  override def onMessage(msg: ControlCommand): Behavior[ControlCommand] = {
    //log.info(s"Submitting point  command with id : ${msg.runId} to Protocol")
    // val commandResponse: CommandResponse = subsystemManager.sendCommand(msg)
    implicit val duration: Timeout = 20 seconds
    implicit val scheduler         = ctx.system.scheduler
    val response: ZeroMQMessage = Await.result(zeroMQProtoActor ? { ref: ActorRef[ZeroMQMessage] =>
      ZeroMQMessage.SubmitCommand(ref, msg)
    }, 10.seconds)
    response match {
      case x: ZeroMQMessage.MCSResponse => {
        //log.info(s"Response from MCS for command runID : ${msg.runId} is : ${x}")
        commandResponseManager.addOrUpdateCommand(x.commandResponse)
      }
      case _ => {
        commandResponseManager.addOrUpdateCommand(
          CommandResponse.Error(msg.runId, "Unable to submit command data to MCS subsystem from worker actor.")
        )
      }
    }
    Behavior.stopped
  }
}
