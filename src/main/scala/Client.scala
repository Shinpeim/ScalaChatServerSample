import akka.actor.IO.SocketHandle
import akka.actor.{Actor, IO, ActorRef}
import akka.util.ByteString
import org.apache.logging.log4j.LogManager

trait Command
case class  NameCommand(name: String) extends Command
case class  EnterCommand(room: String) extends Command
case object ExitCommand extends Command
case class  ChatCommand(message: String) extends Command
case class  UnknownCommand(command: String) extends Command
case class  InvalidCommand(message: String) extends Command

case class Write(message: String)
case class SetSocket(socket: IO.SocketHandle)

class Client(roomService: ActorRef) extends Actor {
  val log = LogManager.getLogger(this.getClass.getName)
  var socket: Option[SocketHandle] = None
  var iterateeRef: Option[IO.IterateeRefAsync[Unit]] = None
  var commandHandler: CommandHandler = null

  type CommandHandler = PartialFunction[Command, Unit]
  val fallbackHandler: CommandHandler = {
    case InvalidCommand(message) => writeToClient(message)
    case UnknownCommand(command) => writeToClient("unknown command:" + command)
    case _ => writeToClient("unsupported command")
  }
  def default: CommandHandler = {
    case ExitCommand          => socket.foreach(_.close)
    case NameCommand(name) =>
      commandHandler = named(name).orElse(fallbackHandler)
      writeToClient("set name to:" + name)
  }
  def named(name: String): CommandHandler = {
    case ExitCommand          => socket.foreach(_.close)
    case EnterCommand(room)   =>
      commandHandler = entered(name, room).orElse(fallbackHandler)
      roomService ! Enter(room, self, name)
  }
  def entered(name: String, room: String): CommandHandler = {
    case ExitCommand =>
      commandHandler = named(name).orElse(fallbackHandler)
      roomService ! Exit(room, self, name)
    case ChatCommand(message) => roomService ! BroadCast(room, name + " said " + message)
  }

  override def preStart = commandHandler = default.orElse(fallbackHandler)

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

  private def writeToClient(s: String) = {
    socket.foreach(_.asWritable.write(ByteString("> " + s + "\r\n")))
  }

  def handleInput: IO.Iteratee[Unit] = IO repeat {
    readCommand.map(commandHandler(_))
  }

  def receive = {
    case SetSocket(clientSocket) =>
      log.debug("Connected!")
      socket = Some(clientSocket)
      iterateeRef = Some(IO.IterateeRef.async(handleInput)(context.dispatcher))

    case IO.Read(socket, bytes) =>
      log.debug("READ:" + bytes.toString)
      iterateeRef.foreach(_.apply(IO.Chunk(bytes)))

    case IO.Closed(socket, cause) =>
      iterateeRef.foreach(_.apply(IO.EOF))
      socket.close
      iterateeRef = None

    case Write(message) =>
      writeToClient(message)
  }
}
