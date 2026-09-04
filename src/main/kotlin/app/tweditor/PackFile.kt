package app.tweditor

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import javax.swing.SwingUtilities

class PackFile(
    private val progressDialog: ProgressDialog,
    private val session: GameSession,
    private val environment: AppEnvironment,
    private val extractDirectory: File
) : Thread() {
    private var saveSuccessful = false

    override fun run() {
        try {
            val entries = session.saveDatabase!!.entries
            for (entry in entries) {
                val file = File(extractDirectory.getPath() + environment.fileSeparator + entry.resourceName)
                if (!file.exists() || !file.isFile) {
                    throw IOException("Resource '" + file.getPath() + "' not found")
                }
                if (entry.isCompressed) {
                    entry.onDisk = false
                    entry.readFromFile(file)
                } else {
                    entry.setResourceFile(file, 0, file.length().toInt())
                }
            }

            session.writeSave()

            this.saveSuccessful = true
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
