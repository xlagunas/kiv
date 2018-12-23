package cat.xlagunas

import cat.xlagunas.model.MessageFrame
import com.google.gson.Gson
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.http.cio.websocket.CloseReason
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.close
import io.ktor.http.cio.websocket.readText
import io.ktor.request.path
import io.ktor.routing.routing
import io.ktor.server.engine.commandLineEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.websocket.WebSockets
import io.ktor.websocket.webSocket
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.mapNotNull
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.time.Duration

fun main(args: Array<String>) {
    embeddedServer(Netty, commandLineEnvironment(args)).start()
}

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    install(WebSockets) {
        val timeouts = if (testing) 2L else 15L
        pingPeriod = Duration.ofSeconds(timeouts)
        timeout = Duration.ofSeconds(timeouts)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    val roomService = RoomService()
    val gsonParser = Gson()
    val log = LoggerFactory.getLogger(this::class.java)

    routing {

        webSocket("/{roomId}/{userId}") {
            val roomId = call.parameters["roomId"]
            val userId = call.parameters["userId"]

            log.info("New user: [$userId] joined room: [$roomId]")

            if (roomId == null || userId == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid room"))
                return@webSocket
            }

            try {
                roomService.addUser(roomId, userId, this)
                incoming.mapNotNull { it as? Frame.Text }.consumeEach { frame ->
                    val message = gsonParser.fromJson(frame.readText(), MessageFrame::class.java)
                    log.info("Message from: $userId: $message")
                    roomService.handleMessage(roomId, userId, message)
                }
            } finally {
                roomService.removeUserSession(roomId, this)
                log.info("user $userId clossed session ${this}")
            }
        }
    }
}

