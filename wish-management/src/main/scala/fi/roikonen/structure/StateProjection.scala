package fi.roikonen.structure

import scala.concurrent.{ExecutionContext, Future}

trait StateProjection[S <: StateProjection[S]] extends State {
  def from: StreamIdentifier
  def project(privateEvent: PrivateEvent): S
  def default: S

  def rehydrate(journal: Journal)(implicit ec: ExecutionContext): Future[(S, Journal.Cursor)] =
    journal
      .read(from)
      .map((events, cursor) =>
        (
          events.foldLeft(default)((s, e) => s.project(e)),
          cursor
        )
      )
}
