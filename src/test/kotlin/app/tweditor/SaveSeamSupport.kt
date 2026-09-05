package app.tweditor

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.CRC32

object SaveSeamSupport {
    const val FIXTURE_RESOURCE = "/saves/000007 - Территория Каэр Морхен-000.TheWitcherSave"
    const val EXPECTED_SAVE_NAME = "000007 - Территория Каэр Морхен-000"

    fun createEnvironment(): AppEnvironment {
        val environment = AppEnvironment()
        environment.fileSeparator = System.getProperty("file.separator")
        environment.languageID = 3
        return environment
    }

    fun copyFixtureTo(directory: Path): File {
        val url = requireNotNull(SaveSeamSupport::class.java.getResource(FIXTURE_RESOURCE)) {
            "fixture not on classpath: " + FIXTURE_RESOURCE
        }
        val target = directory.resolve(FIXTURE_RESOURCE.substring(FIXTURE_RESOURCE.lastIndexOf('/') + 1))
        SaveSeamSupport::class.java.getResourceAsStream(FIXTURE_RESOURCE)!!.use { input ->
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
        }
        return target.toFile()
    }

    /**
     * The real saves the owner drops into the gitignored .local-saves directory
     * (sorted by file name); empty when there are none.
     */
    fun localSaves(): List<File> {
        val savesDir = Path.of(System.getProperty("tweditor.localSaves", ".local-saves")).toFile()
        val saves = savesDir.listFiles { _, name -> name.endsWith(".TheWitcherSave") }
        return saves?.sortedBy { it.getName() } ?: emptyList()
    }

    /**
     * A throwaway copy of a local save: golden round-trips write/reload through
     * the copy so the owner's files are never mutated by a test run.
     */
    fun tempCopy(save: File): File {
        val work = Files.createTempDirectory("tweditor-save")
        val copy = work.resolve(save.getName()).toFile()
        Files.copy(save.toPath(), copy.toPath())
        return copy
    }

    fun load(environment: AppEnvironment, saveFile: File, workDir: Path): Loaded {
        return loadInto(environment, saveFile, workDir, GameSession(workDir.toFile()))
    }

    fun loadInto(environment: AppEnvironment, saveFile: File, workDir: Path, session: GameSession): Loaded {
        val loaded = Loaded(workDir, environment, session)
        val saveDatabase = SaveDatabase(environment, saveFile)
        saveDatabase.load()
        loaded.saveDatabase = saveDatabase
        val saveName = saveDatabase.getName()
        saveDatabase.setSavePrefix(saveName + environment.fileSeparator)
        val smmName = "save_" + saveName.substring(0, 6) + ".smm"
        loaded.smmName = smmName

        val smmFile = workDir.resolve("work-" + saveName.substring(0, 6) + ".smm").toFile()
        loaded.smmFile = smmFile
        extract(saveDatabase.getEntry(smmName)!!, smmFile)
        val smmDatabase = Database(environment, smmFile)
        smmDatabase.load()
        loaded.smmDatabase = smmDatabase
        val smmList = smmDatabase.getTopLevelStruct()!!.getValue() as DBList
        val startingMod = smmList.getString("StartingMod")
        val questBaseList = smmList.getElement("QuestBase_list")!!.getValue() as DBList
        val questBaseFields = questBaseList.getElement(0).getValue() as DBList
        val questDBName = questBaseFields.getString("QuestBase")
        loaded.questDBName = questDBName

        val modName = startingMod + ".sav"
        loaded.modName = modName
        val modFile = workDir.resolve("work-" + saveName.substring(0, 6) + ".sav").toFile()
        loaded.modFile = modFile
        extract(saveDatabase.getEntry(modName)!!, modFile)
        val modDatabase = ResourceDatabase(modFile)
        modDatabase.load()
        loaded.modDatabase = modDatabase

        val ifoFile = workDir.resolve("work-" + saveName.substring(0, 6) + ".ifo").toFile()
        loaded.ifoFile = ifoFile
        modDatabase.getEntry("module.ifo")!!.getInputStream().use { input ->
            Files.copy(input, ifoFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        val ifoDatabase = Database(environment, ifoFile)
        ifoDatabase.load()
        loaded.ifoDatabase = ifoDatabase
        val ifoList = ifoDatabase.getTopLevelStruct()!!.getValue() as DBList
        val playerList = ifoList.getElement("Mod_PlayerList")!!.getValue() as DBList
        loaded.player = playerList.getElement(0).getValue() as DBList

        val qdbEntry = saveDatabase.getEntry(questDBName + ".qdb")!!
        val questDatabase = Database(environment)
        qdbEntry.getInputStream().use { input ->
            questDatabase.load(input)
        }
        val questDBList = questDatabase.getTopLevelStruct()!!.getValue() as DBList
        loaded.questCount = (questDBList.getElement("Quests")!!.getValue() as DBList).getElementCount()
        loaded.session.setJournalData(JournalData(questDBList))
        loaded.session.setQuestDatabase(questDatabase)
        loaded.session.setQuestDBName(questDBName)
        val sessionQuests = ArrayList<Quest>()
        for (quest in questRecords(loaded).values) {
            if (quest.questName.isNotEmpty()) {
                sessionQuests.add(quest)
            }
        }
        loaded.session.setQuests(sessionQuests)

        val playerFile = workDir.resolve("work-" + saveName.substring(0, 6) + ".utc").toFile()
        loaded.playerFile = playerFile
        extract(saveDatabase.getEntry("player.utc")!!, playerFile)
        val playerDatabase = Database(environment, playerFile)
        playerDatabase.load()
        loaded.playerDatabase = playerDatabase

        loaded.session.saveDatabase = saveDatabase
        loaded.session.database = ifoDatabase
        loaded.session.modDatabase = modDatabase
        loaded.session.playerDatabase = playerDatabase
        loaded.session.smmDatabase = smmDatabase
        loaded.session.setSmmName(smmName)
        loaded.session.setModName(modName)

        return loaded
    }

    fun questRecords(loaded: Loaded): Map<String, Quest> {
        val saveDatabase = loaded.saveDatabase!!
        val qdbEntry = saveDatabase.getEntry(loaded.questDBName + ".qdb")!!
        val questDatabase = Database(loaded.environment)
        qdbEntry.getInputStream().use { input ->
            questDatabase.load(input)
        }
        val questDBList = questDatabase.getTopLevelStruct()!!.getValue() as DBList
        val quests = questDBList.getElement("Quests")!!.getValue() as DBList
        val records = LinkedHashMap<String, Quest>()
        for (questElement in quests) {
            val fields = questElement.getValue() as DBList
            val resourceName = fields.getString("File")
            val qstEntry = saveDatabase.getEntry(resourceName + ".qst")!!
            val qstDatabase = Database(loaded.environment)
            qstEntry.getInputStream().use { input ->
                qstDatabase.load(input)
            }
            records[resourceName] = Quest(resourceName, qstDatabase.getTopLevelStruct()!!)
        }
        return records
    }

    /**
     * Save-as at the seam: rebinds the session to a working copy at [target]
     * (the same SavePipeline.rebindToCopy path the app's Save As command uses)
     * and then runs the normal write pipeline into that copy. The original
     * file on disk is never touched.
     */
    fun saveAs(loaded: Loaded, target: File) {
        SavePipeline.rebindToCopy(loaded.session, loaded.environment, target)
        loaded.saveDatabase = loaded.session.saveDatabase
        loaded.smmName = loaded.session.getSmmName()
        save(loaded)
    }

    fun save(loaded: Loaded) {
        val ifoDatabase = loaded.ifoDatabase!!
        val modDatabaseOld = loaded.modDatabase!!
        val saveDatabaseOld = loaded.saveDatabase!!
        val playerDatabase = loaded.playerDatabase!!
        val smmDatabase = loaded.smmDatabase!!

        ifoDatabase.save()
        val resourceEntry = ResourceEntry("module.ifo", loaded.ifoFile!!)
        modDatabaseOld.addEntry(resourceEntry)
        modDatabaseOld.save()

        val modDatabase = ResourceDatabase(modDatabaseOld.getPath())
        modDatabase.load()
        loaded.modDatabase = modDatabase

        saveDatabaseOld.addEntry(loaded.modName!!, loaded.modFile!!)
        playerDatabase.save()
        saveDatabaseOld.addEntry("player.utc", loaded.playerFile!!)
        smmDatabase.save()
        saveDatabaseOld.addEntry(loaded.smmName!!, loaded.smmFile!!)
        if (loaded.session.isJournalDirty()) {
            FileOutputStream(loaded.session.questDatabaseFile).use { out ->
                loaded.session.getQuestDatabase()!!.save(out)
            }
            saveDatabaseOld.addEntry(loaded.session.getQuestDBName()!! + ".qdb", loaded.session.questDatabaseFile)
        }
        loaded.session.writeSave()

        val saveDatabase = SaveDatabase(loaded.environment, saveDatabaseOld.getPath())
        saveDatabase.load()
        loaded.saveDatabase = saveDatabase
        loaded.session.saveDatabase = saveDatabase
    }

    fun changedEntries(before: Map<String, Long>, after: Map<String, Long>): Set<String> {
        val changed = HashSet<String>()
        for (name in before.keys) {
            if (before[name] != after[name]) {
                changed.add(name)
            }
        }
        return changed
    }

    fun entryDigests(saveDatabase: SaveDatabase): Map<String, Long> {
        val digests = LinkedHashMap<String, Long>()
        val crc = CRC32()
        for (entry in saveDatabase.entries) {
            crc.reset()
            entry.getInputStream().use { input ->
                val buffer = ByteArray(8192)
                var count: Int
                while (input.read(buffer).also { count = it } > 0) {
                    crc.update(buffer, 0, count)
                }
            }
            digests[entry.resourceName] = crc.getValue()
        }
        return digests
    }

    private fun extract(entry: SaveEntry, target: File) {
        entry.getInputStream().use { input ->
            FileOutputStream(target).use { out ->
                val buffer = ByteArray(4096)
                var count: Int
                while (input.read(buffer).also { count = it } > 0) {
                    out.write(buffer, 0, count)
                }
            }
        }
    }

    class Loaded(workDir: Path, val environment: AppEnvironment, val session: GameSession = GameSession(workDir.toFile())) {
        var saveDatabase: SaveDatabase? = null
        var smmDatabase: Database? = null
        var modDatabase: ResourceDatabase? = null
        var ifoDatabase: Database? = null
        var playerDatabase: Database? = null
        var player: DBList? = null
        var questCount = 0
        var questDBName: String? = null
        var smmName: String? = null
        var modName: String? = null
        var smmFile: File? = null
        var modFile: File? = null
        var ifoFile: File? = null
        var playerFile: File? = null
    }
}
