package fi.roikonen.domain.wish.policy.eventhandler

import fi.roikonen.domain.wish.Child.ChildEvent
import fi.roikonen.domain.wish.{PublicEvent, Topic}
import fi.roikonen.domain.wish.PublicEvent.PrivateEventPersisted
import fi.roikonen.structure.{EventBus, EventHandler, PrivateEvent, StreamIdentifier}

import scala.concurrent.Future

class PublishPrivateEvents(eventBus: EventBus[PublicEvent]) extends EventHandler {
  override def stream: StreamIdentifier = StreamIdentifier.all
  override def when(event: PrivateEvent): Future[Unit] = {
    event match {
      case e: ChildEvent =>
        eventBus.publish(
          PrivateEventPersisted(upickle.default.write(e)),
          Topic.PrivateEvents.value
        )
    }
    Future.successful(())
  }
}
