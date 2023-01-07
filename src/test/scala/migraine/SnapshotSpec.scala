package migraine

import io.github.scottweaver.zio.testcontainers.postgres.ZPostgreSQLContainer
import zio.ZIO
import zio.test._

import java.nio.file.Paths

object SnapshotSpec extends ZIOSpecDefault {

  val spec =
    suite("SnapshotSpec")(
      test("on a fresh database, starts from latest snapshot") {
        for {
          path     <- ZIO.succeed(Paths.get("src/test/resources/migrations/snapshot_test_with_snapshot"))
          _        <- Migraine.migrateFolder(path)
          metadata <- Migraine.getAllMetadata
        } yield assertTrue {
          metadata.map(_.name) == List("create_users_and_posts", "add_slug_to_posts")
        }
      },
      test("on a previously migrated database, ignores snapshots") {
        for {
          path1    <- ZIO.succeed(Paths.get("src/test/resources/migrations/snapshot_test_no_snapshot"))
          _        <- Migraine.migrateFolder(path1)
          path2    <- ZIO.succeed(Paths.get("src/test/resources/migrations/snapshot_test_with_snapshot"))
          _        <- Migraine.migrateFolder(path2)
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
