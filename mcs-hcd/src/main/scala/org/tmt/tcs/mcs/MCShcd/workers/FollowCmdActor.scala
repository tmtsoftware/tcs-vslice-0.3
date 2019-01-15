package org.tmt.tcs.mcs.MCShcd.workers

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import org.tmt.tcs.mcs.MCShcd.HCDCommandMessage.{ImmediateCommand, ImmediateCommandResponse}
import org.tmt.tcs.mcs.MCShcd.Protocol.{SimpleSimMsg, ZeroMQMessage}

import scala.concurrent.duration._
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import csw.command.client.CommandResponseManager
import csw.logging.scaladsl.{Logger, LoggerFactory}
import csw.params.commands.CommandResponse
import org.tmt.tcs.mcs.MCShcd.constants.Commands

import scala.concurrent.{Await, ExecutionContextExecutor}

object FollowCmdActor {
  def create(commandResponseManager: CommandResponseManager,
             zeroMQProtoActor: ActorRef[ZeroMQMessage],
             simpleSimActor: ActorRef[SimpleSimMsg],
             simulatorMode: String,
             loggerFactory: LoggerFactory): Behavior[ImmediateCommand] =
    Behaviors.setup(
      ctx => FollowCmdActor(ctx, commandResponseManager, zeroMQProtoActor, simpleSimActor, simulatorMode, loggerFactory)
    )
}
case class FollowCmdActor(ctx: ActorContext[ImmediateCommand],
                          commandResponseManager: CommandResponseManager,
                          zeroMQProtoActor: ActorRef[ZeroMQMessage],
                          simplSimActor: ActorRef[SimpleSimMsg],
                          simulatorMode: String,
                          loggerFactory: LoggerFactory)
    extends AbstractBehavior[ImmediateCommand] {
  private val log: Logger                   = loggerFactory.getLogger
  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  override def onMessage(msg: ImmediateCommand): Behavior[ImmediateCommand] = {
    //log.info(s"Submitting follow command with id : ${msg.controlCommand.runId} to Protocol")
    simulatorMode match {
      case Commands.REAL_SIMULATOR =>
        submitToRealSim(msg)
        Behavior.stopped
      case Commands.SIMPLE_SIMULATOR =>
        submitToSimpleSim(msg)
        Behavior.stopped
    }
  }

  private def submitToSimpleSim(msg: ImmediateCommand) = {
    implicit val duration: Timeout = 1 seconds
    implicit val scheduler         = ctx.system.scheduler
    val response: SimpleSimMsg = Await.result(simplSimActor ? { ref: ActorRef[SimpleSimMsg] =>
      SimpleSimMsg.ProcessImmCmd(msg.controlCommand, ref)
    }, 100.millisecond)
    response match {
      case x: SimpleSimMsg.ImmCmdResp => msg.sender ! ImmediateCommandResponse(x.commandResponse)
      case _ =>
        msg.sender ! ImmediateCommandResponse(
          CommandResponse.Error(msg.controlCommand.runId, "Unable to submit command to SimpleSimulator")
        )
    }
  }

  private def submitToRealSim(msg: ImmediateCommand): Unit = {
    implicit val duration: Timeout = 1 seconds
    implicit val scheduler         = ctx.system.scheduler
    val response: ZeroMQMessage = Await.result(zeroMQProtoActor ? { ref: ActorRef[ZeroMQMessage] =>
      ZeroMQMessage.ImmediateCmd(msg.controlCommand, ref)
    }, 100.millisecond)
    response match {
      case x: ZeroMQMessage.ImmediateCmdResp => msg.sender ! ImmediateCommandResponse(x.commandResponse)
      case _ =>
        msg.sender ! ImmediateCommandResponse(
          CommandResponse.Error(msg.controlCommand.runId, "Unable to submit command  to RealSimulator.")
        )
    }
  }
}
