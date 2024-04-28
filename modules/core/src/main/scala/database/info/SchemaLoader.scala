package database.info

import database.info.ResultSetSyntax._
import zio.{Task, ZIO, ZLayer}

import javax.sql.DataSource

final case class Schema(tables: List[TableInfo]) {

  def filterTables(p: TableInfo => Boolean): Schema =
    copy(tables = tables.filter(p))

}

case class SchemaLoader(dataSource: DataSource) {

  def getSchema: Task[Schema] =
    for {
      infos <- ZIO.attemptBlocking(unsafeTableInfo)
    } yield Schema(infos)

  private def unsafeTableInfo: List[TableInfo] = {
    val conn     = dataSource.getConnection
    val metaData = conn.getMetaData

    val tables = metaData
      .getTables(null, null, null, Array("TABLE"))
      .map(TableInfo.fromResultSet(_, metaData))

    tables.toList
  }

}

object SchemaLoader {
  val live = ZLayer.fromFunction(SchemaLoader.apply _)
}

//object SchemaDemo extends ZIOAppDefault {
//
//  val program =
//    ZIO.serviceWithZIO[SchemaLoader](_.getSchemaDDL)
//
//  val run = program.provide(
//    SchemaLoader.live,
//    ZLayer.succeed(
//      new PGSimpleDataSource() {
//        setUrl("jdbc:postgresql://localhost:5432/headache")
//        setUser("kit")
//        setPassword("")
//      }
//    )
//  )
//}
