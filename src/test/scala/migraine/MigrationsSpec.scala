package migraine

import io.github.scottweaver.zio.testcontainers.postgres.ZPostgreSQLContainer
import zio.test._

object MigrationsSpec extends ZIOSpecDefault {

  val spec =
    suite("Migrations")(
      test("migrate") {
        for {
          _ <- Migraine.migrate
        } yield assertCompletes
      }
    ).provide(
      ZPostgreSQLContainer.Settings.default,
      ZPostgreSQLContainer.live,
      Migraine.live
    )

}
