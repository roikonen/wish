package fi.roikonen.engine

trait StateProjection[S <: StateProjection[S]] extends State {
  def from: StreamIdentifier
  def project(privateEvent: PrivateEvent): S
}
