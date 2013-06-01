import akka.actor.IO.SocketHandle
import akka.actor.{Actor, IO, ActorRef}
import akka.util.ByteString
import org.apache.logging.log4j.LogManager
import scala.concurrent.{ExecutionContext, Future}
import ExecutionContext.Implicits.global

trait Command
case class  NameCommand(name: String) extends Command
case class  EnterCommand(room: String) extends Command
case object ExitCommand extends Command
case class  ChatCommand(message: String) extends Command
case class  UnknownCommand(command: String) extends Command
case class  InvalidCommand(message: String) extends Command

case class Write(message: String)
case class SetSocket(socket: IO.SocketHandle)

class Client(roomService: ActorRef, clientSocket:Future[SocketHandle]) extends Actor {
  val log = LogManager.getLogger(this.getClass.getName)
  var iterateeRef: IO.IterateeRefAsync[Unit] = null
  var commandHandler: CommandHandler = null
  var socket:Future[SocketHandle] = clientSocket

  override def preStart = {
    log.debug("Connected!")
    commandHandler = default.orElse(fallbackHandler)
    socket onFailure {
      case e =>
        log.error(e)
        commandHandler(ExitCommand)
    }
    iterateeRef = IO.IterateeRef.async(handleInput)(context.dispatcher)
  }

  private def writeToSocket(message: String) = {
    socket = socket.map({ s =>
      s.asWritable.write(ByteString("> " + message + "\r\n"))
      s
    })
  }

  private def closeSocket = {
    socket = socket.map(s =>{
      s.close
      s
    })
  }

  type CommandHandler = PartialFunction[Command, Unit]
  val fallbackHandler: CommandHandler = {
    case InvalidCommand(message) => writeToSocket(message)
    case UnknownCommand(command) => writeToSocket("unknown command:" + command)
    case _ => writeToSocket("unsupported command")
  }
  def default: CommandHandler = {
    case ExitCommand       => closeSocket
    case NameCommand(name) =>
      commandHandler = named(name).orElse(fallbackHandler)
      writeToSocket("set name to:" + name)
  }
  def named(name: String): CommandHandler = {
    case ExitCommand        => closeSocket
    case EnterCommand(room) =>
      commandHandler = entered(name, room).orElse(fallbackHandler)
      roomService ! Enter(room, self, name)
  }
  def entered(name: String, room: String): CommandHandler = {
    case ExitCommand =>
      commandHandler = named(name).orElse(fallbackHandler)
      roomService ! Exit(room, self, name)
    case ChatCommand(message) => roomService ! BroadCast(room, name + " said " + message)
  }


  private def readCommand: IO.Iteratee[Command] = {
    for {
      line <- IO.takeUntil(ByteString("\r\n"))
      messages = line.decodeString("US-ASCII").split(" ")
      command = messages.head
      args = messages.tail
    } yield command match {
      case "NAME" =>
        log.debug("got NAME command with:" + args.toString)
        args.headOption match {
          case None => InvalidCommand("name is required")
          case Some(name) => NameCommand(name)
        }

      case "ENTER" =>
        log.debug("got ENTER command with: " + args.toString )
        args.headOption match {
          case None => InvalidCommand("room name is required")
          case Some(room) => EnterCommand(room)
        }

      case "EXIT" =>
        log.debug("got EXIT command")
        ExitCommand

      case "CHAT" =>
        log.debug("got CHAT command with: " + args.toString )
        ChatCommand(args.headOption.getOrElse(""))

      case _ =>
        log.debug("got unkown command" + command)
        UnknownCommand(command)
    }
  }

  def handleInput: IO.Iteratee[Unit] = IO repeat {
    readCommand.map(commandHandler(_))
  }

  def receive = {
    case IO.Read(socket, bytes) =>
      log.debug("READ:" + bytes.toString)
      iterateeRef(IO.Chunk(bytes))

    case IO.Closed(socket, cause) =>
      commandHandler(ExitCommand)
      iterateeRef(IO.EOF)
      socket.close

    case Write(message) =>
      writeToSocket(message)
  }
}
