package migraine

import io.github.scottweaver.zio.testcontainers.postgres.ZPostgreSQLContainer
import migraine.MigrationSpecUtils.getMigrationsPath
import zio.Clock.ClockLive
import zio._
import zio.test._

import java.nio.file.{Path, Paths}

object MigraineSpec extends ZIOSpecDefault {

  val spec =
    suite("MigraineSpec")(
      test("earlier migrations in same run are rolled back if an error occurs") {
        for {
          _        <- Migraine.migrateFolder(getMigrationsPath("rollback_on_error_test")).flip
          metadata <- Migraine.getAllMetadata
        } yield assertTrue(metadata.isEmpty)
      },
      test("lock on metadata table prevents concurrent migrations") {
        for {
          _ <- Migraine.migrateFolder(getMigrationsPath("lock_test")) zipPar
                 Migraine
                   .migrateFolder(getMigrationsPath("lock_test_no_sleep"))
                   .delay(200.millis)
                   .flip
                   .withClock(ClockLive)
        } yield assertTrue(true)
      }
    ).provide(
      Migraine.live,
      ZPostgreSQLContainer.Settings.default,
      ZPostgreSQLContainer.live
    )
}
