package cat.xlagunas

import com.google.gson.Gson
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap

class RoomService {

    private val room = ConcurrentHashMap<String, Room>()

    //TODO Dependency injection for reuse of instances!
    private val gsonParser = Gson()

    private fun findOrCreateRoom(roomId: String): Room {
        return room.getOrPut(roomId) { Room(roomId) }
    }

    private fun getUserOnRoom(roomName: String, userId: String): User {
        return findOrCreateRoom(roomName).participants.getOrPut(userId) { User(userId) }
    }

    fun addUser(roomName: String, userId: String, session: WebSocketSession) {
        getUserOnRoom(roomName, userId).addSession(session)
    }

    fun removeUserSession(roomId: String, session: WebSocketSession) {
        room[roomId]?.participants?.values?.forEach { user ->
            if (user.sessions.contains(session)) {
                user.removeSession(session)
                if (user.sessions.isEmpty()) {
                    room[roomId]?.participants?.remove(user.id)
                }
            }
        }
    }

    suspend fun handleMessage(
        roomId: String,
        userId: String,
        msg: Message
    ) {
        val message = msg.copy(from = userId)

        when (msg.destination.toLowerCase()) {
            JOIN -> sendRoomStatus(roomId, userId)
            BROADCAST_MESSAGE -> sendBroadcastMessage(roomId, userId, gsonParser.toJson(message))
            else -> sendDirectMessage(roomId, msg.destination, gsonParser.toJson(message))
        }
    }

    suspend private fun sendRoomStatus(roomId: String, userId: String) {
//        val message = Message(
//            destination = userId,
//            from = "SERVER",
//            data = "Total users on Room ${roomId} is ${findOrCreateRoom(roomId).participants.size}"
//        )
        val roomAttendants = room[roomId]?.participants?.keys()?.toList()?.map { RoomParticipant(it) }
        getUserOnRoom(roomId, userId).sessions.forEach { it.send(Frame.Text(Gson().toJson(roomAttendants))) }
    }

    private suspend fun sendBroadcastMessage(roomId: String, userId: String, msg: String) {
        findOrCreateRoom(roomId).participants
            .filterNot { it.key == userId }
            .forEach {
                it.value.sendMessage(Frame.Text(msg))
            }
    }

    private suspend fun sendDirectMessage(roomId: String, receiverId: String, msg: String) {
        val frame = Frame.Text(msg)

        findOrCreateRoom(roomId).participants[receiverId]?.sendMessage(frame)
    }

    companion object {
        const val BROADCAST_MESSAGE = "broadcast"
        const val JOIN = "join"
    }
}

data class Room(val id: String, val participants: ConcurrentHashMap<String, User> = ConcurrentHashMap())

data class User(val id: String, val sessions: MutableList<WebSocketSession> = ArrayList()) {
    fun addSession(socket: WebSocketSession) {
        sessions += socket
    }

    fun removeSession(socket: WebSocketSession) {
        sessions -= socket
    }

    suspend fun sendMessage(msg: Frame.Text) {
        sessions.forEach { it.send(msg) }
    }
}