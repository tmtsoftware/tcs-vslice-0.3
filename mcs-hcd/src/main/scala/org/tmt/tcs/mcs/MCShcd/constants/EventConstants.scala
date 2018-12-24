package org.tmt.tcs.mcs.MCShcd.constants

import java.time.Instant

import csw.params.core.generics.{Key, KeyType}
import csw.params.core.models.Prefix
import csw.params.events.{EventKey, EventName}

object EventConstants {
  val MOUNT_DEMAND_POSITION: String     = "mcsdemandpositions"
  val TPK_PREFIX: String                = "tcs.pk"
  val POITNTING_KERNEL_TRACK_ID: String = "mcs.trackID"
  val POINTING_KERNEL_AZ_POS: String    = "mcs.az"
  val POINTING_KERNEL_EL_POS: String    = "mcs.el"

  val PositionDemandKey: Set[EventKey] = Set(EventKey(Prefix(TPK_PREFIX), EventName(MOUNT_DEMAND_POSITION)))

  val TrackIDKey: Key[Int]  = KeyType.IntKey.make(POITNTING_KERNEL_TRACK_ID)
  val AzPosKey: Key[Double] = KeyType.DoubleKey.make(POINTING_KERNEL_AZ_POS)
  val ElPosKey: Key[Double] = KeyType.DoubleKey.make(POINTING_KERNEL_EL_POS)

  //for MCS Simulator current position
  val AZ_POS_ERROR: String  = "azPosErrorKey"
  val EL_POS_ERROR: String  = "elPosErrorKey"
  val AZ_InPosition: String = "azInPositionKey"
  val EL_InPosition: String = "elInPositionKey"

  val AZ_POS_ERROR_KEY: Key[Double] = KeyType.DoubleKey.make(AZ_POS_ERROR)
  val EL_POS_ERROR_KEY: Key[Double] = KeyType.DoubleKey.make(EL_POS_ERROR)

  val AZ_InPosition_Key: Key[Boolean] = KeyType.BooleanKey.make(AZ_InPosition)
  val EL_InPosition_Key: Key[Boolean] = KeyType.BooleanKey.make(EL_InPosition)

  // used by all currentStates published by StatePublisherActor
  val TIMESTAMP: String                        = "timeStamp"
  val HCD_ReceivalTime: String                 = "HcdReceivalTime"
  val ASSEMBLY_RECEIVAL_TIME: String           = "assemblyReceivalTime"
  val TimeStampKey: Key[Instant]               = KeyType.TimestampKey.make(TIMESTAMP)
  val HcdReceivalTime_Key: Key[Instant]        = KeyType.TimestampKey.make(HCD_ReceivalTime)
  val ASSEMBLY_RECEIVAL_TIME_KEY: Key[Instant] = KeyType.TimestampKey.make(ASSEMBLY_RECEIVAL_TIME)

  //LifecycleState currentState
  val HCDLifecycleState: String     = "HCDLifecycleState"
  val HCD_EventReceivalTime: String = "HCDEventReceivalTime"
  val LifeCycleStateKey             = KeyType.StringKey.make(HCDLifecycleState)

  val hcdEventReceivalTime_Key: Key[Instant] = KeyType.TimestampKey.make(HCD_EventReceivalTime)

  //MCS Simulator currentPosition state
  val CURRENT_POSITION: String = "CurrentPosition"

  //MCS Diagnosis event
  val DIAGNOSIS_STATE: String = "Diagnosis"

  // MCS Health event
  val HEALTH_STATE: String           = "Health"
  val HEALTH_KEY: Key[String]        = KeyType.StringKey.make("HealthKey")
  val HEALTH_REASON_KEY: Key[String] = KeyType.StringKey.make("HealthReasonKey")

  //MCS Drive Status Event
  val DRIVE_STATE: String                  = "DriveStatus"
  val PROCESSING_PARAM_KEY: Key[Boolean]   = KeyType.BooleanKey.make("processingCommand")
  val MCS_LIFECYCLE_STATE_KEY: Key[String] = KeyType.StringKey.make("mcsLifecycleState")
  val MCS_AZ_STATE: Key[String]            = KeyType.StringKey.make("mcsAZState")
  val MCS_EL_STATE: Key[String]            = KeyType.StringKey.make("mcsELState")

}
