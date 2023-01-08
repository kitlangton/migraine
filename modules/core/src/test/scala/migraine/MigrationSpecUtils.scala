package migraine

import java.nio.file.{Path, Paths}

object MigrationSpecUtils {
  def getMigrationsPath(name: String): Path =
    Paths.get(getClass.getResource(s"/migrations/${name}").toURI)
}
