package cat.xlagunas.model

import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.WebSocketSession

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