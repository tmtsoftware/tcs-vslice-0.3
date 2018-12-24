package org.tmt.tcs.mcs.MCShcd.workers

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.util.Timeout
import org.tmt.tcs.mcs.MCShcd.Protocol.{SimpleSimMsg, ZeroMQMessage}

import scala.concurrent.duration._
import akka.actor.typed.scaladsl.AskPattern._
import csw.command.client.CommandResponseManager
import csw.logging.scaladsl.{Logger, LoggerFactory}
import csw.params.commands.CommandResponse.Completed
import csw.params.commands.{CommandResponse, ControlCommand}
import org.tmt.tcs.mcs.MCShcd.constants.Commands

import scala.concurrent.Await

object StartupCmdActor {
  def create(commandResponseManager: CommandResponseManager,
             zeroMQProtoActor: ActorRef[ZeroMQMessage],
             simpleSimActor: ActorRef[SimpleSimMsg],
             simulatorMode: String,
             loggerFactory: LoggerFactory): Behavior[ControlCommand] =
    Behaviors.setup(
      ctx => StartupCmdActor(ctx, commandResponseManager, zeroMQProtoActor, simpleSimActor, simulatorMode, loggerFactory)
    )
}
case class StartupCmdActor(ctx: ActorContext[ControlCommand],
                           commandResponseManager: CommandResponseManager,
                           zeroMQProtoActor: ActorRef[ZeroMQMessage],
                           simpleSimActor: ActorRef[SimpleSimMsg],
                           simulatorMode: String,
                           loggerFactory: LoggerFactory)
    extends AbstractBehavior[ControlCommand] {
  private val log: Logger = loggerFactory.getLogger

  override def onMessage(msg: ControlCommand): Behavior[ControlCommand] = {
    simulatorMode match {
      case Commands.REAL_SIMULATOR => {
        submitToRealSim(msg)
        Behaviors.stopped
      }
      case Commands.SIMPLE_SIMULATOR => {
        submitToSimpleSim(msg)
        Behaviors.stopped
      }
    }

    Behavior.stopped
  }
  //TODO : Replace ask calls to simulator with commandResponseManager in simple and real simulator actors
  private def submitToSimpleSim(msg: ControlCommand): Unit = {
    implicit val duration: Timeout = 2 seconds
    implicit val scheduler         = ctx.system.scheduler
    val response: SimpleSimMsg = Await.result(simpleSimActor ? { ref: ActorRef[SimpleSimMsg] =>
      SimpleSimMsg.ProcessCommand(msg, ref)
    }, 1 seconds)
    response match {
      case x: SimpleSimMsg.SimpleSimResp => commandResponseManager.addOrUpdateCommand(x.commandResponse)
      case _ =>
        commandResponseManager.addOrUpdateCommand(CommandResponse.Error(msg.runId, "Unable to submit command to SimpleSimulator"))
    }
  }
  private def submitToRealSim(msg: ControlCommand): Unit = {
    implicit val duration: Timeout = 10 seconds
    implicit val scheduler         = ctx.system.scheduler
    val response: ZeroMQMessage = Await.result(zeroMQProtoActor ? { ref: ActorRef[ZeroMQMessage] =>
      ZeroMQMessage.SubmitCommand(ref, msg)
    }, 10.seconds)
    response match {
      case x: ZeroMQMessage.MCSResponse => commandResponseManager.addOrUpdateCommand(x.commandResponse)
      case _ =>
        commandResponseManager.addOrUpdateCommand(
          CommandResponse.Error(msg.runId, "Unable to submit command data to MCS subsystem from worker actor.")
        )
    }
  }
}
