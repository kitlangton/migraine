package migraine

final case class MigrationId(id: Int) extends AnyVal {
  def <=(that: MigrationId): Boolean = id <= that.id
}

object MigrationId {
  implicit val ordering: Ordering[MigrationId] =
    Ordering.by[MigrationId, Int](_.id)
}
