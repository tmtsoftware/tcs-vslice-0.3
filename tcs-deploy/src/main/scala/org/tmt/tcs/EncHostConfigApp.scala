package org.tmt.tcs

import csw.framework.deploy.hostconfig.HostConfig

object EncHostConfigApp extends App {

  HostConfig.start("enc-host-config-app", args)

}
