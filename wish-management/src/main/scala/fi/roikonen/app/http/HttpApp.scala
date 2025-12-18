package fi.roikonen.app.http

import fi.roikonen.structure.{EventBus, Journal, PrivateEvent, StreamIdentifier}
import fi.roikonen.domain.wish.Child.{CancelWish, MakeWish, MarkNaughty}
import fi.roikonen.domain.wish.PrivateEvents.{ChildEvent, WishFulfilled}
import cask.*
import cask.endpoints.QueryParamReader
import fi.roikonen.app.App
import fi.roikonen.structure.Command.CommandResponse
import fi.roikonen.domain.wish.{Child, PublicEvent, projection}
import fi.roikonen.domain.wish.policy.gatekeeper.VerifyWish
import upickle.default.*

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

object HttpApp extends cask.MainRoutes {
  given QueryParamReader[UUID] =
    new QueryParamReader.SimpleParam[UUID](s => UUID.fromString(s))
  given Reader[Instant] = reader[String].map(Instant.parse)
  given ReadWriter[PrivateEvent] = ReadWriter.merge(
    summon[ReadWriter[ChildEvent]]
  )

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

  @cask.postJson("/child/:childId/wish")
  def makeWish(childId: String, wish: String): cask.Response[String] = {
    val responseF =
      for {
        gatekeeperEffect <- VerifyWish(childId, journal)
          .handle(MakeWish(wish))
          .map(_.effect)
        commandResponse <- gatekeeperEffect match {
          case Right(command) =>
            for {
              (state, cursor) <- projection.Child(childId).rehydrate(journal)
              effect = Child.makeWishHandler.handle(state, command)
              commandResponse <- journal
                .append(effect.events, Option(cursor))
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
        (state, cursor) <- projection.Child(childId).rehydrate(journal)
        effect = Child.cancelWishHandler.handle(state, CancelWish(id))
        _ <- journal.append(effect.events, Option(cursor))
      } yield mapToResponse(effect.commandResponse)
    Await.result(responseF, 10.seconds)
  }

  @cask.post("/integration/wish/:id/mark_fulfilled")
  def markWishFulfilled(id: UUID): cask.Response[String] = {
    val responseF = for {
      childId <- projection.Wish(id).rehydrate(journal).map((wish, _) => wish.childId)
      _ <- journal.append(Seq(WishFulfilled(id, childId)), None)
    } yield mapToResponse(CommandResponse())
    Await.result(responseF, 10.seconds)
  }

  @cask.postJson("/integration/child/:id/mark_naughty")
  def markChildNaughty(id: String, expirationTime: Instant): cask.Response[String] = {
    val responseF =
      for {
        (state, cursor) <- projection.Child(id).rehydrate(journal)
        effect = Child.markNaughtyHandler.handle(
          state,
          MarkNaughty(onNaughtyListUntil = expirationTime)
        )
        _ <- journal.append(effect.events, Option(cursor))
      } yield mapToResponse(effect.commandResponse)
    Await.result(responseF, 10.seconds)
  }

  @cask.get("/child/:childId")
  def getChildState(childId: String): cask.Response[String] = {
    val responseF =
      for {
        (state, cursor) <- projection.Child(childId).rehydrate(journal)
      } yield jsonResponse(state)
    Await.result(responseF, 10.seconds)
  }

  case class PublicEventWithCursor(cursor: Long, event: PublicEvent)
  object PublicEventWithCursor {
    implicit val rw: upickle.default.ReadWriter[PublicEventWithCursor] = upickle.default.macroRW
  }

  @cask.get("/events/:topic/:from")
  def getTopicEvents(topic: EventBus.TopicId, from: Long): cask.Response[String] = {
    val responseF =
      for {
        events <- eventBus.fetch(topic, from)
      } yield jsonResponse(events.map { case (cursor, event) =>
        PublicEventWithCursor(cursor, event)
      })
    Await.result(responseF, 10.seconds)
  }

  @cask.get("/events/private/:from")
  def getPrivateEvents(from: Long): cask.Response[String] =
    getTopicEvents("private_events", from)

  case class StreamResponse(events: Seq[PrivateEvent], cursor: Long)

  object StreamResponse {
    implicit val rw: ReadWriter[StreamResponse] = macroRW
  }

  @cask.get("/stream/:stream/:from")
  def getStream(stream: String, from: Long): cask.Response[String] = {
    val responseF =
      for {
        (events, cursor) <- journal.read(StreamIdentifier.fromString(stream), from)
      } yield jsonResponse(StreamResponse(events, cursor))
    Await.result(responseF, 10.seconds)
  }

  initialize()
}
