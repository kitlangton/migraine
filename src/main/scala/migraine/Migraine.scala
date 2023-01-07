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
  * ## TODO
  *   - Configurable database connection
  *   - Ensure migration metadata is saved transactionally along with migration
  *   - Automatic migration tests
  *   - Dry run (by default?)
  *   - Errors
  *     - If accidentally duplicated version ids interactively prompt user to
  *       resolve ambiguity
  *   - Warnings
  *     - adding column to existing table without default value
  *   - Snapshotting
  *   - Generate down migrations automatically
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

final case class Migraine(databaseManager: DatabaseManager) {

  def migrate: Task[Unit] =
    for {
      allMigrations   <- getMigrations.debug("ALL MIGRATIONS")
      latestMigration <- databaseManager.getLatestMigrationId
      migrationsToRun  = allMigrations.dropWhile(_.id <= latestMigration)
      _               <- ZIO.foreachDiscard(migrationsToRun)(runMigration)
    } yield ()

  private def getMigrations: Task[List[Migration]] =
    for {
      paths <- ZIO.attempt {
                 val migrationsPath = Paths.get("src/main/resources/migrations")
                 Files.list(migrationsPath).iterator().asScala.toList
               }
      migrations <- ZIO.foreach(paths)(parseMigration)
    } yield migrations.sortBy(_.id)

  private def parseMigration(path: Path): Task[Migration] =
    path.getFileName.toString match {
      case s"V${version}__${name}.sql" =>
        val id = MigrationId(version.toInt)
        ZIO.succeed(Migration(id, name, path))
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

  val live: ZLayer[DataSource, Nothing, Migraine] =
    DatabaseManager.live >>> layer

  def custom(url: String, user: String, password: String): ULayer[Migraine] =
    DatabaseManager.custom(url, user, password) >>> layer

  private lazy val layer: ZLayer[DatabaseManager, Nothing, Migraine] =
    ZLayer.fromFunction(Migraine.apply _)
}

object MigrationsDemo extends ZIOAppDefault {
  val run =
    Migraine.migrate
      .provide(
        Migraine.custom("jdbc:postgresql://localhost:5432/headache", "kit", "")
      )
}
