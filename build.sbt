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

lazy val root = (project in file("."))
  .settings(
    name := "migraine",
    libraryDependencies ++= Seq(
      "dev.zio"               %% "zio"                               % "2.0.5",
      "dev.zio"               %% "zio-test"                          % "2.0.5" % Test,
      "org.postgresql"         % "postgresql"                        % "42.5.1",
      "io.github.scottweaver" %% "zio-2-0-testcontainers-postgresql" % "0.9.0"
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )
