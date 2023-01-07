package migraine

import java.nio.file.Path

final case class Migration(id: MigrationId, name: String, path: Path)
