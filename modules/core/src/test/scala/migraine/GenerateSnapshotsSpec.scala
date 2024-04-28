package migraine

import migraine.MigrationSpecUtils.getMigrationsPath
import zio.ZIO
import zio.test._

object GenerateSnapshotsSpec extends DatabaseSpec {

  val spec =
    suite("GenerateSnapshotsSpec")(
      test("generated snapshot DDL will recreate the original schema") {
        for {
          migraine                <- ZIO.service[Migraine]
          db                       = migraine.databaseManager
          _                       <- migraine.migrateFolder(getMigrationsPath("snapshot_test_no_snapshot"))
          _                       <- migraine.migrateFolder(getMigrationsPath("snapshot_test_with_snapshot"))
          originalSchema          <- migraine.schemaLoader.getSchema
          ddl                     <- migraine.generateSnapshotDDL(getMigrationsPath("snapshot_test_with_snapshot"))
          _                       <- db.transact(db.dropAllTables)
          _                       <- db.createMetadataTableIfNotExists
          _                       <- db.transact(db.executeSQL(ddl))
          snapshotGeneratedSchema <- migraine.schemaLoader.getSchema
        } yield assertTrue {
          originalSchema == snapshotGeneratedSchema
        }
      }
    ).provide(Migraine.live, datasourceLayer)

}
