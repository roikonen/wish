package fi.roikonen.structure

import scala.concurrent.Future

trait EventHandler {

  def stream: StreamIdentifier

  // At-least-once, take care of deduplication
  def when(event: PrivateEvent): Future[Unit]
}
