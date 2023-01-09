package migraine

import migraine.MigrationSpecUtils.getMigrationsPath
import zio.test._

import javax.sql.DataSource

object SnapshotSpec extends DatabaseSpec {

  val spec =
    suite("SnapshotSpec")(
      /** There are two snapshots in the migrations folder, so migraine will
        * begin executing from the LATEST snapshot (V3 in this case).
        */
      test("on a fresh database, starts from latest snapshot") {
        for {
          _        <- Migraine.migrateFolder(getMigrationsPath("snapshot_test_with_snapshot"))
          metadata <- Migraine.getAllMetadata
        } yield assertTrue {
          metadata.map(_.name) == List("V3_SNAPSHOT", "add_alias_to_users")
        }
      },
      test("on a previously migrated database, ignores snapshots") {
        for {
          _        <- Migraine.migrateFolder(getMigrationsPath("snapshot_test_no_snapshot"))
          _        <- Migraine.migrateFolder(getMigrationsPath("snapshot_test_with_snapshot"))
          metadata <- Migraine.getAllMetadata
        } yield assertTrue {
          metadata.map(_.name) == List("create_users", "create_posts", "add_slug_to_posts", "add_alias_to_users")
        }
      }
    ).provide(Migraine.live, datasourceLayer)

}
