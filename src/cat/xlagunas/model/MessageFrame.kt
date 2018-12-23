package cat.xlagunas.model

import com.google.gson.annotations.SerializedName

data class MessageFrame(
    @SerializedName("from") val from: String,
    @SerializedName("destination") val to: String,
    @SerializedName("type") val type: MessageType,
    @SerializedName("data") val data: String
)