package org.tmt.tcs.mcs.MCShcd.workers

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import org.tmt.tcs.mcs.MCShcd.Protocol.{SimpleSimMsg, ZeroMQMessage}
import csw.command.client.CommandResponseManager
import csw.logging.scaladsl.{Logger, LoggerFactory}
import csw.params.commands.ControlCommand
import org.tmt.tcs.mcs.MCShcd.Protocol.SimpleSimMsg.ProcessCommand
import org.tmt.tcs.mcs.MCShcd.Protocol.ZeroMQMessage.SubmitCommand
import org.tmt.tcs.mcs.MCShcd.constants.Commands

object ReadConfCmdActor {
  def create(commandResponseManager: CommandResponseManager,
             zeroMQProtoActor: ActorRef[ZeroMQMessage],
             simplSimActor: ActorRef[SimpleSimMsg],
             simulatorMode: String,
             loggerFactory: LoggerFactory): Behavior[ControlCommand] =
    Behaviors.setup(
      ctx => ReadConfCmdActor(ctx, commandResponseManager, zeroMQProtoActor, simplSimActor, simulatorMode, loggerFactory)
    )
}
case class ReadConfCmdActor(ctx: ActorContext[ControlCommand],
                            commandResponseManager: CommandResponseManager,
                            zeroMQProtoActor: ActorRef[ZeroMQMessage],
                            simplSimActor: ActorRef[SimpleSimMsg],
                            simulatorMode: String,
                            loggerFactory: LoggerFactory)
    extends AbstractBehavior[ControlCommand] {
  private val log: Logger = loggerFactory.getLogger
  override def onMessage(msg: ControlCommand): Behavior[ControlCommand] = {

    simulatorMode match {
      case Commands.REAL_SIMULATOR =>
        zeroMQProtoActor ! SubmitCommand(msg)
        Behavior.stopped
      case Commands.SIMPLE_SIMULATOR =>
        simplSimActor ! ProcessCommand(msg)
        Behavior.stopped
    }
  }

  //private def submitToSimplSim(msg: ControlCommand) : Unit =
  /*private def submitToSimplSim(msg: ControlCommand) = {
    implicit val duration: Timeout = 7 seconds
    implicit val scheduler         = ctx.system.scheduler

    val response: SimpleSimMsg = Await.result(simplSimActor ? { ref: ActorRef[SimpleSimMsg] =>
      SimpleSimMsg.ProcessCommand(msg, ref)
    }, 2.seconds)
    response match {
      case x: SimpleSimMsg.SimpleSimResp => commandResponseManager.addOrUpdateCommand(x.commandResponse)
      case _ =>
        commandResponseManager.addOrUpdateCommand(
          CommandResponse.Error(msg.runId, "Simple simulator is unable to process submitted command.")
        )
    }
  }*/

  //private def submitToRealSim(msg: ControlCommand) : Unit =
  /*private def submitToRealSim(msg: ControlCommand) = {
    //log.info(s"Submitting datum command with id : ${msg.runId} to Simulator")
    implicit val duration: Timeout = 10 seconds
    implicit val scheduler         = ctx.system.scheduler
    val response: ZeroMQMessage = Await.result(zeroMQProtoActor ? { ref: ActorRef[ZeroMQMessage] =>
      ZeroMQMessage.SubmitCommand(ref, msg)
    }, 3.seconds)
    response match {
      case x: ZeroMQMessage.MCSResponse =>
        log.info(s"Response from MCS for command runID : ${msg.runId}  and command Name : ${msg.commandName} is : $x")
        commandResponseManager.addOrUpdateCommand(x.commandResponse)
      case _ =>
        commandResponseManager.addOrUpdateCommand(
          CommandResponse.Error(msg.runId, "Unable to submit command data to MCS subsystem from worker actor.")
        )
    }
  }*/
}
