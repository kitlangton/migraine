package migraine

import org.postgresql.util.PSQLException

sealed trait MigraineError extends Throwable { self =>
  override def getMessage: String =
    self match {
      case MigraineError.SqlError(sql, _, error) =>
        s"$error\nSQL: $sql"
    }
}

object MigraineError {
  final case class SqlError(
      sql: String,
      position: Int,
      message: String
  ) extends MigraineError

  object SqlError {
    // TODO: Improve error parsing and handle more cases
    // highlight syntax errors in user SQL
    def fromSQLException(sql: String, e: PSQLException): SqlError = {
      val serverError = e.getServerErrorMessage
      SqlError(sql, serverError.getPosition, serverError.getMessage)
    }
  }
}
