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

object DatumCmdActor {
  def create(commandResponseManager: CommandResponseManager,
             zeroMQProtoActor: ActorRef[ZeroMQMessage],
             simplSimActor: ActorRef[SimpleSimMsg],
             simulatorMode: String,
             loggerFactory: LoggerFactory): Behavior[ControlCommand] =
    Behaviors.setup(
      ctx => DatumCmdActor(ctx, commandResponseManager, zeroMQProtoActor, simplSimActor, simulatorMode, loggerFactory)
    )

}
case class DatumCmdActor(ctx: ActorContext[ControlCommand],
                         commandResponseManager: CommandResponseManager,
                         zeroMQProtoActor: ActorRef[ZeroMQMessage],
                         simplSimActor: ActorRef[SimpleSimMsg],
                         simulatorMode: String,
                         loggerFactory: LoggerFactory)
    extends AbstractBehavior[ControlCommand] {
  private val log: Logger = loggerFactory.getLogger
  override def onMessage(msg: ControlCommand): Behavior[ControlCommand] = {

    simulatorMode match {
      case Commands.REAL_SIMULATOR => {
        submitToRealSim(msg)
        Behavior.stopped
      }
      case Commands.SIMPLE_SIMULATOR => {
        submitToSimplSim(msg)
        Behavior.stopped
      }
    }
  }

  private def submitToSimplSim(msg: ControlCommand) = {
    implicit val duration: Timeout = 10 seconds
    implicit val scheduler         = ctx.system.scheduler
    val response: SimpleSimMsg = Await.result(simplSimActor ? { ref: ActorRef[SimpleSimMsg] =>
      SimpleSimMsg.ProcessCommand(msg, ref)
    }, 2.seconds)
    response match {
      case x: SimpleSimMsg.SimpleSimResp => {
        commandResponseManager.addOrUpdateCommand(x.commandResponse)
      }
      case _ => {
        commandResponseManager.addOrUpdateCommand(
          CommandResponse.Error(msg.runId, "Simple simulator is unable to process submitted command.")
        )
      }
    }
  }

  private def submitToRealSim(msg: ControlCommand) = {
    //log.info(s"Submitting datum command with id : ${msg.runId} to Simulator")
    implicit val duration: Timeout = 10 seconds
    implicit val scheduler         = ctx.system.scheduler
    val response: ZeroMQMessage = Await.result(zeroMQProtoActor ? { ref: ActorRef[ZeroMQMessage] =>
      ZeroMQMessage.SubmitCommand(ref, msg)
    }, 3.seconds)
    response match {
      case x: ZeroMQMessage.MCSResponse => {
        log.info(s"Response from MCS for command runID : ${msg.runId}  and command Name : ${msg.commandName} is : $x")
        commandResponseManager.addOrUpdateCommand(x.commandResponse)
      }
      case _ =>
        commandResponseManager.addOrUpdateCommand(
          CommandResponse.Error(msg.runId, "Unable to submit command data to MCS subsystem from worker actor.")
        )
    }
  }
}
