package migraine

import org.postgresql.ds.PGSimpleDataSource
import zio.{IO, Task, ULayer, ZIO, ZLayer}

import javax.sql.DataSource

final case class Metadata(id: MigrationId, name: String, hash: String)

final case class DatabaseManager(dataSource: DataSource) {

  def executeSQL(string: String): IO[MigraineError, Unit] =
    ZIO
      .attemptBlocking {
        val conn = dataSource.getConnection
        val stmt = conn.prepareStatement(string)
        println(s"Executing SQL: $string")
        stmt.execute()
        conn.close()
      }
      .refineOrDie { //
        case sqlException: org.postgresql.util.PSQLException =>
          MigraineError.SqlError.fromSQLException(string, sqlException)
      }
      .unit

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

  def getLatestMigrationId: Task[Option[MigrationId]] = ZIO.attemptBlocking {
    val conn   = dataSource.getConnection
    val stmt   = conn.prepareStatement("SELECT id FROM migraine_metadata ORDER BY id DESC LIMIT 1")
    val rs     = stmt.executeQuery()
    val result = Option.when(rs.next())(MigrationId(rs.getInt("id")))
    conn.close()
    result
  }

  def getAllMetadata: Task[List[Metadata]] = ZIO.attemptBlocking {
    val conn = dataSource.getConnection
    val stmt = conn.prepareStatement("SELECT * FROM migraine_metadata")
    val rs   = stmt.executeQuery()
    val result = Iterator
      .continually(rs.next())
      .takeWhile(identity)
      .map { _ =>
        Metadata(MigrationId(rs.getInt("id")), rs.getString("name"), rs.getString("hash"))
      }
      .toList
    conn.close()
    result
  }

//  def getSchema: Task[String] = ZIO.attemptBlocking {
//    // get all tables
//    // get all columns for each table
//    // get all constraints for each table
//    // get all indexes for each table
//    val conn = dataSource.getConnection
//    val stmt = conn.prepareStatement("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")
//    val rs   = stmt.executeQuery()
//    val tables = Iterator
//      .continually(rs.next())
//      .takeWhile(identity)
//      .map(_ => rs.getString("table_name"))
//      .toList
//    conn.close()
//    tables.mkString(", ")
//  }
}

object DatabaseManager {

  def custom(url: String, user: String, password: String): ULayer[DatabaseManager] =
    ZLayer {
      val manager = DatabaseManager(new PGSimpleDataSource() {
        setUrl(url)
        setUser(user)
        setPassword(password)
      })
      manager.createMetadataTableIfNotExists.orDie.as(manager)
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
