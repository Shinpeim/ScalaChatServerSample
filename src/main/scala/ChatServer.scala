import akka.actor._
import scala.concurrent.{Promise, Future}

case class ChatMessage(message: String)

class ChatServer(port: Int, roomService: ActorRef) extends Actor {
  val state = IO.IterateeRef.Map.async[IO.SocketHandle]()(context.dispatcher)

  override def preStart = IOManager(context.system).listen("localhost", port)

  def receive = {
    case IO.NewClient(serverSocket) =>
      val socketPromise = Promise[IO.SocketHandle]
      val futureSocket = socketPromise.future

      val clientActor = context.actorOf(Props(new Client(roomService, futureSocket)))
      val socket = serverSocket.accept()(clientActor)
      socketPromise.success(socket)
  }
}
