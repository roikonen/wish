package fi.roikonen.domain.wish.projection

import fi.roikonen.domain.wish.PrivateEvents.{
  BecameNaughty,
  RejectionReason,
  WishApproved,
  WishCancelled,
  WishFulfilled,
  WishRejected
}
import fi.roikonen.domain.wish.Stream
import fi.roikonen.structure.{PrivateEvent, StateProjection, StreamIdentifier}

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.{DurationInt, FiniteDuration}

// JSON Serialization as it is hard to hide that from the domain code.
import upickle.default.*

case class Child(
  id: String,
  wishes: Map[UUID, String] = Map.empty,
  onNaughtyListUntil: Instant = Instant.MIN,
  bannedUntil: Instant = Instant.MIN
) extends StateProjection[Child] {
  def sourceStream: StreamIdentifier = Child.identifier(id)

  def project(privateEvent: PrivateEvent): Child = privateEvent match {
    case e: WishApproved  => Child.handleWishApproved(this, e)
    case e: WishRejected  => Child.handleWishRejected(this, e)
    case e: WishCancelled => Child.handleWishCancelled(this, e)
    case e: WishFulfilled => Child.handleWishFulfilled(this, e)
    case e: BecameNaughty => Child.handleBecameNaughty(this, e)
    case _                => this
  }

  override def initialState: Child = Child(id)
}

object Child {
  given instantRW: ReadWriter[Instant] =
    readwriter[String].bimap[Instant](
      _.toString,
      Instant.parse
    )
  given ReadWriter[Child] = macroRW

  // If child gives naughty wish, how long do we prevent wishes from child?
  // This is to make sure that naughty child list has time to update
  // -> prevent immediate non-naughty wishes to pass though. (Eventual consistency in naughty list)
  private final val NAUGHTY_WISH_BAN_DURATION: FiniteDuration = 1.minute

  private def identifier(id: String) = StreamIdentifier(id, Stream.Child.value)

  private def handleWishApproved(state: Child, e: WishApproved): Child =
    state.copy(wishes = state.wishes + (e.wishId -> e.wish))

  private def handleWishRejected(state: Child, e: WishRejected): Child = {
    if (e.reason.equals(RejectionReason.NaughtyWish)) {
      state.copy(bannedUntil = e.rejectionTime.plusMillis(NAUGHTY_WISH_BAN_DURATION.toMillis))
    } else {
      state
    }
  }

  private def handleWishCancelled(state: Child, e: WishCancelled): Child =
    state.copy(wishes = state.wishes - e.wishId)

  private def handleWishFulfilled(state: Child, e: WishFulfilled): Child =
    state.copy(wishes = state.wishes - e.wishId)

  private def handleBecameNaughty(state: Child, e: BecameNaughty): Child = {
    state.copy(onNaughtyListUntil = e.onNaughtyListUntil)
  }
}
