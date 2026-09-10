package app.tweditor

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.regex.Pattern

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        try {
            val environment = AppEnvironment()
            val osName = System.getProperty("os.name").lowercase()
            val osMac = osName.startsWith("mac")
            val osLinux = osName.startsWith("linux")
            val osWin = osName.startsWith("windows")
            environment.fileSeparator = System.getProperty("file.separator")
            environment.lineSeparator = System.getProperty("line.separator")
            var tmpDir = System.getProperty("java.io.tmpdir")
            if (osLinux) {
                tmpDir = tmpDir + "/"
            }
            environment.tmpDir = tmpDir

            val option = System.getProperty("UseShellFolder")
            if (option != null && option == "0") {
                environment.useShellFolder = false
            }

            val appDataDirectory = File(
                System.getProperty("user.home") + environment.fileSeparator + "Application Data" + environment.fileSeparator + "ScripterRon"
            )
            if (!appDataDirectory.exists()) {
                appDataDirectory.mkdirs()
            }
            val propFile = File(appDataDirectory.getPath() + environment.fileSeparator + "TWEditor.properties")
            environment.propFile = propFile
            val properties = environment.properties
            if (propFile.exists()) {
                FileInputStream(propFile).use { input ->
                    properties.load(input)
                }
            }

            val explicitLanguage = System.getProperty("TW.language")?.toIntOrNull()
            val persistedLanguage = properties.getProperty("editor.language")?.toIntOrNull()

            var installPath: String? = System.getProperty("TW.install.path")
            var defaultLanguage = -1
            if (installPath == null || explicitLanguage == null) {
                when {
                    osMac -> {
                        installPath = "/Applications/The Witcher.app/Contents/Resources/drive_c/Program Files/The Witcher"
                        defaultLanguage = 3
                    }
                    osLinux -> {
                        val locateString = "locate dialog_3.tlk | grep \"Witcher.*Data\" | sed -e \"s|/Data/dialog_3.tlk||\""
                        val cmd = arrayOf("/bin/sh", "-c", locateString)
                        val process = Runtime.getRuntime().exec(cmd)
                        val reader = BufferedReader(InputStreamReader(process.inputStream))
                        installPath = reader.readLine()
                        reader.close()
                        defaultLanguage = 3
                    }
                    osWin -> {
                        val regString = "reg query \"HKLM\\Software\\CD Projekt Red\\The Witcher\" /reg:32"
                        val process = Runtime.getRuntime().exec(regString)
                        val streamReader = StreamReader(process.inputStream, environment.lineSeparator)
                        streamReader.start()
                        process.waitFor()
                        streamReader.join()

                        val pattern = Pattern.compile("\\s*(\\S*)\\s*(\\S*)\\s*(.*)")
                        var line: String?
                        while (streamReader.getLine().also { line = it } != null) {
                            val matcher = pattern.matcher(line)
                            if (matcher.matches() && matcher.groupCount() == 3 && matcher.group(2) == "REG_SZ") {
                                val keyName = matcher.group(1)
                                if (keyName == "InstallFolder" && installPath == null) {
                                    installPath = matcher.group(3)
                                } else if (keyName == "Language") {
                                    defaultLanguage = matcher.group(3).toInt()
                                }
                            }
                        }
                    }
                }
            }

            if (installPath == null) {
                throw IOException("Unable to locate The Witcher installation directory")
            }

            var languageID = explicitLanguage ?: persistedLanguage ?: defaultLanguage
            var stringsFile = File(
                installPath + environment.fileSeparator + "Data" + environment.fileSeparator +
                    "dialog_" + languageID + LanguageCatalog.LANGUAGE_FILE_SUFFIX
            )
            if (!stringsFile.exists() && explicitLanguage == null && persistedLanguage != null) {
                // The persisted choice is missing in this install (uninstalled
                // content, moved install); fall back to the install's default
                // language instead of failing the launch.
                if (defaultLanguage != -1 && defaultLanguage != languageID) {
                    languageID = defaultLanguage
                    stringsFile = File(
                        installPath + environment.fileSeparator + "Data" + environment.fileSeparator +
                            "dialog_" + languageID + LanguageCatalog.LANGUAGE_FILE_SUFFIX
                    )
                }
            }
            if (!stringsFile.exists()) {
                throw IOException("Localized strings database " + stringsFile.getPath() + " does not exist")
            }

            environment.installPath = installPath
            environment.languageID = languageID
            val installDataPath = installPath + environment.fileSeparator + "Data"
            environment.installDataPath = installDataPath
            var dirFile = File(installDataPath)
            if (!dirFile.exists()) {
                dirFile.mkdirs()
            }

            environment.stringsDatabase = StringsDatabase(stringsFile)

            var gamePath = System.getProperty("TW.data.path")
            if (gamePath == null) {
                val userSubPath = if (osMac) "com.cdprojektred.TheWitcher/The Witcher" else "The Witcher"
                val defaultDir = File(System.getProperty("user.home"), "Documents")
                gamePath = File(defaultDir, userSubPath).path
            }
            environment.gamePath = gamePath

            dirFile = File(gamePath + environment.fileSeparator + "saves")
            if (!dirFile.exists()) {
                dirFile.mkdirs()
            }

            val keyDatabase = KeyDatabase(environment, installDataPath + environment.fileSeparator + "main.key")
            environment.resourceFiles = resourceFilesFrom(keyDatabase)
            environment.resourceOrigins.clear()
            for ((name, resource) in environment.resourceFiles) {
                environment.resourceOrigins[name] = ResourceOrigin(
                    ResourceOriginKind.BASE_ARCHIVE,
                    keyDatabase.getName()
                )
            }
            environment.baseResourceFiles.clear()
            environment.baseResourceFiles.putAll(environment.resourceFiles)

            discoverPackedModules(environment, File(installDataPath, "modules"))
            processOverrides(environment, File(installDataPath))

            properties.setProperty("app.version", BuildInfo.VERSION)
            properties.setProperty("java.version", System.getProperty("java.version"))
            properties.setProperty("java.home", System.getProperty("java.home"))
            properties.setProperty("os.name", System.getProperty("os.name"))
            properties.setProperty("sun.os.patch.level", System.getProperty("sun.os.patch.level"))
            properties.setProperty("user.name", System.getProperty("user.name"))
            properties.setProperty("user.home", System.getProperty("user.home"))
            properties.setProperty("install.path", installPath)
            properties.setProperty("game.path", gamePath)
            properties.setProperty("temp.path", tmpDir)

            showComposeMainWindow(environment)
        } catch (exc: Throwable) {
            logException("Exception during program initialization", exc)
        }
    }

    /** Resource types used by the editor, including localized module strings. */
    val resourceExtensions: Set<String> = setOf(".2da", ".uti", ".dds", ".tga", ".tlk")

    fun resourceFilesFrom(keyDatabase: KeyDatabase): HashMap<String, Any> {
        val resourceFiles = HashMap<String, Any>(keyDatabase.getEntries().size)
        for (keyEntry in keyDatabase.getEntries()) {
            val name = keyEntry.fileName.lowercase()
            val sep = name.lastIndexOf('.')
            if (sep > 0 && name.substring(sep) in resourceExtensions) {
                resourceFiles[name] = keyEntry
            }
        }
        return resourceFiles
    }

    /**
     * Index packed ERF/MOD resources in stable path order. Later modules win
     * duplicate names, matching the game's layered loose-file behavior; the
     * origin map retains the winning module name for diagnostics and UI.
     */
    fun discoverPackedModules(environment: AppEnvironment, modulesDirectory: File): List<PackedModuleInfo> {
        val oldPackedNames = environment.resourceOrigins
            .filterValues { it.kind == ResourceOriginKind.PACKED_MODULE }
            .keys
        for (name in oldPackedNames) {
            environment.resourceFiles.remove(name)
            environment.resourceOrigins.remove(name)
        }
        environment.packedModules.clear()
        environment.moduleStringsDatabases.clear()
        if (!modulesDirectory.isDirectory) {
            return emptyList()
        }

        val moduleFiles = modulesDirectory.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in setOf("mod", "erf") }
            .toList()
            .sortedBy { it.relativeTo(modulesDirectory).path.lowercase() }

        for (moduleFile in moduleFiles) {
            val database = ResourceDatabase(moduleFile)
            try {
                database.load()
            } catch (_: Throwable) {
                // A non-ERF file with a mod-like suffix should not prevent the
                // base editor from opening. It simply is not an indexed module.
                continue
            }

            val moduleName = moduleFile.name
            val module = PackedModuleInfo(moduleName, moduleFile, database)
            environment.packedModules.add(module)
            for (entry in database.getEntries()) {
                val name = entry.getName().lowercase()
                val origin = ResourceOrigin(ResourceOriginKind.PACKED_MODULE, moduleName, moduleFile)
                val sep = name.lastIndexOf('.')
                if (sep <= 0) {
                    // Keep provenance for resources the editor does not yet
                    // interpret. They remain archive-owned and untouched.
                    environment.resourceOrigins[name] = origin
                    continue
                }
                // ResourceDatabase has already validated the resource type;
                // retain every recognized packed entry so unfamiliar module
                // data can be inspected/preserved without making it editable.
                environment.registerResource(
                    name,
                    entry,
                    origin
                )

                if (name.endsWith(".tlk")) {
                    try {
                        val strings = ResourceAccess.open(entry)?.use { input ->
                            StringsDatabase(input, moduleName + "/" + entry.getName())
                        }
                        if (strings != null) {
                            environment.moduleStringsDatabases.add(
                                ModuleStringsDatabase(moduleName, entry.getName(), strings)
                            )
                        }
                    } catch (_: Throwable) {
                        // Some modules carry non-dialog TLK-like resources;
                        // keep the resource visible but do not use invalid text.
                    }
                }
            }
        }
        return environment.packedModules.toList()
    }

    /** Index supported loose resources recursively, with overrides winning. */
    fun processOverrides(environment: AppEnvironment, dirFile: File) {
        processOverrides(environment, dirFile, dirFile)
    }

    private fun processOverrides(environment: AppEnvironment, dirFile: File, rootDirectory: File) {
        if (!dirFile.isDirectory) {
            return
        }
        val files = dirFile.listFiles()?.sortedBy { it.name.lowercase() } ?: return
        for (file in files) {
            if (file.isDirectory) {
                processOverrides(environment, file, rootDirectory)
            } else {
                val name = file.name.lowercase()
                val sep = name.lastIndexOf('.')
                if (sep > 0 && name.substring(sep) in resourceExtensions) {
                    val relativeName = runCatching { file.relativeTo(rootDirectory).path }.getOrDefault(file.name)
                    environment.registerResource(
                        name,
                        file,
                        ResourceOrigin(ResourceOriginKind.LOOSE_OVERRIDE, relativeName, file)
                    )
                    if (name == "dialog_${environment.languageID}.tlk") {
                        runCatching { StringsDatabase(file) }
                            .onSuccess { environment.stringsDatabase = it }
                    } else if (name.endsWith(".tlk")) {
                        runCatching {
                            FileInputStream(file).use { input ->
                                StringsDatabase(input, relativeName)
                            }
                        }.onSuccess { strings ->
                            environment.moduleStringsDatabases.add(
                                ModuleStringsDatabase(relativeName, file.name, strings)
                            )
                        }
                    }
                }
            }
        }
    }

    fun logException(text: String, exc: Throwable) {
        System.err.println(text + ": " + (exc.message ?: exc.javaClass.simpleName))
        exc.printStackTrace(System.err)
    }

    fun dumpData(text: String, data: ByteArray, offset: Int, length: Int) {
        println(text)

        for (i in 0 until length) {
            if (i % 32 == 0) {
                print(String.format(" %14X  ", i))
            } else if (i % 4 == 0) {
                print(" ")
            }
            print(String.format("%02X", data[offset + i]))

            if (i % 32 == 31) {
                println()
            }
        }
        if (length % 32 != 0) {
            println()
        }
    }
}
