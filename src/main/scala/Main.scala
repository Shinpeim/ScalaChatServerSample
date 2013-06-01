import akka.actor.{Props, ActorSystem}

object Main {
  def main(args: Array[String]) {
    val actorSystem = ActorSystem.create
    val roomService = actorSystem.actorOf(Props[RoomService])
    actorSystem.actorOf(Props(new ChatServer(9876, roomService)))
  }
}
