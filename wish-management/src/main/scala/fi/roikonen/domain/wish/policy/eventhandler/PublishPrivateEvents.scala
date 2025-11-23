package fi.roikonen.domain.wish.policy.eventhandler

import fi.roikonen.domain.wish.PrivateEvents.ChildEvent
import fi.roikonen.domain.wish.{PublicEvent, Topic}
import fi.roikonen.domain.wish.PublicEvent.PrivateEventPersisted
import fi.roikonen.structure.{EventBus, EventHandler, PrivateEvent, StreamIdentifier}

import scala.concurrent.{ExecutionContext, Future}

class PublishPrivateEvents(eventBus: EventBus[PublicEvent])(implicit ec: ExecutionContext)
    extends EventHandler {
  override def stream: StreamIdentifier = StreamIdentifier.all
  override def when(event: PrivateEvent): Future[Unit] = {
    event match {
      case e: ChildEvent =>
        eventBus
          .publish(
            PrivateEventPersisted(upickle.default.write(e)),
            Topic.PrivateEvents.value
          )
          .map(_ => ())

      case _ =>
        Future.successful(())
    }
  }
}
