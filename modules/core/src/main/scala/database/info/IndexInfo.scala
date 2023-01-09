package database.info

import zio.Chunk

import java.sql.DatabaseMetaData

case class IndexInfo(indexName: String, columnName: String, indexType: Short, isUnique: Boolean)
import ResultSetSyntax._

object IndexInfo {
  def fromTable(metaData: DatabaseMetaData, tableName: String): Chunk[IndexInfo] =
    metaData.getIndexInfo(null, null, tableName, false, false).map { resultSet =>
      val indexName  = resultSet.getString("INDEX_NAME")
      val columnName = resultSet.getString("COLUMN_NAME")
      val indexType  = resultSet.getShort("TYPE")
      val isUnique   = resultSet.getBoolean("NON_UNIQUE")
      IndexInfo(indexName, columnName, indexType, isUnique)
    }
}
