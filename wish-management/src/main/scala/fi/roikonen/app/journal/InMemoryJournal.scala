package fi.roikonen.app.journal

import fi.roikonen.structure.Journal.Cursor
import fi.roikonen.structure.{Journal, PrivateEvent, StreamIdentifier}

import scala.concurrent.Future

class InMemoryJournal extends Journal {

  private var streams: Map[String, Seq[PrivateEvent]] = Map.empty
  private var offsets: Map[StreamIdentifier, Journal.Cursor] = Map.empty

  private def getCursor(streamId: StreamIdentifier): Journal.Cursor =
    streams.getOrElse(streamId.headBranch, Seq.empty).size

  override def append(events: Seq[PrivateEvent], cursor: Journal.Cursor): Future[Unit] = {
    require(
      events.map(_.consistencyStream).distinct.size <= 1,
      "All incoming events must have the same consistency stream"
    )

    if (events.isEmpty) return Future.successful(())

    synchronized {

      // Optimistic locking applied here
      if (getCursor(events.head.consistencyStream) != cursor)
        return Future.failed(IllegalStateException("Update failed due to concurrent modification"))

      for (event <- events) {
        val eventStreams = event.projectionStreams + event.consistencyStream
        val branches = eventStreams.flatMap(_.branches) + ""
        streams = branches.foldLeft(streams) { (acc, key) =>
          val updatedSeq = acc.getOrElse(key, Seq.empty) :+ event
          acc.updated(key, updatedSeq)
        }
      }
      Future.successful(())

    }
  }

  override def read(
    streamId: StreamIdentifier,
    offset: Int = 0
  ): Future[(Seq[PrivateEvent], Journal.Cursor)] = {
    val stream = streams.getOrElse(streamId.headBranch, Seq.empty)
    Future.successful(stream.drop(offset), stream.size)
  }

  override def storeOffset(streamId: StreamIdentifier, cursor: Cursor): Future[Unit] = {
    offsets = offsets + (streamId -> cursor)
    Future.successful(())
  }

  override def getOffset(stream: StreamIdentifier): Cursor = offsets.getOrElse(stream, 0)

}
