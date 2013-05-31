import akka.actor.{Props, ActorSystem}

object Main {
  def main(args: Array[String]) {
    val actorSystem = ActorSystem.create
    actorSystem.actorOf(Props(new ChatServer(9876)))
  }
}
