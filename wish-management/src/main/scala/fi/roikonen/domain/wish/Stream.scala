package fi.roikonen.domain.wish

enum Stream(val value: String) {
  case Wish extends Stream("wish")
  case Naughtiness extends Stream("naughtiness")
  case Child extends Stream("child")
}
