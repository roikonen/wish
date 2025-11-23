package fi.roikonen.structure

import fi.roikonen.structure.EventBus.{EventId, TopicId}

import scala.concurrent.Future

/** Simple in-memory EventBus test double that captures published events. */
class TestEventBus[E] extends EventBus[E] {

  /** Stored as (id, event, topic) in reverse chronological order (last published is head). */
  var published: List[(EventId, E, TopicId)] = Nil

  private var nextId: EventId = 1L

  override def publish(event: E, topic: TopicId): Future[EventId] = {
    val id = nextId
    nextId += 1
    published = (id, event, topic) :: published
    Future.successful(id)
  }

  override def fetch(
    topic: TopicId,
    afterId: EventId,
    max: Int = 100
  ): Future[List[(EventId, E)]] = {
    val filtered =
      published
        .filter { case (id, _, t) => t == topic && id > afterId }
        .sortBy(_._1) // ascending by id
        .take(max)
        .map { case (id, e, _) => (id, e) }

    Future.successful(filtered)
  }

  /** Convenience: get the most recently published event, if any. */
  def lastPublished: Option[(EventId, E, TopicId)] = published.headOption

  /** Reset captured events and id counter. */
  def clear(): Unit = {
    published = Nil
    nextId = 1L
  }
}
