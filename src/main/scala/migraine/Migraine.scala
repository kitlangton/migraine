package migraine

import zio._

import java.nio.file.{Files, Path, Paths}
import java.sql.Connection
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
  *   - Configurable CLI (how and where to specify migrations folder, connection
  *     info, etc?)
  *     - migraine.conf? (home, project root, etc)
  *     - the target local database will change per project
  *   - Ensure migration metadata is saved transactionally along with migration
  *   - Automatic migration tests
  *   - Dry run (by default?)
  *     - Schema diff
  *     - Warn for non-reversible migrations
  *   - Errors
  *     - If a migration fails, the metadata and other migrations executed in
  *       the same run should be rolled back.
  *     - If accidentally duplicated version ids interactively prompt user to
  *       resolve ambiguity
  *     - postgres driver is missing (prompt user to add dependency)
  *     - database connection issues
  *     - ensure executed migrations haven't diverged from their stored file
  *       (using checksum)
  *   - Warnings
  *     - adding column to existing table without default value
  *     - missing migrations folder
  *   - Snapshotting
  *     - If fresh database, begin from most recent snapshot, otherwise, ignore
  *       snapshots
  *     - Create snapshot CLI command
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

  private val DEFAULT_MIGRATIONS_PATH =
    "src/main/resources/migrations"

  def migrate: Task[Unit] =
    for {
      path <- findResourcePath(DEFAULT_MIGRATIONS_PATH)
      _    <- migrateFolder(path)
    } yield ()

  // def reset
  // - drop all tables
  // - delete all migration metadata
  // - re-migrate

  private def findResourcePath(name: String): Task[Path] =
    ZIO.attempt(Paths.get(getClass.getResource(name).toURI))

  private[migraine] def getAllMetadata: Task[List[Metadata]] =
    databaseManager.getAllMetadata

  private[migraine] def migrateFolder(migrationsPath: Path): Task[Unit] =
    for {
      allMigrations     <- getMigrations(migrationsPath)
      latestSnapshot     = allMigrations.find(_.isSnapshot)
      upMigrations       = allMigrations.filter(_.isUp)
      latestMigrationId <- databaseManager.getLatestMigrationId
      _ <- (latestMigrationId, latestSnapshot) match {
             // If there is at least one previously executed migration,
             // only execute newer Up migrations.
             case (Some(latestMigration), _) =>
               val migrationsToRun = upMigrations.dropWhile(_.id <= latestMigration)
               runMigrationsInTransaction(migrationsToRun)

             // If there are no previously executed migrations, and there is a snapshot,
             // execute the snapshot and all migrations after it
             case (None, Some(latestSnapshot)) =>
               val migrationsToRun = upMigrations.dropWhile(_.id <= latestSnapshot.id)
               runMigrationsInTransaction(latestSnapshot :: migrationsToRun)

             // If there are no previously executed migrations, and there is no snapshot,
             // run all migrations.
             case (None, None) =>
               runMigrationsInTransaction(upMigrations)
           }
    } yield ()

  def runMigrationsInTransaction(migrations: List[Migration]): Task[Unit] =
    databaseManager.transact {
      ZIO.foreachDiscard(migrations)(runMigration)
    }

  /** Returns a list of Migrations, sorted by id (ascending).
    */
  private def getMigrations(migrationsPath: Path): Task[List[Migration]] =
    for {
      paths      <- ZIO.attempt(Files.list(migrationsPath).iterator().asScala.toList)
      migrations <- ZIO.foreach(paths)(parseMigration)
    } yield migrations.sortBy(_.id)

  /** Parses a [[Migration]] from its file path.
    */
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

  private def createEmptyMigration(name: String, migrationsPath: Path): Task[Unit] =
    for {
      migrations <- getMigrations(migrationsPath)
      nextId      = migrations.lastOption.map(_.id.next).getOrElse(MigrationId(1))
      idString    = nextId.id.toString.reverse.padTo(4, '0').reverse
      path        = Paths.get(s"$migrationsPath/V${idString}__$name.sql")
      _          <- ZIO.writeFile(path, s"-- $name")
    } yield ()

  private def updateMetadata(migration: Migration): ZIO[Connection, MigraineError, Unit] =
    databaseManager.saveRanMigration(migration)

  private def runMigration(migration: Migration): ZIO[Connection, Throwable, Unit] =
    for {
      contents <- ZIO.readFile(migration.path)
      _        <- databaseManager.executeSQL(contents)
      _        <- updateMetadata(migration)
    } yield ()

}

object Migraine {

  val migrate: ZIO[Migraine, Throwable, Unit] =
    ZIO.serviceWithZIO[Migraine](_.migrate)

  private[migraine] def migrateFolder(migrationsPath: Path): ZIO[Migraine, Throwable, Unit] =
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
