package fi.roikonen.app.http

import fi.roikonen.structure.{EventBus, Journal}
import fi.roikonen.domain.wish.Child.{CancelWish, MakeWish}
import cask.*
import cask.endpoints.QueryParamReader
import fi.roikonen.app.App
import fi.roikonen.domain.wish.Child
import fi.roikonen.structure.Command.CommandResponse
import fi.roikonen.domain.wish.PublicEvent
import fi.roikonen.domain.wish.policy.gatekeeper.VerifyWish

import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

object HttpApp extends cask.MainRoutes {
  given QueryParamReader[UUID] =
    new QueryParamReader.SimpleParam[UUID](s => UUID.fromString(s))

  private val application = App()
  private val journal: Journal = application.journal
  private val eventBus: EventBus[PublicEvent] = application.eventBus

  private def jsonResponse[T: upickle.default.Writer](value: T) = cask.Response(
    upickle.default.write(value),
    headers = Seq("Content-Type" -> "application/json")
  )

  private def mapToResponse(commandResponse: CommandResponse) = cask.Response(
    commandResponse.error.getOrElse("OK"),
    commandResponse.code
  )

  @cask.post("/child/:childId/wish/:wish")
  def makeWish(childId: String, wish: String): cask.Response[String] = {
    val responseF =
      for {
        gatekeeperEffect <- VerifyWish(childId, journal)
          .handle(MakeWish(wish, None, None))
          .map(_.effect)
        commandResponse <- gatekeeperEffect match {
          case Right(command) =>
            for {
              (state, cursor) <- Child.State(childId).rehydrate(journal)
              effect = Child.makeWishHandler.handle(state, command)
              commandResponse <- journal
                .append(effect.events, cursor)
                .map(_ => effect.commandResponse)
            } yield commandResponse
          case Left(commandResponse) => Future.successful(commandResponse)
        }
      } yield mapToResponse(commandResponse)
    Await.result(responseF, 10.seconds)
  }

  @cask.post("/child/:childId/wish/:id/cancel")
  def cancelWish(childId: String, id: UUID): cask.Response[String] = {
    val responseF =
      for {
        (state, cursor) <- Child.State(childId).rehydrate(journal)
        effect = Child.cancelWishHandler.handle(state, CancelWish(id))
        _ <- journal.append(effect.events, cursor)
      } yield mapToResponse(effect.commandResponse)
    Await.result(responseF, 10.seconds)
  }

  @cask.get("/child/:childId")
  def getChildState(childId: String): cask.Response[String] = {
    val responseF =
      for {
        (state, cursor) <- Child.State(childId).rehydrate(journal)
      } yield jsonResponse(state)
    Await.result(responseF, 10.seconds)
  }

  @cask.get("/events/:topic/:from")
  def getTopicEvents(topic: EventBus.TopicId, from: Long): cask.Response[String] = {
    val responseF =
      for {
        events <- eventBus.fetch(topic, from)
      } yield jsonResponse(events.map((_, event) => event))
    Await.result(responseF, 10.seconds)
  }

  @cask.get("/events/private/:from")
  def getPrivateEvents(from: Long): cask.Response[String] =
    getTopicEvents("private_events", from)

  initialize()
}
