package io.gh.kdbrian.ttac.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val PROTOCOL_VERSION = 1
const val SERVICE_TYPE = "_ttac._tcp."

/** Messages exchanged between two devices, one JSON object per line. */
@Serializable
sealed interface LanMessage {
    /** First message from each side. The host's [boardSize] decides the board. */
    @Serializable @SerialName("hello")
    data class Hello(val name: String, val color: Long, val boardSize: Int, val version: Int = PROTOCOL_VERSION, val alias: String = "") : LanMessage

    @Serializable @SerialName("move")
    data class Move(val round: Int, val index: Int) : LanMessage

    @Serializable @SerialName("rematch")
    data class Rematch(val round: Int) : LanMessage

    @Serializable @SerialName("bye")
    data object Bye : LanMessage
}

object Protocol {
    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "t" }

    fun encode(message: LanMessage): String = json.encodeToString(LanMessage.serializer(), message)

    /** Returns null for malformed or unknown lines instead of throwing — peers are untrusted input. */
    fun decode(line: String): LanMessage? = runCatching { json.decodeFromString(LanMessage.serializer(), line) }.getOrNull()
}
