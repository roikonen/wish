package fi.roikonen.structure

import scala.concurrent.Future

object Journal {
  type Cursor = Long
}

/** In this solution offset store is within journal implementation, but it could be separated as
  * well.
  */
trait Journal {

  /** @param events
    *   Events to be persisted
    * @param cursor
    *   Can be used to implement optimistic locking
    * @return
    *   Future.success if operation went OK
    */
  def append(events: Seq[PrivateEvent], cursor: Option[Journal.Cursor]): Future[Unit]

  def read(
    stream: StreamIdentifier,
    offset: Journal.Cursor = 0
  ): Future[(Seq[PrivateEvent], Journal.Cursor)]
  def storeOffset(stream: StreamIdentifier, cursor: Journal.Cursor): Future[Unit]
  def getOffset(stream: StreamIdentifier): Journal.Cursor
}
