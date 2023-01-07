package migraine

import org.postgresql.ds.PGSimpleDataSource
import zio.{Task, ZIO, ZLayer}

import javax.sql.DataSource

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

  val live: ZLayer[DataSource, Nothing, DatabaseManager] =
    ZLayer {
      for {
        dataSource <- ZIO.service[DataSource]
        manager     = DatabaseManager(dataSource)
        _          <- manager.createMetadataTableIfNotExists.orDie
      } yield manager
    }

}
