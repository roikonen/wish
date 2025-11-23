package fi.roikonen.structure

object Command {
  trait Command
  case class CommandResponse(code: Int = 200, error: Option[String] = None)
  case class CommandEffect(
    events: Seq[PrivateEvent],
    commandResponse: CommandResponse = CommandResponse()
  )
}
