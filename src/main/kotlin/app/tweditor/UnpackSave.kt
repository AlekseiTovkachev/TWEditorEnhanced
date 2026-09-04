package app.tweditor

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.swing.SwingUtilities

class UnpackSave(
    private val progressDialog: ProgressDialog,
    private val session: GameSession,
    private val environment: AppEnvironment,
    private val dirFile: File
) : Thread() {
    private var unpackSuccessful = false

    override fun run() {
        var file: File? = null
        var input: java.io.InputStream? = null
        var out: FileOutputStream? = null
        try {
            val entries = session.saveDatabase!!.entries
            val buffer = ByteArray(4096)
            val total = entries.size
            var processed = 0
            var currentProgress = 0
            for (entry in entries) {
                val resourceName = entry.resourceName
                file = File(dirFile.getPath() + environment.fileSeparator + resourceName)
                if (file.exists() && !file.delete()) {
                    throw IOException("Unable to delete '" + file.getName() + "'")
                }
                out = FileOutputStream(file)
                input = entry.getInputStream()
                var count: Int
                while (input!!.read(buffer).also { count = it } > 0) {
                    out.write(buffer, 0, count)
                }
                out.close()
                out = null
                input!!.close()
                input = null
                processed++
                val newProgress = processed * 100 / total
                if (newProgress > currentProgress + 9) {
                    currentProgress = newProgress
                    progressDialog.updateProgress(currentProgress)
                }
            }

            this.unpackSuccessful = true
        } catch (exc: IOException) {
            Main.logException("I/O error while unpacking save", exc)
        } catch (exc: Throwable) {
            Main.logException("Exception while unpacking save", exc)
        }

        try {
            input?.close()
            if (out != null) {
                out.close()
                if (file!!.exists()) {
                    file.delete()
                }
            }
        } catch (exc: IOException) {
        }

        SwingUtilities.invokeLater {
            progressDialog.closeDialog(unpackSuccessful)
        }
    }
}
