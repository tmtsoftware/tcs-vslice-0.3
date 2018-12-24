package org.tmt.tcs.mcs.MCSassembly

import akka.actor.Status.Success
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import org.tmt.tcs.mcs.MCSassembly.Constants.Commands

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import akka.util.Timeout
import csw.command.api.scaladsl.CommandService
import csw.command.client.CommandResponseManager
import csw.logging.scaladsl.LoggerFactory
import csw.params.commands.CommandResponse._
import csw.params.commands.{CommandName, CommandResponse, ControlCommand, Setup}
import csw.params.core.models.{Id, Prefix, Subsystem}

object MoveCommandActor {
  def createObject(commandResponseManager: CommandResponseManager,
                   hcdLocation: Option[CommandService],
                   loggerFactory: LoggerFactory): Behavior[ControlCommand] =
    Behaviors.setup(ctx => MoveCommandActor(ctx, commandResponseManager, hcdLocation, loggerFactory))
}
/*
This actor is responsible for handling move command
 */
case class MoveCommandActor(ctx: ActorContext[ControlCommand],
                            commandResponseManager: CommandResponseManager,
                            hcdLocation: Option[CommandService],
                            loggerFactory: LoggerFactory)
    extends AbstractBehavior[ControlCommand] {
  private val log                = loggerFactory.getLogger
  private val mcsHCDPrefix       = Prefix(Subsystem.MCS.toString)
  implicit val duration: Timeout = 20 seconds

  /*
  This function splits move command into point command and point demand command and send it to hcd
  it aggregates command responses from HCD
   */
  override def onMessage(controlCommand: ControlCommand): Behavior[ControlCommand] = {
    log.info(msg = s"Executing Move command $controlCommand")

    val axesParam = controlCommand.paramSet.find(x => x.keyName == "axes").get
    val azParam   = controlCommand.paramSet.find(x => x.keyName == "AZ").get
    val elParam   = controlCommand.paramSet.find(x => x.keyName == "EL").get

    val pointSetup = Setup(mcsHCDPrefix, CommandName(Commands.POINT), controlCommand.maybeObsId)
      .add(axesParam)

    val pointDemandSetup = Setup(mcsHCDPrefix, CommandName(Commands.POINTDEMAND), controlCommand.maybeObsId)
      .add(azParam)
      .add(elParam)

    hcdLocation match {
      case Some(commandService) =>
        val commands: List[ControlCommand]  = List[ControlCommand](pointSetup, pointDemandSetup)
        val submitAll: List[SubmitResponse] = Await.result(commandService.submitAll(commands), 3.seconds)
        log.info(s"Response for move command is : $submitAll")
        var cmd1Succ = false
        var cmd2Succ = false
        submitAll(0) match {
          case _: Completed => cmd1Succ = true
        }
        submitAll(1) match {
          case _: Completed => cmd2Succ = true
        }

        //TODO : Check response of both the commands if both are completed or not as of now not getting what is the response
        commandResponseManager.addSubCommand(controlCommand.runId, submitAll(0).runId)
        commandResponseManager.updateSubCommand(submitAll(0))
        log.info(msg = s"completed move command execution for command id : $controlCommand.runId")
        Behavior.stopped

      case None =>
        Future.successful(Error(Id(), s"Can't locate mcs hcd location : $hcdLocation"))
        Behavior.unhandled

    }

  }
}
