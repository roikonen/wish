package fi.roikonen.domain.wish.policy.eventhandler

import fi.roikonen.domain.wish.PrivateEvents.{ChildEvent, WishApproved, WishCancelled}
import fi.roikonen.domain.wish.{PublicEvent, Stream, Topic}
import fi.roikonen.structure.{EventBus, EventHandler, PrivateEvent, StreamIdentifier}

import scala.concurrent.Future

class PublishWishUpdates(eventBus: EventBus[PublicEvent]) extends EventHandler {
  override def stream: StreamIdentifier = StreamIdentifier(Stream.Wish.value)

  override def when(event: PrivateEvent): Future[Unit] = {
    val publicEventOption = event match {
      case ce: ChildEvent =>
        ce match {
          case WishApproved(id, childId, wish) =>
            Option(PublicEvent.WishApproved(id, childId, wish))
          case WishCancelled(id, _) => Option(PublicEvent.WishCancelled(id))
          case _                    => None
        }
      case _ => None
    }
    publicEventOption.map(eventBus.publish(_, Topic.WishUpdates.value))
    Future.successful(())
  }
}
