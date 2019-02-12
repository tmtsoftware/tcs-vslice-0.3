package org.tmt.tcs.mcs

import scala.concurrent.ExecutionContext.Implicits.global
/*
This object acts as a client object to test execution of commands
and subscription of events
 */
object MCSMainApp extends App {

  println("Please enter SimulationMode: SimpleSimulator or RealSimulator")
  val simulationMode = scala.io.StdIn.readLine()
  println(s"SimulatorMode selected is : $simulationMode")
  val mcsDeployer: MCSDeployHelper = MCSDeployHelper.create(simulationMode)
  try {
    val resp0 = mcsDeployer.sendSimulationModeCommand()
    val resp1 = mcsDeployer.sendStartupCommand()
    val resp2 = mcsDeployer.sendDatumCommand()
    val resp3 = mcsDeployer.sendFollowCommand()
  } catch {
    case e: Exception =>
      e.printStackTrace()
  }
  /* println(
    s"=======================================================Command set completed ============================================================================="
  )*/
  mcsDeployer.startSubscribingEvents()
}
