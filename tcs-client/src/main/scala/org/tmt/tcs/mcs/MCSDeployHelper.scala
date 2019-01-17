package org.tmt.tcs.mcs

import java.io._
import java.net.InetAddress
import java.time.{Instant, LocalDateTime, ZoneId}
import java.time.format.DateTimeFormatter
import java.time.Duration
import akka.actor.{typed, ActorRefFactory, ActorSystem, Scheduler}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import akka.actor.typed.scaladsl.adapter._
import csw.command.api.scaladsl.CommandService
import csw.command.client.CommandServiceFactory
import csw.event.api.scaladsl.EventService
import csw.event.client.EventServiceFactory
import csw.location.api.models.{AkkaLocation, ComponentId}
import csw.location.api.models.ComponentType.Assembly
import csw.location.api.models.Connection.AkkaConnection
import csw.location.client.ActorSystemFactory
import csw.location.client.scaladsl.HttpLocationServiceFactory
import csw.logging.scaladsl.LoggingSystemFactory
import csw.params.commands.CommandResponse.SubmitResponse
import csw.params.commands.{CommandName, CommandResponse, Setup}
import csw.params.core.generics.{Key, KeyType, Parameter}
import csw.params.core.models.{Id, Prefix}
import csw.params.events.{Event, SystemEvent}
import org.tmt.tcs.mcs.constants.{DeployConstants, EventConstants}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.ListBuffer
import scala.concurrent.{Await, ExecutionContext, Future}

object MCSDeployHelper {
  def create(simulatorMode: String): MCSDeployHelper = MCSDeployHelper(simulatorMode)
}
case class CurrentPosHolder(simPublishTime: Instant, hcdRecTime: Instant, assemblyRecTime: Instant, clientAppRecTime: Instant)
case class MCSDeployHelper(simulatorMode: String) {

  private val system: ActorSystem     = ActorSystemFactory.remote("Client-App")
  implicit val mat: ActorMaterializer = ActorMaterializer()
  private val locationService         = HttpLocationServiceFactory.makeLocalClient(system, mat)
  private val host                    = InetAddress.getLocalHost.getHostName
  LoggingSystemFactory.start("MCSMainApp", "0.1", host, system)

  import system._
  implicit val timeout: Timeout                 = Timeout(300.seconds)
  implicit val scheduler: Scheduler             = system.scheduler
  implicit def actorRefFactory: ActorRefFactory = system

  private val connection           = AkkaConnection(ComponentId("McsAssembly", Assembly))
  private val axesKey: Key[String] = KeyType.StringKey.make("axes")
  private val azKey: Key[Double]   = KeyType.DoubleKey.make("AZ")
  private val elKey: Key[Double]   = KeyType.DoubleKey.make("EL")

  /**
   * Gets a reference to the running assembly from the location service, if found.
   */
  private def getAssembly: CommandService = {
    implicit val sys: typed.ActorSystem[Nothing] = system.toTyped
    val akkaLocations: List[AkkaLocation]        = Await.result(locationService.listByPrefix("tcs.mcs.assembly"), 60.seconds)
    CommandServiceFactory.make(akkaLocations.head)(sys)
  }
  private def getEventService: EventService = {
    locationService.resolve(connection, 3000.seconds)
    val eventService: EventService = new EventServiceFactory().make(locationService)(system)
    eventService
  }

  val prefix               = Prefix("tmt.tcs.McsAssembly-Client")
  var currPosCounter: Long = 0

  val currPosBuffer = ListBuffer[CurrentPosHolder]()

  // Below commented code is for sending dummy commands.
  // val resp4 = Await.result(sendMoveCommand, 250.seconds)
  //println(s"Move command response is : $resp4 at : ${System.currentTimeMillis()}")

  /* var dummyImmCmd: Long = System.currentTimeMillis()
  val resp5             = Await.result(sendDummyImmediateCommand(), 10.seconds)
  println(s"Dummy immediate command Response is : $resp5 total time taken is : ${System.currentTimeMillis() - dummyImmCmd}")

  var dummyLongCmd: Long = System.currentTimeMillis()
  val resp6              = Await.result(sendDummyLongCommand(), 50.seconds)
  println(s"Dummy Long Command Response is : $resp6 total time taken is : ${System.currentTimeMillis() - dummyLongCmd}")*/

  /*var shutdownCmd: Long = System.currentTimeMillis()
  val resp7             = Await.result(sendShutDownCmd, 30.seconds)
  println(s"Shutdown Command Response is : ${resp7} total time taken is : ${System.currentTimeMillis() - shutdownCmd}")*/

  val logFilePath: String = System.getenv("LogFiles")

  // Below commented code is for writing command related things
  /*
  val clientAppCmdFile: File = new File(logFilePath + "/ClientAppCmdTime_" + System.currentTimeMillis() + "_.txt")
  clientAppCmdFile.createNewFile()
  var cmdCounter: Long            = 0
  val cmdPrintStream: PrintStream = new PrintStream(new FileOutputStream(clientAppCmdFile))
  this.cmdPrintStream.println("ClientApp cmd Name,clientApp Publish Timestamp")
  sendReadConfCommand()
   */

  def sendDummyImmediateCommand()(implicit ec: ExecutionContext): Future[CommandResponse] = {
    val dummyImmediate = Setup(prefix, CommandName("DummyImmediate"), None)
    val commandService = getAssembly
    commandService.submit(dummyImmediate)
  }
  def sendDummyLongCommand()(implicit ex: ExecutionContext): Future[CommandResponse] = {
    val commandService = getAssembly
    val dummyLong      = Setup(prefix, CommandName("DummyLong"), None)
    commandService.submit(dummyLong)
  }

  def sendStartupCommand()(implicit ec: ExecutionContext): CommandResponse = {
    val commandService  = getAssembly
    val setup           = Setup(prefix, CommandName("Startup"), None)
    val startUpSentTime = Instant.now
    val response        = Await.result(commandService.submit(setup), 10.seconds)
    val startUpRespTime = Instant.now
    import java.time.Duration
    val diff      = Duration.between(startUpSentTime, startUpRespTime).toNanos
    val millidiff = Duration.between(startUpSentTime, startUpRespTime).toMillis
    //println(s"Startup command response is :$response time is : $diff, $millidiff")
    response
  }
  def sendShutDownCmd()(implicit ec: ExecutionContext): CommandResponse = {
    val commandService   = getAssembly
    val setup            = Setup(prefix, CommandName("ShutDown"), None)
    val shutDownSentTime = Instant.now
    val response         = Await.result(commandService.submit(setup), 10.seconds)
    val shutDownRespTime = Instant.now
    import java.time.Duration
    val diff     = Duration.between(shutDownSentTime, shutDownRespTime).toNanos
    val millDiff = Duration.between(shutDownSentTime, shutDownRespTime).toMillis
    //println(s"Shutdown command response is :$response time is : $diff, $millDiff")
    response
  }
  def sendDatumCommand(): CommandResponse = {
    val datumParam: Parameter[String] = axesKey.set("BOTH")
    val setup                         = Setup(prefix, CommandName("Datum"), None).add(datumParam)
    val commandService                = getAssembly
    val datumSentTime                 = Instant.now
    val response                      = Await.result(commandService.submit(setup), 10.seconds)
    val datumRespTime                 = Instant.now
    import java.time.Duration
    val diff      = Duration.between(datumSentTime, datumRespTime).toNanos
    val milliDiff = Duration.between(datumSentTime, datumRespTime).toMillis
    //println(s"Datum command response is :$response time is : $diff, $milliDiff")
    response
  }
  def sendFollowCommand(): SubmitResponse = {
    val setup          = Setup(prefix, CommandName("Follow"), None)
    val commandService = getAssembly
    val followSentTime = Instant.now
    val response       = Await.result(commandService.submit(setup), 10.seconds)
    val followRespTime = Instant.now
    import java.time.Duration
    val diff      = Duration.between(followSentTime, followRespTime).toNanos
    val milliDiff = Duration.between(followSentTime, followRespTime).toMillis
   // println(s"Follow command response is :$response time is : $diff, $milliDiff")
    response
  }
  def sendSimulationModeCommand(): SubmitResponse = {
    val simulationModeKey: Key[String]    = KeyType.StringKey.make("SimulationMode")
    val simulModeParam: Parameter[String] = simulationModeKey.set(simulatorMode)
    val commandService                    = getAssembly
    val setup                             = Setup(prefix, CommandName("setSimulationMode"), None).add(simulModeParam)
    val simModeSentTime                   = Instant.now()
    val response                          = Await.result(commandService.submit(setup), 10.seconds)
    val simModeRespTime                   = Instant.now()
    import java.time.Duration
    val diff      = Duration.between(simModeSentTime, simModeRespTime).toNanos
    val milliDiff = Duration.between(simModeSentTime, simModeRespTime).toMillis
    //println(s"SimulationMode command response is : $response total time taken is : $diff, milli difference : $milliDiff")
    response
  }
  def sendMoveCommand(): SubmitResponse = {
    val axesParam: Parameter[String] = axesKey.set("BOTH")
    val azParam: Parameter[Double]   = azKey.set(1.5)
    val elParam: Parameter[Double]   = elKey.set(10)
    val commandService               = getAssembly
    val setup = Setup(prefix, CommandName("Move"), None)
      .add(axesParam)
      .add(azParam)
      .add(elParam)
    val moveSentTime = Instant.now()
    val response     = Await.result(commandService.submit(setup), 10.seconds)
    val moveRespTime = Instant.now()
    import java.time.Duration
    val diff      = Duration.between(moveSentTime, moveRespTime).toNanos
    val milliDiff = Duration.between(moveSentTime, moveRespTime).toMillis
   // println(s"Move command response is : $response total time taken is : $diff, milli diff.n is : $milliDiff")
    response
  }

  var fileBuilt: Boolean = false
  //Below commented code is for sending read conf command
  /*  def sendReadConfCommand()(implicit ec: ExecutionContext): Unit = {
    val commandService    = getAssembly
    val clientLogFilePath = clientAppCmdFile.getPath
    println(s"cmd log file path is : $clientLogFilePath")
    while (cmdCounter <= 100000000) {
      cmdCounter = cmdCounter + 1
      val setup                                              = Setup(prefix, CommandName(DeployConstants.READ_CONFIGURATION), None)
      val readConfSentTime                                   = getDate()
      val respFuture: Future[CommandResponse.SubmitResponse] = commandService.submit(setup)
      respFuture.onComplete {
        case Success(resp) => println(s"Response for command: ${setup.commandName.name + "_" + cmdCounter} is $resp")
        case Failure(e) =>
          println(s"Error occured while getting response for command : ${setup.commandName.name + "_" + cmdCounter}")
          e.printStackTrace()
      }
      this.cmdPrintStream.println(s"${setup.commandName.name},$readConfSentTime")
      /*val response         = Await.result(commandService.submit(setup), 10.seconds)
      val cmdName          = setup.commandName.name + "_" + cmdCounter
      println(s"Response for command : $cmdName is $response")*/

    }
    println("Successfully sent 10,00,00,000 commands to assembly stopped sending commands now.")
  }*/
  def getDate(): String =
    LocalDateTime.ofInstant(Instant.now(), ZoneId.of(DeployConstants.zoneFormat)).format(DeployConstants.formatter)

  def getDate(instant: Instant) =
    LocalDateTime.ofInstant(instant, ZoneId.of(DeployConstants.zoneFormat)).format(DeployConstants.formatter)

  def startSubscribingEvents(): Unit = {
    //println(" ****** Started subscribing Events from Assembly ****** ")
    val eventService = getEventService
    val subscriber   = eventService.defaultSubscriber
    subscriber.subscribeCallback(DeployConstants.currentPositionSet, event => processCurrentPosition(event))
    subscriber.subscribeCallback(DeployConstants.healthSet, event => processHealth(event))
  }

  def processHealth(event: Event): Unit = {
    val clientAppRecTime = Instant.now()
    event match {
      case systemEvent: SystemEvent =>
        val params                               = systemEvent.paramSet
        val simulatorSentTimeParam: Parameter[_] = params.find(msg => msg.keyName == EventConstants.TIMESTAMP).get
        val simulatorPublishTime                 = simulatorSentTimeParam.head
        val hcdReceiveTime                       = params.find(msg => msg.keyName == EventConstants.HCD_EventReceivalTime).get.head
        val assemblyRecTime                      = params.find(msg => msg.keyName == EventConstants.ASSEMBLY_EVENT_RECEIVAL_TIME).get.head
      //println(s"Health, $simulatorPublishTime, $hcdReceiveTime, $assemblyRecTime, $clientAppRecTime")
    }
    //Future.successful[String]("Successfully processed Health event from assembly")
  }
  def processCurrentPosition(event: Event): Unit = {
    currPosCounter = currPosCounter + 1
    if (currPosCounter <= 100000) {
      val clientAppRecTime = Instant.now()
      event match {
        case systemEvent: SystemEvent =>
          val params = systemEvent.paramSet
          // val azPosParam: Parameter[_] = params.find(msg => msg.keyName == EventConstants.POINTING_KERNEL_AZ_POS).get
          //val elPosParam: Parameter[_] = params.find(msg => msg.keyName == EventConstants.POINTING_KERNEL_EL_POS).get
          val simulatorSentTimeParam  = params.find(msg => msg.keyName == EventConstants.TIMESTAMP).get
          val simulatorPublishTime    = simulatorSentTimeParam.head
          val hcdReceiveTime          = params.find(msg => msg.keyName == EventConstants.HCD_EventReceivalTime).get.head
          val assemblyRecTime         = params.find(msg => msg.keyName == EventConstants.ASSEMBLY_EVENT_RECEIVAL_TIME).get.head
          var simPubTime: Instant     = null
          var hcdRecTime: Instant     = null
          var assemblyReTime: Instant = null
          simulatorPublishTime match {
            case x: Instant => simPubTime = x
          }
          hcdReceiveTime match {
            case x: Instant => hcdRecTime = x
          }
          assemblyRecTime match {
            case x: Instant => assemblyReTime = x
          }
          currPosBuffer += CurrentPosHolder(simPubTime, hcdRecTime, assemblyReTime, clientAppRecTime)
      }

    } else {
      println("Stopped subscribing events as counter reached 1,00,000")
      if (!fileBuilt) {
        writeCurrPosToFile
        fileBuilt = true
      }
      Thread.sleep(10000)
    }
  }

  private def writeCurrPosToFile = {
    val currPosLogFile: File = new File(logFilePath + "/CurrentPosition_" + System.currentTimeMillis() + "_.txt")
    currPosLogFile.createNewFile()

    val printStream: PrintStream = new PrintStream(new FileOutputStream(currPosLogFile), true)
    printStream.println(
      "Simulator publish timeStamp(t0),HCD receive timeStamp(t1),Assembly receive timeStamp(t2),ClientApp receive timeStamp(t3),Simulator to HCD(t1-t0)," +
      "HCD to Assembly(t2-t1),Assembly to ClientApp(t3-t2),Simulator to ClientApp totalTime(t3-t0)"
    )
    val currPosList = currPosBuffer.toList

    currPosList.foreach(cp => {
      val simToHCD: Double            = Duration.between(cp.simPublishTime, cp.hcdRecTime).toNanos.toDouble / 1000000
      val hcdToAssembly: Double       = Duration.between(cp.hcdRecTime, cp.assemblyRecTime).toNanos.toDouble / 1000000
      val assemblyToClientApp: Double = Duration.between(cp.assemblyRecTime, cp.clientAppRecTime).toNanos.toDouble / 1000000
      val simToClientApp: Double      = Duration.between(cp.simPublishTime, cp.clientAppRecTime).toNanos.toDouble / 1000000
      val str: String = s"${getDate(cp.simPublishTime).trim},${getDate(cp.hcdRecTime).trim}," +
      s"${getDate(cp.assemblyRecTime).trim},${getDate(cp.clientAppRecTime).trim},${simToHCD.toString.trim}," +
      s"${hcdToAssembly.toString.trim},${assemblyToClientApp.toString.trim},${simToClientApp.toString.trim}"
      printStream.println(str)
    })
    printStream.flush()
    printStream.close()
    println(s"Successfully written data to file:${currPosLogFile.getAbsolutePath}")
  }
}
