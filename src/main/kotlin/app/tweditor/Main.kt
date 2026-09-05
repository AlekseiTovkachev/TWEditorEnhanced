package app.tweditor

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.regex.Pattern
import javax.swing.JFrame
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileSystemView

object Main {
    @JvmField
    var mainWindow: JFrame? = null

    private var deferredText: String? = null
    private var deferredException: Throwable? = null

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

            var installPath: String? = System.getProperty("TW.install.path")
            val languageString = System.getProperty("TW.language")
            var languageID = -1
            if (languageString != null) {
                languageID = languageString.toInt()
            }
            if (installPath == null || languageID == -1) {
                when {
                    osMac -> {
                        installPath = "/Applications/The Witcher.app/Contents/Resources/drive_c/Program Files/The Witcher"
                        languageID = 3
                    }
                    osLinux -> {
                        val locateString = "locate dialog_3.tlk | grep \"Witcher.*Data\" | sed -e \"s|/Data/dialog_3.tlk||\""
                        val cmd = arrayOf("/bin/sh", "-c", locateString)
                        val process = Runtime.getRuntime().exec(cmd)
                        val reader = BufferedReader(InputStreamReader(process.inputStream))
                        installPath = reader.readLine()
                        reader.close()
                        languageID = 3
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
                                } else if (keyName == "Language" && languageID == -1) {
                                    languageID = matcher.group(3).toInt()
                                }
                            }
                        }
                    }
                }

                if (installPath == null) {
                    throw IOException("Unable to locate The Witcher installation directory")
                }
                if (languageID == -1) {
                    throw IOException("Unable to determine the installed language")
                }
            }

            environment.installPath = installPath
            environment.languageID = languageID
            val installDataPath = installPath + environment.fileSeparator + "Data"
            environment.installDataPath = installDataPath
            var dirFile = File(installDataPath)
            if (!dirFile.exists()) {
                dirFile.mkdirs()
            }

            var gamePath = System.getProperty("TW.data.path")
            if (gamePath == null) {
                val defaultDir = FileSystemView.getFileSystemView().defaultDirectory
                val userSubPath = if (osMac) "com.cdprojektred.TheWitcher/The Witcher" else "The Witcher"
                gamePath = defaultDir.path + environment.fileSeparator + userSubPath
            }
            environment.gamePath = gamePath

            dirFile = File(gamePath + environment.fileSeparator + "saves")
            if (!dirFile.exists()) {
                dirFile.mkdirs()
            }

            val stringsFile = File(installDataPath + environment.fileSeparator + "dialog_" + languageID + ".tlk")
            if (!stringsFile.exists()) {
                throw IOException("Localized strings database " + stringsFile.getPath() + " does not exist")
            }
            environment.stringsDatabase = StringsDatabase(stringsFile)

            val keyDatabase = KeyDatabase(environment, installDataPath + environment.fileSeparator + "main.key")
            environment.resourceFiles = resourceFilesFrom(keyDatabase)

            processOverrides(environment, File(installDataPath))

            dirFile = File(System.getProperty("user.home") + environment.fileSeparator + "Application Data" + environment.fileSeparator + "ScripterRon")
            if (!dirFile.exists()) {
                dirFile.mkdirs()
            }
            val propFile = File(dirFile.getPath() + environment.fileSeparator + "TWEditor.properties")
            environment.propFile = propFile
            val properties = environment.properties
            if (propFile.exists()) {
                FileInputStream(propFile).use { input ->
                    properties.load(input)
                }
            }

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

            ThemeSelection.install()
            SwingUtilities.invokeLater {
                createAndShowGUI(environment)
            }
        } catch (exc: Throwable) {
            logException("Exception during program initialization", exc)
        }
    }

    /** Resource extensions the editor needs: data tables, item templates, and icon textures. */
    val resourceExtensions: Set<String> = setOf(".2da", ".uti", ".dds", ".tga")

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

    private fun processOverrides(environment: AppEnvironment, dirFile: File) {
        val files = dirFile.listFiles()
        for (file in files) {
            if (file.isDirectory) {
                processOverrides(environment, file)
            } else {
                val name = file.getName().lowercase()
                val sep = name.lastIndexOf('.')
                if (sep > 0 && name.substring(sep) in resourceExtensions) {
                    environment.resourceFiles[name] = file
                }
            }
        }
    }

    private fun createAndShowGUI(environment: AppEnvironment) {
        try {
            JFrame.setDefaultLookAndFeelDecorated(true)

            mainWindow = MainWindow(environment)
            mainWindow!!.pack()
            mainWindow!!.isVisible = true

            SwingUtilities.invokeLater {
                buildTemplates(environment)
            }
        } catch (exc: Throwable) {
            logException("Exception while initializing application window", exc)
        }
    }

    private fun buildTemplates(environment: AppEnvironment) {
        val dialog = ProgressDialog(mainWindow, "Loading item templates")
        val task = LoadTemplates(dialog, environment)
        task.start()
        dialog.showDialog()
    }

    fun logException(text: String, exc: Throwable) {
        System.runFinalization()
        System.gc()

        if (SwingUtilities.isEventDispatchThread()) {
            val string = StringBuilder(512)

            string.append("<html><b>")
            string.append(text)
            string.append("</b><br><br>")

            string.append("<b>")
            string.append(exc.toString())
            string.append("</b><br><br>")

            val trace = exc.stackTrace
            var count = 0
            for (element in trace) {
                string.append(element.toString())
                string.append("<br>")
                count++
                if (count == 25) {
                    break
                }
            }
            string.append("</html>")
            JOptionPane.showMessageDialog(mainWindow, string.toString(), "Error", 0)
        } else if (deferredException == null) {
            deferredText = text
            deferredException = exc
            try {
                SwingUtilities.invokeAndWait {
                    logException(deferredText!!, deferredException!!)
                }
            } catch (swingException: Throwable) {
                deferredException = null
                deferredText = null
            }
        }
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
