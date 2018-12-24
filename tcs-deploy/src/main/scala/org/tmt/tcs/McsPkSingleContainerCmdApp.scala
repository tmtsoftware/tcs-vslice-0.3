package org.tmt.tcs

import csw.framework.deploy.containercmd.ContainerCmd

object McsPkSingleContainerCmdApp extends App {

  ContainerCmd.start("mcs-pk-single-container-cmd-app", args)

}
