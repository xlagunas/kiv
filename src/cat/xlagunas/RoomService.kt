package cat.xlagunas

import cat.xlagunas.model.*
import com.google.gson.Gson
import converter.FrameConverter
import io.ktor.http.cio.websocket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap

class RoomService {

    private val room = ConcurrentHashMap<String, Room>()

    private val gsonParser = Gson()
    private val frameConverter = FrameConverter(gsonParser)

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
        msg: MessageFrame
    ) {
        when (msg.type) {
            MessageType.ROOM_DISCOVERY -> sendRoomStatus(roomId, userId)
            else -> sendDirectMessage(roomId, msg.to, msg)
        }
    }

    suspend private fun sendRoomStatus(roomId: String, userId: String) {
        val roomAttendants = room[roomId]?.participants?.keys()?.toList()?.map { RoomParticipant(it) }
        if (roomAttendants != null) {
            val roomUsers = RoomUsers(roomAttendants)
            val roomStatusMessage = MessageFrame(
                SERVER_SENDER, userId, MessageType.ROOM_DISCOVERY, gsonParser.toJson(roomUsers)
            )

            getUserOnRoom(roomId, userId).sessions.forEach { it.send(frameConverter.convertToFrame(roomStatusMessage)) }
        }
    }

    private suspend fun sendDirectMessage(roomId: String, receiverId: String, msg: MessageFrame) {
        val frame = frameConverter.convertToFrame(msg)
        findOrCreateRoom(roomId).participants[receiverId]?.sendMessage(frame)
    }

    companion object {
        const val SERVER_SENDER = "SERVER"
    }
}