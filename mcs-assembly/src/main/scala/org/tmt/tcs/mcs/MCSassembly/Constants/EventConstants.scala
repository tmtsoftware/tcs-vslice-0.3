package org.tmt.tcs.mcs.MCSassembly.Constants

object EventConstants {
  val MOUNT_DEMAND_POSITION     = "mcsdemandpositions"
  val TPK_PREFIX                = "tcs.pk"
  val POITNTING_KERNEL_TRACK_ID = "mcs.trackID"
  val POINTING_KERNEL_AZ_POS    = "mcs.az"
  val POINTING_KERNEL_EL_POS    = "mcs.el"
  val LIFECYLE_STATE_KEY        = "LifecycleState"
  val lIFECYLE_STATE_PREFIX     = "tmt.tcs.mcs.state"
  val OPERATIONAL_STATE_KEY     = "OperationalState"
  val ASSEMBLY_STATE_EVENT      = "AssemblyState"

  //HCDLifecycleState currentState constants
  val HCDLifecycleState: String            = "HCDLifecycleState"
  val HCDState_Off: String                 = "Off"
  val HCDState_Running: String             = "Running"
  val HCDState_Initialized                 = "Initialized"
  val AZ_InPosition: String                = "azInPositionKey"
  val EL_InPosition: String                = "elInPositionKey"
  val AZ_POS_ERROR: String                 = "azPosErrorKey"
  val EL_POS_ERROR: String                 = "elPosErrorKey"
  val TIMESTAMP: String                    = "timeStamp"
  val ASSEMBLY_RECEIVAL_TIME: String       = "assemblyReceivalTime"
  val HCD_EVENT_RECEIVAL_TIME: String      = "HCDEventReceivalTime"
  val ASSEMBLY_EVENT_RECEIVAL_TIME: String = "AssemblyEventReceivalTime"

  //MCS Simulator currentPosition state
  val CURRENT_POSITION: String = "CurrentPosition"
  val CURRENT_POS_PREFIX       = "tmt.tcs.mcsA"

  //MCS Diagnosis event
  val DIAGNOSIS_STATE: String  = "Diagnosis"
  val DIAGNOSIS_PREFIX: String = "tmt.tcs.mcs.diagnostics"

  // MCS Health event
  val HEALTH_STATE: String  = "Health"
  val Health_Prefix: String = "tmt.tcs.mcsA"
  val HealthKey: String     = "HealthKey"
  val HealthReason: String  = "HealthReasonKey"

  //MCS Drive Status Event
  val DRIVE_STATE: String     = "DriveStatus"
  val DRIVE_STATE_PREFIX      = "tmt.tcs.mcs.driveStatus"
  val MCS_PROCESSING_COMMAND  = "processingCommand"
  val MCS_LIFECYCLE_STATE_KEY = "mcsLifecycleState"
  val MCS_AZ_STATE            = "mcsAZState"
  val MCS_EL_STATE            = "mcsELState"

  //MCS Dummy event
  val DUMMY_STATE        = "DummyEventState"
  val DUMMY_STATE_PREFIX = "tmt.tcs.mcs.dummyEvent"
  val DUMMY_STATE_KEY    = "DummyStateKey"

}
