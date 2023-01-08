package migraine

import io.github.scottweaver.zio.testcontainers.postgres.ZPostgreSQLContainer
import zio.Clock.ClockLive
import zio._
import zio.test._

import java.nio.file.Paths

object MigraineSpec extends ZIOSpecDefault {

  val spec =
    suite("MigraineSpec")(
      test("earlier migrations in same run are rolled back if an error occurs") {
        for {
          _        <- Migraine.migrateFolder(Paths.get("src/test/resources/migrations/rollback_on_error_test")).flip
          metadata <- Migraine.getAllMetadata
        } yield assertTrue(metadata.isEmpty)
      },
      test("lock on metadata table prevents concurrent migrations") {
        for {
          _ <- Migraine.migrateFolder(Paths.get("src/test/resources/migrations/lock_test")) zipPar
                 Migraine
                   .migrateFolder(Paths.get("src/test/resources/migrations/lock_test_no_sleep"))
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
