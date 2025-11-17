package fi.roikonen.http

import fi.roikonen.engine.impl.MemoryJournal
import fi.roikonen.engine.Journal
import fi.roikonen.wish.Child.{CancelWish, MakeWish}
import cask.*
import fi.roikonen.wish.Child

import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.concurrent.Await

object HttpApp extends cask.MainRoutes {

  val journal: Journal = MemoryJournal()

  @cask.post("/child/:childId/:wish")
  def makeWish(childId: String, wish: String): cask.Response[String] = {
    val responseF =
      for {
        events <- journal.read(Child.identifier(childId))
        state = events.foldLeft(Child.State(childId))((s, e) => s.project(e))
        effect = Child.makeWishHandler.handle(state, MakeWish(wish))
        _ <- journal.append(effect.events)
      } yield cask.Response(
        effect.commandResponse.error.getOrElse("OK"),
        effect.commandResponse.code
      )
    Await.result(responseF, 10.seconds)
  }

  @cask.post("/child/:childId/wish/:id/cancel")
  def cancelWish(childId: String, id: String): cask.Response[String] = {
    val uuid = try {
      UUID.fromString(id)
    } catch {
      case _: IllegalArgumentException => return cask.Response[String](
        "Invalid ID, UUID expected",
        400
      )
    }
    val responseF =
      for {
        events <- journal.read(Child.identifier(childId))
        state = events.foldLeft(Child.State(childId))((s, e) => s.project(e))
        effect = Child.cancelWishHandler.handle(state, CancelWish(uuid))
        _ <- journal.append(effect.events)
      } yield cask.Response(
        effect.commandResponse.error.getOrElse("OK"),
        effect.commandResponse.code
      )
    Await.result(responseF, 10.seconds)
  }

  @cask.get("/child/:childId")
  def getState(childId: String): cask.Response[String] = {
    val responseF =
      for {
        events <- journal.read(Child.identifier(childId))
        state = events.foldLeft(Child.State(childId))((s, e) => s.project(e))
      } yield cask.Response(
        upickle.default.write(state),
        headers = Seq("Content-Type" -> "application/json")
      )
    Await.result(responseF, 10.seconds)
  }

  initialize()
}