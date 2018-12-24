package org.tmt.tcs.mcs.MCShcd.Protocol

import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import org.tmt.tcs.mcs.MCShcd.EventMessage
import org.tmt.tcs.mcs.MCShcd.EventMessage.PublishState
import org.tmt.tcs.mcs.MCShcd.Protocol.SimpleSimMsg._
import org.tmt.tcs.mcs.MCShcd.constants.{Commands, EventConstants}
import java.lang.Double.doubleToLongBits
import java.lang.Double.longBitsToDouble
import java.time.Instant
import java.util.concurrent.{Executors, ScheduledExecutorService, ScheduledThreadPoolExecutor, TimeUnit}

import csw.logging.scaladsl.{Logger, LoggerFactory}
import csw.params.commands.CommandResponse.SubmitResponse
import csw.params.commands.{CommandResponse, ControlCommand}
import csw.params.core.generics.Parameter
import csw.params.core.models.Units.degree
import csw.params.core.models.{Prefix, Subsystem}
import csw.params.core.states.{CurrentState, StateName}
import csw.params.events.SystemEvent

sealed trait SimpleSimMsg
object SimpleSimMsg {
  case class ProcessCommand(command: ControlCommand, sender: ActorRef[SimpleSimMsg]) extends SimpleSimMsg

  case class SimpleSimResp(commandResponse: SubmitResponse) extends SimpleSimMsg
  case class ProcEventDemand(event: SystemEvent)            extends SimpleSimMsg
  case class ProcOneWayDemand(command: ControlCommand)      extends SimpleSimMsg
  case class ProcCurrStateDemand(currState: CurrentState)   extends SimpleSimMsg
}

object SimpleSimulator {
  def create(loggerFactory: LoggerFactory, statePublisherActor: ActorRef[EventMessage]): Behavior[SimpleSimMsg] =
    Behaviors.setup(ctx => SimpleSimulator(ctx, loggerFactory, statePublisherActor))
}
case class SimpleSimulator(ctx: ActorContext[SimpleSimMsg],
                           loggerFactory: LoggerFactory,
                           statePublisherActor: ActorRef[EventMessage])
    extends AbstractBehavior[SimpleSimMsg] {
  private val log: Logger = loggerFactory.getLogger

  val prefix: Prefix = Prefix(Subsystem.MCS.toString)

  val azPosDemand: AtomicLong = new AtomicLong(doubleToLongBits(0.0))
  val elPosDemand: AtomicLong = new AtomicLong(doubleToLongBits(0.0))

  val MIN_AZ_POS: Double = -330
  val MAX_AZ_POS: Double = 170
  val MIN_EL_POS: Double = -3
  val MAX_EL_POS: Double = 93

  val currentPosPublisher: AtomicBoolean = new AtomicBoolean(true)
  val healthPublisher: AtomicBoolean     = new AtomicBoolean(true)
  val posDemandSubScriber: AtomicBoolean = new AtomicBoolean(true)

  val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(2)
  var currPosCounter: Long                = 0

  override def onMessage(msg: SimpleSimMsg): Behavior[SimpleSimMsg] = {
    msg match {
      case msg: ProcessCommand =>
        //log.info(s"Received command : ${msg.command} in simpleSimulator.")
        updateSimulator(msg.command.commandName.name)
        msg.sender ! SimpleSimResp(CommandResponse.Completed(msg.command.runId))
        Behavior.same
      case msg: ProcOneWayDemand =>
        val simulatorRecTime                    = System.currentTimeMillis()
        val paramSet                            = msg.command.paramSet
        val azPosParam: Option[Parameter[_]]    = paramSet.find(msg => msg.keyName == EventConstants.POINTING_KERNEL_AZ_POS)
        val elPosParam: Option[Parameter[_]]    = paramSet.find(msg => msg.keyName == EventConstants.POINTING_KERNEL_EL_POS)
        val sentTimeParam: Option[Parameter[_]] = paramSet.find(msg => msg.keyName == EventConstants.TIMESTAMP)
        val azPos                               = azPosParam.getOrElse(EventConstants.AzPosKey.set(0.0))
        val elPos                               = elPosParam.getOrElse(EventConstants.ElPosKey.set(0.0))
        val sentTime                            = sentTimeParam.getOrElse(EventConstants.TimeStampKey.set(Instant.now()))
        val assemblyRecTime                     = paramSet.find(msg => msg.keyName == EventConstants.ASSEMBLY_RECEIVAL_TIME).get
        val hcdRecTime                          = paramSet.find(msg => msg.keyName == EventConstants.HCD_ReceivalTime).get
        log.error(
          s"${azPos.head}, ${elPos.head}, ${sentTime.head}, ${assemblyRecTime.head}, ${hcdRecTime.head}, $simulatorRecTime"
        )
        Behavior.same

      case msg: ProcEventDemand =>
        val cs               = msg.event
        val simpleSimRecTime = System.currentTimeMillis()
        val assemblyRecTime  = cs.get(EventConstants.ASSEMBLY_RECEIVAL_TIME_KEY).get.head
        val hcdRecTime       = cs.get(EventConstants.HcdReceivalTime_Key).get.head
        val tpkPublishTime   = cs.get(EventConstants.TimeStampKey).get.head
        val azPos            = cs.get(EventConstants.AzPosKey).get.head
        val elPos            = cs.get(EventConstants.ElPosKey).get.head
        log.error(s"Received event :$azPos, $elPos, $tpkPublishTime, $assemblyRecTime, $hcdRecTime, $simpleSimRecTime")
        Behavior.same

      case msg: ProcCurrStateDemand =>
        val cs               = msg.currState
        val simpleSimRecTime = System.currentTimeMillis()
        val assemblyRecTime  = cs.get(EventConstants.ASSEMBLY_RECEIVAL_TIME_KEY).get.head
        val hcdRecTime       = cs.get(EventConstants.HcdReceivalTime_Key).get.head
        val tpkPublishTime   = cs.get(EventConstants.TimeStampKey).get.head
        val azPos            = cs.get(EventConstants.AzPosKey).get.head
        val elPos            = cs.get(EventConstants.ElPosKey).get.head
        this.azPosDemand.set(doubleToLongBits(azPos))
        this.elPosDemand.set(doubleToLongBits(elPos))
        log.info(
          s"Received demanded positions :${longBitsToDouble(this.azPosDemand.get())}, ${longBitsToDouble(this.elPosDemand.get())}," +
          s" $tpkPublishTime, $assemblyRecTime, $hcdRecTime, $simpleSimRecTime"
        )
        Behavior.same
    }
  }
  def updateSimulator(commandName: String): Unit = {
    commandName match {
      case Commands.STARTUP =>
        new Thread(() => startPublishingCurrPos()).start()
        new Thread(() => startPublishingHealth()).start()
        log.info("Starting publish current position and health threads")
      case Commands.SHUTDOWN =>
        updateCurrPosPublisher(false)
        updateHealthPublisher(false)
        log.info("Updating current position publisher and health publisher to false")
      case _ =>
        log.info(s"Not changing publisher thread state as command received is $commandName")
    }
  }
  def updateCurrPosPublisher(value: Boolean): Unit = {
    this.currentPosPublisher.set(value)
    println(s"Updating CurrentPosition publisher to : $value")
  }
  def updateHealthPublisher(value: Boolean): Unit = {
    this.healthPublisher.set(value)
    println(s"Updating Health publisher to : ${this.healthPublisher.get()}")
  }

  val currentPosRunner = new Runnable {
    override def run(): Unit = {
      // log.info(s"Publish Current position thread started")
      var elC: Double = 0
      var azC: Double = 0
      def updateElC() = {
        if (elC >= longBitsToDouble(elPosDemand.get())) {
          elC = elPosDemand.get()
        } else if (longBitsToDouble(elPosDemand.get()) > 0.0) {
          // demanded positions are positive
          elC = elC + 0.0005
        } else {
          // for -ve demanded el positions
          elC = elC - 0.0005
        }
        // log.info(s"Updated el position is : $elC")
      }
      def updateAzC = {
        if (azC >= longBitsToDouble(azPosDemand.get())) {
          azC = azPosDemand.get()
        } else if (longBitsToDouble(azPosDemand.get()) > 0.0) {
          //for positive demanded positions
          azC = azC + 0.0005
        } else {
          azC = azC - 0.0005
        }
        //  log.info(s"Updated az position is : $azC")
      }
      //log.info(s"currentPosPublisher current value is : ${currentPosPublisher.get()}")
      while (currentPosPublisher.get()) {

        currPosCounter = currPosCounter + 1
        if (currPosCounter == 100000) {
          updateCurrPosPublisher(false)
          log.info("Stopping current position thread as current pos counter reached 1,00,000")
        }
        updateAzC
        updateElC
        val azPosParam: Parameter[Double] = EventConstants.AzPosKey.set(azC).withUnits(degree)
        val elPosParam: Parameter[Double] = EventConstants.ElPosKey.set(elC).withUnits(degree)

        val azPosErrorParam: Parameter[Double] =
          EventConstants.AZ_POS_ERROR_KEY.set(longBitsToDouble(azPosDemand.get())).withUnits(degree)
        val elPosErrorParam: Parameter[Double] =
          EventConstants.EL_POS_ERROR_KEY.set(longBitsToDouble(elPosDemand.get())).withUnits(degree)

        val azInPositionParam: Parameter[Boolean] = EventConstants.AZ_InPosition_Key.set(true)
        val elInPositionParam: Parameter[Boolean] = EventConstants.EL_InPosition_Key.set(true)
        val timestamp                             = EventConstants.TimeStampKey.set(Instant.now())
        /* log.info(
          s"Publishing Az position : $azC and el position : $elC demanded az : ${azPosDemand.get()}, el : ${elPosDemand.get()}"
        )*/

        val currentState = CurrentState(prefix, StateName(EventConstants.CURRENT_POSITION))
          .add(azPosParam)
          .add(elPosParam)
          .add(azPosErrorParam)
          .add(elPosErrorParam)
          .add(azInPositionParam)
          .add(elInPositionParam)
          .add(timestamp)
        statePublisherActor ! PublishState(currentState)
      }
    }
  }

  def startPublishingCurrPos(): Unit = {
    // Thread.sleep(100) //Temporarily taking to 100 from 10
    scheduler.scheduleWithFixedDelay(currentPosRunner, 10, 10, TimeUnit.MILLISECONDS)
  }

  val healthRunner = new Runnable {
    override def run(): Unit = {
      while (healthPublisher.get()) {

        val healthParam: Parameter[String]       = EventConstants.HEALTH_KEY.set("Good")
        val healthReasonParam: Parameter[String] = EventConstants.HEALTH_REASON_KEY.set("Good Reason")
        val timestamp                            = EventConstants.TimeStampKey.set(Instant.now())
        val currentState = CurrentState(prefix, StateName(EventConstants.HEALTH_STATE))
          .add(healthParam)
          .add(healthReasonParam)
          .add(timestamp)
        // log.info(s"Health publisher current value is : ${healthPublisher.get()}, publishing health")
        statePublisherActor ! PublishState(currentState)
      }
    }
  }
  def startPublishingHealth(): Unit = {

    scheduler.scheduleWithFixedDelay(healthRunner, 1000, 1000, TimeUnit.MILLISECONDS)
  }
}
