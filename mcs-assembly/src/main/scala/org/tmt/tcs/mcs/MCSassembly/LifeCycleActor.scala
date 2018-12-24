package org.tmt.tcs.mcs.MCSassembly

import java.nio.file.{Path, Paths}

import akka.actor.ActorRefFactory
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import csw.framework.exceptions.FailureStop
import org.tmt.tcs.mcs.MCSassembly.LifeCycleMessage.{AssemblyConfig, GetAssemblyConfig, InitializeMsg, ShutdownMsg}
import akka.actor.typed.scaladsl.adapter.UntypedActorSystemOps
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import csw.command.client.CommandResponseManager
import csw.config.api.models.ConfigData
import csw.config.api.scaladsl.ConfigClientService
import csw.logging.scaladsl.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

sealed trait LifeCycleMessage
object LifeCycleMessage {
  case class InitializeMsg()                                       extends LifeCycleMessage
  case class ShutdownMsg()                                         extends LifeCycleMessage
  case class GetAssemblyConfig(sender: ActorRef[LifeCycleMessage]) extends LifeCycleMessage
  case class AssemblyConfig(config: Option[Config])                extends LifeCycleMessage
}
object LifeCycleActor {
  def createObject(commandResponseManager: CommandResponseManager,
                   configClient: ConfigClientService,
                   loggerFactory: LoggerFactory): Behavior[LifeCycleMessage] =
    Behaviors.setup(ctx => LifeCycleActor(ctx, commandResponseManager, configClient, loggerFactory))
}
/*
This actor is responsible for processing lifecycle commands,
It is called through lifecycle hooks of CSW
 */
case class LifeCycleActor(ctx: ActorContext[LifeCycleMessage],
                          commandResponseManager: CommandResponseManager,
                          configClient: ConfigClientService,
                          loggerFactory: LoggerFactory)
    extends AbstractBehavior[LifeCycleMessage] {

  private val log                           = loggerFactory.getLogger
  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  private var config: Option[Config]        = None
  override def onMessage(msg: LifeCycleMessage): Behavior[LifeCycleMessage] = {
    msg match {
      case _: InitializeMsg => doInitialize()
      case _: ShutdownMsg   => doShutdown()
      case msg: GetAssemblyConfig =>
        msg.sender ! AssemblyConfig(config)
        Behavior.same
      case _ =>
        log.error(s"Incorrect message is sent to LifeCycleActor : $msg")
        Behavior.unhandled
    }
    this
  }
  /*
   This function loads assembly configuration file from config server
   and configures assembly accordingly

   */
  private def doInitialize(): Behavior[LifeCycleMessage] = {
    val assemblyConfig: Config = getAssemblyConfig()
    log.info(s"Config object is : $assemblyConfig")
    val commandTimeout  = assemblyConfig.getInt("tmt.tcs.mcs.cmdtimeout")
    val numberOfRetries = assemblyConfig.getInt("tmt.tcs.mcs.retries")
    val velAccLimit     = assemblyConfig.getInt("tmt.tcs.mcs.limit")
    config = Some(assemblyConfig)
    Behavior.same
  }

  private def doShutdown(): Behavior[LifeCycleMessage] = {
    log.info(msg = "Shutting down MCS assembly.")
    Behavior.stopped
  }
  private def getAssemblyConfig(): Config = {
    val filePath = Paths.get("org/tmt/tcs/mcs_assembly.conf")
    log.info(msg = s" Loading config file : $filePath from config server")
    implicit val context: ActorRefFactory        = ctx.system.toUntyped
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    val configData: ConfigData                   = Await.result(getConfigData(filePath), 30.seconds)
    log.info(msg = s" Successfully loaded config file : $filePath : $configData from config server")
    Await.result(configData.toConfigObject, 20.seconds)
  }

  private def getConfigData(filePath: Path): Future[ConfigData] = {
    val futConfigData: Future[Option[ConfigData]] = configClient.getActive(filePath)

    futConfigData flatMap {
      case Some(configData: ConfigData) =>
        Future.successful(configData)
      case None =>
        throw ConfigNotFoundException()
    }
  }
  case class ConfigNotFoundException() extends FailureStop("Failed to find assembly configuration")
}
