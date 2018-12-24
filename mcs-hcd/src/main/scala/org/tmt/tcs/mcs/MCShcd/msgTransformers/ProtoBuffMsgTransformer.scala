package org.tmt.tcs.mcs.MCShcd.msgTransformers
import java.time.Instant

import com.google.protobuf.Timestamp
import csw.logging.scaladsl.LoggerFactory
import csw.params.commands.ControlCommand
import csw.params.core.generics.Parameter
import csw.params.core.states.CurrentState
import csw.params.events.SystemEvent
import org.tmt.tcs.mcs.MCShcd.constants.{Commands, EventConstants}
import org.tmt.tcs.mcs.MCShcd.msgTransformers.protos.TcsMcsCommandProtos._
import org.tmt.tcs.mcs.MCShcd.msgTransformers.protos.TcsMcsEventsProtos._

object ProtoBuffMsgTransformer {
  def create(loggerFactory: LoggerFactory): ProtoBuffMsgTransformer = ProtoBuffMsgTransformer(loggerFactory)
}

case class ProtoBuffMsgTransformer(loggerFactory: LoggerFactory) extends IMessageTransformer {
  private val log                                      = loggerFactory.getLogger
  private val paramSetTransformer: ParamSetTransformer = ParamSetTransformer.create(loggerFactory)

  override def decodeCommandResponse(responsePacket: Array[Byte]): SubystemResponse = {
    val commandResponse: MCSCommandResponse   = MCSCommandResponse.parseFrom(responsePacket)
    val cmdError: MCSCommandResponse.CmdError = commandResponse.getCmdError
    var cmdErrorBool: Boolean                 = false
    cmdError match {
      case MCSCommandResponse.CmdError.OK =>
        cmdErrorBool = true
        SubystemResponse(cmdErrorBool, None, None)
      case _ => SubystemResponse(cmdErrorBool, Some(commandResponse.getCmdError.toString), Some(commandResponse.getErrorInfo))
    }

  }
  override def decodeEvent(eventName: String, encodedEventData: Array[Byte]): CurrentState = {
    eventName match {
      case EventConstants.CURRENT_POSITION =>
        val mcsCurrentPosEvent: McsCurrentPositionEvent = McsCurrentPositionEvent.parseFrom(encodedEventData)
        paramSetTransformer.getMountCurrentPosition(mcsCurrentPosEvent)
      case EventConstants.DIAGNOSIS_STATE =>
        val diagnosis: MountControlDiags = MountControlDiags.parseFrom(encodedEventData)
        paramSetTransformer.getMountControlDignosis(diagnosis)
      case EventConstants.DRIVE_STATE =>
        val driveState: McsDriveStatus = McsDriveStatus.parseFrom(encodedEventData)
        paramSetTransformer.getMCSDriveStatus(driveState)
      case EventConstants.HEALTH_STATE =>
        var healthState: McsHealth = null
        try {
          healthState = McsHealth.parseFrom(encodedEventData)
        } catch {
          case e: Exception =>
            e.printStackTrace()
            val instant = Instant.now()
            val timestamp: com.google.protobuf.Timestamp =
              Timestamp.newBuilder().setNanos(instant.getNano).setSeconds(instant.getEpochSecond).build()
            healthState = McsHealth
              .newBuilder()
              .setHealth(McsHealth.Health.Good)
              .setReason("All is well")
              .setTime(timestamp)
              .build()
            log.error(s"Publishing dummy health from exception :$healthState")
        }
        paramSetTransformer.getMCSHealth(healthState)
    }

  }

  override def encodeMessage(controlCommand: ControlCommand): Array[Byte] = {

    controlCommand.commandName.name match {
      case Commands.FOLLOW       => getFollowCommandBytes
      case Commands.DATUM        => getDatumCommandBytes(controlCommand)
      case Commands.POINT        => getPointCommandBytes(controlCommand)
      case Commands.POINT_DEMAND => getPointDemandCommandBytes(controlCommand)
      case Commands.STARTUP      => getStartupCommandBytes
      case Commands.SHUTDOWN     => getShutdownCommandBytes
    }
  }
  override def encodeCurrentState(currentState: CurrentState): Array[Byte] = {
    var azPos = 0.0
    var elPos = 0.0
    if (currentState.exists(EventConstants.AzPosKey)) {
      azPos = currentState.get(EventConstants.AzPosKey).get.head
    }
    if (currentState.exists(EventConstants.ElPosKey)) {
      elPos = currentState.get(EventConstants.ElPosKey).get.head
    }

    val hcdRecInstant   = currentState.get(EventConstants.HcdReceivalTime_Key).get.head
    val hcdRecTimeStamp = Timestamp.newBuilder().setNanos(hcdRecInstant.getNano).setSeconds(hcdRecInstant.getEpochSecond).build()

    val tpkPublishInstant = currentState.get(EventConstants.TimeStampKey).get.head
    val tpkPubTimeStamp =
      Timestamp.newBuilder().setNanos(tpkPublishInstant.getNano).setSeconds(tpkPublishInstant.getEpochSecond).build()

    val assemblyRecInstant = currentState.get(EventConstants.ASSEMBLY_RECEIVAL_TIME_KEY).get.head
    val assemblyRecTimeStamp =
      Timestamp.newBuilder().setNanos(assemblyRecInstant.getNano).setSeconds(assemblyRecInstant.getEpochSecond).build()
    val event: TcsPositionDemandEvent = TcsPositionDemandEvent
      .newBuilder()
      .setAzimuth(azPos)
      .setElevation(elPos)
      .setHcdReceivalTime(hcdRecTimeStamp)
      .setAssemblyReceivalTime(assemblyRecTimeStamp)
      .setTpkPublishTime(tpkPubTimeStamp)
      .build()
    event.toByteArray
  }

  override def encodeEvent(systemEvent: SystemEvent): Array[Byte] = {

    var azParam = 0.0
    if (systemEvent.exists(EventConstants.AzPosKey)) {
      azParam = systemEvent.get(EventConstants.AzPosKey).get.head
    }
    var elParam = 0.0
    if (systemEvent.exists(EventConstants.ElPosKey)) {
      elParam = systemEvent.get(EventConstants.ElPosKey).get.head
    }

    var assemblySentTime = Instant.now()
    if (systemEvent.exists(EventConstants.ASSEMBLY_RECEIVAL_TIME_KEY)) {
      assemblySentTime = systemEvent.get(EventConstants.ASSEMBLY_RECEIVAL_TIME_KEY).get.head
    }

    val hcdRecInstant   = systemEvent.get(EventConstants.HcdReceivalTime_Key).get.head
    val hcdRecTimeStamp = Timestamp.newBuilder().setNanos(hcdRecInstant.getNano).setSeconds(hcdRecInstant.getEpochSecond).build()

    val pkPubInstant   = systemEvent.get(EventConstants.TimeStampKey).get.head
    val pkPubTimeStamp = Timestamp.newBuilder().setNanos(pkPubInstant.getNano).setSeconds(pkPubInstant.getEpochSecond).build()

    val assemblyRecInstant = systemEvent.get(EventConstants.ASSEMBLY_RECEIVAL_TIME_KEY).get.head
    val assemblyRecTimeStamp =
      Timestamp.newBuilder().setNanos(assemblyRecInstant.getNano).setSeconds(assemblyRecInstant.getEpochSecond).build()

    val event = TcsPositionDemandEvent
      .newBuilder()
      .setAzimuth(azParam)
      .setElevation(elParam)
      .setHcdReceivalTime(hcdRecTimeStamp)
      .setTpkPublishTime(pkPubTimeStamp)
      .setAssemblyReceivalTime(assemblyRecTimeStamp)
      .build()
    event.toByteArray

  }

  def getFollowCommandBytes: Array[Byte] = {
    val command: FollowCommand = FollowCommand.newBuilder().build()
    command.toByteArray
  }
  def getDatumCommandBytes(controlCommand: ControlCommand): Array[Byte] = {
    val axesParam: Parameter[_] = controlCommand.paramSet.find(msg => msg.keyName == "axes").get
    val param1                  = axesParam.head
    var axes: Axes              = Axes.BOTH
    if (param1 == "AZ") {
      axes = Axes.AZ
    }
    if (param1 == "EL") {
      axes = Axes.EL
    }

    val command: DatumCommand = DatumCommand.newBuilder().setAxes(axes).build()
    command.toByteArray
  }
  def getPointCommandBytes(controlCommand: ControlCommand): Array[Byte] = {
    val axesParam: Parameter[_] = controlCommand.paramSet.find(msg => msg.keyName == "axes").get
    val param1                  = axesParam.head
    var axes: Axes              = Axes.BOTH
    if (param1 == "AZ") {
      axes = Axes.AZ
    }
    if (param1 == "EL") {
      axes = Axes.EL
    }

    val command: PointCommand = PointCommand.newBuilder().setAxes(axes).build()
    command.toByteArray
  }
  def getPointDemandCommandBytes(controlCommand: ControlCommand): Array[Byte] = {
    val azParam: Parameter[_]       = controlCommand.paramSet.find(msg => msg.keyName == "AZ").get
    val azValue: Any                = azParam.head
    val elParam: Parameter[_]       = controlCommand.paramSet.find(msg => msg.keyName == "EL").get
    val elValue: Any                = elParam.head
    val az: Double                  = azValue.asInstanceOf[Number].doubleValue()
    val el: Double                  = elValue.asInstanceOf[Number].doubleValue()
    val command: PointDemandCommand = PointDemandCommand.newBuilder().setAZ(az).setEL(el).build()
    command.toByteArray
  }
  def getStartupCommandBytes: Array[Byte] = {
    val command: Startup = Startup.newBuilder().build()
    command.toByteArray
  }
  def getShutdownCommandBytes: Array[Byte] = {
    val command: Shutdown = Shutdown.newBuilder().build()
    command.toByteArray
  }

}
