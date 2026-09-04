package app.tweditor

import java.awt.Dimension
import java.awt.Point
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.io.IOException
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTabbedPane

class MainWindow(val environment: AppEnvironment) : JFrame("The Witcher Save Editor"), ActionListener {
    private var windowMinimized = false
    private var titleModified = false

    @JvmField
    val session = GameSession(File(environment.tmpDir))

    @JvmField
    val tabbedPane = JTabbedPane()

    @JvmField
    val statsPanel = StatsPanel(session)

    @JvmField
    val attributesPanel = AttributesPanel(session, environment)

    @JvmField
    val signsPanel = SignsPanel(session, environment)

    @JvmField
    val stylesPanel = StylesPanel(session, environment)

    @JvmField
    val equipPanel = EquipPanel(session, environment)

    @JvmField
    val inventoryPanel = InventoryPanel(session, environment)

    @JvmField
    val questsPanel = QuestsPanel(session, environment)

    @JvmField
    val difficultyPanel = DifficultyPanel(session, environment)

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE

        var propValue = environment.properties.getProperty("window.main.position")
        if (propValue != null) {
            val sep = propValue.indexOf(',')
            val frameX = propValue.substring(0, sep).toInt()
            val frameY = propValue.substring(sep + 1).toInt()
            setLocation(frameX, frameY)
        }

        var frameWidth = 800
        var frameHeight = 600
        propValue = environment.properties.getProperty("window.main.size")
        if (propValue != null) {
            val sep = propValue.indexOf(',')
            frameWidth = propValue.substring(0, sep).toInt().coerceAtLeast(frameWidth)
            frameHeight = propValue.substring(sep + 1).toInt().coerceAtLeast(frameHeight)
        }

        preferredSize = Dimension(frameWidth, frameHeight)

        val menuBar = JMenuBar()
        menuBar.isOpaque = true

        var menu = JMenu("File")
        menu.mnemonic = 70

        var menuItem = JMenuItem("Open")
        menuItem.actionCommand = "open"
        menuItem.addActionListener(this)
        menu.add(menuItem)

        menuItem = JMenuItem("Save")
        menuItem.actionCommand = "save"
        menuItem.addActionListener(this)
        menu.add(menuItem)

        menuItem = JMenuItem("Close")
        menuItem.actionCommand = "close"
        menuItem.addActionListener(this)
        menu.add(menuItem)

        menu.addSeparator()

        menuItem = JMenuItem("Exit")
        menuItem.actionCommand = "exit"
        menuItem.addActionListener(this)
        menu.add(menuItem)

        menuBar.add(menu)

        menu = JMenu("Actions")
        menu.mnemonic = 65

        menuItem = JMenuItem("Unpack Save")
        menuItem.actionCommand = "unpack save"
        menuItem.addActionListener(this)
        menu.add(menuItem)

        menuItem = JMenuItem("Repack Save")
        menuItem.actionCommand = "repack save"
        menuItem.addActionListener(this)
        menu.add(menuItem)

        menuBar.add(menu)

        menu = JMenu("Help")
        menu.mnemonic = 72

        menuItem = JMenuItem("About")
        menuItem.actionCommand = "about"
        menuItem.addActionListener(this)
        menu.add(menuItem)

        menuBar.add(menu)

        jMenuBar = menuBar

        tabbedPane.isVisible = false
        contentPane = tabbedPane

        var panel = JPanel()
        panel.add(statsPanel)
        tabbedPane.addTab("Stats", panel)

        panel = JPanel()
        panel.add(attributesPanel)
        tabbedPane.addTab("Attributes", panel)

        panel = JPanel()
        panel.add(signsPanel)
        tabbedPane.addTab("Signs", panel)

        panel = JPanel()
        panel.add(stylesPanel)
        tabbedPane.addTab("Styles", panel)

        panel = JPanel()
        panel.add(equipPanel)
        tabbedPane.addTab("Equipment", panel)

        panel = JPanel()
        panel.add(inventoryPanel)
        tabbedPane.addTab("Inventory", panel)

        panel = JPanel()
        panel.add(questsPanel)
        tabbedPane.addTab("Quests", panel)

        panel = JPanel()
        panel.add(difficultyPanel)
        tabbedPane.addTab("Difficulty", panel)

        addWindowListener(ApplicationWindowListener())
    }

    override fun setTitle(title: String?) {
        if (title != null) {
            super.setTitle(title)
            titleModified = false
        } else if (session.saveDatabase == null) {
            super.setTitle("The Witcher Save Editor")
            titleModified = false
        } else if (session.isDataModified() && !titleModified) {
            super.setTitle("The Witcher Save Editor - " + session.saveDatabase!!.getName() + "*")
            titleModified = true
        } else if (!session.isDataModified() && titleModified) {
            super.setTitle("The Witcher Save Editor - " + session.saveDatabase!!.getName())
            titleModified = false
        }
    }

    override fun actionPerformed(ae: ActionEvent?) {
        try {
            val action = ae!!.actionCommand
            if (action == "open") {
                openFile()
                if (session.saveDatabase != null) {
                    setTitle("The Witcher Save Editor - " + session.saveDatabase!!.getName())
                } else {
                    setTitle(null)
                }
            } else if (action == "about") {
                aboutProgram()
            } else if (action == "exit") {
                exitProgram()
            } else if (session.saveDatabase == null) {
                JOptionPane.showMessageDialog(this, "No save file is open", "No Save", 0)
            } else if (action == "save") {
                saveFile()
                setTitle(null)
            } else if (action == "close") {
                closeFile()
                setTitle(null)
            } else if (action == "unpack save") {
                unpackSave()
            } else if (action == "repack save") {
                packSave()
                setTitle(null)
            }
        } catch (exc: Throwable) {
            Main.logException("Exception while processing action event", exc)
        }
    }

    private fun openFile() {
        if (!closeFile()) {
            return
        }

        val currentDirectory = environment.properties.getProperty("current.directory")
        val chooser: JFileChooser
        if (currentDirectory != null) {
            val dirFile = File(currentDirectory)
            chooser = if (dirFile.exists() && dirFile.isDirectory) {
                JFileChooser(dirFile)
            } else {
                JFileChooser(environment.gamePath + environment.fileSeparator + "saves")
            }
        } else {
            chooser = JFileChooser(environment.gamePath + environment.fileSeparator + "saves")
        }

        chooser.putClientProperty("FileChooser.useShellFolder", java.lang.Boolean.valueOf(environment.isUseShellFolder))
        chooser.dialogTitle = "Select Save File"
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return
        }
        val file = chooser.selectedFile
        environment.properties.setProperty("current.directory", file.getParent())

        loadSave(file)
    }

    private fun loadSave(file: File) {
        var saveName = file.getName()
        val sep = saveName.lastIndexOf('.')
        if (sep > 0) {
            saveName = saveName.substring(0, sep)
        }

        val dialog = ProgressDialog(this, "Loading " + saveName)
        val task = LoadFile(dialog, session, environment, file)
        task.start()
        val success = dialog.showDialog()

        if (success) {
            try {
                session.setDataChanging(true)

                var list = session.database!!.getTopLevelStruct()!!.getValue() as DBList
                list = list.getElement("Mod_PlayerList")!!.getValue() as DBList
                list = list.getElement(0).getValue() as DBList

                statsPanel.setFields(list)
                attributesPanel.setFields(list)
                signsPanel.setFields(list)
                stylesPanel.setFields(list)
                equipPanel.setFields(list)
                inventoryPanel.setFields(list)
                questsPanel.setFields(list)
                difficultyPanel.setFields(list)

                tabbedPane.selectedIndex = 0
                tabbedPane.isVisible = true

                session.setDataChanging(false)
                session.setDataModified(false)
            } catch (exc: DBException) {
                Main.logException("Database format is not valid", exc)
            } catch (exc: IOException) {
                Main.logException("I/O error while building tabbed panes", exc)
            }
        }
    }

    private fun saveFile(): Boolean {
        if (session.saveDatabase == null) {
            return false
        }
        var saved = false
        try {
            var list = session.database!!.getTopLevelStruct()!!.getValue() as DBList
            list = list.getElement("Mod_PlayerList")!!.getValue() as DBList
            list = list.getElement(0).getValue() as DBList
            statsPanel.getFields(list)
            attributesPanel.getFields(list)
            signsPanel.getFields(list)
            stylesPanel.getFields(list)
            equipPanel.getFields(list)
            inventoryPanel.getFields(list)
            questsPanel.getFields(list)
            difficultyPanel.getFields(list)

            val dialog = ProgressDialog(this, "Saving " + session.saveDatabase!!.getName())
            val task = SaveFile(dialog, session, environment)
            task.start()
            saved = dialog.showDialog()
            if (saved) {
                session.setDataModified(false)
            }
        } catch (exc: DBException) {
            Main.logException("Database format is not valid", exc)
        }

        return saved
    }

    private fun closeFile(): Boolean {
        if (session.saveDatabase == null) {
            return true
        }

        if (session.isDataModified()) {
            val option = JOptionPane.showConfirmDialog(this, "The current save has been modified.  Do you want to save the changes?", "Save Modified", 1)

            if (option == 2) {
                return false
            }
            if (option == 0 && !saveFile()) {
                return false
            }
        }

        session.close()
        tabbedPane.isVisible = false
        return true
    }

    private fun unpackSave() {
        val extractDirectory = environment.properties.getProperty("extract.directory")
        val chooser: JFileChooser
        if (extractDirectory != null) {
            val dirFile = File(extractDirectory)
            chooser = if (dirFile.exists() && dirFile.isDirectory) {
                JFileChooser(dirFile)
            } else {
                JFileChooser()
            }
        } else {
            chooser = JFileChooser()
        }

        chooser.putClientProperty("FileChooser.useShellFolder", java.lang.Boolean.valueOf(environment.isUseShellFolder))
        chooser.dialogTitle = "Select Destination Directory"
        chooser.approveButtonText = "Select"
        chooser.fileSelectionMode = 1
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return
        }
        val dirFile = chooser.selectedFile
        environment.properties.setProperty("extract.directory", dirFile.getPath())
        if (!dirFile.exists()) {
            dirFile.mkdirs()
        }

        val dialog = ProgressDialog(this, "Unpacking " + session.saveDatabase!!.getName())
        val task = UnpackSave(dialog, session, environment, dirFile)
        task.start()
        if (dialog.showDialog()) {
            JOptionPane.showMessageDialog(this, "Save game unpacked to " + dirFile.getPath(), "Save Unpacked", 1)
        }
    }

    private fun packSave() {
        if (session.isDataModified()) {
            val option = JOptionPane.showConfirmDialog(this, "The current save has been modified and these changes will be lost.  Do you want to continue?", "Save Modified", 0)

            if (option != 0) {
                return
            }
        }

        val extractDirectory = environment.properties.getProperty("extract.directory")
        val chooser: JFileChooser
        if (extractDirectory != null) {
            val dirFile = File(extractDirectory)
            chooser = if (dirFile.exists() && dirFile.isDirectory) {
                JFileChooser(dirFile)
            } else {
                JFileChooser()
            }
        } else {
            chooser = JFileChooser()
        }

        chooser.putClientProperty("FileChooser.useShellFolder", java.lang.Boolean.valueOf(environment.isUseShellFolder))
        chooser.dialogTitle = "Select Source Directory"
        chooser.approveButtonText = "Select"
        chooser.fileSelectionMode = 1
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return
        }
        val dirFile = chooser.selectedFile
        environment.properties.setProperty("extract.directory", dirFile.getPath())
        if (!dirFile.exists()) {
            JOptionPane.showMessageDialog(this, "Source directory does not exist", "Directory not found", 0)
            return
        }

        session.setDataModified(false)
        val dialog = ProgressDialog(this, "Packing " + session.saveDatabase!!.getName())
        val task = PackFile(dialog, session, environment, dirFile)
        task.start()
        val saved = dialog.showDialog()

        val file = session.saveDatabase!!.getFile()!!
        closeFile()
        if (saved) {
            loadSave(file)
        }
    }

    private fun exitProgram() {
        closeFile()

        if (session.modFile.exists()) {
            session.modFile.delete()
        }
        if (session.databaseFile.exists()) {
            session.databaseFile.delete()
        }

        if (!windowMinimized) {
            val p: Point = location
            val d: Dimension = size
            environment.properties.setProperty("window.main.position", p.x.toString() + "," + p.y)
            environment.properties.setProperty("window.main.size", d.width.toString() + "," + d.height)
        }

        environment.saveProperties()

        System.exit(0)
    }

    private fun aboutProgram() {
        val info = StringBuilder(256)
        info.append("<html>The Witcher Save Editor Version 3.0.1<br>")

        info.append("<br>User name: ")
        info.append(System.getProperty("user.name"))

        info.append("<br>Home directory: ")
        info.append(System.getProperty("user.home"))

        info.append("<br><br>OS: ")
        info.append(System.getProperty("os.name"))

        info.append("<br>OS version: ")
        info.append(System.getProperty("os.version"))

        info.append("<br>OS patch level: ")
        info.append(System.getProperty("sun.os.patch.level"))

        info.append("<br><br>Java vendor: ")
        info.append(System.getProperty("java.vendor"))

        info.append("<br>Java version: ")
        info.append(System.getProperty("java.version"))

        info.append("<br>Java home directory: ")
        info.append(System.getProperty("java.home"))

        info.append("<br>Java class path: ")
        info.append(System.getProperty("java.class.path"))

        info.append("<br><br>TW install path: ")
        info.append(environment.installPath)

        info.append("<br>TW data path: ")
        info.append(environment.gamePath)

        info.append("<br>Temporary data path: ")
        info.append(environment.tmpDir)

        info.append("<br>Language identifier: ")
        info.append(environment.languageID)

        info.append("</html>")
        JOptionPane.showMessageDialog(this, info.toString(), "About The Witcher Save Editor", 1)
    }

    private inner class ApplicationWindowListener : WindowAdapter() {
        override fun windowIconified(we: WindowEvent?) {
            windowMinimized = true
        }

        override fun windowDeiconified(we: WindowEvent?) {
            windowMinimized = false
        }

        override fun windowClosing(we: WindowEvent?) {
            try {
                exitProgram()
            } catch (exc: Exception) {
                Main.logException("Exception while closing application window", exc)
            }
        }
    }
}
