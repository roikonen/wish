package fi.roikonen.domain.wish.projection

import fi.roikonen.domain.wish.Child.BecameNaughty
import fi.roikonen.structure.{PrivateEvent, StateProjection, StreamIdentifier}

import java.time.Instant

/** @param children
  *   child ID and the time when they are released from the naughty list.
  */
case class NaughtyList(children: Map[String, Instant] = Map.empty)
    extends StateProjection[NaughtyList] {

  override def from: StreamIdentifier = StreamIdentifier("naughtiness")

  override def project(event: PrivateEvent): NaughtyList = event match {
    case e: BecameNaughty => this.copy(children = children + (e.childId -> e.onNaughtyListUntil))
  }

  override def default: NaughtyList = NaughtyList()
}
