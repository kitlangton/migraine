package migraine

import zio._

import java.nio.file.{Files, Path, Paths}
import java.sql.Connection
import javax.sql.DataSource
import scala.jdk.CollectionConverters.IteratorHasAsScala

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

  private[migraine] def findResourcePath(name: String): Task[Path] =
    ZIO.attempt(Paths.get(getClass.getResource(name).toURI))

  private[migraine] def getAllMetadata: Task[List[Metadata]] =
    databaseManager.getAllMetadata

  private[migraine] def migrateFolder(migrationsPath: Path): Task[Unit] =
    for {
      allMigrations     <- getMigrations(migrationsPath)
      latestSnapshot     = allMigrations.findLast(_.isSnapshot)
      upMigrations       = allMigrations.filter(_.isUp)
      latestMigrationId <- databaseManager.getLatestMigrationId
      _ <- (latestMigrationId, latestSnapshot) match {
             // If there is at least one previously executed migration,
             // only execute newer Up migrations.
             case (Some(latestMigrationId), _) =>
               val migrationsToRun = upMigrations.dropWhile(_.id <= latestMigrationId)
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
      // migraine new migration 'create users'
      //
      // V0010__create_users.sql
      // ~/migraine.conf
      // ./migraine.conf
      _ <- Migraine.migrate // migraine ...
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
