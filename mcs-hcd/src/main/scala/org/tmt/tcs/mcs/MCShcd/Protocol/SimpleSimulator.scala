package org.tmt.tcs.mcs.MCShcd.Protocol

import java.io._
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import org.tmt.tcs.mcs.MCShcd.EventMessage
import org.tmt.tcs.mcs.MCShcd.EventMessage.PublishState
import org.tmt.tcs.mcs.MCShcd.Protocol.SimpleSimMsg._
import org.tmt.tcs.mcs.MCShcd.constants.{Commands, EventConstants}
import java.lang.Double.doubleToLongBits
import java.lang.Double.longBitsToDouble
import java.time.{Duration, Instant, LocalDateTime, ZoneId}
import java.util.concurrent.{Executors, ScheduledExecutorService, TimeUnit}

import csw.command.client.CommandResponseManager
import csw.logging.scaladsl.{Logger, LoggerFactory}
import csw.params.commands.CommandResponse.SubmitResponse
import csw.params.commands.{CommandResponse, ControlCommand}
import csw.params.core.generics.Parameter
import csw.params.core.models.Units.degree
import csw.params.core.models.{Prefix, Subsystem}
import csw.params.core.states.{CurrentState, StateName}
import csw.params.events.SystemEvent
import scala.collection.mutable.ListBuffer

sealed trait SimpleSimMsg
object SimpleSimMsg {
  case class ProcessCommand(command: ControlCommand)                                extends SimpleSimMsg
  case class ProcessImmCmd(command: ControlCommand, sender: ActorRef[SimpleSimMsg]) extends SimpleSimMsg
  case class ImmCmdResp(commandResponse: SubmitResponse)                            extends SimpleSimMsg
  case class ProcEventDemand(event: SystemEvent)                                    extends SimpleSimMsg
  case class ProcOneWayDemand(command: ControlCommand)                              extends SimpleSimMsg
  case class ProcCurrStateDemand(currState: CurrentState)                           extends SimpleSimMsg
}

object SimpleSimulator {
  def create(commandResponseManager: CommandResponseManager,
             loggerFactory: LoggerFactory,
             statePublisherActor: ActorRef[EventMessage]): Behavior[SimpleSimMsg] =
    Behaviors.setup(
      ctx => SimpleSimulator(ctx, commandResponseManager: CommandResponseManager, loggerFactory, statePublisherActor)
    )
}
case class DemandPosHolder(pkPublishTime: Instant, assemblyRecTime: Instant, hcdRecTime: Instant, simRecTime: Instant)
case class SimpleSimulator(ctx: ActorContext[SimpleSimMsg],
                           commandResponseManager: CommandResponseManager,
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
  val logFilePath: String                 = System.getenv("LogFiles")
  /*

  val simpleSimCmdFile: File = new File(logFilePath + "/Cmd_SimpleSim" + System.currentTimeMillis() + "_.txt")
  simpleSimCmdFile.createNewFile()
  var cmdCounter: Long            = 0
  val cmdPrintStream: PrintStream = new PrintStream(new FileOutputStream(simpleSimCmdFile))
  this.cmdPrintStream.println("SimpleSimReceiveTimeStamp")
   */
  def getDate(instant: Instant): String =
    LocalDateTime.ofInstant(instant, ZoneId.of(Commands.zoneFormat)).format(Commands.formatter)

  var demandCounter       = 0
  val demandBuffer        = new ListBuffer[DemandPosHolder]()
  var fileUpdate: Boolean = false

  override def onMessage(msg: SimpleSimMsg): Behavior[SimpleSimMsg] = {
    msg match {
      case msg: ProcessCommand =>
        updateSimulator(msg.command.commandName.name)
        commandResponseManager.addOrUpdateCommand(CommandResponse.Completed(msg.command.runId))
        Behavior.same
      case msg: ProcessImmCmd =>
        msg.sender ! ImmCmdResp(CommandResponse.Completed(msg.command.runId))
        Behavior.same
      case msg: ProcOneWayDemand =>
        val simulatorRecTime                    = Instant.now()
        val paramSet                            = msg.command.paramSet
        val azPosParam: Option[Parameter[_]]    = paramSet.find(msg => msg.keyName == EventConstants.POINTING_KERNEL_AZ_POS)
        val elPosParam: Option[Parameter[_]]    = paramSet.find(msg => msg.keyName == EventConstants.POINTING_KERNEL_EL_POS)
        val sentTimeParam: Option[Parameter[_]] = paramSet.find(msg => msg.keyName == EventConstants.TIMESTAMP)
        val azPos: Parameter[_]                 = azPosParam.getOrElse(EventConstants.AzPosKey.set(0.0))
        val elPos: Parameter[_]                 = elPosParam.getOrElse(EventConstants.ElPosKey.set(0.0))
        val sentTime                            = sentTimeParam.getOrElse(EventConstants.TimeStampKey.set(Instant.now()))
        val assemblyRecTime                     = paramSet.find(msg => msg.keyName == EventConstants.ASSEMBLY_RECEIVAL_TIME).get
        val hcdRecTime                          = paramSet.find(msg => msg.keyName == EventConstants.HCD_ReceivalTime).get
        this.azPosDemand.set(doubleToLongBits(azPos.head.toString.toDouble))
        this.elPosDemand.set(doubleToLongBits(elPos.head.toString.toDouble))
        var hcdRecIns: Instant      = null
        var assemblyRecIns: Instant = null
        var pkPublishIns: Instant   = null

        sentTime.head match {
          case x: Instant => pkPublishIns = x
        }
        assemblyRecTime.head match {
          case x: Instant => assemblyRecIns = x
        }
        hcdRecTime.head match {
          case x: Instant => hcdRecIns = x
        }

        demandCounter = demandCounter + 1
        demandBuffer += DemandPosHolder(pkPublishIns, assemblyRecIns, hcdRecIns, simulatorRecTime)
        if (demandCounter == 100000 && !fileUpdate) {
          writeOneWayCmdDataToFile
          fileUpdate = true
        } else {
          log.info(s"$demandCounter")
        }
        Behavior.same

      case msg: ProcEventDemand =>
        val event            = msg.event
        val simpleSimRecTime = Instant.now()
        val assemblyRecTime  = event.get(EventConstants.ASSEMBLY_RECEIVAL_TIME_KEY).get.head
        val hcdRecTime       = event.get(EventConstants.HcdReceivalTime_Key).get.head
        val tpkPublishTime   = event.get(EventConstants.TimeStampKey).get.head
        val azPos            = event.get(EventConstants.AzPosKey).get.head
        val elPos            = event.get(EventConstants.ElPosKey).get.head
        this.azPosDemand.set(doubleToLongBits(azPos))
        this.elPosDemand.set(doubleToLongBits(elPos))
        demandCounter = demandCounter + 1
        demandBuffer += DemandPosHolder(tpkPublishTime, assemblyRecTime, hcdRecTime, simpleSimRecTime)
        if (demandCounter == 100000 && !fileUpdate) {
          writeEventDemandDataToFile
          fileUpdate = true
        } else {
          log.info(s" $demandCounter")
        }
        Behavior.same

      case msg: ProcCurrStateDemand =>
        val cs               = msg.currState
        val simpleSimRecTime = Instant.now()
        val assemblyRecTime  = cs.get(EventConstants.ASSEMBLY_RECEIVAL_TIME_KEY).get.head
        val hcdRecTime       = cs.get(EventConstants.HcdReceivalTime_Key).get.head
        val tpkPublishTime   = cs.get(EventConstants.TimeStampKey).get.head
        val azPos            = cs.get(EventConstants.AzPosKey).get.head
        val elPos            = cs.get(EventConstants.ElPosKey).get.head
        this.azPosDemand.set(doubleToLongBits(azPos))
        this.elPosDemand.set(doubleToLongBits(elPos))
        demandCounter = demandCounter + 1
        demandBuffer += DemandPosHolder(tpkPublishTime, assemblyRecTime, hcdRecTime, simpleSimRecTime)
        if (demandCounter == 100000 && !fileUpdate) {
          writeCurrentStatesDataToFile
          fileUpdate = true
        } else {
          log.info(s"$demandCounter")
        }
        Behavior.same
    }
  }

  private def writeCurrentStatesDataToFile = {
    val demandPosLogFile: File    = new File(logFilePath + "/PosDemEventSimpleLog_" + System.currentTimeMillis() + ".txt")
    val isDemFileCreated: Boolean = demandPosLogFile.createNewFile()
    log.error(s"Pos demand log file created ?: $isDemFileCreated")
    val printStream: PrintStream = new PrintStream(new FileOutputStream(demandPosLogFile), true)
    printStream.println(
      "PK publish timeStamp(t0),Assembly receive timeStamp(t1),HCD receive timeStamp(t2),Simulator receive timeStamp(t3)," +
      "PK to AssemblyTime(t1-t0),Assembly to HCDTime(t2-t0),HCD to SimulatorTime(t3-t2),PK to simulator totalTime(t3-t0)"
    )
    val demandPosList = demandBuffer.toList
    demandPosList.foreach(cp => {
      val pkToAssembly: Double  = Duration.between(cp.pkPublishTime, cp.assemblyRecTime).toNanos.toDouble / 1000000
      val assemblyToHCD: Double = Duration.between(cp.assemblyRecTime, cp.hcdRecTime).toNanos.toDouble / 1000000
      val hcdToSim: Double      = Duration.between(cp.hcdRecTime, cp.simRecTime).toNanos.toDouble / 1000000
      val pkToSim: Double       = Duration.between(cp.pkPublishTime, cp.simRecTime).toNanos.toDouble / 1000000

      val str: String = s"${getDate(cp.pkPublishTime).trim},${getDate(cp.assemblyRecTime).trim}," +
      s"${getDate(cp.hcdRecTime).trim},${getDate(cp.simRecTime).trim},${pkToAssembly.toString.trim}," +
      s"${assemblyToHCD.toString.trim},${hcdToSim.toString.trim},${pkToSim.toString.trim}"
      printStream.println(str)
    })
    printStream.flush()
    printStream.close()
    log.error(s"Successfully written data to file: ${demandPosLogFile.getAbsolutePath}")
  }

  private def writeEventDemandDataToFile = {
    val demandPosLogFile: File    = new File(logFilePath + "/PosDemEventSimpleLog_" + System.currentTimeMillis() + ".txt")
    val isDemFileCreated: Boolean = demandPosLogFile.createNewFile()
    log.info(s"Pos demand log file created ?: $isDemFileCreated")
    val printStream: PrintStream = new PrintStream(new FileOutputStream(demandPosLogFile), true)
    printStream.println(
      "PK publish timeStamp(t0),Assembly receive timeStamp(t1),HCD receive timeStamp(t2),Simulator receive timeStamp(t3)," +
      "PK to AssemblyTime(t1-t0),Assembly to HCDTime(t2-t0),HCD to SimulatorTime(t3-t2),PK to simulator totalTime(t3-t0)"
    )
    val demandPosList = demandBuffer.toList
    demandPosList.foreach(cp => {
      val pkToAssembly: Double  = Duration.between(cp.pkPublishTime, cp.assemblyRecTime).toNanos.toDouble / 1000000
      val assemblyToHCD: Double = Duration.between(cp.assemblyRecTime, cp.hcdRecTime).toNanos.toDouble / 1000000
      val hcdToSim: Double      = Duration.between(cp.hcdRecTime, cp.simRecTime).toNanos.toDouble / 1000000
      val pkToSim: Double       = Duration.between(cp.pkPublishTime, cp.simRecTime).toNanos.toDouble / 1000000

      val str: String = s"${getDate(cp.pkPublishTime).trim},${getDate(cp.assemblyRecTime).trim}," +
      s"${getDate(cp.hcdRecTime).trim},${getDate(cp.simRecTime).trim},${pkToAssembly.toString.trim}," +
      s"${assemblyToHCD.toString.trim},${hcdToSim.toString.trim},${pkToSim.toString.trim}"
      printStream.println(str)
    })
    log.info(s"Successfully written data to file: ${demandPosLogFile.getAbsolutePath}")
    printStream.flush()
    printStream.close()
  }

  private def writeOneWayCmdDataToFile = {
    val demandPosLogFile: File    = new File(logFilePath + "/PosDemEventSimpleLog_" + System.currentTimeMillis() + ".txt")
    val isDemFileCreated: Boolean = demandPosLogFile.createNewFile()
    log.info(s"Pos demand log file created ?: $isDemFileCreated")
    val printStream: PrintStream = new PrintStream(new FileOutputStream(demandPosLogFile), true)
    printStream.println(
      "PK publish timeStamp(t0),Assembly receive timeStamp(t1),HCD receive timeStamp(t2),Simulator receive timeStamp(t3)," +
      "PK to AssemblyTime(t1-t0),Assembly to HCDTime(t2-t0),HCD to SimulatorTime(t3-t2),PK to simulator totalTime(t3-t0)"
    )
    val demandPosList = demandBuffer.toList
    demandPosList.foreach(cp => {
      val pkToAssembly: Double  = Duration.between(cp.pkPublishTime, cp.assemblyRecTime).toNanos.toDouble / 1000000
      val assemblyToHCD: Double = Duration.between(cp.assemblyRecTime, cp.hcdRecTime).toNanos.toDouble / 1000000
      val hcdToSim: Double      = Duration.between(cp.hcdRecTime, cp.simRecTime).toNanos.toDouble / 1000000
      val pkToSim: Double       = Duration.between(cp.pkPublishTime, cp.simRecTime).toNanos.toDouble / 1000000

      val str: String = s"${getDate(cp.pkPublishTime).trim},${getDate(cp.assemblyRecTime).trim}," +
      s"${getDate(cp.hcdRecTime).trim},${getDate(cp.simRecTime).trim},${pkToAssembly.toString.trim}," +
      s"${assemblyToHCD.toString.trim},${hcdToSim.toString.trim},${pkToSim.toString.trim}"
      printStream.println(str)
    })
    printStream.flush()
    printStream.close()
    log.info(s"Successfully written data to file: ${demandPosLogFile.getAbsolutePath}")
  }

  def updateSimulator(commandName: String): Unit = {
    commandName match {
      case Commands.READCONFIGURATION =>
      //this.cmdPrintStream.println(getDate(Instant.now()).trim)
      case Commands.STARTUP =>
        startPublishingCurrPos()
        startPublishingHealth()
        log.info("Starting publish current position and health threads")
      case Commands.SHUTDOWN =>
        updateCurrPosPublisher(false)
        updateHealthPublisher(false)
        this.scheduler.shutdown()
        log.info("Updating current position publisher and health publisher to false")
      case _ =>
        log.info(s"Not changing publisher thread state as command received is $commandName")
    }
  }
  def updateCurrPosPublisher(value: Boolean): Unit = {
    this.currentPosPublisher.set(value)
    // println(s"Updating CurrentPosition publisher to : $value")
  }
  def updateHealthPublisher(value: Boolean): Unit = {
    this.healthPublisher.set(value)
    //println(s"Updating Health publisher to : ${this.healthPublisher.get()}")
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
      }
      if (currentPosPublisher.get()) {

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
          s"currentPos publisher curr val: ${currentPosPublisher.get()} Az position : $azC and el position : $elC " +
          s"demanded az : ${azPosDemand.get()}, el : ${elPosDemand.get()}"
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

  def startPublishingCurrPos(): Unit = scheduler.scheduleWithFixedDelay(currentPosRunner, 10, 10, TimeUnit.MILLISECONDS)

  val healthRunner = new Runnable {
    override def run(): Unit = {
      if (healthPublisher.get()) {

        val healthParam: Parameter[String]       = EventConstants.HEALTH_KEY.set("Good")
        val healthReasonParam: Parameter[String] = EventConstants.HEALTH_REASON_KEY.set("Good Reason")
        val timestamp                            = EventConstants.TimeStampKey.set(Instant.now())
        val currentState = CurrentState(prefix, StateName(EventConstants.HEALTH_STATE))
          .add(healthParam)
          .add(healthReasonParam)
          .add(timestamp)
        //log.info(s"Health publisher current value is : ${healthPublisher.get()}, publishing health")
        statePublisherActor ! PublishState(currentState)
      }
    }
  }
  def startPublishingHealth(): Unit = scheduler.scheduleWithFixedDelay(healthRunner, 1000, 1000, TimeUnit.MILLISECONDS)
}
