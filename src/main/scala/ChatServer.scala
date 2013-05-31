import akka.actor.{IO, IOManager, Actor}
import akka.util.ByteString

case class ChatMessage(message: String)

class ChatServer(port: Int) extends Actor {
  val state = IO.IterateeRef.Map.async[IO.SocketHandle]()(context.dispatcher)

  override def preStart = IOManager(context.system).listen("localhost", port)

  def receive = {
    case ChatMessage(message) =>
      state.keys.foreach(_.asWritable.write(ByteString("message: " + message + "\r\n")))

    case IO.NewClient(serverSocket) =>
      val socket = serverSocket.accept()
      state(socket).flatMap(_ => ClientHandler.handleInput(self, socket))

    case IO.Read(socket: IO.SocketHandle, bytes) =>
      state(socket)(IO Chunk bytes)

    case IO.Closed(socket: IO.SocketHandle, cause) =>
      state(socket)(IO EOF)
      socket.close
      state -= socket
  }
}
