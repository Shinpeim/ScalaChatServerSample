import akka.actor.{IO, ActorRef}
import akka.util.ByteString
import org.apache.logging.log4j.LogManager

trait Command
case class ChatCommand(message:String) extends Command
case class ExitCommand() extends Command
case class UnknownCommand(command:String) extends Command

object ClientIteratee {
 def readCommand: IO.Iteratee[Command] = {
    for {
      line <- IO.takeUntil(ByteString("\r\n"))
      messages = line.decodeString("US-ASCII").split(" ")
      command = messages.head
      args = messages.tail
    } yield command match {
      case "CHAT" => ChatCommand(args.lift(0).getOrElse(""))
      case "EXIT" => ExitCommand()
      case _ => UnknownCommand(command)
    }
  }
}

object ClientHandler {
  val log = LogManager.getLogger(this.getClass.getName)

  def handleInput(server: ActorRef, socket: IO.SocketHandle): IO.Iteratee[Unit] = IO repeat {
    ClientIteratee.readCommand map {
      case ExitCommand() =>
        log.debug("got EXIT command")
        socket.close

      case ChatCommand(message) =>
        log.debug("got CHAT command")
        server ! ChatMessage(message)

      case UnknownCommand(command) =>
        log.debug("got unknown command: " + command)
        socket.asWritable.write(ByteString("unknown command:" + command + "\r\n"))
    }
  }
}
