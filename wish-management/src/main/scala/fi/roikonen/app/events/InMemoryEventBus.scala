package fi.roikonen.app.events

import fi.roikonen.domain.wish.PublicEvent
import fi.roikonen.structure.EventBus
import fi.roikonen.structure.EventBus.{EventId, TopicId}

import scala.collection.mutable
import scala.concurrent.Future

class InMemoryEventBus extends EventBus[PublicEvent] {
  private val topics = mutable.Map.empty[TopicId, Vector[(EventId, PublicEvent)]]
  private var nextId: EventId = 1L

  def publish(event: PublicEvent, topic: TopicId): Future[EventId] = {
    val id = this.synchronized {
      val id = nextId
      nextId = nextId + 1L
      val list = topics.getOrElse(topic, Vector.empty)
      topics.update(topic, list :+ (id -> event))
      id
    }
    Future.successful(id)
  }

  def fetch(
    topic: TopicId,
    afterId: EventId,
    max: Int = 100
  ): Future[List[(EventId, PublicEvent)]] = {
    val result = this.synchronized {
      val list = topics.getOrElse(topic, Vector.empty)
      val start = {
        if (afterId <= 0L) {
          0
        } else {
          val idx = list.indexWhere(_._1 > afterId)
          if (idx == -1) {
            list.size
          } else {
            idx
          }
        }
      }
      list.slice(start, start + max).toList
    }
    Future.successful(result)
  }
}
