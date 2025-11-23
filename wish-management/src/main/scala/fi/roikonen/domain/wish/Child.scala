package fi.roikonen.domain.wish

import fi.roikonen.domain.wish.PrivateEvents.{
  BecameNaughty,
  RejectionReason,
  WishApproved,
  WishCancelled,
  WishFulfilled,
  WishRejected
}
import fi.roikonen.domain.wish.projection.Child
import fi.roikonen.structure.Command.{Command, CommandEffect, CommandResponse}
import fi.roikonen.structure.CommandHandler

import java.time.Instant
import java.util.UUID

object Child {

  // COMMANDS

  case class MakeWish(
    wish: String,
    naughtyWish: Option[Boolean],
    onNaughtyListUntil: Option[Instant]
  ) extends Command

  case class CancelWish(id: UUID) extends Command

  case class MarkNaughty(onNaughtyListUntil: Instant) extends Command

  // COMMAND HANDLERS

  val makeWishHandler: CommandHandler[Child, MakeWish] =
    (state: Child, command: MakeWish) => {

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

  val cancelWishHandler: CommandHandler[Child, CancelWish] =
    (state: Child, command: CancelWish) => {
      if (state.wishes.contains(command.id)) {
        CommandEffect(Seq(WishCancelled(command.id, state.id)))
      } else {
        CommandEffect(
          Seq.empty,
          CommandResponse(404, Some("Wish not found"))
        )
      }
    }

  val markNaughtyHandler: CommandHandler[Child, MarkNaughty] =
    (state: Child, command: MarkNaughty) => {
      if (command.onNaughtyListUntil.isBefore(Instant.now())) {
        CommandEffect(
          Seq.empty,
          CommandResponse(
            400,
            Some(s"The given date has already expired: ${command.onNaughtyListUntil}")
          )
        )
      } else if (state.onNaughtyListUntil.isBefore(command.onNaughtyListUntil)) {
        CommandEffect(
          Seq(BecameNaughty(state.id, command.onNaughtyListUntil)) ++
            state.wishes.keys.map(wish => WishCancelled(wish, state.id)),
          CommandResponse(
            200,
            Some(s"Child ${state.id} marked naughty until ${command.onNaughtyListUntil}")
          )
        )
      } else {
        CommandEffect(
          Seq.empty,
          CommandResponse(400, Some("Child was already on the naughty list"))
        )
      }
    }
}
