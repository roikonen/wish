package fi.roikonen.domain.wish.policy.eventhandler

import fi.roikonen.domain.wish.Child.{RejectionReason, WishRejected}
import fi.roikonen.domain.wish.{PublicEvent, Stream, Topic}
import fi.roikonen.domain.wish.PublicEvent.NaughtinessDetected
import fi.roikonen.structure.{EventBus, EventHandler, PrivateEvent, StreamIdentifier}

import scala.concurrent.Future

class PublishNaughtiness(eventBus: EventBus[PublicEvent]) extends EventHandler {
  override def stream: StreamIdentifier = StreamIdentifier(Stream.Naughtiness.value)

  override def when(event: PrivateEvent): Future[Unit] = {
    val publicEventOption = event match {
      case e: WishRejected if e.reason.equals(RejectionReason.NaughtyWish) =>
        Option(
          NaughtinessDetected(id = e.childId, naughtyWish = e.wish, rejectionTime = e.rejectionTime)
        )
      case _ => None
    }
    publicEventOption.map(eventBus.publish(_, Topic.Naughtiness.value))
    Future.successful(())
  }
}
