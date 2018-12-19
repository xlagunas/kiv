package cat.xlagunas

import cat.xlagunas.RoomService.Companion.JOIN
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readText
import io.ktor.server.testing.withTestApplication
import kotlinx.coroutines.experimental.channels.filter
import kotlinx.coroutines.experimental.channels.filterNotNull
import kotlinx.coroutines.experimental.channels8.asStream
import org.junit.Test
import java.lang.reflect.Type
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ApplicationTest {


    @Test
    fun givenEmptyRoom_whenNewUser_thenUserAdded() {
        withTestApplication({ module(testing = true) }) {
            lateinit var message: RoomParticipant
            val expected = listOf(RoomParticipant("user1"))

            val gson = Gson()
            val conversionValue : Type = object : TypeToken<List<RoomParticipant>>() {}.type::class.java

            handleWebSocketConversation("/testRoom/user1") { incoming, outgoing ->
                outgoing.send(Frame.Text(gson.toJson(Message(JOIN, "me", ""))))
                incoming.filterNotNull().filter { it is Frame.Text }.asStream().forEach{
                    message = gson.fromJson((it as Frame.Text).readText(), conversionValue)
                }

            }

            assertEquals(
                expected as List<RoomParticipant>,
                message as List<RoomParticipant>,
                "Unexpected number of users in the room"
            )
        }
    }

    @Test
    fun givenUserInRoom_whenNewUser_thenNewUserReceiveOldUserInfo() {
        withTestApplication({ module(testing = true) }) {

            val messageToUser2 = hashSetOf<Message>()
            val messageToUser1 = hashSetOf<Message>()

            handleWebSocketConversation("/testRoom/user1") { incoming, outgoing ->

                handleWebSocketConversation("/testRoom/user2") { incoming2, outgoing2 ->

                    val msg = Gson().toJson(Message("user2", "this is a test message"))
                    outgoing.send(Frame.Text(msg))

                    val plainMsgForUser1 = (incoming.receive() as Frame.Text).readText()
                    messageToUser1 += Gson().fromJson(plainMsgForUser1, Message::class.java)

                    for (n in 0 until 2) {
                        val plainMsgForUser2 = (incoming2.receive() as Frame.Text).readText()
                        messageToUser2 += Gson().fromJson(plainMsgForUser2, Message::class.java)
                    }
                }
            }

            assertEquals(
                setOf(
                    Message("user2", from = "SERVER", data = "Total users on Room testRoom is 2"),
                    Message("user2", from = "user1", data = "this is a test message")
                ),
                messageToUser2
            )

            assertEquals(
                setOf(
                    Message("user1", from = "SERVER", data = "Total users on Room testRoom is 1")
                ), messageToUser1
            )


        }
    }

    data class TestRoomAssistance(val roomParticipant: List<RoomParticipant>)
}
