package app.tweditor

import java.io.IOException
import javax.swing.SwingUtilities

class SaveFile(
    private val progressDialog: ProgressDialog,
    private val session: GameSession,
    private val environment: AppEnvironment
) : Thread() {
    private var saveSuccessful = false

    override fun run() {
        try {
            session.database!!.save()
            progressDialog.updateProgress(15)

            val resourceEntry = ResourceEntry("module.ifo", session.databaseFile)
            session.modDatabase!!.addEntry(resourceEntry)
            session.modDatabase!!.save()
            progressDialog.updateProgress(30)

            val modDatabase = ResourceDatabase(session.modDatabase!!.getPath())
            modDatabase.load()
            session.modDatabase = modDatabase
            progressDialog.updateProgress(45)

            session.saveDatabase!!.addEntry(session.getModName()!!, session.modFile)
            progressDialog.updateProgress(60)

            session.playerDatabase!!.save()
            session.saveDatabase!!.addEntry(session.getPlayerName()!!, session.playerFile)
            progressDialog.updateProgress(70)

            session.smmDatabase!!.save()
            session.saveDatabase!!.addEntry(session.getSmmName()!!, session.smmFile)
            progressDialog.updateProgress(80)

            session.writeSave()
            progressDialog.updateProgress(90)

            val saveDatabase = SaveDatabase(environment, session.saveDatabase!!.getPath())
            saveDatabase.load()
            session.saveDatabase = saveDatabase

            progressDialog.updateProgress(100)

            this.saveSuccessful = true
        } catch (exc: DBException) {
            Main.logException("Unable to update save database", exc)
        } catch (exc: IOException) {
            Main.logException("Unable to save file", exc)
        } catch (exc: Throwable) {
            Main.logException("Exception while saving file", exc)
        }

        SwingUtilities.invokeLater {
            progressDialog.closeDialog(saveSuccessful)
        }
    }
}
