package fi.roikonen.app

import fi.roikonen.structure.{EventHandler, Journal, StreamIdentifier}

import scala.concurrent.{ExecutionContext, Future}

case class Reactor(journal: Journal, eventHandlers: Seq[EventHandler])(implicit
  ec: ExecutionContext
) {

  def start(): Unit = {
    val handlersByStream =
      eventHandlers.groupBy(_.stream)

    // start async loops for every stream
    handlersByStream.foreach { case (stream, handlers) =>
      loopStream(stream, handlers) // non-blocking
    }
  }

  private def processStreamOnce(
    stream: StreamIdentifier,
    handlers: Seq[EventHandler]
  ): Future[Unit] = {

    val offset = journal.getOffset(stream)

    val result = for {
      (events, newCursor) <- journal.read(stream, offset)

      _ <- Future.sequence {
        for {
          event <- events
          handler <- handlers
        } yield handler.when(event)
      }

      _ <- journal.storeOffset(stream, newCursor)
    } yield ()

    result
  }

  private def loopStream(
    stream: StreamIdentifier,
    handlers: Seq[EventHandler]
  ): Unit = {

    processStreamOnce(stream, handlers)
      .recover { case err =>
        // TODO: proper logging/backoff
        ()
      }
      .foreach { _ =>
        loopStream(stream, handlers)
      }
  }
}
