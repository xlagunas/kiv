package converter

import cat.xlagunas.model.MessageFrame
import com.google.gson.Gson
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readText

class FrameConverter(private val gson: Gson) {
    fun extractFrame(frame: Frame.Text): MessageFrame {
        return gson.fromJson(frame.readText(), MessageFrame::class.java)
    }

    fun convertToFrame(messageFrame: MessageFrame): Frame.Text {
        return Frame.Text(gson.toJson(messageFrame))
    }
}
