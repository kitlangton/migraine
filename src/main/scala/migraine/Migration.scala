package migraine

import java.nio.file.Path

final case class Migration(
    id: MigrationId,
    name: String,
    path: Path,
    tpe: MigrationType
) {

  def isSnapshot: Boolean = tpe == MigrationType.Snapshot
  def isUp: Boolean       = tpe == MigrationType.Up

}

sealed trait MigrationType extends Product with Serializable

object MigrationType {
  case object Up       extends MigrationType
  case object Down     extends MigrationType
  case object Snapshot extends MigrationType
}
