package fi.roikonen.engine

import scala.concurrent.Future

trait StreamProcessor {
  def stream: StreamIdentifier
  def currentOffset: Int
  def when(event: PrivateEvent): Future[Unit]
}
