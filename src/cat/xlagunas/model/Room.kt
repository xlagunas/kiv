package cat.xlagunas.model

import java.util.concurrent.ConcurrentHashMap

data class Room(val id: String, val participants: ConcurrentHashMap<String, User> = ConcurrentHashMap())