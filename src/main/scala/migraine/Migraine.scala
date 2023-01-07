package migraine

import zio._

import java.nio.file.{Files, Path, Paths}
import javax.sql.DataSource
import scala.jdk.CollectionConverters.IteratorHasAsScala

/** # Migraine
  *
  * A minimal, modern database migration tool.
  *
  * ## Goals
  *   - Helpful error messages
  *   - Built-in CLI
  *
  * ## Todos
  *   - Configurable database connection
  *   - Ensure migration metadata is saved transactionally along with migration
  *   - Automatic migration tests
  *   - Dry run (by default?)
  *     - Schema diff
  *     - Warn for non-reversible migrations
  *   - Errors
  *     - If accidentally duplicated version ids interactively prompt user to
  *       resolve ambiguity
  *   - Warnings
  *     - adding column to existing table without default value
  *   - Snapshotting
  *     - Create snapshot CLI command
  *     - If fresh database, begin from most recent snapshot, otherwise, ignore
  *       snapshots
  *   - Generate down migrations automatically
  *   - Rollback
  *     - UX: Check to see how many migrations were run "recently", or in the
  *       last batch (a group of migrations executed at the same time. If
  *       there's more than just one, ask the user.
  *   - Editing migrations
  *     - If a user has edited the mostly recently run migration/migrations and
  *       tries to run migrate again, ask the user if they want to rollback the
  *       prior migration before running the edits. (we could store the SQL text
  *       of the executed migrations... would that get too big?)
  *
  * ## Prior Art
  *   - Flyway
  *   - Liquibase
  *   - Active Record (migrations)
  *   - Play Evolutions
  *   - DBEvolv (https://github.com/mnubo/dbevolv)
  *   - Squitch (https://sqitch.org)
  *   - Delta (https://delta.io)
  */

// How should migration snapshots work?
//
// - Ask the user up to which migration they want to snapshot
// - Run all of the migrations up to that point on a fresh database (using docker/test containers?)
// - Store the resulting database schema in a migration file
//
// Here's an example set of migrations and snapshots
// M1 M2 M3 S3 M4 M5
// - S3 is a snapshot of the database after migration M3
// - M4 and M5 are new migrations that are applied on top of S3
//
// Scenarios
// - When run on a fresh database
//   - Find the latest snapshot, then run all migrations after that snapshot
// - When run on a database that has already been migrated
//   - Simply run all migrations that haven't been run yet, ignoring snapshots

final case class Migraine(databaseManager: DatabaseManager) {

  def migrate: Task[Unit] =
    for {
      path <- ZIO.attempt(Paths.get("src/main/resources/migrations"))
      _    <- migrateFolder(path)
    } yield ()

  private[migraine] def getAllMetadata: Task[List[Metadata]] =
    databaseManager.getAllMetadata

  private[migraine] def migrateFolder(migrationsPath: Path): Task[Unit] =
    for {
      allMigrations   <- getMigrations(migrationsPath)
      latestSnapshot   = allMigrations.find(_.isSnapshot)
      upMigrations     = allMigrations.filter(_.isUp)
      latestMigration <- databaseManager.getLatestMigrationId
      _ <- (latestMigration, latestSnapshot) match {
             // If there is at least one previously run migration,
             // only run newer Up migrations.
             case (Some(latestMigration), _) =>
               val migrationsToRun = upMigrations.dropWhile(_.id <= latestMigration)
               ZIO.foreachDiscard(migrationsToRun)(runMigration)

             // If there are no previously run migrations, and there is a snapshot,
             // run the snapshot and all migrations after it
             case (None, Some(latestSnapshot)) =>
               val migrationsToRun = upMigrations.dropWhile(_.id <= latestSnapshot.id)
               ZIO.foreachDiscard(latestSnapshot :: migrationsToRun)(runMigration)

             // If there are no previously run migrations, and there is no snapshot,
             // run all migrations.
             case (None, None) =>
               ZIO.foreachDiscard(upMigrations)(runMigration)
           }
    } yield ()

  private def getMigrations(migrationsPath: Path): Task[List[Migration]] =
    for {
      paths      <- ZIO.attempt(Files.list(migrationsPath).iterator().asScala.toList)
      migrations <- ZIO.foreach(paths)(parseMigration)
    } yield migrations.sortBy(_.id)

  private def parseMigration(path: Path): Task[Migration] =
    path.getFileName.toString match {
      case s"V${version}_SNAPSHOT__${name}.sql" =>
        ZIO.succeed(Migration(MigrationId(version.toInt), name, path, MigrationType.Snapshot))
      case s"V${version}_DOWN__${name}.sql" =>
        ZIO.succeed(Migration(MigrationId(version.toInt), name, path, MigrationType.Down))
      case s"V${version}__${name}.sql" =>
        ZIO.succeed(Migration(MigrationId(version.toInt), name, path, MigrationType.Up))
      case _ =>
        ZIO.fail(new Exception(s"Invalid migration path: $path"))
    }

  private def updateMetadata(migration: Migration): Task[Unit] =
    databaseManager.saveRanMigration(migration)

  private def runMigration(migration: Migration): Task[Unit] =
    for {
      contents <- ZIO.readFile(migration.path)
      _        <- databaseManager.executeSQL(contents)
      _        <- updateMetadata(migration)
    } yield ()

}

object Migraine {

  val migrate: ZIO[Migraine, Throwable, Unit] =
    ZIO.serviceWithZIO[Migraine](_.migrate)

  private[migraine] def migratePath(migrationsPath: Path): ZIO[Migraine, Throwable, Unit] =
    ZIO.serviceWithZIO[Migraine](_.migrateFolder(migrationsPath))

  private[migraine] def getAllMetadata: ZIO[Migraine, Throwable, List[Metadata]] =
    ZIO.serviceWithZIO[Migraine](_.getAllMetadata)

  val live: ZLayer[DataSource, Nothing, Migraine] =
    DatabaseManager.live >>> layer

  def custom(url: String, user: String, password: String): ULayer[Migraine] =
    DatabaseManager.custom(url, user, password) >>> layer

  private lazy val layer: ZLayer[DatabaseManager, Nothing, Migraine] =
    ZLayer.fromFunction(Migraine.apply _)
}

object MigrationsDemo extends ZIOAppDefault {
  val run = {
    for {
      _ <- Migraine.migrate
//      _ <- ZIO.serviceWithZIO[DatabaseManager](_.getSchema).debug("SCHEMA")
    } yield ()
  }
    .provide(
      Migraine.custom("jdbc:postgresql://localhost:5432/headache", "kit", ""),
      DatabaseManager.custom(
        "jdbc:postgresql://localhost:5432/headache",
        "kit",
        ""
      )
    )
}
