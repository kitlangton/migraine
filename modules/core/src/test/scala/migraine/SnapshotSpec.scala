package migraine

import io.github.scottweaver.zio.testcontainers.postgres.ZPostgreSQLContainer
import migraine.MigrationSpecUtils.getMigrationsPath
import zio.test._

object SnapshotSpec extends ZIOSpecDefault {

  val spec =
    suite("SnapshotSpec")(
      test("on a fresh database, starts from latest snapshot") {
        for {
          _        <- Migraine.migrateFolder(getMigrationsPath("snapshot_test_with_snapshot"))
          metadata <- Migraine.getAllMetadata
        } yield assertTrue {
          metadata.map(_.name) == List("create_users_and_posts", "add_slug_to_posts")
        }
      },
      test("on a previously migrated database, ignores snapshots") {
        for {
          _        <- Migraine.migrateFolder(getMigrationsPath("snapshot_test_no_snapshot"))
          _        <- Migraine.migrateFolder(getMigrationsPath("snapshot_test_with_snapshot"))
          metadata <- Migraine.getAllMetadata
        } yield assertTrue {
          metadata.map(_.name) == List("create_users", "create_posts", "add_slug_to_posts")
        }
      }
    ).provide(
      ZPostgreSQLContainer.Settings.default,
      ZPostgreSQLContainer.live,
      Migraine.live
    )

}
