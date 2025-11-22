package fi.roikonen.structure

import scala.concurrent.Future

object Journal {
  type Cursor = Int
}

/**
 * In this solution offset store is within journal implementation, but it could be separated as well.
 */
trait Journal {
  def append(events: Seq[PrivateEvent], cursor: Journal.Cursor): Future[Unit]
  def read(stream: StreamIdentifier, offset: Int = 0): Future[(Seq[PrivateEvent], Journal.Cursor)]
  def storeOffset(stream: StreamIdentifier, cursor: Journal.Cursor): Future[Unit]
  def getOffset(stream: StreamIdentifier): Journal.Cursor
}
