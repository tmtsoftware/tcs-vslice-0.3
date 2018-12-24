package org.tmt.tcs.mcs.MCShcd.constants

object Commands {
  val POINT        = "Point"
  val POINT_DEMAND = "PointDemand"
  val AXIS         = "Axis"
  val DATUM        = "Datum"

  val FOLLOW                  = "Follow"
  val SERVO_OFF               = "ServoOff"
  val RESET                   = "Reset"
  val SETDIAGNOSTICS          = "SetDiagnostics"
  val CANCELPROCESSING        = "CancelProcessing"
  val READCONFIGURATION       = "ReadConfiguration"
  val ELEVATION_STOW_LOCK     = "ElevationStowLock"
  val ELEVATION_STOW_POSITION = "ElevationStowPosition"

  val STARTUP          = "Startup"
  val SHUTDOWN         = "ShutDown"
  val POSITION_DEMANDS = "MountPositionDemands"

  val SET_SIMULATION_MODE = "setSimulationMode"
  val SIMULATION_MODE     = "SimulationMode"
  val REAL_SIMULATOR      = "RealSimulator"
  val SIMPLE_SIMULATOR    = "SimpleSimulator"
}
