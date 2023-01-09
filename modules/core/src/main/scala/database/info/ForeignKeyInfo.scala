package database.info

import database.info.ResultSetSyntax._
import zio.{Chunk, NonEmptyChunk}

import java.sql.DatabaseMetaData

sealed trait DeleteRule

object DeleteRule {
  case object NoAction   extends DeleteRule
  case object Cascade    extends DeleteRule
  case object SetNull    extends DeleteRule
  case object SetDefault extends DeleteRule
  case object Restrict   extends DeleteRule
}

sealed trait UpdateRule extends Product with Serializable

object UpdateRule {
  case object NoAction   extends UpdateRule
  case object Cascade    extends UpdateRule
  case object Restrict   extends UpdateRule
  case object SetDefault extends UpdateRule
  case object SetNull    extends UpdateRule
}

case class ForeignKeyInfo(
  parentKeyName: Option[String],
  foreignKeyName: Option[String],
  parentKeyTable: String,
  relations: NonEmptyChunk[(String, String)],
  updateRule: UpdateRule,
  deleteRule: DeleteRule,
)

object ForeignKeyInfo {
  def fromTable(metaData: DatabaseMetaData, tableName: String): Chunk[ForeignKeyInfo] = {
    val rows = metaData.getImportedKeys(null, null, tableName).map { result =>
      val pkName       = Option(result.getString("PK_NAME"))
      val fkName       = Option(result.getString("FK_NAME"))
      val pkTableName  = result.getString("PKTABLE_NAME")
      val fkColumnName = result.getString("FKCOLUMN_NAME")
      val pkColumnName = result.getString("PKCOLUMN_NAME")
      val keySeq       = result.getString("KEY_SEQ")

      val deleteRule = result.getShort("DELETE_RULE") match {
        case DatabaseMetaData.importedKeyCascade    => DeleteRule.Cascade
        case DatabaseMetaData.importedKeySetNull    => DeleteRule.SetNull
        case DatabaseMetaData.importedKeySetDefault => DeleteRule.SetDefault
        case DatabaseMetaData.importedKeyRestrict   => DeleteRule.Restrict
        case DatabaseMetaData.importedKeyNoAction   => DeleteRule.NoAction
      }
      val updateRule = result.getShort("UPDATE_RULE") match {
        case DatabaseMetaData.importedKeyCascade    => UpdateRule.Cascade
        case DatabaseMetaData.importedKeySetNull    => UpdateRule.SetNull
        case DatabaseMetaData.importedKeySetDefault => UpdateRule.SetDefault
        case DatabaseMetaData.importedKeyRestrict   => UpdateRule.Restrict
        case DatabaseMetaData.importedKeyNoAction   => UpdateRule.NoAction
      }

      (pkName, fkName, pkTableName, fkColumnName, pkColumnName, keySeq, updateRule, deleteRule)
    }

    Chunk.fromIterable(
      rows
        .groupBy(_._2)
        .values
        .flatMap { rows =>
          NonEmptyChunk.fromChunk(rows).map { rows =>
            val row = rows.head
            ForeignKeyInfo(
              row._1,
              row._2,
              row._3,
              rows.map(row => (row._4, row._5)),
              row._7,
              row._8,
            )
          }
        },
    )
  }
}
