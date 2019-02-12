package org.tmt.tcs.mcs.MCSassembly

import java.time.Instant

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
import csw.params.core.generics.{Key, KeyType, Parameter}
import csw.params.core.models.{Id, Prefix, Subsystem}

object ReadCmdActor {
  def createObject(commandResponseManager: CommandResponseManager,
                   hcdLocation: Option[CommandService],
                   loggerFactory: LoggerFactory): Behavior[ControlCommand] =
    Behaviors.setup(ctx => ReadCmdActor(ctx, commandResponseManager, hcdLocation, loggerFactory))
}
/*
This actor is responsible for handling move command
 */
case class ReadCmdActor(ctx: ActorContext[ControlCommand],
                        commandResponseManager: CommandResponseManager,
                        hcdLocation: Option[CommandService],
                        loggerFactory: LoggerFactory)
    extends AbstractBehavior[ControlCommand] {
  private val log                                 = loggerFactory.getLogger
  implicit val duration: Timeout                  = 8 seconds
  private val AssemblyCmdRecTimeKey: Key[Instant] = KeyType.TimestampKey.make("AssemblyCmdRecTime")

  override def onMessage(controlCommand: ControlCommand): Behavior[ControlCommand] = {
    hcdLocation match {
      case Some(commandService) =>
        val clientAppSentTime: Parameter[_] = controlCommand.paramSet.find(msg => msg.keyName == "ClientAppSentTime").get
        //  log.info(s"Assembly received command: $controlCommand and parameters are: $clientAppSentTime")
        val setup = Setup(Prefix(Subsystem.MCS.toString), controlCommand.commandName, None)
          .add(clientAppSentTime)
          .add(AssemblyCmdRecTimeKey.set(Instant.now()))
        val response = Await.result(commandService.submit(setup), 8.seconds)
        commandResponseManager.addSubCommand(controlCommand.runId, setup.runId)
        commandResponseManager.updateSubCommand(response)
        Behavior.stopped
      case None =>
        Future.successful(Error(Id(), s"Can't locate mcs hcd location : $hcdLocation in ReadCmdActor "))
        Behavior.unhandled
    }

  }
}
