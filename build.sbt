inThisBuild(
  List(
    scalaVersion                                   := "3.3.3",
    version                                        := "0.0.1",
    organization                                   := "io.github.kitlangton",
    organizationName                               := "kitlangton",
    semanticdbEnabled                              := true,
    scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.6.0",
    scalacOptions ++= Seq("-Ywarn-unused")
  )
)

Global / onChangedBuildSource := ReloadOnSourceChanges

val zioVersion = "2.0.22"

lazy val sharedSettings =
  Seq(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio"          % zioVersion,
      "dev.zio" %% "zio-test"     % zioVersion % Test,
      "dev.zio" %% "zio-test-sbt" % zioVersion % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

lazy val root = (project in file("."))
  .settings(
    name           := "migraine",
    publish / skip := true
  )
  .aggregate(core, cli)

lazy val core = (project in file("modules/core"))
  .settings(
    name := "migraine-core",
    sharedSettings,
    libraryDependencies ++= Seq(
      "org.postgresql"         % "postgresql"                        % "42.7.3",
      "io.github.scottweaver" %% "zio-2-0-testcontainers-postgresql" % "0.10.0",
      "org.slf4j"              % "slf4j-nop"                         % "2.0.13"
    )
  )
  .enablePlugins(JavaAppPackaging)

lazy val cli = (project in file("modules/cli"))
  .settings(
    name := "migraine-cli",
    sharedSettings,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-cli" % "0.5.0"
    )
  )
  .dependsOn(core)
