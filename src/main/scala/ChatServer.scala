import akka.actor._
import akka.actor.SupervisorStrategy.{Resume, Stop}
import scala.concurrent.Promise

case class ChatMessage(message: String)

class ChatServer(port: Int, roomService: ActorRef) extends Actor {
  override def preStart = IOManager(context.system).listen("localhost", port)

  override val supervisorStrategy = OneForOneStrategy() {
    case _: SocketClosedException => Stop // stop actor when socket is closed
  }

  def receive = {
    case IO.NewClient(serverSocket) =>
      val socketPromise = Promise[IO.SocketHandle]
      val futureSocket = socketPromise.future

      val clientActor = context.actorOf(Props(new Client(roomService, futureSocket)))
      val socket = serverSocket.accept()(clientActor)
      socketPromise.success(socket)
  }
}
