package fi.roikonen.structure

trait PrivateEvent {
  def consistencyStream: StreamIdentifier
  def projectionStreams: Set[StreamIdentifier]
}
