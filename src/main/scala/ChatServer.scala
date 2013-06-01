import akka.actor._

case class ChatMessage(message: String)

class ChatServer(port: Int, roomService: ActorRef) extends Actor {
  val state = IO.IterateeRef.Map.async[IO.SocketHandle]()(context.dispatcher)

  override def preStart = IOManager(context.system).listen("localhost", port)

  def receive = {
    case IO.NewClient(serverSocket) =>
      val clientActor = context.actorOf(Props(new Client(roomService)))
      clientActor ! SetSocket(serverSocket.accept()(clientActor))
  }
}
