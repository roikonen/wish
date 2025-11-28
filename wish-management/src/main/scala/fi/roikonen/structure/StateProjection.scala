package fi.roikonen.structure

import scala.concurrent.{ExecutionContext, Future}

trait StateProjection[S <: StateProjection[S]] extends State {
  def sourceStream: StreamIdentifier
  def project(privateEvent: PrivateEvent): S
  def initialState: S

  def rehydrate(journal: Journal)(implicit ec: ExecutionContext): Future[(S, Journal.Cursor)] =
    journal
      .read(sourceStream)
      .map((events, cursor) =>
        (
          events.foldLeft(initialState)((state, event) => state.project(event)),
          cursor
        )
      )
}
