package fi.roikonen.structure

object StreamIdentifier {

  final val SEPARATOR: Char = '_'

  // This is stream identifier for every event.
  def all: StreamIdentifier = StreamIdentifier()

  def fromString(identifier: String): StreamIdentifier = StreamIdentifier(
    identifier.split(StreamIdentifier.SEPARATOR)*
  )
}

/** Events belong to the main stream and additionally to substreams. e.g. CatJumped could belong to:
  * main-stream, animal-stream, cat-stream and Sophie-stream (individual cat stream).
  *
  * @param ids
  *   e.g. Sophie, cat, animal
  */
case class StreamIdentifier(ids: String*) {
  require(
    ids.forall(id => id.isEmpty || id.matches("^[A-Za-z0-9-]+$")),
    "Invalid id: must be empty or contain only letters (A–Z, a–z), digits (0–9), or hyphens (-)"
  )

  // e.g. "Sophie_cat_animal(_main)" - "main" is implicit.
  def headBranch: String = ids.mkString("_")

  // Build stream IDs. e.g. ["a_b_c", "b_c", "c"]
  // e.g. "Sophie_cat_animal(_main)", "cat_animal(_main)" & "animal(_main)" - "main" is implicit.
  def branches: Set[String] =
    ids.tails.collect {
      case seg if seg.nonEmpty => seg.mkString(StreamIdentifier.SEPARATOR.toString)
    }.toSet
}
