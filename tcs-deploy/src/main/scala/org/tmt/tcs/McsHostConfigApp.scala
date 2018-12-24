package org.tmt.tcs

import csw.framework.deploy.hostconfig.HostConfig

object McsHostConfigApp extends App {

  HostConfig.start("mcs-host-config-app", args)

}
