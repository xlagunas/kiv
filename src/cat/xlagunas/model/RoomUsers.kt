package cat.xlagunas.model

import com.google.gson.annotations.SerializedName

data class RoomUsers(@SerializedName("participants") val connectedUsers: List<RoomParticipant>)