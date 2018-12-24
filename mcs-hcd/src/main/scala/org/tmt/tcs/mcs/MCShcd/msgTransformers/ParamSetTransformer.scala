package org.tmt.tcs.mcs.MCShcd.msgTransformers

import java.time.Instant

import csw.logging.scaladsl.LoggerFactory
import csw.params.commands.CommandIssue.WrongInternalStateIssue
import csw.params.commands.CommandResponse.{Completed, SubmitResponse}
import csw.params.commands.{CommandIssue, CommandResponse, ControlCommand}
import csw.params.core.generics.{Key, Parameter}
import csw.params.core.models.Units.degree
import csw.params.core.models.{Id, Prefix, Subsystem}
import csw.params.core.states.{CurrentState, StateName}
import csw.params.events.{EventName, SystemEvent}
import org.tmt.tcs.mcs.MCShcd.constants.EventConstants
import org.tmt.tcs.mcs.MCShcd.msgTransformers.protos.TcsMcsEventsProtos.{
  McsCurrentPositionEvent,
  McsDriveStatus,
  McsHealth,
  MountControlDiags
}

object ParamSetTransformer {
  def create(loggerFactory: LoggerFactory): ParamSetTransformer = ParamSetTransformer(loggerFactory)
}

case class ParamSetTransformer(loggerFactory: LoggerFactory) {

  //private val log                     = loggerFactory.getLogger
  private val prefix                     = Prefix(Subsystem.MCS.toString)
  private val timeStampKey: Key[Instant] = EventConstants.TimeStampKey
  def getMountDemandPositions(msg: ControlCommand): SystemEvent = {
    val paramSet                            = msg.paramSet
    val azPosParam: Option[Parameter[_]]    = paramSet.find(msg => msg.keyName == EventConstants.POINTING_KERNEL_AZ_POS)
    val elPosParam: Option[Parameter[_]]    = paramSet.find(msg => msg.keyName == EventConstants.POINTING_KERNEL_EL_POS)
    val sentTimeParam: Option[Parameter[_]] = paramSet.find(msg => msg.keyName == EventConstants.TIMESTAMP)
    val azPos                               = azPosParam.getOrElse(EventConstants.AzPosKey.set(0.0))
    val elPos                               = elPosParam.getOrElse(EventConstants.ElPosKey.set(0.0))
    val sentTime                            = sentTimeParam.getOrElse(EventConstants.TimeStampKey.set(Instant.now()))
    val assemblyRecTime                     = paramSet.find(msg => msg.keyName == EventConstants.ASSEMBLY_RECEIVAL_TIME).get
    val hcdRecTime                          = paramSet.find(msg => msg.keyName == EventConstants.HCD_ReceivalTime).get
    SystemEvent(Prefix(EventConstants.TPK_PREFIX), EventName(EventConstants.MOUNT_DEMAND_POSITION))
      .add(azPos)
      .add(elPos)
      .add(sentTime)
      .add(assemblyRecTime)
      .add(hcdRecTime)

  }
  def getMountDemandPositions(currentState: CurrentState): SystemEvent = {
    val azPosOption    = currentState.get(EventConstants.AzPosKey)
    val elPosOption    = currentState.get(EventConstants.ElPosKey)
    val sentTimeOption = currentState.get(EventConstants.TimeStampKey)
    val azPos          = azPosOption.getOrElse(EventConstants.AzPosKey.set(0.0))
    val elPos          = elPosOption.getOrElse(EventConstants.ElPosKey.set(0.0))
    val sentTime       = sentTimeOption.getOrElse(EventConstants.TimeStampKey.set(Instant.now()))
    val event = SystemEvent(Prefix(EventConstants.TPK_PREFIX), EventName(EventConstants.MOUNT_DEMAND_POSITION))
      .add(azPos)
      .add(elPos)
      .add(sentTime)
      .add(currentState.get(EventConstants.ASSEMBLY_RECEIVAL_TIME_KEY).get)
      .add(currentState.get(EventConstants.HcdReceivalTime_Key).get)
    event
  }
  /* def getMountDemandPositions(systemEvent: SystemEvent): MCSPositionDemand = {

    MCSPositionDemand(trackID, azParam, elParam)
  }*/
  def getHCDState(state: String): CurrentState = {
    val lifeCycleStateKey                 = EventConstants.LifeCycleStateKey
    val lifeCycleParam: Parameter[String] = lifeCycleStateKey.set(state)
    val timestamp                         = timeStampKey.set(Instant.now())
    CurrentState(prefix, StateName(EventConstants.HCDLifecycleState)).add(lifeCycleParam).add(timestamp)
  }
  /*
    This function takes mcs current position proto as input and transforms it into
    CSW current state for this it uses keys present EventConstants Helper class.
   */
  def getMountCurrentPosition(mcsCurrentPosEvent: McsCurrentPositionEvent): CurrentState = {

    val azPosParam: Parameter[Double] = EventConstants.AzPosKey.set(mcsCurrentPosEvent.getAzPos).withUnits(degree)
    val elPosParam: Parameter[Double] = EventConstants.ElPosKey.set(mcsCurrentPosEvent.getElPos).withUnits(degree)

    val azPosErrorParam: Parameter[Double] =
      EventConstants.AZ_POS_ERROR_KEY.set(mcsCurrentPosEvent.getAzPosError).withUnits(degree)
    val elPosErrorParam: Parameter[Double] =
      EventConstants.EL_POS_ERROR_KEY.set(mcsCurrentPosEvent.getElPosError).withUnits(degree)

    val azInPositionParam: Parameter[Boolean]         = EventConstants.AZ_InPosition_Key.set(mcsCurrentPosEvent.getAzInPosition)
    val elInPositionParam: Parameter[Boolean]         = EventConstants.EL_InPosition_Key.set(mcsCurrentPosEvent.getElInPosition)
    val protoTimeStamp: com.google.protobuf.Timestamp = mcsCurrentPosEvent.getTime
    val timestamp                                     = timeStampKey.set(Instant.ofEpochSecond(protoTimeStamp.getSeconds, protoTimeStamp.getNanos))

    CurrentState(prefix, StateName(EventConstants.CURRENT_POSITION))
      .add(azPosParam)
      .add(elPosParam)
      .add(azPosErrorParam)
      .add(elPosErrorParam)
      .add(azInPositionParam)
      .add(elInPositionParam)
      .add(timestamp)
  }
  /*
    This function takes MountControlDiags Proto as input and transforms it into
    CSW CurrentState object for publishing to Assembly
   */
  def getMountControlDignosis(diagnosis: MountControlDiags): CurrentState = {
    val azPosParam: Parameter[Double] = EventConstants.AzPosKey.set(diagnosis.getAzPosDemand).withUnits(degree)
    val elPosParam: Parameter[Double] = EventConstants.ElPosKey.set(diagnosis.getElPosDemand).withUnits(degree)

    val azPosErrorParam: Parameter[Double] = EventConstants.AZ_POS_ERROR_KEY.set(diagnosis.getAzPosError).withUnits(degree)
    val elPosErrorParam: Parameter[Double] = EventConstants.EL_POS_ERROR_KEY.set(diagnosis.getElPosError).withUnits(degree)

    val azInPositionParam: Parameter[Boolean]         = EventConstants.AZ_InPosition_Key.set(diagnosis.getAzInPosition)
    val elInPositionParam: Parameter[Boolean]         = EventConstants.EL_InPosition_Key.set(diagnosis.getElInPosition)
    val protoTimeStamp: com.google.protobuf.Timestamp = diagnosis.getTime
    val timestamp                                     = timeStampKey.set(Instant.ofEpochSecond(protoTimeStamp.getSeconds, protoTimeStamp.getNanos))
    CurrentState(prefix, StateName(EventConstants.DIAGNOSIS_STATE))
      .add(azPosParam)
      .add(elPosParam)
      .add(azPosErrorParam)
      .add(elPosErrorParam)
      .add(azInPositionParam)
      .add(elInPositionParam)
      .add(timestamp)

  }
  /*
  This function takes MCSDriveStatus proto as input and returns CSW currentState
  populated with drive status parameters
   */
  def getMCSDriveStatus(driveStatus: McsDriveStatus): CurrentState = {
    val processingCmdParam: Parameter[Boolean]        = EventConstants.PROCESSING_PARAM_KEY.set(driveStatus.getProcessing)
    val mcdLifecycleStateParam: Parameter[String]     = EventConstants.LifeCycleStateKey.set(driveStatus.getLifecycle.toString)
    val mcsAzState: Parameter[String]                 = EventConstants.MCS_AZ_STATE.set(driveStatus.getAzstate.name())
    val mcsElState: Parameter[String]                 = EventConstants.MCS_EL_STATE.set(driveStatus.getElstate.name())
    val protoTimeStamp: com.google.protobuf.Timestamp = driveStatus.getTime
    val timestamp                                     = timeStampKey.set(Instant.ofEpochSecond(protoTimeStamp.getSeconds, protoTimeStamp.getNanos))

    CurrentState(prefix, StateName(EventConstants.DRIVE_STATE))
      .add(processingCmdParam)
      .add(mcdLifecycleStateParam)
      .add(mcsAzState)
      .add(mcsElState)
      .add(timestamp)
  }
  def getMCSHealth(health: McsHealth): CurrentState = {
    val healthParam: Parameter[String]                = EventConstants.HEALTH_KEY.set(health.getHealth.name())
    val healthReasonParam: Parameter[String]          = EventConstants.HEALTH_REASON_KEY.set(health.getReason)
    val protoTimeStamp: com.google.protobuf.Timestamp = health.getTime
    val timestamp                                     = timeStampKey.set(Instant.ofEpochSecond(protoTimeStamp.getSeconds, protoTimeStamp.getNanos))

    CurrentState(prefix, StateName(EventConstants.HEALTH_STATE))
      .add(healthParam)
      .add(healthReasonParam)
      .add(timestamp)
  }

  def getCSWResponse(runID: Id, subsystemResponse: SubystemResponse): SubmitResponse = {
    if (subsystemResponse.commandResponse) {
      Completed(runID)
    } else {
      decodeErrorState(runID, subsystemResponse)
    }

  }
  def decodeErrorState(runID: Id, response: SubystemResponse): SubmitResponse = {
    response.errorReason.get match {
      case "ILLEGAL_STATE" => CommandResponse.Invalid(runID, WrongInternalStateIssue(response.errorInfo.get))
      case "BUSY"          => CommandResponse.Invalid(runID, CommandIssue.OtherIssue(response.errorInfo.get))
      case "OUT_OF_RANGE"  => CommandResponse.Invalid(runID, CommandIssue.ParameterValueOutOfRangeIssue(response.errorInfo.get))
      case "OUT_OF_SPEC"   => CommandResponse.Invalid(runID, CommandIssue.WrongParameterTypeIssue(response.errorInfo.get))
      case "FAILED"        => CommandResponse.Error(runID, response.errorInfo.get)
      case _               => CommandResponse.Invalid(runID, CommandIssue.UnsupportedCommandInStateIssue("unknown command send"))
    }
  }

}
