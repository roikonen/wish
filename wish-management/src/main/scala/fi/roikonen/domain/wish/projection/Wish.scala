package fi.roikonen.domain.wish.projection

import fi.roikonen.domain.wish.Child.WishApproved
import fi.roikonen.structure.{PrivateEvent, StateProjection, StreamIdentifier}
import fi.roikonen.domain.wish.Stream

import java.util.UUID

case class Wish(id: UUID, childId: String = "") extends StateProjection[Wish] {

  override def from: StreamIdentifier = StreamIdentifier(id.toString, Stream.Wish.value)

  override def project(event: PrivateEvent): Wish = event match {
    case e: WishApproved => this.copy(childId = e.childId)
  }

  override def default: Wish = Wish(id)
}
