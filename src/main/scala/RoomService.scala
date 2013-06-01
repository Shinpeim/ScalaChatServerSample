import akka.actor.{ActorRef, Actor}
import scala.collection.immutable.{Set, Map}

case class Enter(room: String, clientActorRef: ActorRef, name: String)
case class Exit(room: String, clientActorRef: ActorRef, name: String)
case class BroadCast(room: String, message: String)

class RoomService extends Actor {
  var rooms: Map[String, Set[ActorRef]] = Map.empty

  private def enter(room: String, client: ActorRef, name: String): Unit = {
    rooms.get(room) match {
      case None => rooms += ((room, Set(client)))
      case Some(clients) => rooms = rooms.updated(room, (clients + client))
    }
    broadCast(room, "a new member joined: " + name)
  }

  private def exit(room: String, client: ActorRef, name: String): Unit = rooms.get(room) match {
    case None => Unit
    case Some(clients) => (clients - client) match {
      case updated if updated.isEmpty =>  rooms -= room
      case updated => rooms = rooms.updated(room, updated)
    }
    broadCast(room, "someone left the room: " + name)
    client ! Write("you left the room: " + room)
  }

  private def broadCast(room: String, message: String): Unit = rooms.get(room) match {
    case None => Unit
    case Some(clients) =>
      clients.foreach(_ ! Write(message))
  }

  def receive = {
    case Enter(room, client, name) => enter(room, client, name)
    case Exit(room, client, name)  => exit(room, client, name)
    case BroadCast(room, message) => broadCast(room, message)
  }
}
