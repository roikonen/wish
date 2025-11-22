package fi.roikonen.structure

object Command {
  trait Command
  case class CommandResponse(code: Int, error: Option[String])
  case class CommandEffect(
    events: Seq[PrivateEvent],
    commandResponse: CommandResponse = CommandResponse(200, None)
  )
}
