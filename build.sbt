inThisBuild(
  List(
    scalaVersion                                   := "2.13.10",
    version                                        := "0.0.1",
    organization                                   := "io.github.kitlangton",
    organizationName                               := "kitlangton",
    semanticdbEnabled                              := true,
    scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.6.0",
    scalacOptions ++= Seq("-Ywarn-unused")
  )
)

Global / onChangedBuildSource := ReloadOnSourceChanges

lazy val core = (project in file("modules/core"))
  .settings(
    name := "migraine",
    libraryDependencies ++= Seq(
      "dev.zio"               %% "zio"                               % "2.0.5",
      "dev.zio"               %% "zio-test"                          % "2.0.5" % Test,
      "org.postgresql"         % "postgresql"                        % "42.5.1",
      "io.github.scottweaver" %% "zio-2-0-testcontainers-postgresql" % "0.9.0",
      "org.slf4j"              % "slf4j-nop"                         % "2.0.5"
    )
  )

lazy val cli = (project in file("modules/cli"))
  .settings(
    name := "migraine-cli",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio"      % "2.0.5",
      "dev.zio" %% "zio-test" % "2.0.5" % Test
    )
  )
  .dependsOn(core)
