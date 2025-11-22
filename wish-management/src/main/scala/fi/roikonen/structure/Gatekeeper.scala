package fi.roikonen.structure

import fi.roikonen.structure.Command.{Command, CommandResponse}
import fi.roikonen.structure.Gatekeeper.Effect

import scala.concurrent.Future

object Gatekeeper {
  case class Effect[C](effect: Either[CommandResponse, C])
}

trait Gatekeeper[C <: Command] {
  def handle(command: C): Future[Effect[C]]
}
