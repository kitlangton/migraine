package migraine

import io.github.scottweaver.zio.testcontainers.postgres.ZPostgreSQLContainer
import zio._
import zio.test._

object DatabaseManagerSpec extends ZIOSpecDefault {

  val spec =
    suite("DatabaseManagerSpec")(
      test("malformed SQL") {
        for {
          error <- ZIO
                     .serviceWithZIO[DatabaseManager](
                       _.executeSQL("CREATE TABLE migraine_metadata (id SERIAL PRIMARY KEY, name FAKE_TYPE);")
                     )
                     .flip
          _ <- ZIO.debug(error)
        } yield assertCompletes
      }
    ).provide(
      DatabaseManager.live,
      ZPostgreSQLContainer.Settings.default,
      ZPostgreSQLContainer.live
    )
}
