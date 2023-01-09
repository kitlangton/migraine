package migraine.cli

import zio._
import zio.cli._

object Main extends ZIOAppDefault {
  // Commands:
  // - migrate
  // - rollback
  // - snapshot

  sealed trait Action extends Product with Serializable

  object Action {
    case object Migrate  extends Action
    case object Rollback extends Action
    case object Snapshot extends Action
  }

  val migrateCommand  = Command("migrate").as(Action.Migrate)
  val rollbackCommand = Command("rollback").as(Action.Rollback)
  val snapshotCommand = Command("snapshot").as(Action.Snapshot)

  val migraineCommand: Command[Action] =
    Command("migraine")
      .subcommands(migrateCommand, rollbackCommand, snapshotCommand)

  val run =
    for {
      args <- getArgs
      _ <- CliApp
             .make("migraine", "0.0.1", HelpDoc.Span.Text("Migraine"), migraineCommand) { action =>
               ZIO.debug(s"running action: $action")
             }
             .run(args.toList)
    } yield ()
}
