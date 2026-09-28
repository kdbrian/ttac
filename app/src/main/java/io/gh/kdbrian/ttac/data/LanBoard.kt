package io.gh.kdbrian.ttac.data

/** Someone who has played LAN matches on this device: a remote alias, or one of your profiles. */
data class LanMember(
    val id: String,
    val name: String,
    /** How the member is known on the network. */
    val alias: String,
    val record: Record,
    val lastPlayed: Long,
    val isRemote: Boolean,
)

/** LAN-only standings, rebuilt from match history so every member's streaks are tracked. */
object LanBoard {

    fun members(stats: StatsData, myAlias: String): List<LanMember> {
        val records = HashMap<String, Record>()
        val names = HashMap<String, String>()
        val last = HashMap<String, Long>()
        // History is newest first; replay oldest first so streaks come out right.
        for (m in stats.history.asReversed()) {
            if (m.mode != GameMode.LAN || m.xId.isEmpty() || m.oId.isEmpty()) continue
            val winner = m.result
            for ((id, name, mark) in listOf(Triple(m.xId, m.xName, "X"), Triple(m.oId, m.oName, "O"))) {
                val outcome = when (winner) {
                    "DRAW" -> Outcome.DRAW
                    mark -> Outcome.WIN
                    else -> Outcome.LOSS
                }
                records[id] = (records[id] ?: Record()).add(outcome)
                names[id] = name
                last[id] = m.timestamp
            }
        }
        return records.map { (id, rec) ->
            val remote = id.startsWith("lan:")
            LanMember(
                id = id,
                name = names[id] ?: "Unknown",
                alias = if (remote) id.removePrefix("lan:") else myAlias,
                record = rec,
                lastPlayed = last[id] ?: 0L,
                isRemote = remote,
            )
        }.sortedWith(compareByDescending<LanMember> { it.record.wins }.thenByDescending { it.record.bestStreak }.thenBy { it.record.losses })
    }

    fun historyOf(stats: StatsData, id: String): List<MatchRecord> =
        stats.history.filter { it.mode == GameMode.LAN && (it.xId == id || it.oId == id) }
}
