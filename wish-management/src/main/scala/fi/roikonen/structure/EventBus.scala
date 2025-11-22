package fi.roikonen.structure

import fi.roikonen.structure.EventBus.{EventId, TopicId}

import scala.concurrent.Future

object EventBus {
  type EventId = Long
  type TopicId = String
}

trait EventBus[E] {
  def publish(event: E, topic: TopicId): Future[EventId]
  def fetch(topic: TopicId, afterId: EventId, max: Int = 100): Future[List[(EventId, E)]]
}
