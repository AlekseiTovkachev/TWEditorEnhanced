package app.tweditor

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import javax.swing.SwingUtilities

class LoadFile(
    private val progressDialog: ProgressDialog,
    private val session: GameSession,
    private val environment: AppEnvironment,
    private var file: File
) : Thread() {
    private var loadSuccessful = false

    override fun run() {
        var input: InputStream? = null
        var out: FileOutputStream? = null
        try {
            val saveDatabase = SaveDatabase(environment, this.file)
            saveDatabase.load()
            progressDialog.updateProgress(25)
            val saveName = saveDatabase.getName()
            saveDatabase.setSavePrefix(saveName + environment.fileSeparator)

            val sep = saveName.indexOf(' ')
            if (sep != 6 || !Character.isDigit(saveName[0])) {
                throw DBException("Save name is not formatted correctly")
            }
            session.setSmmName("save_" + saveName.substring(0, 6) + ".smm")
            var saveEntry = saveDatabase.getEntry(session.getSmmName()!!)
            if (saveEntry == null) {
                throw DBException("Save does not contain " + session.getSmmName())
            }
            input = saveEntry.getInputStream()
            if (session.smmFile.exists()) {
                session.smmFile.delete()
            }
            var buffer = ByteArray(4096)
            out = FileOutputStream(session.smmFile)
            var count: Int
            while (input!!.read(buffer).also { count = it } > 0) {
                out!!.write(buffer, 0, count)
            }
            input!!.close()
            input = null
            out!!.close()
            out = null
            val smmDatabase = Database(environment, session.smmFile)
            smmDatabase.load()
            progressDialog.updateProgress(35)

            var list = smmDatabase.getTopLevelStruct()!!.getValue() as DBList
            val startingMod = list.getString("StartingMod")
            if (startingMod.isEmpty()) {
                throw DBException("StartingMod not found in SMM database")
            }
            var element = list.getElement("QuestBase_list")
            if (element == null || element.getType() != 15) {
                throw DBException("QuestBaseList not found in SMM database")
            }
            var questList = element.getValue() as DBList
            if (questList.getElementCount() == 0) {
                throw DBException("No quest list found in SMM database")
            }
            var fieldList = questList.getElement(0).getValue() as DBList
            val questDBName = fieldList.getString("QuestBase")
            if (questDBName.isEmpty()) {
                throw DBException("No quest database name found in SMM database")
            }

            session.setModName(startingMod + ".sav")
            saveEntry = saveDatabase.getEntry(session.getModName()!!)
            if (saveEntry == null) {
                throw DBException("Save does not contain " + session.getModName())
            }
            input = saveEntry.getInputStream()
            if (session.modFile.exists()) {
                session.modFile.delete()
            }

            buffer = ByteArray(4096)
            out = FileOutputStream(session.modFile)
            while (input!!.read(buffer).also { count = it } > 0) {
                out!!.write(buffer, 0, count)
            }
            input!!.close()
            input = null
            out!!.close()
            out = null
            progressDialog.updateProgress(50)

            val modDatabase = ResourceDatabase(session.modFile)
            modDatabase.load()
            progressDialog.updateProgress(60)

            val resourceEntry = modDatabase.getEntry("module.ifo")
            if (resourceEntry == null) {
                throw DBException("Save does not contain module.ifo")
            }
            input = resourceEntry.getInputStream()
            if (session.databaseFile.exists()) {
                session.databaseFile.delete()
            }
            out = FileOutputStream(session.databaseFile)
            while (input!!.read(buffer).also { count = it } > 0) {
                out!!.write(buffer, 0, count)
            }
            input!!.close()
            input = null
            out!!.close()
            out = null
            progressDialog.updateProgress(75)

            val database = Database(environment, session.databaseFile)
            database.load()
            list = database.getTopLevelStruct()!!.getValue() as DBList
            element = list.getElement("Mod_PlayerList")
            if (element == null || element.getType() != 15) {
                throw DBException("module.ifo does not contain Mod_PlayerList")
            }
            list = element.getValue() as DBList
            if (list.getElementCount() == 0) {
                throw DBException("Mod_PlayerList is empty")
            }
            progressDialog.updateProgress(80)

            var fileName = questDBName + ".qdb"
            saveEntry = saveDatabase.getEntry(fileName)
            if (saveEntry == null) {
                throw DBException("Save does not contain " + fileName)
            }
            input = saveEntry.getInputStream()
            var questDatabase = Database(environment)
            questDatabase.load(input)
            input!!.close()
            input = null
            list = questDatabase.getTopLevelStruct()!!.getValue() as DBList
            element = list.getElement("Quests")
            if (element == null || element.getType() != 15) {
                throw DBException("Quests not found in quest database")
            }
            questList = element.getValue() as DBList
            progressDialog.updateProgress(85)

            count = questList.getElementCount()
            val quests = ArrayList<Quest>(count)
            for (questElement in questList) {
                fieldList = questElement.getValue() as DBList
                val resourceName = fieldList.getString("File")
                fileName = resourceName + ".qst"
                saveEntry = saveDatabase.getEntry(fileName)
                if (saveEntry == null) {
                    throw DBException("Save does not contain " + fileName)
                }
                input = saveEntry.getInputStream()
                questDatabase = Database(environment)
                questDatabase.load(input)
                input!!.close()
                input = null
                val quest = Quest(resourceName, questDatabase.getTopLevelStruct()!!)
                if (quest.questName.isNotEmpty()) {
                    quests.add(quest)
                }
            }
            session.setQuests(quests)

            session.setPlayerName("player.utc")
            saveEntry = saveDatabase.getEntry(session.getPlayerName()!!)
            if (saveEntry == null) {
                throw DBException("Save does not contain " + session.getPlayerName())
            }
            input = saveEntry.getInputStream()
            if (session.playerFile.exists()) {
                session.playerFile.delete()
            }
            out = FileOutputStream(session.playerFile)
            while (input!!.read(buffer).also { count = it } > 0) {
                out!!.write(buffer, 0, count)
            }
            input!!.close()
            input = null
            out!!.close()
            out = null

            val playerDatabase = Database(environment, session.playerFile)
            playerDatabase.load()

            progressDialog.updateProgress(100)

            session.saveDatabase = saveDatabase
            session.modDatabase = modDatabase
            session.database = database
            session.playerDatabase = playerDatabase
            session.smmDatabase = smmDatabase
            this.loadSuccessful = true
        } catch (exc: DBException) {
            Main.logException("Save file structure is not valid", exc)
        } catch (exc: IOException) {
            Main.logException("Unable to read save file", exc)
        } catch (exc: Throwable) {
            Main.logException("Exception while opening save file", exc)
        }

        try {
            input?.close()
            out?.close()
        } catch (exc: IOException) {
        }

        SwingUtilities.invokeLater {
            progressDialog.closeDialog(loadSuccessful)
        }
    }
}
