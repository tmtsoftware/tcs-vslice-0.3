package org.tmt.tcs.mcs.MCSassembly.Constants
import java.time.Instant

import csw.params.core.generics.{Key, KeyType}
import csw.params.core.models.{Prefix, Subsystem}
import csw.params.events.{EventKey, EventName}

object EventHandlerConstants {
  //These parameters are needed by Position Demand Event
  val PositionDemandKey: Set[EventKey] = Set(
    EventKey(Prefix(EventConstants.TPK_PREFIX), EventName(EventConstants.MOUNT_DEMAND_POSITION))
  )

  val mcsHCDPrefix = Prefix(Subsystem.MCS.toString)

  //These parameters are needed for assembly current state event generation
  val LifecycleStateKey: Key[String]   = KeyType.StringKey.make(EventConstants.LIFECYLE_STATE_KEY)
  val OperationalStateKey: Key[String] = KeyType.StringKey.make(EventConstants.OPERATIONAL_STATE_KEY)
  val assemblyStateEventPrefix         = Prefix(EventConstants.lIFECYLE_STATE_PREFIX)
  val eventName                        = EventName(EventConstants.ASSEMBLY_STATE_EVENT)

  //This parameter is needed for position demand event sent from Assemby to HCD
  val TrackIDKey: Key[Int] = KeyType.IntKey.make(EventConstants.POITNTING_KERNEL_TRACK_ID)

  //These parameters are needed for current position event send from assembly to tpk
  val CURRENT_POSITION_STATE                      = EventName(EventConstants.CURRENT_POSITION)
  val CURRENT_POSITION_PREFIX                     = Prefix(EventConstants.CURRENT_POS_PREFIX)
  val AzPosKey: Key[Double]                       = KeyType.DoubleKey.make(EventConstants.POINTING_KERNEL_AZ_POS)
  val ElPosKey: Key[Double]                       = KeyType.DoubleKey.make(EventConstants.POINTING_KERNEL_EL_POS)
  val AZ_POS_ERROR_KEY: Key[Double]               = KeyType.DoubleKey.make(EventConstants.AZ_POS_ERROR)
  val EL_POS_ERROR_KEY: Key[Double]               = KeyType.DoubleKey.make(EventConstants.EL_POS_ERROR)
  val AZ_InPosition_Key: Key[Boolean]             = KeyType.BooleanKey.make(EventConstants.AZ_InPosition)
  val EL_InPosition_Key: Key[Boolean]             = KeyType.BooleanKey.make(EventConstants.EL_InPosition)
  val TimeStampKey: Key[Instant]                  = KeyType.TimestampKey.make(EventConstants.TIMESTAMP)
  val ASSEMBLY_RECEIVAL_TIME_KEY: Key[Instant]    = KeyType.TimestampKey.make(EventConstants.ASSEMBLY_RECEIVAL_TIME)
  val HCD_Event_RECEV_TIME_KEY: Key[Instant]      = KeyType.TimestampKey.make(EventConstants.HCD_EVENT_RECEIVAL_TIME)
  val ASSEMBLY_EVENT_RECEV_TIME_KEY: Key[Instant] = KeyType.TimestampKey.make(EventConstants.ASSEMBLY_EVENT_RECEIVAL_TIME)

  //These parameters are needed for diagnosis event
  val DIAGNOSIS_STATE  = EventName(EventConstants.DIAGNOSIS_STATE)
  val DIAGNOSIS_PREFIX = Prefix(EventConstants.DIAGNOSIS_PREFIX)

  //These parameters are needed for Health event
  val HEALTH_STATE                   = EventName(EventConstants.HEALTH_STATE)
  val HEALTH_PREFIX                  = Prefix(EventConstants.Health_Prefix)
  val HEALTH_KEY: Key[String]        = KeyType.StringKey.make(EventConstants.HealthKey)
  val HEALTH_REASON_KEY: Key[String] = KeyType.StringKey.make(EventConstants.HealthReason)

  //These parameters are needed for Drive State
  val DRIVE_STATE                           = EventName(EventConstants.DRIVE_STATE)
  val LIFECYCLE_PREFIX                      = Prefix(EventConstants.DRIVE_STATE_PREFIX)
  val PROCESSING_PARAM_KEY: Key[Boolean]    = KeyType.BooleanKey.make(EventConstants.MCS_PROCESSING_COMMAND)
  val MCS_LIFECYCLE_STATTE_KEY: Key[String] = KeyType.StringKey.make(EventConstants.MCS_LIFECYCLE_STATE_KEY)
  val MCS_AZ_STATE: Key[String]             = KeyType.StringKey.make(EventConstants.MCS_AZ_STATE)
  val MCS_EL_STATE: Key[String]             = KeyType.StringKey.make(EventConstants.MCS_EL_STATE)

  //These are parameters needed for dummy events
  val DUMMY_STATE             = EventName(EventConstants.DUMMY_STATE)
  val DUMMY_STATE_PREFIX      = Prefix(EventConstants.DUMMY_STATE_PREFIX)
  val DummyEventKey: Key[Int] = KeyType.IntKey.make(EventConstants.DUMMY_STATE_KEY)

}
