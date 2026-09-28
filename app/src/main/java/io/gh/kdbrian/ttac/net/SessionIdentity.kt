package io.gh.kdbrian.ttac.net

import kotlin.random.Random

/** Friendly random names for LAN sessions, so players never see raw addresses. */
object Aliases {
    private val adjectives = listOf(
        "Cosmic", "Neon", "Blazing", "Sneaky", "Turbo", "Lucky", "Fuzzy", "Electric", "Mighty", "Swift",
        "Molten", "Frosty", "Wobbly", "Golden", "Shadow", "Bouncy", "Sonic", "Crimson", "Velvet", "Rogue",
    )
    private val animals = listOf(
        "Otter", "Falcon", "Panda", "Gecko", "Lynx", "Badger", "Koala", "Raven", "Tiger", "Narwhal",
        "Fox", "Yak", "Moth", "Heron", "Wombat", "Shark", "Mantis", "Bison", "Ibis", "Quokka",
    )

    fun random(random: Random = Random.Default): String =
        "${adjectives.random(random)} ${animals.random(random)} ${random.nextInt(10, 100)}"
}

/**
 * Encodes an IPv4 address and port into a short, shareable code (Crockford base32, e.g.
 * "K7Q2M-9XZ4P") for joining by hand when discovery is blocked — without showing the address.
 */
object SessionCode {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    fun encode(ipv4: String, port: Int): String? {
        val octets = ipv4.split(".").mapNotNull { it.toIntOrNull() }
        if (octets.size != 4 || octets.any { it !in 0..255 } || port !in 1..65535) return null
        var bits = 0L
        for (o in octets) bits = (bits shl 8) or o.toLong()
        bits = (bits shl 16) or port.toLong()
        // 48 bits → 10 base32 characters (50 bits, top two always zero).
        val chars = CharArray(10) { i -> ALPHABET[((bits shr ((9 - i) * 5)) and 31).toInt()] }
        return String(chars, 0, 5) + "-" + String(chars, 5, 5)
    }

    /** Returns (address, port), or null if the code is malformed. Accepts lowercase and common look-alikes. */
    fun decode(code: String): Pair<String, Int>? {
        val clean = code.uppercase().filter { it != '-' && !it.isWhitespace() }
            .map { when (it) { 'O' -> '0'; 'I', 'L' -> '1'; else -> it } }
        if (clean.size != 10) return null
        var bits = 0L
        for (ch in clean) {
            val v = ALPHABET.indexOf(ch)
            if (v < 0) return null
            bits = (bits shl 5) or v.toLong()
        }
        if (bits shr 48 != 0L) return null
        val port = (bits and 0xFFFF).toInt()
        val ip = (bits shr 16)
        val address = (3 downTo 0).joinToString(".") { k -> ((ip shr (k * 8)) and 0xFF).toString() }
        if (port == 0) return null
        return address to port
    }
}

/** What a host's QR code carries: its session code and alias, never a raw address. */
object JoinLink {
    private const val PREFIX = "ttac://join?"

    fun build(code: String, alias: String): String =
        PREFIX + "c=" + code + "&a=" + java.net.URLEncoder.encode(alias, "UTF-8")

    /** Returns (code, alias) or null if [text] isn't a TTac join link with a valid code. */
    fun parse(text: String): Pair<String, String>? {
        if (!text.startsWith(PREFIX)) return null
        val params = text.removePrefix(PREFIX).split("&").mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to runCatching { java.net.URLDecoder.decode(it.substring(i + 1), "UTF-8") }.getOrNull()
        }.toMap()
        val code = params["c"] ?: return null
        if (SessionCode.decode(code) == null) return null
        return code to (params["a"]?.take(40) ?: "a session")
    }
}
