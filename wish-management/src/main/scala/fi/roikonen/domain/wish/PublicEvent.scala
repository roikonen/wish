package fi.roikonen.domain.wish

import upickle.default.*

import java.time.Instant
import java.util.UUID

sealed trait PublicEvent derives ReadWriter

object PublicEvent {
  given instantRW: ReadWriter[Instant] =
    readwriter[String].bimap[Instant](
      _.toString,
      Instant.parse
    )

  case class PrivateEventPersisted(json: String) extends PublicEvent derives ReadWriter

  case class WishApproved(id: UUID, childId: String, wish: String) extends PublicEvent
      derives ReadWriter

  case class WishCancelled(id: UUID) extends PublicEvent derives ReadWriter

  case class NaughtinessDetected(id: String, naughtyWish: String, rejectionTime: Instant)
      extends PublicEvent derives ReadWriter

}
