package fi.roikonen.domain.wish

import fi.roikonen.structure.Command.{Command, CommandEffect, CommandResponse}
import fi.roikonen.structure.{CommandHandler, PrivateEvent, StateProjection, StreamIdentifier}

import java.time.{Instant, LocalDate, ZoneOffset}
import java.util.UUID
import scala.concurrent.duration.{DurationInt, FiniteDuration}

// JSON Serialization as it is hard to hide that from the domain code.
import upickle.default.*

object Child {
  given instantRW: ReadWriter[Instant] =
    readwriter[String].bimap[Instant](
      _.toString,
      Instant.parse
    )

  private def identifier(id: String) = StreamIdentifier(id, Stream.Child.value)

  // If child gives naughty wish, how long do we prevent wishes from child?
  // This is to make sure that naughty child list has time to update
  // -> prevent immediate non-naughty wishes to pass though. (Eventual consistency in naughty list)
  private final val NAUGHTY_WISH_BAN_DURATION: FiniteDuration = 1.minute

  enum RejectionReason derives ReadWriter {
    case NaughtyWish
    case ChildInNaughtyList
    case TooManyWishes
  }

  // EVENTS

  sealed trait ChildEvent extends PrivateEvent {
    def childId: String
    def consistencyStream: StreamIdentifier = StreamIdentifier(childId, Stream.Child.value)

  }

  sealed trait WishRelated { self: ChildEvent =>
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

  case class BecameNaughty(childId: String, onNaughtyListUntil: Instant)
      extends ChildEvent
      with NaughtinessRelated derives ReadWriter

  object ChildEvent {
    given ReadWriter[ChildEvent] = ReadWriter.merge(
      summon[ReadWriter[WishApproved]],
      summon[ReadWriter[WishRejected]],
      summon[ReadWriter[WishCancelled]],
      summon[ReadWriter[BecameNaughty]]
    )
  }

  // STATE

  case class State(
    id: String,
    wishes: Map[UUID, String] = Map.empty,
    onNaughtyListUntil: Instant = Instant.MIN,
    bannedUntil: Instant = Instant.MIN
  ) extends StateProjection[State] {
    def from: StreamIdentifier = identifier(id)

    def project(privateEvent: PrivateEvent): State = privateEvent match {
      case e: WishApproved  => handleWishApproved(this, e)
      case e: WishRejected  => handleWishRejected(this, e)
      case e: WishCancelled => handleWishCancelled(this, e)
      case e: BecameNaughty => handleBecameNaughty(this, e)
    }

    override def default: State = State(id)
  }
  given ReadWriter[State] = macroRW

  // EVENT HANDLERS

  private def handleWishApproved(state: State, e: WishApproved): State =
    state.copy(wishes = state.wishes + (e.wishId -> e.wish))

  private def handleWishCancelled(state: State, e: WishCancelled): State =
    state.copy(wishes = state.wishes - e.wishId)

  private def handleWishRejected(state: State, e: WishRejected): State = {
    if (e.reason.equals(RejectionReason.NaughtyWish)) {
      state.copy(bannedUntil = e.rejectionTime.plusMillis(NAUGHTY_WISH_BAN_DURATION.toMillis))
    } else {
      state
    }
  }

  private def handleBecameNaughty(state: State, e: BecameNaughty): State = {
    state.copy(onNaughtyListUntil = e.onNaughtyListUntil)
  }

  // COMMANDS

  case class MakeWish(
    wish: String,
    naughtyWish: Option[Boolean],
    onNaughtyListUntil: Option[Instant]
  ) extends Command
  case class CancelWish(id: UUID) extends Command
  case class MarkNaughty(onNaughtyListUntil: Instant) extends Command

  // COMMAND HANDLERS

  val makeWishHandler: CommandHandler[State, MakeWish] =
    (state: State, command: MakeWish) => {

      val wishRejectedDefault = WishRejected(
        wishId = UUID.randomUUID(),
        childId = state.id,
        wish = command.wish,
        reason = RejectionReason.TooManyWishes,
        rejectionTime = Instant.now()
      )

      if (state.bannedUntil.isAfter(Instant.now())) {
        CommandEffect(
          Seq.empty,
          CommandResponse(
            400,
            Some(s"You are banned due to naughty wishes until ${state.bannedUntil.toString}")
          )
        )
      } else if (state.wishes.size == 10) {
        CommandEffect(
          Seq(wishRejectedDefault.copy(reason = RejectionReason.TooManyWishes)),
          CommandResponse(
            400,
            Some("Can't have more than ten wishes")
          )
        )
      } else if (state.onNaughtyListUntil.isAfter(Instant.now())) {
        CommandEffect(
          Seq(wishRejectedDefault.copy(reason = RejectionReason.ChildInNaughtyList)),
          CommandResponse(
            400,
            Some(s"You naughty child! You can't wish until ${state.onNaughtyListUntil.toString}")
          )
        )
      } else if (command.naughtyWish.contains(true)) {
        CommandEffect(
          Seq(wishRejectedDefault.copy(reason = RejectionReason.NaughtyWish)),
          CommandResponse(
            400,
            Some("Your wish was naughty, shame on you")
          )
        )
      } else {
        CommandEffect(
          Seq(WishApproved(UUID.randomUUID(), state.id, command.wish))
        )
      }
    }

  val cancelWishHandler: CommandHandler[State, CancelWish] =
    (state: State, command: CancelWish) => {
      if (state.wishes.contains(command.id)) {
        CommandEffect(Seq(WishCancelled(command.id, state.id)))
      } else {
        CommandEffect(
          Seq.empty,
          CommandResponse(404, Some("Wish not found"))
        )
      }
    }

  val markNaughtyHandler: CommandHandler[State, MarkNaughty] =
    (state: State, command: MarkNaughty) => {
      if (state.onNaughtyListUntil.isBefore(command.onNaughtyListUntil)) {
        CommandEffect(
          Seq(BecameNaughty(state.id, command.onNaughtyListUntil)) ++
            state.wishes.keys.map(wish => WishCancelled(wish, state.id))
        )
      } else {
        CommandEffect(
          Seq.empty,
          CommandResponse(400, Some("Child was already on the naughty list"))
        )
      }
    }
}
