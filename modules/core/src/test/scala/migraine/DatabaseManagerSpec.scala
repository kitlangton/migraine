package migraine

import zio._
import zio.test._

import javax.sql.DataSource

object DatabaseManagerSpec extends DatabaseSpec {

  val spec =
    suite("DatabaseManagerSpec")(
      test("malformed SQL") {
        for {
          error <- ZIO
                     .serviceWithZIO[DatabaseManager](dm =>
                       dm.transact {
                         dm.executeSQL("CREATE TABLE migraine_metadata (id SERIAL PRIMARY KEY, name FAKE_TYPE);")
                       }
                     )
                     .flip
          _ <- ZIO.debug(error)
        } yield assertCompletes
      }
    )
      .provide(DatabaseManager.live, datasourceLayer)
}
