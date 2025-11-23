package fi.roikonen.domain.wish

import fi.roikonen.structure.{PrivateEvent, StreamIdentifier}

import java.time.{Instant, LocalDate, ZoneOffset}
import java.util.UUID

// JSON Serialization as it is hard to hide that from the domain code.
import upickle.default.*

object PrivateEvents {
  given instantRW: ReadWriter[Instant] =
    readwriter[String].bimap[Instant](
      _.toString,
      Instant.parse
    )

  enum RejectionReason derives ReadWriter {
    case NaughtyWish
    case ChildInNaughtyList
    case TooManyWishes
  }

  // BASE TRAITS

  sealed trait ChildEvent extends PrivateEvent {
    def childId: String
    def consistencyStream: StreamIdentifier = StreamIdentifier(childId, Stream.Child.value)
  }

  sealed trait WishRelated {
    self: ChildEvent =>
    def wishId: UUID

    def projectionStreams: Set[StreamIdentifier] = Set(
      StreamIdentifier(wishId.toString, Stream.Wish.value)
    )
  }

  sealed trait NaughtinessRelated {
    self: ChildEvent =>

    // Create ability to subscribe naughtiness events per day (UTC), just as an example.
    def projectionStreams: Set[StreamIdentifier] = Set(
      StreamIdentifier(LocalDate.now(ZoneOffset.UTC).toString, Stream.Naughtiness.value)
    )
  }

  // EVENTS

  case class WishApproved(wishId: UUID, childId: String, wish: String)
      extends ChildEvent
      with WishRelated derives ReadWriter

  case class WishRejected(
    wishId: UUID,
    childId: String,
    wish: String,
    reason: RejectionReason,
    rejectionTime: Instant
  ) extends ChildEvent
      derives ReadWriter {
    def projectionStreams: Set[StreamIdentifier] = Set(
      Option(StreamIdentifier(wishId.toString, Stream.Wish.value)),
      Option.when(reason.equals(RejectionReason.NaughtyWish))(
        StreamIdentifier(LocalDate.now(ZoneOffset.UTC).toString, Stream.Naughtiness.value)
      )
    ).flatten
  }

  case class WishCancelled(wishId: UUID, childId: String) extends ChildEvent with WishRelated
      derives ReadWriter

  case class WishFulfilled(wishId: UUID, childId: String) extends ChildEvent with WishRelated
      derives ReadWriter

  case class BecameNaughty(childId: String, onNaughtyListUntil: Instant)
      extends ChildEvent
      with NaughtinessRelated derives ReadWriter

  // SERIALIZATION

  object ChildEvent {
    given ReadWriter[ChildEvent] = ReadWriter.merge(
      summon[ReadWriter[WishApproved]],
      summon[ReadWriter[WishRejected]],
      summon[ReadWriter[WishFulfilled]],
      summon[ReadWriter[WishCancelled]],
      summon[ReadWriter[BecameNaughty]]
    )
  }

}
