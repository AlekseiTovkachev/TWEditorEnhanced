package app.tweditor

import java.util.TreeMap

data class KillCount(val tag: String, val count: Int)

data class ActQuests(val act: String, val known: Int, val begun: Int, val completed: Int)

data class DayCount(val day: Int, val count: Int, val firstTOD: Long, val lastTOD: Long)

class StatisticsData(
    val totalKills: Int,
    val distinctOpponents: Int,
    val topKills: List<KillCount>,
    val acts: List<ActQuests>,
    val journalEntries: Int,
    val firstEntryTOD: Long,
    val lastEntryTOD: Long,
    val days: List<DayCount>
) {
    companion object {
        val ACT_ORDER = listOf("prologue1", "act1", "act2", "act3", "act4", "act5", "epilogue")
        const val SECONDS_PER_DAY = 86400L

        fun compute(killTags: List<String>, quests: List<Quest>, journalEntries: List<JournalEntry>): StatisticsData {
            val killCounts = LinkedHashMap<String, Int>()
            for (tag in killTags) {
                val key = tag.lowercase()
                killCounts[key] = (killCounts[key] ?: 0) + 1
            }
            val sortedKills = killCounts.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .map { KillCount(it.key, it.value) }

            val byAct = HashMap<String, ActQuests>()
            val buckets = HashMap<String, MutableList<Quest>>()
            for (quest in quests) {
                val act = if (quest.motherDb.isNotEmpty() && ACT_ORDER.contains(quest.motherDb)) {
                    quest.motherDb
                } else {
                    "other"
                }
                buckets.getOrPut(act) { ArrayList() }.add(quest)
            }
            val acts = ArrayList<ActQuests>()
            for (act in ACT_ORDER + if (buckets.contains("other")) listOf("other") else emptyList()) {
                val list = buckets[act] ?: continue
                val begun = list.count { it.questState != 0 }
                val completed = list.count { it.questState == 2 }
                acts.add(ActQuests(act, list.size, begun, completed))
            }

            val timed = journalEntries.filter { it.timeOfDay > 0 }
            val first = timed.minOfOrNull { it.timeOfDay } ?: 0L
            val last = timed.maxOfOrNull { it.timeOfDay } ?: 0L
            val byDay = TreeMap<Int, LongArray>()
            for (entry in timed) {
                val day = (entry.timeOfDay / SECONDS_PER_DAY).toInt()
                val slot = byDay.getOrPut(day) { LongArray(3) }
                if (slot[2] == 0L) {
                    slot[0] = entry.timeOfDay
                    slot[1] = entry.timeOfDay
                } else {
                    slot[0] = minOf(slot[0], entry.timeOfDay)
                    slot[1] = maxOf(slot[1], entry.timeOfDay)
                }
                slot[2] += 1
            }
            val days = byDay.entries.map { DayCount(it.key, it.value[2].toInt(), it.value[0], it.value[1]) }

            return StatisticsData(
                killTags.size,
                killCounts.size,
                sortedKills,
                acts,
                journalEntries.size,
                first,
                last,
                days)
        }
    }
}
