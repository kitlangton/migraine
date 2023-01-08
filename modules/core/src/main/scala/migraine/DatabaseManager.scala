package migraine

import org.postgresql.ds.PGSimpleDataSource
import zio._

import java.sql.Connection
import javax.sql.DataSource

final case class Metadata(id: MigrationId, name: String, hash: String)

final case class DatabaseManager(dataSource: DataSource) {

  def executeSQL(string: String): ZIO[Connection, MigraineError, Unit] =
    for {
      conn <- ZIO.service[Connection]
      _    <- ZIO.logInfo(s"Executing SQL: $string")
      _ <-
        ZIO
          .attemptBlocking(conn.createStatement().execute(string))
          .refineOrDie { case sqlException: org.postgresql.util.PSQLException =>
            MigraineError.SqlError.fromSQLException(string, sqlException)
          }
    } yield ()

  def transact[R: Tag, E <: Throwable, A](
      effect: ZIO[Connection with R, E, A],
      withLock: Boolean = true
  ): ZIO[R, Throwable, A] =
    ZIO.scoped[R] {
      for {
        conn     <- getConnection
        connLayer = ZLayer.succeed(conn)
        _        <- ZIO.attemptBlocking(conn.setAutoCommit(false))
        _        <- acquireMetadataLock.provide(connLayer).when(withLock)
        _        <- ZIO.addFinalizer(ZIO.succeedBlocking(conn.commit()))
        res      <- effect.provideSome[R](connLayer)
      } yield res
    }

  lazy val getConnection: ZIO[Scope, Throwable, Connection] =
    ZIO.acquireRelease {
      ZIO.attemptBlocking(dataSource.getConnection)
    } { conn =>
      ZIO.succeedBlocking(conn.close())
    }

  def createMetadataTableIfNotExists: Task[Unit] =
    transact(
      executeSQL {
        """
CREATE TABLE IF NOT EXISTS migraine_metadata (
  id SERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  hash VARCHAR(255) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
    """.trim
      },
      withLock = false
    )

  def saveRanMigration(migration: Migration): ZIO[Connection, MigraineError, Unit] =
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

  private lazy val acquireMetadataLock: ZIO[Connection, MigraineError, Unit] =
    executeSQL("LOCK TABLE migraine_metadata IN EXCLUSIVE MODE;")

//  def runTransaction
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
