package app.tweditor

import java.io.File

class GameSession(tmpDir: File) {
    private val tempDirectory = tmpDir
    val smmFile: File = File(tmpDir, "TWEditor.smm")
    val databaseFile: File = File(tmpDir, "TWEditor.ifo")
    val modFile: File = File(tmpDir, "TWEditor.mod")
    val playerFile: File = File(tmpDir, "TWEditor.player")
    val questDatabaseFile: File = File(tmpDir, "TWEditor.qdb")

    var saveDatabase: SaveDatabase? = null
    var database: Database? = null
    var modDatabase: ResourceDatabase? = null
    var playerDatabase: Database? = null
    var smmDatabase: Database? = null

    private var journalData: JournalData? = null
    private var questDatabase: Database? = null
    private var questDBName: String? = null
    private var journalDirty = false

    private var smmName: String? = null
    private var modName: String? = null
    private var playerName: String? = null
    private var moduleOwnership = SaveModuleOwnership(null, emptyList())
    private var quests: MutableList<Quest>? = null
    private var dataModified = false
    private var dataChanging = false
    private var saveBackedUp = false
    private var draftDirty = false
    private var baseline: SessionBaseline? = null

    val validationGates: MutableList<ValidationGate> = ArrayList()

    fun getSmmName(): String? = smmName
    fun setSmmName(smmName: String?) {
        this.smmName = smmName
    }

    fun getModName(): String? = modName
    fun setModName(modName: String?) {
        this.modName = modName
    }

    fun getModuleOwnership(): SaveModuleOwnership = moduleOwnership
    fun setModuleOwnership(moduleOwnership: SaveModuleOwnership) {
        this.moduleOwnership = moduleOwnership
    }

    fun getPlayerName(): String? = playerName
    fun setPlayerName(playerName: String?) {
        this.playerName = playerName
    }

    fun getQuests(): MutableList<Quest>? = quests
    fun setQuests(quests: MutableList<Quest>?) {
        this.quests = quests
    }

    fun isDataModified(): Boolean = dataModified
    fun setDataModified(dataModified: Boolean) {
        this.dataModified = dataModified
        this.draftDirty = dataModified
    }

    /** Restores the two edit flags captured by an editor-command history entry. */
    fun restoreEditState(dataModified: Boolean, draftDirty: Boolean) {
        this.dataModified = dataModified
        this.draftDirty = draftDirty
    }

    fun isDraftDirty(): Boolean = draftDirty

    fun createBaseline() {
        val ifoTop = database?.getTopLevelStruct()
        val playerTop = playerDatabase?.getTopLevelStruct()
        val smmTop = smmDatabase?.getTopLevelStruct()
        val qdbTop = questDatabase?.getTopLevelStruct()
        val baselineDataModified = this.dataModified
        baseline = if (ifoTop != null && playerTop != null && smmTop != null) {
            SessionBaseline(
                ifoTop.clone(),
                playerTop.clone(),
                smmTop.clone(),
                qdbTop?.clone(),
                quests.orEmpty().map { it.snapshot() },
                journalDirty,
                baselineDataModified
            )
        } else {
            null
        }
        this.draftDirty = false
    }

    fun applyDraft() {
        createBaseline()
    }

    fun revertToBaseline(): Boolean {
        val snapshot = baseline ?: return false
        val ifoDatabase = database ?: return false
        ifoDatabase.setTopLevelStruct(snapshot.ifoTop.clone())
        playerDatabase!!.setTopLevelStruct(snapshot.playerTop.clone())
        smmDatabase!!.setTopLevelStruct(snapshot.smmTop.clone())
        val questDatabase = questDatabase
        if (questDatabase != null && snapshot.qdbTop != null) {
            questDatabase.setTopLevelStruct(snapshot.qdbTop.clone())
            setJournalData(JournalData(questDatabase.getTopLevelStruct()!!.getValue() as DBList))
        }
        val questsByName = quests.orEmpty().associateBy { it.getResourceName() }
        for (questSnapshot in snapshot.questSnapshots) {
            questsByName[questSnapshot.resourceName]?.restore(questSnapshot)
        }
        this.journalDirty = snapshot.journalDirty
        this.dataModified = snapshot.dataModified
        this.draftDirty = false
        return true
    }

    fun runValidation(): List<LocalizedText> {
        val problems = ArrayList<LocalizedText>()
        for (gate in validationGates) {
            problems.addAll(gate.validate(this))
        }
        return problems
    }

    fun isDataChanging(): Boolean = dataChanging
    fun setDataChanging(dataChanging: Boolean) {
        this.dataChanging = dataChanging
    }

    fun getJournalData(): JournalData? = journalData
    fun setJournalData(journalData: JournalData?) {
        this.journalData = journalData
    }

    fun getQuestDatabase(): Database? = questDatabase
    fun setQuestDatabase(questDatabase: Database?) {
        this.questDatabase = questDatabase
    }

    fun getQuestDBName(): String? = questDBName
    fun setQuestDBName(questDBName: String?) {
        this.questDBName = questDBName
    }

    fun isJournalDirty(): Boolean = journalDirty

    fun addJournalEntry(category: String, entryId: String, entryTod: Int = 0) {
        val questDatabase = requireNotNull(this.questDatabase) { "No quest database is open" }
        val topList = questDatabase.getTopLevelStruct()!!.getValue() as DBList
        var journalElement = topList.getElement("Journal")
        if (journalElement == null || journalElement.getType() != DBElement.LIST) {
            journalElement = DBElement(DBElement.LIST, 0, "Journal", DBList(questDatabase.environment, 4))
            topList.setElement("Journal", journalElement)
        }
        val journalList = journalElement.getValue() as DBList

        val newElement: DBElement = if (journalList.getElementCount() > 0) {
            journalList.getElement(0).clone()
        } else {
            val fields = DBList(questDatabase.environment, 4)
            fields.addElement(DBElement(DBElement.STRING, 0, "Entry", category + ":" + entryId))
            fields.addElement(DBElement(DBElement.DWORD, 0, "EntryCD", 0L))
            fields.addElement(DBElement(DBElement.DWORD, 0, "EntryTOD", 0L))
            fields.addElement(DBElement(DBElement.BYTE, 0, "EntryRead", 0))
            DBElement(DBElement.STRUCT, 0, "", fields)
        }
        val fields = newElement.getValue() as DBList
        fields.setString("Entry", category + ":" + entryId)
        fields.setInteger("EntryCD", 0, DBElement.DWORD)
        fields.setInteger("EntryTOD", entryTod, DBElement.DWORD)
        fields.setInteger("EntryRead", 0)
        journalList.addElement(newElement)
        refreshJournal(topList)
    }

    /** Runs a command-owned mutation against the Journal and refreshes its immutable view. */
    internal fun mutateJournal(mutator: (DBList) -> Unit) {
        val questDatabase = requireNotNull(this.questDatabase) { "No quest database is open" }
        val topList = questDatabase.getTopLevelStruct()!!.getValue() as DBList
        mutator(topList)
        refreshJournal(topList)
    }

    /** Restores a command snapshot without exposing raw journal structures to the UI. */
    internal fun restoreJournalSnapshot(topLevel: DBElement, journalDirty: Boolean) {
        val database = requireNotNull(questDatabase) { "No quest database is open" }
        database.setTopLevelStruct(topLevel.clone())
        val topList = database.getTopLevelStruct()!!.getValue() as DBList
        setJournalData(JournalData(topList))
        this.journalDirty = journalDirty
    }

    fun removeJournalEntries(entries: Collection<JournalEntry>) {
        if (entries.isEmpty()) {
            return
        }
        val questDatabase = requireNotNull(this.questDatabase) { "No quest database is open" }
        val topList = questDatabase.getTopLevelStruct()!!.getValue() as DBList
        val journalElement = topList.getElement("Journal") ?: return
        if (journalElement.getType() != DBElement.LIST) {
            return
        }
        val journalList = journalElement.getValue() as DBList
        val targets = HashSet<String>()
        for (entry in entries) {
            targets.add((entry.category + ":" + entry.entryId).lowercase())
        }
        val victims = ArrayList<DBElement>()
        for (element in journalList) {
            val fields = element.getValue() as DBList
            if (targets.contains(fields.getString("Entry").lowercase())) {
                victims.add(element)
            }
        }
        for (victim in victims) {
            journalList.removeElement(victim)
        }
        refreshJournal(topList)
    }

    private fun refreshJournal(topList: DBList) {
        this.journalData = JournalData(topList)
        this.journalDirty = true
        setDataModified(true)
    }

    fun writeSave() {
        val saveDatabase = requireNotNull(this.saveDatabase) { "No save file is open" }
        writeModifiedQuests()
        if (!saveBackedUp) {
            saveBackup().createBackup()
            this.saveBackedUp = true
        }
        saveDatabase.save()
        quests.orEmpty().forEach { it.setModified(false) }
    }

    private fun writeModifiedQuests() {
        val saveDatabase = requireNotNull(this.saveDatabase) { "No save file is open" }
        for ((index, quest) in quests.orEmpty().withIndex()) {
            if (!quest.isModified()) continue
            val qstFile = File(tempDirectory, "TWEditor-quest-$index.qst")
            quest.saveTo(qstFile)
            saveDatabase.addEntry(quest.getResourceName() + ".qst", qstFile)
        }
    }

    fun hasSaveBackup(): Boolean {
        return saveDatabase != null && saveBackup().hasBackup()
    }

    fun restoreSaveBackup() {
        saveBackup().restoreBackup()
    }

    private fun saveBackup(): SaveBackup {
        val saveDatabase = requireNotNull(this.saveDatabase) { "No save file is open" }
        return SaveBackup(saveDatabase.getFile())
    }

    fun close() {
        this.database = null
        this.modDatabase = null
        this.saveDatabase = null
        this.quests = null
        this.journalData = null
        this.questDatabase = null
        this.questDBName = null
        this.moduleOwnership = SaveModuleOwnership(null, emptyList())
        this.journalDirty = false
        this.dataModified = false
        this.draftDirty = false
        this.baseline = null
        this.saveBackedUp = false
    }
}

private class SessionBaseline(
    val ifoTop: DBElement,
    val playerTop: DBElement,
    val smmTop: DBElement,
    val qdbTop: DBElement?,
    val questSnapshots: List<QuestSnapshot>,
    val journalDirty: Boolean,
    val dataModified: Boolean
)
