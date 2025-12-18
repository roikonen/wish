package fi.roikonen.app

import fi.roikonen.domain.wish.PublicEvent
import fi.roikonen.domain.wish.policy.eventhandler.{
  PublishPrivateEvents,
  PublishNaughtiness,
  PublishWishUpdates
}
import fi.roikonen.structure.{EventBus, EventHandler, Journal}

import scala.concurrent.ExecutionContext

class App(implicit
  ec: ExecutionContext
) {

  val journal: Journal = InMemoryJournal()
  val eventBus: EventBus[PublicEvent] = InMemoryEventBus()

  // Define event handlers to be activated.
  private val eventHandlers: Seq[EventHandler] = Seq(
    PublishPrivateEvents(eventBus),
    PublishWishUpdates(eventBus),
    PublishNaughtiness(eventBus)
  )

  private val reactor = Reactor(journal, eventHandlers)
  reactor.start() // returns instantly, processing runs in background

}
