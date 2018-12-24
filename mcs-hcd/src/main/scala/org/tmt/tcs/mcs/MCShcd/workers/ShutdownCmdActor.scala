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
import org.tmt.tcs.mcs.MCShcd.constants.Commands

import scala.concurrent.Await

object ShutdownCmdActor {
  def create(commandResponseManager: CommandResponseManager,
             zeroMQProtoActor: ActorRef[ZeroMQMessage],
             simpleSimActor: ActorRef[SimpleSimMsg],
             simulatorMode: String,
             loggerFactory: LoggerFactory): Behavior[ControlCommand] =
    Behaviors.setup(
      ctx => ShutdownCmdActor(ctx, commandResponseManager, zeroMQProtoActor, simpleSimActor, simulatorMode, loggerFactory)
    )
}
case class ShutdownCmdActor(ctx: ActorContext[ControlCommand],
                            commandResponseManager: CommandResponseManager,
                            zeroMQProtoActor: ActorRef[ZeroMQMessage],
                            simpleSimActor: ActorRef[SimpleSimMsg],
                            simulatorMode: String,
                            loggerFactory: LoggerFactory)
    extends AbstractBehavior[ControlCommand] {
  private val log: Logger = loggerFactory.getLogger

  override def onMessage(msg: ControlCommand): Behavior[ControlCommand] = {
    log.info(s"Submitting shutdown  command with id : ${msg.runId} to Protocol")
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

    //val commandResponse: CommandResponse = subSystemManager.sendCommand(msg)
    //log.info(s"Response from Protocol for command runID : ${msg.runId} is : ${commandResponse}")
    // commandResponseManager.addOrUpdateCommand(msg.runId, commandResponse)
    Behavior.stopped
  }
  def submitToRealSim(msg: ControlCommand) = {
    implicit val duration: Timeout = 20 seconds
    implicit val scheduler         = ctx.system.scheduler
    val response: ZeroMQMessage = Await.result(zeroMQProtoActor ? { ref: ActorRef[ZeroMQMessage] =>
      ZeroMQMessage.SubmitCommand(ref, msg)
    }, 10.seconds)
    response match {
      case x: ZeroMQMessage.MCSResponse => {
        log.info(s"Response from MCS for command runID : ${msg.runId} is : ${x}")
        commandResponseManager.addOrUpdateCommand(x.commandResponse)
      }
      case _ =>
        commandResponseManager.addOrUpdateCommand(
          CommandResponse.Error(msg.runId, "Unable to submit command data to MCS subsystem from worker actor.")
        )
    }
  }
  private def submitToSimpleSim(msg: ControlCommand): Unit = {
    implicit val duration: Timeout = 20 seconds
    implicit val scheduler         = ctx.system.scheduler
    val response: SimpleSimMsg = Await.result(simpleSimActor ? { ref: ActorRef[SimpleSimMsg] =>
      SimpleSimMsg.ProcessCommand(msg, ref)
    }, 10.seconds)
    response match {
      case x: SimpleSimMsg.SimpleSimResp => commandResponseManager.addOrUpdateCommand(x.commandResponse)
      case _ =>
        commandResponseManager.addOrUpdateCommand(CommandResponse.Error(msg.runId, "Unable to submit command to SimpleSimulator"))
    }
  }
}
