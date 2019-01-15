package org.tmt.tcs.mcs.MCShcd

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import csw.command.client.CommandResponseManager
import csw.logging.scaladsl.LoggerFactory
import csw.params.commands.CommandResponse.SubmitResponse
import csw.params.commands.ControlCommand
import csw.params.core.generics.Parameter
import org.tmt.tcs.mcs.MCShcd.constants.Commands
import org.tmt.tcs.mcs.MCShcd.workers._

import scala.concurrent.ExecutionContextExecutor
import org.tmt.tcs.mcs.MCShcd.HCDCommandMessage.{submitCommand, ImmediateCommand}
import org.tmt.tcs.mcs.MCShcd.Protocol.SimpleSimMsg.ProcessCommand
import org.tmt.tcs.mcs.MCShcd.Protocol.ZeroMQMessage.SubmitCommand
import org.tmt.tcs.mcs.MCShcd.Protocol.{SimpleSimMsg, ZeroMQMessage}

sealed trait HCDCommandMessage
object HCDCommandMessage {
  case class ImmediateCommand(sender: ActorRef[HCDCommandMessage], controlCommand: ControlCommand) extends HCDCommandMessage
  case class ImmediateCommandResponse(submitResponse: SubmitResponse)                              extends HCDCommandMessage
  case class submitCommand(controlCommand: ControlCommand)                                         extends HCDCommandMessage
  case class SimulatorMode(controlCommand: ControlCommand)                                         extends HCDCommandMessage
}
object CommandHandlerActor {
  def createObject(commandResponseManager: CommandResponseManager,
                   lifeCycleActor: ActorRef[LifeCycleMessage],
                   zeroMQProtoActor: ActorRef[ZeroMQMessage],
                   simpleSimActor: ActorRef[SimpleSimMsg],
                   simulatorMode: String,
                   loggerFactory: LoggerFactory): Behavior[HCDCommandMessage] =
    Behaviors.setup(
      ctx =>
        CommandHandlerActor(ctx,
                            commandResponseManager,
                            lifeCycleActor,
                            zeroMQProtoActor,
                            simpleSimActor,
                            simulatorMode,
                            loggerFactory)
    )
}

/*
This actor acts as simple Protocol for HCD commands, it simply sleeps the current thread and updates
command responses with completed messages
 */
case class CommandHandlerActor(ctx: ActorContext[HCDCommandMessage],
                               commandResponseManager: CommandResponseManager,
                               lifeCycleActor: ActorRef[LifeCycleMessage],
                               zeroMQProtoActor: ActorRef[ZeroMQMessage],
                               simpleSimActor: ActorRef[SimpleSimMsg],
                               simulatorMode: String,
                               loggerFactory: LoggerFactory)
    extends AbstractBehavior[HCDCommandMessage] {
  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  private val log                           = loggerFactory.getLogger

  override def onMessage(cmdMessage: HCDCommandMessage): Behavior[HCDCommandMessage] = {

    cmdMessage match {
      case msg: ImmediateCommand => processImmediateCommand(msg)
      case msg: submitCommand    => processSubmitCommand(msg)
    }
  }
  private def processImmediateCommand(immediateCommand: ImmediateCommand): Behavior[HCDCommandMessage] = {
    log.info("Received follow command in HCD commandHandler")
    val followCmdActor: ActorRef[ImmediateCommand] = ctx.spawn(
      FollowCmdActor.create(commandResponseManager, zeroMQProtoActor, simpleSimActor, simulatorMode, loggerFactory),
      name = "FollowCmdActor"
    )
    followCmdActor ! immediateCommand
    Behavior.same
  }
  private def processSubmitCommand(cmdMessage: submitCommand): Behavior[HCDCommandMessage] = {
    cmdMessage.controlCommand.commandName.name match {
      case Commands.READCONFIGURATION =>
        simulatorMode match {
          case Commands.REAL_SIMULATOR =>
            zeroMQProtoActor ! SubmitCommand(cmdMessage.controlCommand)
          case Commands.SIMPLE_SIMULATOR =>
            simpleSimActor ! ProcessCommand(cmdMessage.controlCommand)
        }
        Behavior.same
      // log.info("Received readConf command in HCD commandHandler")
      /* val readConfCmdActor: ActorRef[ControlCommand] = ctx.spawnAnonymous(
    ReadConfCmdActor.create(commandResponseManager, zeroMQProtoActor, simpleSimActor, simulatorMode, loggerFactory)
    )
    readConfCmdActor ! cmdMessage.controlCommand*/
      case Commands.STARTUP =>
        log.info("Starting MCS HCD")
        val startupCmdActor: ActorRef[ControlCommand] = ctx.spawn(
          StartupCmdActor.create(commandResponseManager, zeroMQProtoActor, simpleSimActor, simulatorMode, loggerFactory),
          "StartupCmdActor"
        )
        startupCmdActor ! cmdMessage.controlCommand
        Behavior.same
      case Commands.SET_SIMULATION_MODE =>
        val modeParam: Parameter[_] = cmdMessage.controlCommand.paramSet.find(msg => msg.keyName == Commands.SIMULATION_MODE).get
        val param: Any              = modeParam.head
        log.info(s"Changing commandHandlers simulation mode from: $simulatorMode to ${param.toString}")
        CommandHandlerActor.createObject(commandResponseManager,
                                         lifeCycleActor,
                                         zeroMQProtoActor,
                                         simpleSimActor,
                                         param.toString,
                                         loggerFactory)
      case Commands.SHUTDOWN =>
        log.info("ShutDown MCS HCD")
        val shutDownCmdActor: ActorRef[ControlCommand] = ctx.spawn(
          ShutdownCmdActor.create(commandResponseManager, zeroMQProtoActor, simpleSimActor, simulatorMode, loggerFactory),
          "ShutdownCmdActor"
        )
        shutDownCmdActor ! cmdMessage.controlCommand
        Behavior.stopped
      case Commands.POINT =>
        log.debug(s"handling point command: ${cmdMessage.controlCommand}")
        val pointCmdActor: ActorRef[ControlCommand] = ctx.spawn(
          PointCmdActor.create(commandResponseManager, zeroMQProtoActor, simpleSimActor, simulatorMode, loggerFactory),
          "PointCmdActor"
        )
        pointCmdActor ! cmdMessage.controlCommand
        Behavior.same
      case Commands.POINT_DEMAND =>
        log.debug(s"handling pointDemand command: ${cmdMessage.controlCommand}")
        val pointDemandCmdActor: ActorRef[ControlCommand] = ctx.spawn(
          PointDemandCmdActor.create(commandResponseManager, zeroMQProtoActor, simpleSimActor, simulatorMode, loggerFactory),
          "PointDemandCmdActor"
        )
        pointDemandCmdActor ! cmdMessage.controlCommand
        Behavior.same
      case Commands.DATUM =>
        log.info("Received datum command in HCD commandHandler")
        val datumCmdActor: ActorRef[ControlCommand] = ctx.spawn(
          DatumCmdActor.create(commandResponseManager, zeroMQProtoActor, simpleSimActor, simulatorMode, loggerFactory),
          "DatumCmdActor"
        )
        datumCmdActor ! cmdMessage.controlCommand
        Behavior.same
      case _ =>
        log.error(msg = s"Incorrect command is sent to MCS HCD : ${cmdMessage.controlCommand}")
        Behavior.unhandled
    }
  }
}
