package org.tmt.tcs

import csw.framework.deploy.containercmd.ContainerCmd

object EncContainerCmdApp extends App {

  ContainerCmd.start("enc-container-cmd-app", args)

}
