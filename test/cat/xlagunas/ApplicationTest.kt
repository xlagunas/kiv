package cat.xlagunas

import cat.xlagunas.model.MessageFrame
import cat.xlagunas.model.MessageType
import cat.xlagunas.model.RoomParticipant
import cat.xlagunas.model.RoomUsers
import com.google.gson.Gson
import converter.FrameConverter
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readText
import io.ktor.server.testing.withTestApplication
import kotlinx.coroutines.channels.consumeEachIndexed
import kotlinx.coroutines.channels.mapNotNull
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ApplicationTest {

    private val gson = Gson()
    private val converter = FrameConverter(gson)

    @Test
    fun givenEmptyRoom_whenNewUser_thenUserAdded() {
        withTestApplication({ module(testing = true) }) {
            lateinit var message: MessageFrame
            val expected = RoomUsers(listOf(RoomParticipant("user1")))

            handleWebSocketConversation("/testRoom/user1") { incoming, outgoing ->
                outgoing.send(
                    converter
                        .convertToFrame(MessageFrame("user1", "", MessageType.ROOM_DISCOVERY, ""))
                )
                val sentText = incoming.mapNotNull { it as? Frame.Text }.receive()
                message = converter.extractFrame(sentText)
            }

            assertEquals(
                expected,
                gson.fromJson(message.data, RoomUsers::class.java),
                "Unexpected number of users in the room"
            )
        }
    }

    @Test
    fun givenUserInRoom_whenNewUser_thenNewUserReceiveOldUserInfo() {
        val gson = Gson()
        withTestApplication({ module(testing = true) }) {

            lateinit var registerMessageToUser1: RoomUsers
            lateinit var registerMessageToUser2: RoomUsers
            val sentMessageToUser2 = MessageFrame("user1", "user2", MessageType.OFFER, "this is a test message")
            lateinit var messageReceivedToUser2: MessageFrame

            handleWebSocketConversation("/testRoom/user1") { incoming, outgoing ->
                outgoing.send(converter.convertToFrame(MessageFrame("user1", "", MessageType.ROOM_DISCOVERY, "")))

                handleWebSocketConversation("/testRoom/user2") { incoming2, outgoing2 ->
                    outgoing2.send(converter.convertToFrame(MessageFrame("user2", "", MessageType.ROOM_DISCOVERY, "")))
                    outgoing.send(converter.convertToFrame(sentMessageToUser2))

                    incoming.mapNotNull { it as? Frame.Text }.consumeEachIndexed {
                        val frameContent = it.value.readText()
                        registerMessageToUser1 = gson.fromJson(frameContent, RoomUsers::class.java)
                    }

                    incoming2.mapNotNull { it as? Frame.Text }.consumeEachIndexed {
                        if (it.index == 0) {
                            val receivedHelloMessage = converter.extractFrame(it.value)
                            registerMessageToUser2 = gson.fromJson(receivedHelloMessage.data, RoomUsers::class.java)
                        } else {
                            messageReceivedToUser2 = converter.extractFrame(it.value)
                        }
                    }
                }
            }

            assertNotNull(registerMessageToUser1)
            assertNotNull(registerMessageToUser2)
            assertEquals(sentMessageToUser2, messageReceivedToUser2)
        }
    }
}
