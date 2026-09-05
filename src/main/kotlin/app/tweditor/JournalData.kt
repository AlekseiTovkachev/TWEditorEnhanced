package app.tweditor

class JournalData(topList: DBList) {
    val storyPhase: String
    val entries: MutableList<JournalEntry> = ArrayList()
    val trackedQuests: MutableList<String> = ArrayList()

    init {
        this.storyPhase = topList.getString("StoryPhase")

        val journalElement = topList.getElement("Journal")
        if (journalElement != null && journalElement.getType() == 15) {
            val journalList = journalElement.getValue() as DBList
            for (element in journalList) {
                val fields = element.getValue() as DBList
                val entry = fields.getString("Entry")
                if (entry.isEmpty()) {
                    continue
                }
                val sep = entry.indexOf(':')
                val category = if (sep > 0) entry.substring(0, sep) else ""
                val entryId = if (sep > 0) entry.substring(sep + 1) else entry
                this.entries.add(
                    JournalEntry(
                        category.lowercase(), entryId, fields.getInteger("EntryRead") == 1,
                        fields.getInteger("EntryTOD").toLong()))
            }
        }

        val trackedElement = topList.getElement("JournalQ")
        if (trackedElement != null && trackedElement.getType() == 15) {
            val trackedList = trackedElement.getValue() as DBList
            for (element in trackedList) {
                val fields = element.getValue() as DBList
                val questName = fields.getString("UnReadQName")
                if (questName.isNotEmpty()) {
                    this.trackedQuests.add(questName)
                }
            }
        }
    }

    fun entriesInCategory(category: String): List<JournalEntry> {
        val result = ArrayList<JournalEntry>()
        for (entry in entries) {
            if (entry.category == category.lowercase()) {
                result.add(entry)
            }
        }
        return result
    }
}

class JournalEntry(val category: String, val entryId: String, val isRead: Boolean, val timeOfDay: Long = 0)
