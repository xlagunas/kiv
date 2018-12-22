package cat.xlagunas

import cat.xlagunas.RoomService.Companion.JOIN
import com.google.gson.Gson
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readText
import io.ktor.server.testing.withTestApplication
import kotlinx.coroutines.channels.consumeEachIndexed
import kotlinx.coroutines.channels.filter
import kotlinx.coroutines.channels.filterNotNull
import kotlinx.coroutines.channels.mapNotNull
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ApplicationTest {

    @Test
    fun givenEmptyRoom_whenNewUser_thenUserAdded() {
        withTestApplication({ module(testing = true) }) {
            lateinit var message: RoomUsers
            val expected = RoomUsers(listOf(RoomParticipant("user1")))
            val gson = Gson()


            handleWebSocketConversation("/testRoom/user1") { incoming, outgoing ->
                outgoing.send(Frame.Text(gson.toJson(Message(JOIN, "me", ""))))
                val sentText = (incoming.filterNotNull().filter { it is Frame.Text }.receive() as Frame.Text).readText()
                message = gson.fromJson(sentText, RoomUsers::class.java)
            }

            assertEquals(
                expected,
                message,
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
            val sentMessageToUser2 = Message("user2", "this is a test message", "user1")
            lateinit var messageReceivedToUser2: Message

            handleWebSocketConversation("/testRoom/user1") { incoming, outgoing ->
                outgoing.send(Frame.Text(gson.toJson(Message(JOIN, "me"))))

                handleWebSocketConversation("/testRoom/user2") { incoming2, outgoing2 ->

                    outgoing2.send(Frame.Text(gson.toJson(Message(JOIN, "me"))))
                    val msg = gson.toJson(sentMessageToUser2)
                    outgoing.send(Frame.Text(msg))

                    incoming.mapNotNull { it as? Frame.Text }.consumeEachIndexed {
                        val frameContent = (it.value as Frame.Text).readText()
                        registerMessageToUser1 = gson.fromJson(frameContent, RoomUsers::class.java)
                    }

                    incoming2.mapNotNull { it as? Frame.Text }.consumeEachIndexed {
                        val frameContent = (it.value as Frame.Text).readText()
                        if (it.index == 0) {
                            registerMessageToUser2 = gson.fromJson(frameContent, RoomUsers::class.java)
                        } else {
                            messageReceivedToUser2 = gson.fromJson(frameContent, Message::class.java)
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
