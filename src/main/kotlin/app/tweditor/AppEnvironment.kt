package app.tweditor

import java.io.File
import java.io.FileOutputStream

class AppEnvironment {
    var fileSeparator: String = "/"
    var lineSeparator: String = "\n"
    var tmpDir: String = System.getProperty("java.io.tmpdir")
    var useShellFolder: Boolean = true
    var installPath: String? = null
    var installDataPath: String? = null
    var gamePath: String? = null
    var propFile: File? = null
    var properties: java.util.Properties = java.util.Properties()
    var stringsDatabase: StringsDatabase? = null
    var languageID: Int = -1
    var resourceFiles: MutableMap<String, Any> = HashMap()
    var itemTemplates: MutableList<ItemTemplate> = ArrayList()

    fun getString(stringRef: Int): String = stringsDatabase!!.getString(stringRef)

    fun getLabel(stringRef: Int): String = stringsDatabase!!.getLabel(stringRef)

    fun getHeading(stringRef: Int): String = stringsDatabase!!.getHeading(stringRef)

    fun saveProperties() {
        try {
            FileOutputStream(propFile).use { out ->
                properties.store(out, "TWEditor Properties")
            }
        } catch (exc: Throwable) {
            Main.logException("Exception while saving application properties", exc)
        }
    }
}
