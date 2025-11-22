package fi.roikonen.structure

import fi.roikonen.structure.Command.{Command, CommandEffect}

trait CommandHandler[S <: State, C <: Command] {
  def handle(state: S, command: C): CommandEffect
}
