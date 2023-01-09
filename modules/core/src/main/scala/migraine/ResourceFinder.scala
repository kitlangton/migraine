package migraine
import zio._

import java.nio.file.{FileSystemNotFoundException, FileSystems, Files, Path, Paths}
import scala.jdk.CollectionConverters.IteratorHasAsScala

object ResourceFinder extends ZIOAppDefault {
  def getResourceFolderPath(folder: String): Task[Path] = ZIO.attemptBlocking {
    val uri = getClass.getResource(folder).toURI()

    try Paths.get(uri)
    catch {
      case _: FileSystemNotFoundException =>
        val env: java.util.Map[String, String] = new java.util.HashMap[String, String]()
        FileSystems.newFileSystem(uri, env).getPath(folder)
    }
  }

  def getResourceFolderContents(folder: String): Task[List[Path]] = ZIO.attemptBlocking {
    val uri = getClass.getResource(folder).toURI()

    val dirPath =
      try Paths.get(uri)
      catch {
        case _: FileSystemNotFoundException =>
          val env: java.util.Map[String, String] = new java.util.HashMap[String, String]()
          FileSystems.newFileSystem(uri, env).getPath(folder)
      }

    Files.list(dirPath).iterator().asScala.toList
  }

  val run = getResourceFolderContents("/migrations/").flatMap { paths =>
    val path = paths.head
//    val fs = path.getFileSystem
    val string = new String(Files.readAllBytes(path))
    ZIO.succeed(string).debug

  }
}
