ThisBuild / scalaVersion     := "2.13.10"
ThisBuild / version          := "0.0.1"
ThisBuild / organization     := "io.github.kitlangton"
ThisBuild / organizationName := "kitlangton"

lazy val root = (project in file("."))
  .settings(
    name := "migraine",
    libraryDependencies ++= Seq(
      "dev.zio"       %% "zio"        % "2.0.5",
      "dev.zio"       %% "zio-test"   % "2.0.5" % Test,
      "org.postgresql" % "postgresql" % "42.5.1"
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )
