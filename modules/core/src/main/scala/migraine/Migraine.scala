package migraine

import database.info.SchemaLoader
import org.postgresql.ds.PGSimpleDataSource
import zio._

import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{Files, Path, Paths}
import java.sql.Connection
import javax.sql.DataSource
import scala.jdk.CollectionConverters._

final case class Migraine(
    databaseManager: DatabaseManager,
    schemaLoader: SchemaLoader
) {

  private val DEFAULT_MIGRATIONS_FOLDER_RESOURCE = "/migrations/"

  def migrate: Task[Unit] =
    for {
      path <- findResourcePath(DEFAULT_MIGRATIONS_FOLDER_RESOURCE)
      _    <- migrateFolder(path)
    } yield ()

  // def reset
  // - drop all tables
  // - delete all migration metadata
  // - re-migrate

  private[migraine] def findResourcePath(name: String): Task[Path] =
    ResourceFinder.getResourceFolderPath(name)

  private[migraine] def getAllMetadata: Task[List[Metadata]] =
    databaseManager.getAllMetadata

  private[migraine] def migrateFolder(migrationsPath: Path): Task[Unit] =
    for {
      allMigrations     <- getMigrations(migrationsPath).debug("ALL MIGRATIONS")
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

  // 1. ensure that all migrations have ran (latest migration id is the same as highest version in folder)
  // 2. load schema
  // 3. generate migrations from schema
  // 4. create new snapshot file in migrations folder
  // TODO:
  //  - Make sure there exists at least one migration
  //  - Make sure the snapshot doesn't already exist for the same id
  def snapshot: Task[Unit] =
    for {
      latestMigrationId      <- databaseManager.getLatestMigrationId
      migrationsFolderPath   <- findResourcePath(DEFAULT_MIGRATIONS_FOLDER_RESOURCE)
      allMigrations          <- getMigrations(migrationsFolderPath)
      highestWrittenMigration = allMigrations.map(_.id).maxOption
      _ <- ZIO
             .fail(new Error("All migrations must have run before snapshot"))
             .unless(latestMigrationId == highestWrittenMigration)
      schema            <- schemaLoader.getSchema
      ddl                = schema.filterTables(_.name != "migraine_metadata").toDDL
      migrationsSrcPath <- findDefaultMigrationFolder.debug("FOLDER PATH")
      _                 <- ZIO.writeFile(s"$migrationsSrcPath/V${latestMigrationId.get.id}_SNAPSHOT.sql", ddl)
    } yield ()

  private def runMigrationsInTransaction(migrations: List[Migration]): Task[Unit] =
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
      case s"V${version}_SNAPSHOT.sql" =>
        println(s"version ${version}")
        ZIO.succeed(Migration(MigrationId(version.toInt), s"V${version}_SNAPSHOT", path, MigrationType.Snapshot))
      case s"V${version}_DOWN__${name}.sql" =>
        ZIO.succeed(Migration(MigrationId(version.toInt), name, path, MigrationType.Down))
      case s"V${version}__${name}.sql" =>
        ZIO.succeed(Migration(MigrationId(version.toInt), name, path, MigrationType.Up))
      case _ =>
        ZIO.fail(new Exception(s"Invalid migration path: $path"))
    }

  private def updateMetadata(migration: Migration): ZIO[Connection, MigraineError, Unit] =
    databaseManager.saveRanMigration(migration)

  private def runMigration(migration: Migration): ZIO[Connection, Throwable, Unit] =
    for {
      contents <- ZIO.attemptBlocking(new String(Files.readAllBytes(migration.path)))
      _        <- databaseManager.executeSQL(contents)
      _        <- updateMetadata(migration)
    } yield ()

  private def findDefaultMigrationFolder: Task[Path] =
    ZIO.attemptBlocking {
      findMigrationsFolders().headOption match {
        case Some(path) => path
        case None       => throw new Error("Missing migration folder")
      }
    }

  private def findMigrationsFolders(): Seq[Path] = {
    val cwd: Path = Paths.get(".")
    val migrationsFolders = Files
      .find(
        cwd,
        Integer.MAX_VALUE,
        (path: Path, _: BasicFileAttributes) => {
          val pathString = path.toString
          pathString.endsWith("resources/migrations") && Files.isDirectory(path) &&
          pathString.contains("src/main/")
        }
      )
      .iterator()
      .asScala
      .toSeq

    migrationsFolders
  }

}

object Migraine {

  val migrate: ZIO[Migraine, Throwable, Unit] =
    ZIO.serviceWithZIO[Migraine](_.migrate)

  val snapshot: ZIO[Migraine, Throwable, Unit] =
    ZIO.serviceWithZIO[Migraine](_.snapshot)

  private[migraine] def migrateFolder(migrationsPath: Path): ZIO[Migraine, Throwable, Unit] =
    ZIO.serviceWithZIO[Migraine](_.migrateFolder(migrationsPath))

  private[migraine] def getAllMetadata: ZIO[Migraine, Throwable, List[Metadata]] =
    ZIO.serviceWithZIO[Migraine](_.getAllMetadata)

  val live: ZLayer[DataSource, Nothing, Migraine] =
    DatabaseManager.live ++ SchemaLoader.live >>> layer

  def custom(url: String, user: String, password: String): ULayer[Migraine] =
    ZLayer.make[Migraine](dataSourceLayer(url, user, password), live)

  private def dataSourceLayer(url: String, user: String, password: String) =
    ZLayer.succeed(
      new PGSimpleDataSource() {
        setUrl(url)
        setUser(user)
        setPassword(password)
      }
    )

  private lazy val layer: ZLayer[DatabaseManager with SchemaLoader, Nothing, Migraine] =
    ZLayer.fromFunction(Migraine.apply _)
}

object MigrationsDemo extends ZIOAppDefault {
  val run = {
    for {
      _ <- Migraine.migrate
    } yield ()
  }
    .provide(
      Migraine.custom("jdbc:postgresql://localhost:5432/headache", "kit", "")
    )
}
