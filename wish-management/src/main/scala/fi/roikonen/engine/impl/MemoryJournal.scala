package fi.roikonen.engine.impl

import fi.roikonen.engine.{Journal, PrivateEvent, StreamIdentifier}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class MemoryJournal extends Journal {

  private var streams: Map[String, Seq[PrivateEvent]] = Map.empty

  override def append(event: PrivateEvent): Future[Unit] = synchronized {
    val branches = event.streams.flatMap(_.branches) + ""
    streams = branches.foldLeft(streams) { (acc, key) =>
      val updatedSeq = acc.getOrElse(key, Seq.empty) :+ event
      acc.updated(key, updatedSeq)
    }
    Future.successful(())
  }

  override def append(events: Seq[PrivateEvent]): Future[Unit] =
    Future.traverse(events)(append).map(_ => ())

  override def read(stream: StreamIdentifier, offset: Int): Future[Seq[PrivateEvent]] =
    Future.successful(streams.getOrElse(stream.headBranch, Seq.empty))
}
