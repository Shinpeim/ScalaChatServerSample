import akka.actor.{ActorRef, Actor}
import scala.collection.immutable.{Set, Map}

case class Enter(room: String, clientActorRef: ActorRef)
case class Exit(room: String, clientActorRef: ActorRef)
case class Chat(room: String, message: String)

class RoomService extends Actor {
  var rooms: Map[String, Set[ActorRef]] = Map.empty

  private def enter(room: String, client: ActorRef): Unit = {
    rooms.get(room) match {
      case None => rooms += ((room, Set(client)))
      case Some(clients) => rooms = rooms.updated(room, (clients + client))
    }
    client ! Write("entered the room: " + room)
  }

  private def exit(room: String, client: ActorRef): Unit = rooms.get(room) match {
    case None => Unit
    case Some(clients) => (clients - client) match {
      case updated if updated.isEmpty =>  rooms -= room
      case updated => rooms = rooms.updated(room, updated)
    }
    client ! Write("exited from the room: " + room)
  }

  private def broadCast(room: String, message: String): Unit = rooms.get(room) match {
    case None => Unit
    case Some(clients) =>
      clients.foreach(_ ! Write(message))
  }

  def receive = {
    case Enter(room, client) => enter(room, client)
    case Exit(room, client)  => exit(room, client)
    case Chat(room, message) => broadCast(room, message)
  }
}
