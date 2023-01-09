package migraine

import io.github.scottweaver.zio.testcontainers.postgres.ZPostgreSQLContainer
import zio._
import zio.test.ZIOSpecDefault

import java.sql.DriverManager
import javax.sql.DataSource
import scala.jdk.CollectionConverters.EnumerationHasAsScala

trait DatabaseSpec extends ZIOSpecDefault {
  DriverManager.getDrivers.asScala.toList

  val datasourceLayer: ZLayer[Any, Throwable, DataSource] =
    ZPostgreSQLContainer.Settings.default >>> ZPostgreSQLContainer.live
}
