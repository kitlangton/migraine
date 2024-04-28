package database.info

import zio.Chunk

sealed trait Migration

object Migration {
  case class CreateTable(tableInfo: TableInfo)                          extends Migration
  case class DropTable(tableName: String)                               extends Migration
  case class AddColumn(tableName: String, columnInfo: ColumnInfo)       extends Migration
  case class AlterColumnType(tableName: String, columnInfo: ColumnInfo) extends Migration

  def render(mod: Migration): String = mod match {
    case CreateTable(tableInfo) =>
      val columns: Chunk[String] = tableInfo.columnInfo.map { columnInfo =>
        s"${columnInfo.name} ${columnInfo.columnType.renderSql}"
      }

      val primaryKey = tableInfo.primaryKeyInfo
        .map { pk =>
          Chunk(s"CONSTRAINT ${pk.name} PRIMARY KEY ${pk.columns.mkString("(", ", ", ")")}")
        }
        .getOrElse(Chunk.empty)

      val foreignKeys = tableInfo.foreignKeys.map { fk =>
        val (selfColumns, parentColumns) = fk.relations.unzip

        val onDelete = (fk.deleteRule match {
          case DeleteRule.NoAction   => None
          case DeleteRule.Cascade    => Some("CASCADE")
          case DeleteRule.SetNull    => Some("SET NULL")
          case DeleteRule.SetDefault => Some("SET DEFAULT")
          case DeleteRule.Restrict   => Some("RESTRICT")
        }).map(rule => s" ON DELETE $rule").getOrElse("")

        s"CONSTRAINT ${fk.foreignKeyName.getOrElse("")} FOREIGN KEY ${selfColumns
          .mkString("(", ", ", ")")} REFERENCES ${fk.parentKeyTable} ${parentColumns.mkString("(", ", ", ")")}$onDelete"
      }

      val info = columns ++ primaryKey ++ foreignKeys

      s"""
CREATE TABLE ${tableInfo.name} (
  ${info.mkString(",\n  ")}
);
         """.trim

    case DropTable(tableName) =>
      s"DROP TABLE $tableName;"

    case AddColumn(tableName, columnInfo) =>
      s"ALTER TABLE $tableName ADD COLUMN ${columnInfo.name} ${columnInfo.columnType.renderSql};"

    case AlterColumnType(tableName, columnInfo) =>
      s"ALTER TABLE $tableName ALTER COLUMN ${columnInfo.name} TYPE ${columnInfo.columnType.renderSql};"
  }

  def diff(old: List[TableInfo], incoming: List[TableInfo]): List[Migration] = ???

  def generateSchemaDump(schema: Schema): String = {
    val createTables          = schema.tables.map(createTable).mkString("\n\n")
    val foreignKeyConstraints = schema.tables.map(alterTableAddForeignKey).mkString("\n\n")

    createTables ++ "\n\n" ++ foreignKeyConstraints
  }

  def createTable(tableInfo: TableInfo): String = {
    val columns: Chunk[String] = tableInfo.columnInfo.map { columnInfo =>
      val basic   = Chunk(s"${columnInfo.name} ${columnInfo.columnType.renderSql}")
      val notNull = if (columnInfo.isNullable) Chunk.empty else Chunk("NOT NULL")
      val default = columnInfo.default.map(d => Chunk(s"DEFAULT $d")).getOrElse(Chunk.empty)
      (basic ++ notNull ++ default).mkString(" ")
    }

    val primaryKey = tableInfo.primaryKeyInfo
      .map { pk =>
        Chunk(s"CONSTRAINT ${pk.name} PRIMARY KEY ${pk.columns.mkString("(", ", ", ")")}")
      }
      .getOrElse(Chunk.empty)

    val info = columns ++ primaryKey

    s"""
CREATE TABLE ${tableInfo.name} (
  ${info.mkString(",\n  ")}
);
    """.trim
  }

  def alterTableAddForeignKey(tableInfo: TableInfo): String = {
    val addForeignKeys = tableInfo.foreignKeys
      .map { fkInfo =>
        val (selfColumns, parentColumns) = fkInfo.relations.unzip
        val parentColumnsStr             = parentColumns.mkString("(", ", ", ")")
        val selfColumnsStr               = selfColumns.mkString("(", ", ", ")")
        s"ADD FOREIGN KEY $selfColumnsStr REFERENCES ${fkInfo.parentKeyTable} $parentColumnsStr"
      }
      .mkString(",\n  ")

    s"""
ALTER TABLE ${tableInfo.name}
  $addForeignKeys;
""".trim
  }
}
