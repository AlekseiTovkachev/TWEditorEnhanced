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
    var resourceOrigins: MutableMap<String, ResourceOrigin> = HashMap()

    /**
     * Main-game-archive resources, kept separate because loose overrides
     * (user mods) replace entries in [resourceFiles] by name; the editor shell
     * wants the vanilla art regardless of installed mods.
     */
    val baseResourceFiles: MutableMap<String, Any> = HashMap()
    val packedModules: MutableList<PackedModuleInfo> = ArrayList()
    val moduleStringsDatabases: MutableList<ModuleStringsDatabase> = ArrayList()
    var itemTemplates: MutableList<ItemTemplate> = ArrayList()
    val icons: IconLibrary by lazy { IconLibrary(this) }

    fun registerResource(resourceName: String, resource: Any, origin: ResourceOrigin) {
        val name = resourceName.lowercase()
        resourceFiles[name] = resource
        resourceOrigins[name] = origin
    }

    fun resourceOrigin(resourceName: String): ResourceOrigin? = resourceOrigins[resourceName.lowercase()]

    /**
     * Switches the whole editor's display language: resolves the content TLK
     * for the new id (a loose Data\dialog_<id>.tlk wins, else the archive
     * entry), swaps the strings database and persists the choice for the next
     * launch. Throws IOException when the language is not available.
     */
    fun setLanguage(language: EditorLanguage) {
        val dataPath = installDataPath
            ?: throw java.io.IOException("Install data path is not configured")
        val suffix = LanguageCatalog.LANGUAGE_FILE_SUFFIX
        val name = "dialog_" + language.id + suffix
        val resolved = resolveContentTlk(File(dataPath, name), language.id)
            ?: resolveContentTlk(resourceFiles[name], language.id)
            ?: throw java.io.IOException("Localized strings database $name (${language.displayName}) is not available")
        runCatching { stringsDatabase?.close() }
        stringsDatabase = resolved
        languageID = language.id
        properties.setProperty("editor.language", language.id.toString())
        saveProperties()
    }

    private fun resolveContentTlk(source: Any?, languageId: Int): StringsDatabase? {
        val input = ResourceAccess.open(source ?: return null) ?: return null
        return try {
            StringsDatabase(input, "dialog_" + languageId + LanguageCatalog.LANGUAGE_FILE_SUFFIX)
        } catch (_: Throwable) {
            null
        }
    }

    fun owningModule(resourceName: String): String? = resourceOrigin(resourceName)?.moduleName

    /**
     * Base strings remain the default layer. Module strings are a fallback for
     * references that are outside the base TLK, which is how module-local
     * LocalizedName references are represented in the game data.
     */
    fun getString(stringRef: Int, preferredModule: String? = null): String {
        val preferred = preferredModule?.lowercase()
        if (preferred != null) {
            val preferredString = moduleStringsDatabases
                .firstOrNull { it.moduleName.lowercase() == preferred }
                ?.database
                ?.getString(stringRef)
                .orEmpty()
            if (preferredString.isNotEmpty()) {
                return preferredString
            }
        }

        val baseString = stringsDatabase?.getString(stringRef).orEmpty()
        if (baseString.isNotEmpty()) {
            return baseString
        }

        for (moduleStrings in moduleStringsDatabases) {
            val moduleString = moduleStrings.database.getString(stringRef)
            if (moduleString.isNotEmpty()) {
                return moduleString
            }
        }
        return ""
    }

    fun getLabel(stringRef: Int): String = stringsDatabase!!.getLabel(stringRef)

    fun getHeading(stringRef: Int): String = stringsDatabase!!.getHeading(stringRef)

    fun saveProperties() {
        val file = propFile ?: return
        try {
            FileOutputStream(file).use { out ->
                properties.store(out, "TWEditor Properties")
            }
        } catch (exc: Throwable) {
            Main.logException("Exception while saving application properties", exc)
        }
    }
}
