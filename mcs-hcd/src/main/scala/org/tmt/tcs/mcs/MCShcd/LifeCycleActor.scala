package org.tmt.tcs.mcs.MCShcd

import java.nio.file.{Path, Paths}

import akka.actor.ActorRefFactory
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.stream.ActorMaterializer
import com.typesafe.config._
import csw.framework.exceptions.FailureStop
import csw.command.client.CommandResponseManager
import csw.config.api.models.ConfigData
import csw.config.api.scaladsl.ConfigClientService
import csw.config.client.scaladsl.ConfigClientFactory
import csw.location.api.scaladsl.LocationService
import csw.logging.scaladsl.LoggerFactory
import org.tmt.tcs.mcs.MCShcd.LifeCycleMessage.{GetConfig, HCDConfig, InitializeMsg, ShutdownMsg}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

sealed trait LifeCycleMessage
object LifeCycleMessage {
  case class InitializeMsg(sender: ActorRef[LifeCycleMessage]) extends LifeCycleMessage
  case class ShutdownMsg()                                     extends LifeCycleMessage
  case class GetConfig(sender: ActorRef[LifeCycleMessage])     extends LifeCycleMessage
  case class HCDConfig(config: Config)                         extends LifeCycleMessage
}
object LifeCycleActor {
  def createObject(commandResponseManager: CommandResponseManager,
                   locationService: LocationService,
                   loggerFactory: LoggerFactory): Behavior[LifeCycleMessage] =
    Behaviors.setup(ctx => LifeCycleActor(ctx, commandResponseManager, locationService, loggerFactory))
}
/*
This actor is responsible for initialization of HCD it is called through CSW lifecycle hooks
 */
case class LifeCycleActor(ctx: ActorContext[LifeCycleMessage],
                          commandResponseManager: CommandResponseManager,
                          locationService: LocationService,
                          loggerFactory: LoggerFactory)
    extends AbstractBehavior[LifeCycleMessage] {
  implicit val ec: ExecutionContextExecutor     = ctx.executionContext
  private val configClient: ConfigClientService = ConfigClientFactory.clientApi(ctx.system.toUntyped, locationService)
  private val log                               = loggerFactory.getLogger
  private var hcdConfig: Option[Config]         = None
  override def onMessage(msg: LifeCycleMessage): Behavior[LifeCycleMessage] = {
    msg match {
      case msg: InitializeMsg => {
        val config: Config = doInitialize(msg)
        msg.sender ! HCDConfig(config)
        Behavior.same
      }
      case msg: ShutdownMsg => doShutdown()
      case msg: GetConfig => {
        msg.sender ! HCDConfig(hcdConfig.get)
        Behavior.same
      }
      case _ => {
        log.info(s"Incorrect message is sent to LifeCycleActor : $msg")
        Behavior.unhandled
      }
    }
    // this
  }
  /*
    This functions loads configuration from  config server and configures assembly accordingly
   */
  private def doInitialize(message: LifeCycleMessage): Config = {
    log.info(msg = " Initializing MCS HCD with the help of Config Server")
    val config: Config = getHCDConfig()
    log.info(s"Config object is : ${config}")
    val zeroMQPushSocket: Int = config.getInt("tmt.tcs.mcs.zeroMQPush")
    log.info(msg = s"zeroMQPushSocket from config file : mcs_hcd.conf is ${zeroMQPushSocket}")
    hcdConfig = Some(config)
    config
  }
  /*
   This functions shuts down assembly
   */
  private def doShutdown(): Behavior[LifeCycleMessage] = {
    log.info(msg = s"Shutting down MCS hcd.")
    Behavior.stopped
  }
  private def getHCDConfig(): Config = {
    val fileName: String = "org/tmt/tcs/mcs_hcd.conf"
    val filePath         = Paths.get(fileName)
    log.info(msg = s" Loading config file : ${fileName} from config server")
    implicit val context: ActorRefFactory        = ctx.system.toUntyped
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    val configData: ConfigData                   = Await.result(getConfigData(filePath), 20.seconds)
    log.info(msg = s" Successfully loaded config file : ${fileName} from config server : ${configData}")
    Await.result(configData.toConfigObject, 3.seconds)

  }

  private def getConfigData(filePath: Path): Future[ConfigData] = {
    configClient.getActive(filePath).flatMap {
      case Some(configData) => {
        //Await.result(configData.toConfigObject, 3.seconds)
        Future.successful(configData)
        //configData
      }
      case None => throw ConfigNotFoundException()

    }
  }

  case class ConfigNotFoundException() extends FailureStop("Failed to find HCD configuration")
}
