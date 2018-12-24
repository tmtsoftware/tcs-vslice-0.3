lazy val aggregatedProjects: Seq[ProjectReference] = Seq(
  `enc-assembly`,
  `enc-hcd`,
  `mcs-assembly`,
  `mcs-hcd`,
  `pk-assembly`, 
  `tcs-client`,
  `tcs-deploy`
)

lazy val `tcs` = project
  .in(file("."))
  .aggregate(aggregatedProjects: _*)

lazy val `enc-assembly` = project
  .settings(
    libraryDependencies ++= Dependencies.EncAssembly
  )

lazy val `enc-hcd` = project
  .settings(
    libraryDependencies ++= Dependencies.EncHcd
  )

lazy val `mcs-assembly` = project
  .settings(
    libraryDependencies ++= Dependencies.McsAssembly
  )

lazy val `mcs-hcd` = project
  .settings(
    libraryDependencies ++= Dependencies.McsHcd
  )

lazy val `pk-assembly` = project
  .settings(
    libraryDependencies ++= Dependencies.PkAssembly
  )

lazy val `tcs-client` = project
  .settings(
    libraryDependencies ++= Dependencies.TcsClient
  ).enablePlugins(JavaAppPackaging, CswBuildInfo)

lazy val `tcs-deploy` = project
  .dependsOn(
    `enc-assembly`,
    `enc-hcd`,
    `mcs-assembly`,
    `mcs-hcd`,
    `pk-assembly`,
    `tcs-client`
  )
  .enablePlugins(JavaAppPackaging, CswBuildInfo)
  .settings(
    libraryDependencies ++= Dependencies.TcsDeploy
  )
