package fi.roikonen.engine

import scala.concurrent.Future

trait Journal {
  def append(event: PrivateEvent): Future[Unit]
  def append(events: Seq[PrivateEvent]): Future[Unit]
  def read(stream: StreamIdentifier, offset: Int = 0): Future[Seq[PrivateEvent]]
}
