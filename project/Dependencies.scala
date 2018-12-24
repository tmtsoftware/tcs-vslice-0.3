import sbt._

object Dependencies {

  val EncAssembly = Seq(
    CSW.`csw-framework`,
    CSW.`csw-testkit` % Test,
    Libs.`scalatest` % Test,
    Libs.`junit` % Test,
    Libs.`junit-interface` % Test,
    Libs.`mockito-core` % Test,
    Libs.`powermock-module` % Test,
    Libs.`powermock-api` % Test
  )

  val EncHcd = Seq(
    CSW.`csw-framework`,
    CSW.`csw-testkit` % Test,
    Libs.`scalatest` % Test,
    Libs.`junit` % Test,
    Libs.`junit-interface` % Test,
    Libs.`mockito-core` % Test
  )

  val McsAssembly = Seq(
    CSW.`csw-framework`,
    CSW.`csw-testkit`,
    Libs.`scalatest` % Test,
    Libs.`junit` % Test,
    Libs.`junit-interface` % Test,
    Libs.`mockito-core` % Test,
    Libs.`akka-test` % Test

  )

  val McsHcd = Seq(
    CSW.`csw-framework`,
    CSW.`csw-testkit`,
    Libs.`zeroMQ`,
    Libs.`protobuf`,
    Libs.`scalatest` % Test,
    Libs.`junit` % Test,
    Libs.`junit-interface` % Test,
    Libs.`mockito-core` % Test,
    Libs.`akka-test` % Test

  )

  val PkAssembly = Seq(
    CSW.`csw-framework`,
    CSW.`csw-testkit`,
    Libs.`scalatest` % Test,
    Libs.`junit` % Test,
    Libs.`junit-interface` % Test
  )

  val TcsClient = Seq(
    CSW.`csw-framework`,
    CSW.`csw-testkit`,
    Libs.`scalatest` % Test,
    Libs.`junit` % Test,
    Libs.`junit-interface` % Test
  )

  val TcsDeploy = Seq(
    CSW.`csw-framework`,
    CSW.`csw-testkit` % Test
  )
}
