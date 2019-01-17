package org.tmt.tcs.mcs.MCSassembly.msgTransformer

import java.time.Instant

import csw.logging.scaladsl.LoggerFactory
import csw.params.commands.{CommandName, ControlCommand, Setup}
import csw.params.core.generics.Parameter
import csw.params.core.models.Prefix
import csw.params.core.states.{CurrentState, StateName}
import csw.params.events.{Event, SystemEvent}
import org.tmt.tcs.mcs.MCSassembly.Constants.{Commands, EventConstants, EventHandlerConstants}
import org.tmt.tcs.mcs.MCSassembly.MonitorMessage.AssemblyCurrentState

object EventTransformerHelper {
  def create(loggerFactory: LoggerFactory): EventTransformerHelper = EventTransformerHelper(loggerFactory)
}
case class EventTransformerHelper(loggerFactory: LoggerFactory) {

  private val log = loggerFactory.getLogger

  /*
  This function takes assemblyCurrentState as input and returns
  AssemblyState system event
   */
  def getAssemblyEvent(assemblyState: AssemblyCurrentState): Event = {
    val lifeCycleKey                        = EventHandlerConstants.LifecycleStateKey
    val operationalStateKey                 = EventHandlerConstants.OperationalStateKey
    val lifecycleParam: Parameter[String]   = lifeCycleKey.set(assemblyState.lifeCycleState.toString)
    val operationalParam: Parameter[String] = operationalStateKey.set(assemblyState.operationalState.toString)
    val systemEvent = SystemEvent(EventHandlerConstants.assemblyStateEventPrefix,
                                  EventHandlerConstants.eventName,
                                  Set(lifecycleParam, operationalParam))
    systemEvent

  }

  /*
    This function transforms mount demand positions systemEvent into CurrrentState
   */
  def getCurrentState(event: SystemEvent): CurrentState = {
    val azParamOption: Option[Parameter[Double]]       = event.get(EventHandlerConstants.AzPosKey)
    val elParamOption: Option[Parameter[Double]]       = event.get(EventHandlerConstants.ElPosKey)
    val sentTimeOption: Option[Parameter[Instant]]     = event.get(EventHandlerConstants.TimeStampKey) //tpk publish time
    val assemblyRecTimeOpt: Option[Parameter[Instant]] = event.get(EventHandlerConstants.ASSEMBLY_RECEIVAL_TIME_KEY)
    CurrentState(Prefix(EventConstants.TPK_PREFIX), StateName(EventConstants.MOUNT_DEMAND_POSITION))
      .add(azParamOption.get)
      .add(elParamOption.get)
      .add(sentTimeOption.get)
      .add(assemblyRecTimeOpt.get)
  }
  /*
    This function converts currentPosition from HCD wrapped in  currentState to systemEvent
   */
  def getCurrentPositionEvent(currentState: CurrentState, assemblyEventRecvTime: Instant): SystemEvent = {
    // log.info(s"Received event : $currentState from simulator")
    val azPosParam: Option[Parameter[Double]]        = currentState.get(EventHandlerConstants.AzPosKey)
    val elPosParam: Option[Parameter[Double]]        = currentState.get(EventHandlerConstants.ElPosKey)
    val azPosErrorParam: Option[Parameter[Double]]   = currentState.get(EventHandlerConstants.AZ_POS_ERROR_KEY)
    val elPosErrorParam: Option[Parameter[Double]]   = currentState.get(EventHandlerConstants.EL_POS_ERROR_KEY)
    val azInPosKey: Option[Parameter[Boolean]]       = currentState.get(EventHandlerConstants.AZ_InPosition_Key)
    val elInPosKey: Option[Parameter[Boolean]]       = currentState.get(EventHandlerConstants.EL_InPosition_Key)
    val timeStampKey: Option[Parameter[Instant]]     = currentState.get(EventHandlerConstants.TimeStampKey)
    val hcdEventRecevKey: Option[Parameter[Instant]] = currentState.get(EventHandlerConstants.HCD_Event_RECEV_TIME_KEY)

    SystemEvent(EventHandlerConstants.CURRENT_POSITION_PREFIX, EventHandlerConstants.CURRENT_POSITION_STATE)
      .add(azPosParam.getOrElse(EventHandlerConstants.AzPosKey.set(0)))
      .add(elPosParam.getOrElse(EventHandlerConstants.ElPosKey.set(0)))
      .add(azPosErrorParam.getOrElse(EventHandlerConstants.AZ_POS_ERROR_KEY.set(0)))
      .add(elPosErrorParam.getOrElse(EventHandlerConstants.EL_POS_ERROR_KEY.set(0)))
      .add(azInPosKey.getOrElse(EventHandlerConstants.AZ_InPosition_Key.set(true)))
      .add(elInPosKey.getOrElse(EventHandlerConstants.EL_InPosition_Key.set(true)))
      .add(hcdEventRecevKey.get)
      .add(timeStampKey.getOrElse(timeStampKey.get))
      .add(EventHandlerConstants.ASSEMBLY_EVENT_RECEV_TIME_KEY.set(assemblyEventRecvTime))

  }
  def getDiagnosisEvent(currentState: CurrentState, assemblyEventRecvTime: Instant): Event = {
    val azPosParam: Option[Parameter[Double]]        = currentState.get(EventHandlerConstants.AzPosKey)
    val elPosParam: Option[Parameter[Double]]        = currentState.get(EventHandlerConstants.ElPosKey)
    val azPosErrorParam: Option[Parameter[Double]]   = currentState.get(EventHandlerConstants.AZ_POS_ERROR_KEY)
    val elPosErrorParam: Option[Parameter[Double]]   = currentState.get(EventHandlerConstants.EL_POS_ERROR_KEY)
    val azInPosKey: Option[Parameter[Boolean]]       = currentState.get(EventHandlerConstants.AZ_InPosition_Key)
    val elInPosKey: Option[Parameter[Boolean]]       = currentState.get(EventHandlerConstants.EL_InPosition_Key)
    val timeStampKey: Option[Parameter[Instant]]     = currentState.get(EventHandlerConstants.TimeStampKey)
    val hcdEventRecevKey: Option[Parameter[Instant]] = currentState.get(EventHandlerConstants.HCD_Event_RECEV_TIME_KEY)

    val event: SystemEvent = SystemEvent(
      EventHandlerConstants.DIAGNOSIS_PREFIX,
      EventHandlerConstants.DIAGNOSIS_STATE,
      Set(azPosParam.get,
          elPosParam.get,
          azPosErrorParam.get,
          elPosErrorParam.get,
          azInPosKey.get,
          elInPosKey.get,
          timeStampKey.get,
          hcdEventRecevKey.get,
      )
    )
    event.add(EventHandlerConstants.ASSEMBLY_EVENT_RECEV_TIME_KEY.set(assemblyEventRecvTime))
  }
  def getHealthEvent(currentState: CurrentState, assemblyEventRecvTime: Instant): Event = {
    val health: Option[Parameter[String]]            = currentState.get(EventHandlerConstants.HEALTH_KEY)
    val healthReason: Option[Parameter[String]]      = currentState.get(EventHandlerConstants.HEALTH_REASON_KEY)
    val timeStampKey: Option[Parameter[Instant]]     = currentState.get(EventHandlerConstants.TimeStampKey)
    val hcdEventRecevKey: Option[Parameter[Instant]] = currentState.get(EventHandlerConstants.HCD_Event_RECEV_TIME_KEY)

    val event: SystemEvent = SystemEvent(EventHandlerConstants.HEALTH_PREFIX, EventHandlerConstants.HEALTH_STATE)
      .add(health.get)
      .add(healthReason.get)
      .add(timeStampKey.get)

    event
      .add(EventHandlerConstants.ASSEMBLY_EVENT_RECEV_TIME_KEY.set(assemblyEventRecvTime))
      .add(hcdEventRecevKey.get)

  }
  def getDriveState(currentState: CurrentState, assemblyEventRecvTime: Instant): Event = {
    val processing: Option[Parameter[Boolean]]       = currentState.get(EventHandlerConstants.PROCESSING_PARAM_KEY)
    val lifecycleState: Option[Parameter[String]]    = currentState.get(EventHandlerConstants.MCS_LIFECYCLE_STATTE_KEY)
    val azState: Option[Parameter[String]]           = currentState.get(EventHandlerConstants.MCS_AZ_STATE)
    val elState: Option[Parameter[String]]           = currentState.get(EventHandlerConstants.MCS_EL_STATE)
    val timeStampKey: Option[Parameter[Instant]]     = currentState.get(EventHandlerConstants.TimeStampKey)
    val hcdEventRecevKey: Option[Parameter[Instant]] = currentState.get(EventHandlerConstants.HCD_Event_RECEV_TIME_KEY)

    val event: SystemEvent = SystemEvent(
      EventHandlerConstants.HEALTH_PREFIX,
      EventHandlerConstants.HEALTH_STATE,
      Set(processing.get, lifecycleState.get, azState.get, elState.get, timeStampKey.get)
    )
    event
      .add(EventHandlerConstants.ASSEMBLY_EVENT_RECEV_TIME_KEY.set(assemblyEventRecvTime))
      .add(hcdEventRecevKey.get)
  }

  /*
  This function takes system event as input and from systemEvent it builds  controlCommand object
  for sending to HCD as oneWayCommand
   */
  def getOneWayCommandObject(systemEvent: SystemEvent): ControlCommand = {
    val sentTimeOption: Option[Parameter[Instant]]     = systemEvent.get(EventHandlerConstants.TimeStampKey) //tpk publish time
    val assemblyRecTimeOpt: Option[Parameter[Instant]] = systemEvent.get(EventHandlerConstants.ASSEMBLY_RECEIVAL_TIME_KEY)
    val azParam: Option[Parameter[Double]]             = systemEvent.get(EventHandlerConstants.AzPosKey)
    val elParamOption: Option[Parameter[Double]]       = systemEvent.get(EventHandlerConstants.ElPosKey)

    val setup = Setup(EventHandlerConstants.mcsHCDPrefix, CommandName(Commands.POSITION_DEMANDS), None)
      .add(azParam.get)
      .add(elParamOption.get)
      .add(assemblyRecTimeOpt.get)
      .add(sentTimeOption.get)
    setup
  }

}
