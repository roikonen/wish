package fi.roikonen.engine

import fi.roikonen.engine.Command.{Command, CommandEffect}

trait CommandHandler[S <: State, C <: Command] {
  def handle(state: S, command: C): CommandEffect
}
