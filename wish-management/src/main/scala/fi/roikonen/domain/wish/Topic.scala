package fi.roikonen.domain.wish

enum Topic(val value: String) {
  case PrivateEvents extends Topic("private_events")
  case Naughtiness extends Topic("naughtiness")
  case WishUpdates extends Topic("wish_updates")
}
