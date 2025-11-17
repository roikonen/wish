package fi.roikonen.wish

import fi.roikonen.engine.Command.{Command, CommandEffect, CommandResponse}
import fi.roikonen.engine.{CommandHandler, PrivateEvent, StateProjection, StreamIdentifier}

import java.util.UUID

// JSON Serialization as it is really hard to hide that from the domain code.
import upickle.default.*

object Child {

  def identifier(id: String) = StreamIdentifier(id, "child")

  enum RejectionReason derives ReadWriter {
    case NaughtyWish
    case ChildInNaughtyList
    case TooManyWishes
  }

  sealed trait ChildEvent extends PrivateEvent {
    def id: UUID
    def childId: String
    def streams: Set[StreamIdentifier] = Set(
      StreamIdentifier(childId, "child"),
      StreamIdentifier(id.toString, "wish")
    )
  }

  case class WishApproved(id: UUID, childId: String, wish: String)
    extends ChildEvent derives ReadWriter

  case class WishRejected(id: UUID, childId: String, wish: String, reason: RejectionReason)
    extends ChildEvent derives ReadWriter

  case class WishCancelled(id: UUID, childId: String)
    extends ChildEvent derives ReadWriter

  object ChildEvent {
    given ReadWriter[ChildEvent] = ReadWriter.merge(
      summon[ReadWriter[WishApproved]],
      summon[ReadWriter[WishRejected]],
      summon[ReadWriter[WishCancelled]]
    )
  }

  case class State(id: String, wishes: Map[UUID, String] = Map.empty)
    extends StateProjection[State] {
    def from: StreamIdentifier = identifier(id)

    def project(privateEvent: PrivateEvent): State = privateEvent match {
      case e: WishApproved => this.copy(wishes = this.wishes + (e.id -> e.wish))
      case _: WishRejected => this
      case e: WishCancelled => this.copy(wishes = this.wishes - e.id)
    }
  }
  given ReadWriter[State] = macroRW

  case class MakeWish(wish: String) extends Command derives ReadWriter
  case class CancelWish(id: UUID) extends Command derives ReadWriter

  val makeWishHandler: CommandHandler[State, MakeWish] =
    (state: State, command: MakeWish) => {
      if (state.wishes.size == 10) {
        CommandEffect(
          Seq(WishRejected(UUID.randomUUID(), state.id, command.wish, RejectionReason.TooManyWishes)),
          CommandResponse(400, Some("Can't have more than ten wishes"))
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
}