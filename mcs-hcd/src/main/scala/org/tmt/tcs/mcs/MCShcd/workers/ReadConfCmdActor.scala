package org.tmt.tcs.mcs.MCShcd.workers

import java.time.Instant

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.util.Timeout
import akka.actor.typed.scaladsl.AskPattern._
import org.tmt.tcs.mcs.MCShcd.Protocol.{SimpleSimMsg, ZeroMQMessage}
import csw.command.client.CommandResponseManager
import csw.logging.scaladsl.{Logger, LoggerFactory}
import csw.params.commands.{ControlCommand, Setup}
import csw.params.core.generics.{Key, KeyType, Parameter}
import csw.params.core.models.{Prefix, Subsystem}
import org.tmt.tcs.mcs.MCShcd.Protocol.SimpleSimMsg.{ProcessCommand, ReadConfResp}
import org.tmt.tcs.mcs.MCShcd.Protocol.ZeroMQMessage.{ReadConfRealCmdResp, SubmitCommand}
import org.tmt.tcs.mcs.MCShcd.constants.Commands

import scala.concurrent.Await
import scala.concurrent.duration._
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
  private val log: Logger                    = loggerFactory.getLogger
  private val HCDCmdRecTimeKey: Key[Instant] = KeyType.TimestampKey.make("HCDCmdRecTime")

  override def onMessage(msg: ControlCommand): Behavior[ControlCommand] = {
    val clientAppSentTime: Parameter[_] = msg.paramSet.find(msg => msg.keyName == "ClientAppSentTime").get
    val assemblyRecevTime: Parameter[_] = msg.paramSet.find(msg => msg.keyName == "AssemblyCmdRecTime").get
    val setup = Setup(Prefix(Subsystem.MCS.toString), msg.commandName, None)
      .add(clientAppSentTime)
      .add(assemblyRecevTime)
      .add(HCDCmdRecTimeKey.set(Instant.now))
    simulatorMode match {
      case Commands.REAL_SIMULATOR =>
        zeroMQProtoActor ! SubmitCommand(setup)
        Behavior.stopped
      case Commands.SIMPLE_SIMULATOR =>
        submitCmdToSimpleSim(msg, setup)
        Behavior.stopped
    }
  }

  private def submitCmdToRealSim(msg: ControlCommand, setup: Setup): Unit = {
    implicit val duration: Timeout = 7 seconds
    implicit val scheduler         = ctx.system.scheduler
    val response: ZeroMQMessage = Await.result(zeroMQProtoActor ? { ref: ActorRef[ZeroMQMessage] =>
      ZeroMQMessage.ReadConfRealCmd(setup, ref)
    }, 5.seconds)
    response match {
      case x: ReadConfRealCmdResp =>
        commandResponseManager.addSubCommand(msg.runId, setup.runId)
        commandResponseManager.updateSubCommand(x.commandResponse)
    }
  }

  private def submitCmdToSimpleSim(msg: ControlCommand, setup: Setup): Unit = {
    implicit val duration: Timeout = 7 seconds
    implicit val scheduler         = ctx.system.scheduler
    val response: SimpleSimMsg = Await.result(simplSimActor ? { ref: ActorRef[SimpleSimMsg] =>
      SimpleSimMsg.ReadConfCmd(setup, ref)
    }, 5.seconds)
    response match {
      case x: ReadConfResp =>
        commandResponseManager.addSubCommand(msg.runId, setup.runId)
        commandResponseManager.updateSubCommand(x.commandResponse)
    }
  }
}
