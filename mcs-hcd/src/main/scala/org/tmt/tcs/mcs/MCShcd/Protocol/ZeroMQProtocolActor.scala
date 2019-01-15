package org.tmt.tcs.mcs.MCShcd.Protocol

import java.time.Instant
import java.util.concurrent.{ExecutorService, Executors, ScheduledExecutorService}
import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import com.typesafe.config.Config
import csw.command.client.CommandResponseManager
import csw.logging.scaladsl.{noId, Logger, LoggerFactory}
import csw.params.commands.CommandResponse.{Error, SubmitResponse}
import csw.params.commands.{CommandResponse, ControlCommand}
import csw.params.core.models.Id
import csw.params.core.states.CurrentState
import csw.params.events.SystemEvent
import org.tmt.tcs.mcs.MCShcd.EventMessage
import org.tmt.tcs.mcs.MCShcd.EventMessage.PublishState
import org.tmt.tcs.mcs.MCShcd.Protocol.ZeroMQMessage._
import org.tmt.tcs.mcs.MCShcd.constants.{Commands, EventConstants}
import org.tmt.tcs.mcs.MCShcd.msgTransformers._
import org.zeromq.ZMQ
sealed trait ZeroMQMessage
object ZeroMQMessage {

  case class InitializeSimulator(sender: ActorRef[ZeroMQMessage], config: Config) extends ZeroMQMessage

  case class SubmitCommand(controlCommand: ControlCommand)                                 extends ZeroMQMessage
  case class ImmediateCmd(controlCommand: ControlCommand, sender: ActorRef[ZeroMQMessage]) extends ZeroMQMessage
  case class ImmediateCmdResp(commandResponse: SubmitResponse)                             extends ZeroMQMessage
  case class PublishEvent(event: SystemEvent)                                              extends ZeroMQMessage
  case class StartSimulEventSubscr()                                                       extends ZeroMQMessage

  case class SimulatorConnResponse(connected: Boolean)            extends ZeroMQMessage
  case class Disconnect()                                         extends ZeroMQMessage
  case class PublishCurrStateToZeroMQ(currentState: CurrentState) extends ZeroMQMessage

}
object ZeroMQProtocolActor {
  def create(commandResponseManager: CommandResponseManager,
             statePublisherActor: ActorRef[EventMessage],
             loggerFactory: LoggerFactory): Behavior[ZeroMQMessage] =
    Behaviors.setup(ctx => ZeroMQProtocolActor(ctx, commandResponseManager, statePublisherActor, loggerFactory))
}
case class ZeroMQProtocolActor(ctx: ActorContext[ZeroMQMessage],
                               commandResponseManager: CommandResponseManager,
                               statePublisherActor: ActorRef[EventMessage],
                               loggerFactory: LoggerFactory)
    extends AbstractBehavior[ZeroMQMessage] {
  private val log: Logger                 = loggerFactory.getLogger
  private val zmqContext: ZMQ.Context     = ZMQ.context(1)
  private val pushSocket: ZMQ.Socket      = zmqContext.socket(ZMQ.PUSH) //55579
  private val pullSocket: ZMQ.Socket      = zmqContext.socket(ZMQ.PULL) //55578
  private val pubSocket: ZMQ.Socket       = zmqContext.socket(ZMQ.PUB) //55581
  private val subscribeSocket: ZMQ.Socket = zmqContext.socket(ZMQ.SUB) //55580

  private val messageTransformer: IMessageTransformer  = ProtoBuffMsgTransformer.create(loggerFactory)
  private val paramSetTransformer: ParamSetTransformer = ParamSetTransformer.create(loggerFactory)
  private var zeroMQPullSocketStr: String              = _
  private var zeroMQPushSocketStr: String              = _
  private var zeroMQSubScribeSocketStr: String         = _
  private var zeroMQPubSocketStr: String               = _
  val simEventSubscriber: AtomicBoolean                = new AtomicBoolean(true)
  private val scheduler: ExecutorService               = Executors.newSingleThreadExecutor()

  /*posts
  1. PublishEvent is used when positionDemand is propagated from Assembly to HCD using CSW EventService.
  2. PublishCurrStateToZeroMQ is used when positionDemand is propagated from Assembly to HCD using CSW CurrentState.
   */
  override def onMessage(msg: ZeroMQMessage): Behavior[ZeroMQMessage] = {
    msg match {
      case msg: InitializeSimulator =>
        if (initMCSConnection(msg.config)) {
          log.info("CONNECTION ESTABLISHED WITH MCS SIMULATOR")
          msg.sender ! SimulatorConnResponse(true)
        } else {
          log.error("UNABLE TO MAKE CONNECTION WITH MCS SIMULATOR")
          msg.sender ! SimulatorConnResponse(false)
        }
        Behavior.same
      case msg: ImmediateCmd => // follow command will be immediate command to MCS
        immediateCommandToMCS(msg)
        Behavior.same
      case msg: SubmitCommand =>
        msg.controlCommand.commandName.name match {
          case Commands.STARTUP =>
            submitCommandToMCS(msg)
            // new Thread(() => startSubToSimEvents()).start()
            this.scheduler.execute(eventSubscriber)
            log.info("Started subscribing to events from real Simulator.")
            Behavior.same
          case Commands.SHUTDOWN =>
            simEventSubscriber.set(false)
            this.scheduler.shutdown()
            log.info("Stopping event subscription from real simulator")
            submitCommandToMCS(msg)
            Behavior.same
          case _ =>
            submitCommandToMCS(msg)
            Behavior.same
        }
      case msg: PublishEvent =>
        val positionDemands: Array[Byte] = messageTransformer.encodeEvent(msg.event)
        if (pubSocket.sendMore(EventConstants.MOUNT_DEMAND_POSITION)) {
          pubSocket.send(positionDemands)
        }
        Behavior.same

      case msg: PublishCurrStateToZeroMQ =>
        try {
          val positionDemands: Array[Byte] = messageTransformer.encodeCurrentState(msg.currentState)
          if (pubSocket.sendMore(EventConstants.MOUNT_DEMAND_POSITION)) {
            pubSocket.send(positionDemands)
          }
        } catch {
          case ex: Exception =>
            ex.printStackTrace()
            log.error("Exception in converting current state to byte array")
        }
        Behavior.same

      case _: Disconnect =>
        disconnectFromMCS()
        Behavior.same
    }
  }
  val eventSubscriber = new Runnable {
    override def run(): Unit = {
      while (simEventSubscriber.get()) {
        try {
          val eventName: String = subscribeSocket.recvStr()
          if (subscribeSocket.hasReceiveMore) {
            val eventData       = subscribeSocket.recv(ZMQ.NOBLOCK)
            val hcdReceivalTime = Instant.now
            val currentState    = messageTransformer.decodeEvent(eventName, eventData)
            //  log.error(s"simEventSubscription : ${simEventSubscriber.get()} and event details are: $currentState")
            val currState = currentState.add(EventConstants.hcdEventReceivalTime_Key.set(hcdReceivalTime))
            statePublisherActor ! PublishState(currState)
          } else {
            log.error(s"No event data is received for event: $eventName")
          }
        } catch {
          case e: Exception =>
            e.printStackTrace()
            log.error("exception in subscribing events from simulator: ", Map.empty, e, noId)
        }
      }
    }
  }

  private def immediateCommandToMCS(msg: ImmediateCmd): Unit = {
    val commandName: String = msg.controlCommand.commandName.name
    if (pushSocket.sendMore(commandName)) {
      val encodedCommand = messageTransformer.encodeMessage(msg.controlCommand)
      if (pushSocket.send(encodedCommand, ZMQ.NOBLOCK)) {
        msg.sender ! ImmediateCmdResp(readCommandResponse(commandName, msg.controlCommand.runId))
      } else {
        msg.sender ! ImmediateCmdResp(
          CommandResponse.Error(msg.controlCommand.runId, "Unable to submit command data to MCS subsystem.")
        )
      }
    } else {
      msg.sender ! ImmediateCmdResp(
        CommandResponse.Error(msg.controlCommand.runId, "Unable to submit command data to MCS subsystem.")
      )
    }
  }
  private def submitCommandToMCS(msg: SubmitCommand): Unit = {
    val commandName: String = msg.controlCommand.commandName.name
    if (pushSocket.sendMore(commandName)) {
      val encodedCommand = messageTransformer.encodeMessage(msg.controlCommand)
      if (pushSocket.send(encodedCommand, ZMQ.NOBLOCK)) {
        commandResponseManager.addOrUpdateCommand(readCommandResponse(commandName, msg.controlCommand.runId))
      } else {
        commandResponseManager.addOrUpdateCommand(
          CommandResponse.Error(msg.controlCommand.runId, "Unable to submit command data to MCS subsystem.")
        )
      }
    } else {
      commandResponseManager.addOrUpdateCommand(
        CommandResponse.Error(msg.controlCommand.runId, "Unable to submit command data to MCS subsystem.")
      )
    }
  }

  private def readCommandResponse(commandName: String, runId: Id): SubmitResponse = {
    val responseCommandName: String = pullSocket.recvStr()
    if (commandName == responseCommandName) {
      if (pullSocket.hasReceiveMore) {
        val responsePacket: Array[Byte] = pullSocket.recv(ZMQ.DONTWAIT)
        val response: SubystemResponse  = messageTransformer.decodeCommandResponse(responsePacket)
        paramSetTransformer.getCSWResponse(runId, response)
      } else {
        Error(runId, "unknown command send")
      }
    } else {
      Error(runId, "unknown command send")
    }
  }

  private def initMCSConnection(config: Config): Boolean = {
    log.info(s"config object is :$config")

    val tcsAddress = config.getString("tmt.tcs.mcs.TCSMCSAddr")
    log.info(msg = s"ZeroMQ is connecting to machine:$tcsAddress")

    val mcsAddress = config.getString("tmt.tcs.mcs.MCSSimulatorAddr")
    log.info(msg = s"MCS Remote server address is: $mcsAddress") //tcp://192.168.1.2:

    zeroMQPushSocketStr = tcsAddress + config.getInt("tmt.tcs.mcs.zeroMQPush")
    val pushSocketConn = pushSocket.bind(zeroMQPushSocketStr)
    log.info(msg = s"ZeroMQ push socket is: $zeroMQPushSocketStr and connection: $pushSocketConn")

    zeroMQPullSocketStr = mcsAddress + config.getInt("tmt.tcs.mcs.zeroMQPull")
    val pullSocketConn = pullSocket.connect(zeroMQPullSocketStr)
    log.info(msg = s"ZeroMQ pull socket is: $zeroMQPullSocketStr and connection: $pullSocketConn")

    zeroMQSubScribeSocketStr = mcsAddress + config.getInt("tmt.tcs.mcs.zeroMQSub")
    val subSockConn = subscribeSocket.connect(zeroMQSubScribeSocketStr)
    subscribeSocket.subscribe(ZMQ.SUBSCRIPTION_ALL) // added this becz unable to receive msgs without this.
    log.info(msg = s"ZeroMQ subscribe socket is: $zeroMQSubScribeSocketStr and connection is: $subSockConn")

    zeroMQPubSocketStr = tcsAddress + config.getInt("tmt.tcs.mcs.zeroMQPub")
    val pubSockConn = pubSocket.bind(zeroMQPubSocketStr)
    log.info(msg = s"ZeroMQ pub socket is: $zeroMQPubSocketStr and connection is: $pubSockConn")
    pushSocketConn && pullSocketConn && subSockConn && pubSockConn
  }
  private def disconnectFromMCS(): Unit = {
    pushSocket.disconnect(zeroMQPushSocketStr)
    pushSocket.close()
    pullSocket.disconnect(zeroMQPullSocketStr)
    pullSocket.close()
    subscribeSocket.disconnect(zeroMQSubScribeSocketStr)
    subscribeSocket.close()
    pubSocket.disconnect(zeroMQPubSocketStr)
    pubSocket.close()
  }
}
