package app.tweditor

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.ArrayList
import javax.swing.SwingUtilities

class LoadTemplates(private val progressDialog: ProgressDialog, private val environment: AppEnvironment) : Thread() {
    private var success = false

    override fun run() {
        try {
            val mapSet: Set<Map.Entry<String, Any>> = environment.resourceFiles.entries
            val entryCount = mapSet.size
            environment.itemTemplates = ArrayList<ItemTemplate>(entryCount)
            var processedCount = 0
            var currentProgress = 0

            for (mapEntry in mapSet) {
                val template = processEntry(mapEntry.value, environment)
                if (template != null) {
                    environment.itemTemplates.add(template)
                }

                processedCount++
                val newProgress = processedCount * 100 / entryCount
                if (newProgress > currentProgress + 9) {
                    currentProgress = newProgress
                    progressDialog.updateProgress(currentProgress)
                }
            }

            environment.icons.primeTemplates(environment.itemTemplates)
            this.success = true
        } catch (exc: DBException) {
            Main.logException("Database error while loading inventory templates", exc)
        } catch (exc: IOException) {
            Main.logException("I/O error while loading inventory templates", exc)
        } catch (exc: Throwable) {
            Main.logException("Exception while loading inventory templates", exc)
        }

        SwingUtilities.invokeLater {
            progressDialog.closeDialog(success)
        }
    }

    companion object {
        /**
         * Parses one resource-map entry into an item template, or null when the
         * entry is not a named .uti. The app runs this on the loader thread;
         * tests call the whole scan directly.
         */
        fun processEntry(entryObject: Any, environment: AppEnvironment): ItemTemplate? {
            var resourceName: String? = null
            var input: InputStream? = null
            if (entryObject is File) {
                val name = entryObject.getName().lowercase()
                val sep = name.lastIndexOf('.')
                if (sep > 0 && name.substring(sep) == ".uti") {
                    resourceName = name.substring(0, sep)
                    input = FileInputStream(entryObject)
                }
            } else if (entryObject is KeyEntry) {
                val name = entryObject.fileName.lowercase()
                val sep = name.lastIndexOf('.')
                if (sep > 0 && name.substring(sep) == ".uti") {
                    resourceName = entryObject.resourceName
                    input = entryObject.getInputStream()
                }
            }

            if (input == null) {
                return null
            }

            val database = Database(environment)
            database.load(input)
            input.close()
            val fieldList = database.getTopLevelStruct()!!.getValue() as DBList
            val itemName = fieldList.getString("LocalizedName")
            // Description is often empty (books, gems); only a name is required.
            if (itemName.isEmpty()) {
                return null
            }
            val resourceElement = DBElement(11, 0, "TemplateResRef", resourceName)
            fieldList.setElement("TemplateResRef", resourceElement)
            return ItemTemplate(fieldList)
        }

        /** The app's template scan, callable from tests (no progress dialog). */
        fun loadItemTemplates(environment: AppEnvironment) {
            environment.itemTemplates = ArrayList(environment.resourceFiles.size)
            for (mapEntry in environment.resourceFiles.entries) {
                val template = processEntry(mapEntry.value, environment)
                if (template != null) {
                    environment.itemTemplates.add(template)
                }
            }
            environment.icons.primeTemplates(environment.itemTemplates)
        }
    }
}
