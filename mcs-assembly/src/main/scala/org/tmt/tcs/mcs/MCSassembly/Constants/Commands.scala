package org.tmt.tcs.mcs.MCSassembly.Constants
import java.time.format.DateTimeFormatter

object Commands {
  val AXIS                = "Axis"
  val DATUM               = "Datum"
  val MOVE                = "Move"
  val FOLLOW              = "Follow"
  val SERVO_OFF           = "ServoOff"
  val RESET               = "Reset"
  val SETDIAGNOSTICS      = "SetDiagnostics"
  val CANCELPROCESSING    = "CancelProcessing"
  val READCONFIGURATION   = "ReadConfiguration"
  val ELEVATIONSTOW       = "ElevationStow"
  val POINT               = "Point"
  val POINTDEMAND         = "PointDemand"
  val STARTUP             = "Startup"
  val SHUTDOWN            = "ShutDown"
  val POSITION_DEMANDS    = "MountPositionDemands"
  val DUMMY_IMMEDIATE     = "DummyImmediate"
  val DUMMY_LONG          = "DummyLong"
  val SET_SIMULATION_MODE = "setSimulationMode"

  val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
  val zoneFormat: String           = "UTC"
}
