package scala.meta.internal.bsp

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import scala.meta.internal.metals.BuildServerConnection
import scala.meta.internal.metals.Cancelable
import scala.meta.internal.metals.ImportedBuild
import scala.meta.internal.metals.MetalsLanguageServer

case class BspSession(
    main: BuildServerConnection,
    meta: List[BuildServerConnection]
)(implicit ec: ExecutionContext)
    extends Cancelable {

  val connections: List[BuildServerConnection] = main :: meta

  def importBuilds(): Future[List[BspSession.BspBuild]] = {
    def importSingle(conn: BuildServerConnection): Future[BspSession.BspBuild] =
      MetalsLanguageServer.importedBuild(conn).map(BspSession.BspBuild(conn, _))

    Future.sequence(connections.map(importSingle))
  }

  def cancel(): Unit = connections.foreach(_.cancel())

  def shutdown(): Future[Unit] =
    Future.sequence(connections.map(_.shutdown())).map(_ => ())

  def mainConnection: BuildServerConnection = main

  def mainConnectionIsBloop: Boolean = main.name == "Bloop"

  def version: String = main.version

  def workspaceReload(): Future[List[Object]] =
    Future.sequence(connections.map(conn => conn.workspaceReload()))
}

object BspSession {
  case class BspBuild(
      connection: BuildServerConnection,
      build: ImportedBuild
  )
}
