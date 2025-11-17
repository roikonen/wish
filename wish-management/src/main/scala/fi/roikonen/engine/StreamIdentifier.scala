package fi.roikonen.engine

import scala.annotation.tailrec

/**
 * Events belong to the main stream and additionally to substreams.
 * e.g. CatJumped could belong to: main-stream, animal-stream, cat-stream and Sophie-stream (individual cat stream).
 * 
 * @param ids e.g. Sophie, cat, animal
 */
case class StreamIdentifier(ids: String*) {
  require(
    ids.map(_.matches("^[A-Za-z0-9-]+$")).exists(!_.equals(false)),
    s"Invalid id: must contain only letters (A–Z, a–z), digits (0–9), or hyphens (-)"
  )

  def headBranch: String = ids.mkString("_")

  // Build stream IDs. e.g. ["a_b_c", "b_c", "c"]
  // e.g. "Sophie_cat_animal(_main)", "cat_animal(_main)" & "animal(_main)" - "main" is implicit.
  def branches: Set[String] =
    ids.tails.collect { case seg if seg.nonEmpty => seg.mkString("_") }.toSet
}
