package migraine

import org.postgresql.ds.PGSimpleDataSource
import zio._

import java.nio.file.{Files, Path, Paths}
import javax.sql.DataSource
import scala.jdk.CollectionConverters.IteratorHasAsScala

/** # Migraine
  *
  * A minimal, modern database migration tool.
  */

// THINGS WE NEED
// - folder of migrations
// - metadata table
// - read some file names
// - run SQL on a DB (Postgres) zio-jdbc
//
// rename "this" "that" -> rename "that" "this"
// create users -> drop_table users;
// drop_table users;

// V1_add_index.sql
// V2_add_index.sql
// CLI
final case class MigrationId(id: Int) extends AnyVal {
  def <=(that: MigrationId): Boolean = id <= that.id
}

object MigrationId {
  implicit val ordering: Ordering[MigrationId] =
    Ordering.by[MigrationId, Int](_.id)
}

final case class Migration(id: MigrationId, name: String, path: Path)

final case class DatabaseManager(dataSource: DataSource) {

  def executeSQL(string: String): Task[Unit] = ZIO.attemptBlocking {
    val conn = dataSource.getConnection
    val stmt = conn.prepareStatement(string)
    println(s"Executing SQL: $string")
    stmt.execute()
    conn.close()
  }

  def createMetadataTableIfNotExists: Task[Unit] =
    executeSQL {
      """
CREATE TABLE IF NOT EXISTS migraine_metadata (
  id SERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  hash VARCHAR(255) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
    """.trim
    }

  def saveRanMigration(migration: Migration): Task[Unit] =
    executeSQL {
      s"INSERT INTO migraine_metadata (id, name, hash) VALUES (${migration.id.id}, '${migration.name}', 'TODO');"
    }

  def getLatestMigrationId: Task[MigrationId] = ZIO.attemptBlocking {
    val conn = dataSource.getConnection
    val stmt = conn.prepareStatement("SELECT id FROM migraine_metadata ORDER BY id DESC LIMIT 1")
    val rs   = stmt.executeQuery()
    val id   = if (rs.next()) rs.getInt("id") else 0
    conn.close()
    MigrationId(id)
  }
}

object DatabaseManager {
  def make: DatabaseManager = {
    val connectionString = "jdbc:postgresql://localhost:5432/headache"

    val pgDataSource = new PGSimpleDataSource()
    pgDataSource.setUrl(connectionString)
    pgDataSource.setUser("kit")
    pgDataSource.setPassword("")

    val manager = DatabaseManager(pgDataSource)
    manager.createMetadataTableIfNotExists
    manager
  }
}

object Migraine extends ZIOAppDefault {

  val manager: DatabaseManager = DatabaseManager.make

  // src/main/resources/migrations/...
  def getMigrations: Task[List[Migration]] =
    ZIO
      .attempt {
        val migrationsPath = Paths.get("src/main/resources/migrations")
        val paths          = Files.list(migrationsPath).iterator().asScala.toList
        val migrations = paths.map { path =>
          val name = path.getFileName.toString
          val id   = name.split("_").head.drop(1).toInt
          Migration(MigrationId(id), name, path)
        }
        migrations.sortBy(_.id)
      }

//  provide(
//    Migraine.migrate
//  )

  def migrate: Task[Unit] =
    for {
      allMigrations   <- getMigrations
      latestMigration <- manager.getLatestMigrationId
      migrationsToRun  = allMigrations.dropWhile(_.id <= latestMigration)
      _               <- ZIO.debug(s"Last Ran Migration is $latestMigration")
      _               <- ZIO.debug(s"Found ${allMigrations.size} migrations")
      _               <- ZIO.debug(s"Running ${migrationsToRun.length} migrations")
      _               <- ZIO.foreachDiscard(migrationsToRun)(runMigration)
    } yield ()

  // store in metadata table that we've run this migration successfully
  def updateMetadata(migration: Migration): Task[Unit] =
    ZIO.debug(s"Updating metadata for $migration") *>
      manager.saveRanMigration(migration)

  def runMigration(migration: Migration): Task[Unit] =
    for {
      contents <- ZIO.readFile(migration.path)
      _        <- manager.executeSQL(contents)
      _        <- updateMetadata(migration)
    } yield ()

  val run =
    migrate
}
// RANDOM UGLY NOTES
//
// Competitive Analysis
// - Flyway
// - Liquibase
// - Active Record (migrations)
// - Play Evolutions
//
// THINGS WE NEED
// - metadata
// - read some file names
// - run SQL on a DB (Postgres) zio-jdbc
// - nice errors and CLI experience
//   - if accidentally duplicated version ids
//     interactively prompt user to resolve ambiguity
//
// Stretch Goals
// - snapshotting
// - automatic down migrations / rollback / invertible migrations
//
// users, events, other_tables, metadata
//
// - V2
// flyway_schema_history
//
// Snapshot_99_set_up_db.sql | RAFT, Event Sourcing
// V100_alter_some_table.sql
// ADD COLUMN to something_table;
//
// Flyway
// src/main/resources/db/migrations/
// S0
//   V1__create_users_table.sql
//     > CREATE TABLE users (id SERIAL PRIMARY KEY, name VARCHAR(255) NOT NULL);
// S1
//   V2__add_email_field_to_users.sql
//     > ALTER TABLE users ADD COLUMN email VARCHAR(255) NOT NULL;
// S2
//   V3__add_email_field_to_users.sql
//     > ALTER TABLE users ADD COLUMN email VARCHAR(255) NOT NULL;
// S3
