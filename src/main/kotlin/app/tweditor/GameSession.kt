package app.tweditor

import java.io.File

class GameSession(tmpDir: File) {
    val smmFile: File = File(tmpDir, "TWEditor.smm")
    val databaseFile: File = File(tmpDir, "TWEditor.ifo")
    val modFile: File = File(tmpDir, "TWEditor.mod")
    val playerFile: File = File(tmpDir, "TWEditor.player")

    var saveDatabase: SaveDatabase? = null
    var database: Database? = null
    var modDatabase: ResourceDatabase? = null
    var playerDatabase: Database? = null
    var smmDatabase: Database? = null

    private var smmName: String? = null
    private var modName: String? = null
    private var playerName: String? = null
    private var quests: MutableList<Quest>? = null
    private var dataModified = false
    private var dataChanging = false

    fun getSmmName(): String? = smmName
    fun setSmmName(smmName: String?) {
        this.smmName = smmName
    }

    fun getModName(): String? = modName
    fun setModName(modName: String?) {
        this.modName = modName
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
    }

    fun isDataChanging(): Boolean = dataChanging
    fun setDataChanging(dataChanging: Boolean) {
        this.dataChanging = dataChanging
    }

    fun close() {
        this.database = null
        this.modDatabase = null
        this.saveDatabase = null
        this.dataModified = false
    }
}
