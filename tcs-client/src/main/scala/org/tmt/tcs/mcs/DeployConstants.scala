package org.tmt.tcs.mcs

import java.time.Instant

import csw.params.core.generics.{Key, KeyType}
import csw.params.core.models.{Prefix, Subsystem}
import csw.params.events.{EventKey, EventName}
import org.tmt.tcs.mcs.EventConstants._

object DeployConstants {
  //Below are set of keys required for subscribing to events
  val PositionDemandKey: Set[EventKey] = Set(
    EventKey(Prefix(EventConstants.TPK_PREFIX), EventName(EventConstants.MOUNT_DEMAND_POSITION))
  )
  val currentPositionSet: Set[EventKey] = Set(
    EventKey(Prefix(EventConstants.CURRENT_POS_PREFIX), EventName(EventConstants.CURRENT_POSITION))
  )
  val healthSet: Set[EventKey] = Set(
    EventKey(Prefix(EventConstants.Health_Prefix), EventName(EventConstants.HEALTH_STATE))
  )

  val dummyEventKey: Set[EventKey] = Set(
    EventKey(Prefix(EventConstants.DUMMY_STATE_PREF), EventName(EventConstants.DUMMY_STATE_NAME))
  )

  val mcsHCDPrefix = Prefix(Subsystem.MCS.toString)

  //These parameters are needed by position demand event
  val PositionDemandPref: Prefix         = Prefix(TPK_PREFIX)
  val PositionDemandEventName: EventName = EventName(MOUNT_DEMAND_POSITION)

  //These parameters are needed for assembly current state event generation
  val LifecycleStateKey: Key[String]   = KeyType.StringKey.make(LIFECYLE_STATE_KEY)
  val OperationalStateKey: Key[String] = KeyType.StringKey.make(OPERATIONAL_STATE_KEY)
  val assemblyStateEventPrefix         = Prefix(lIFECYLE_STATE_PREFIX)
  val eventName                        = EventName(ASSEMBLY_STATE_EVENT)

  //This parameter is needed for position demand event sent from Assemby to HCD
  val TrackIDKey: Key[Int] = KeyType.IntKey.make(POITNTING_KERNEL_TRACK_ID)

  //These parameters are needed for current position event send from assembly to tpk

  val AzPosKey: Key[Double]           = KeyType.DoubleKey.make(POINTING_KERNEL_AZ_POS)
  val ElPosKey: Key[Double]           = KeyType.DoubleKey.make(POINTING_KERNEL_EL_POS)
  val AZ_POS_ERROR_KEY: Key[Double]   = KeyType.DoubleKey.make(AZ_POS_ERROR)
  val EL_POS_ERROR_KEY: Key[Double]   = KeyType.DoubleKey.make(EL_POS_ERROR)
  val AZ_InPosition_Key: Key[Boolean] = KeyType.BooleanKey.make(AZ_InPosition)
  val EL_InPosition_Key: Key[Boolean] = KeyType.BooleanKey.make(EL_InPosition)
  val TimeStampKey: Key[Instant]      = KeyType.TimestampKey.make(TIMESTAMP)

  //These parameters are needed for diagnosis event
  val DIAGNOSIS_STATE  = EventName(EventConstants.DIAGNOSIS_STATE)
  val DIAGNOSIS_PREFIX = Prefix(EventConstants.DIAGNOSIS_PREFIX)

  //These parameters are needed for Health event

  val HEALTH_KEY: Key[String]        = KeyType.StringKey.make(EventConstants.HealthKey)
  val HEALTH_REASON_KEY: Key[String] = KeyType.StringKey.make(EventConstants.HealthReason)

  //These parameters are needed for Drive State
  val DRIVE_STATE                           = EventName(EventConstants.DRIVE_STATE)
  val LIFECYCLE_PREFIX                      = Prefix(EventConstants.DRIVE_STATE_PREFIX)
  val PROCESSING_PARAM_KEY: Key[Boolean]    = KeyType.BooleanKey.make(EventConstants.MCS_PROCESSING_COMMAND)
  val MCS_LIFECYCLE_STATTE_KEY: Key[String] = KeyType.StringKey.make(EventConstants.MCS_LIFECYCLE_STATE_KEY)
  val MCS_AZ_STATE: Key[String]             = KeyType.StringKey.make(EventConstants.MCS_AZ_STATE)
  val MCS_EL_STATE: Key[String]             = KeyType.StringKey.make(EventConstants.MCS_EL_STATE)

}
