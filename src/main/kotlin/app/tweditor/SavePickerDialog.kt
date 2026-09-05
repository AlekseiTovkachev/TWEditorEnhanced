package app.tweditor

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.io.File
import java.text.DateFormat
import java.util.Date
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.KeyStroke
import javax.swing.DefaultListModel
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingWorker

/**
 * The save browser: lists the *.TheWitcherSave files of a directory with their embedded
 * screenshot, in-game save name and player level, loaded one save at a time in the
 * background. The stock file chooser stays available through "Browse...".
 */
class SavePickerDialog(
    private val owner: JFrame?,
    environment: AppEnvironment,
    private val directory: File,
    private val summaryCache: SaveSummaryCache
) : JDialog(owner as java.awt.Frame?, "Select Save", true), ActionListener {
    var selectedFile: File? = null
        private set

    private val listModel = DefaultListModel<SaveListItem>()
    private val saveList = JList(listModel)
    private val statusLabel = JLabel(" ")
    private val openButton = JButton("Open")
    private var worker: SummaryWorker? = null

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE

        contentPane.layout = BorderLayout()

        saveList.cellRenderer = SaveCellRenderer()
        saveList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        saveList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2) {
                    approveSelected()
                }
            }
        })
        saveList.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "approve")
        saveList.actionMap.put("approve", object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent) {
                approveSelected()
            }
        })
        contentPane.add(JScrollPane(saveList), BorderLayout.CENTER)

        val buttonPanel = JPanel()
        val browseButton = JButton("Browse...")
        browseButton.actionCommand = "browse"
        browseButton.addActionListener(this)
        buttonPanel.add(browseButton)
        openButton.actionCommand = "open"
        openButton.addActionListener(this)
        buttonPanel.add(openButton)
        val cancelButton = JButton("Cancel")
        cancelButton.actionCommand = "cancel"
        cancelButton.addActionListener(this)
        buttonPanel.add(cancelButton)

        val southPanel = JPanel(BorderLayout())
        southPanel.border = BorderFactory.createEmptyBorder(4, 10, 8, 10)
        southPanel.add(statusLabel, BorderLayout.CENTER)
        southPanel.add(buttonPanel, BorderLayout.EAST)
        contentPane.add(southPanel, BorderLayout.SOUTH)

        rootPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel")
        rootPane.actionMap.put("cancel", object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent) {
                selectedFile = null
                dispose()
            }
        })

        val files = scanFiles()
        for (file in files) {
            listModel.addElement(SaveListItem(file))
        }
        if (listModel.size() > 0) {
            saveList.selectedIndex = 0
        }
        if (files.isEmpty()) {
            statusLabel.text = "No saves found in " + directory.getPath()
        } else {
            statusLabel.text = "Reading " + files.size + " saves..."
            worker = SummaryWorker()
            worker!!.execute()
        }

        preferredSize = Dimension(560, 480)
        pack()
        setLocationRelativeTo(owner)
    }

    fun showDialog(): File? {
        isVisible = true
        return selectedFile
    }

    fun loadedCount(): Int {
        return (0 until listModel.size()).count { listModel.get(it).summary != null }
    }

    fun summaryAt(index: Int): SaveSummary? {
        return listModel.get(index).summary
    }

    private fun describeLoaded(): String {
        val readable = loadedCount()
        val total = listModel.size()
        val readableText = readable.toString() + " of " + total + " saves in " + directory.getPath()
        return if (readable == total) readableText else readableText + " (" + (total - readable) + " unreadable)"
    }

    override fun actionPerformed(event: ActionEvent) {
        if (event.actionCommand == "open") {
            approveSelected()
        } else if (event.actionCommand == "browse") {
            browseForSave()
        } else if (event.actionCommand == "cancel") {
            selectedFile = null
            dispose()
        }
    }

    override fun dispose() {
        worker?.cancel(true)
        super.dispose()
    }

    private fun approveSelected() {
        val item = saveList.selectedValue ?: return
        selectedFile = item.file
        dispose()
    }

    private fun browseForSave() {
        val chooser: JFileChooser
        if (directory.exists() && directory.isDirectory) {
            chooser = JFileChooser(directory)
        } else {
            chooser = JFileChooser()
        }
        chooser.dialogTitle = "Select Save"
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedFile = chooser.selectedFile
            dispose()
        }
    }

    private fun scanFiles(): List<File> {
        val files = directory.listFiles { _, name -> name.endsWith(".TheWitcherSave") }
        return files?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    private fun describe(item: SaveListItem): String {
        val summary = item.summary
        return if (summary == null) {
            "<html><b>" + escapeHtml(item.file.getName()) + "</b><br>reading...</html>"
        } else {
            val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(summary.lastModified))
            val level = if (summary.level >= 0) summary.level else "?"
            "<html><b>" + escapeHtml(summary.saveName) + "</b><br>Level " + level + " &middot; " + date +
                "<br><small>" + escapeHtml(item.file.getName()) + "</small></html>"
        }
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }

    private inner class SummaryWorker : SwingWorker<Void, LoadedSummary>() {
        override fun doInBackground(): Void? {
            for (index in 0 until listModel.size()) {
                if (isCancelled) {
                    break
                }
                val item = listModel.get(index)
                val summary = try {
                    summaryCache.get(item.file)
                } catch (exc: Throwable) {
                    null
                }
                publish(LoadedSummary(index, summary))
            }
            return null
        }

        override fun process(chunks: List<LoadedSummary>) {
            for (loaded in chunks) {
                val item = listModel.get(loaded.index)
                item.summary = loaded.summary
                loaded.summary?.screenshot?.let { item.icon = ImageIcon(toBufferedImage(it)) }
                listModel.set(loaded.index, item)
            }
        }

        override fun done() {
            statusLabel.text = describeLoaded()
        }
    }

    private class LoadedSummary(val index: Int, val summary: SaveSummary?)

    private class SaveListItem(val file: File) {
        var summary: SaveSummary? = null
        var icon: Icon? = null
    }

    private fun toBufferedImage(tga: TgaImage): BufferedImage {
        val image = BufferedImage(tga.width, tga.height, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, tga.width, tga.height, tga.argb, 0, tga.width)
        return image
    }

    private inner class SaveCellRenderer : JPanel(BorderLayout(10, 0)), ListCellRenderer<SaveListItem> {
        private val iconLabel = JLabel()
        private val textLabel = JLabel()
        private val placeholderIcon: Icon

        init {
            isOpaque = true
            border = BorderFactory.createEmptyBorder(6, 10, 6, 10)
            add(iconLabel, BorderLayout.WEST)
            add(textLabel, BorderLayout.CENTER)

            val image = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
            val graphics: Graphics = image.createGraphics()
            graphics.color = Color(128, 128, 128, 64)
            graphics.fillRect(0, 0, 64, 64)
            graphics.color = Color(128, 128, 128)
            graphics.drawRect(0, 0, 63, 63)
            graphics.dispose()
            placeholderIcon = ImageIcon(image)
        }

        override fun getListCellRendererComponent(
            list: JList<out SaveListItem>,
            value: SaveListItem,
            index: Int,
            selected: Boolean,
            focused: Boolean
        ): Component {
            iconLabel.icon = value.icon ?: placeholderIcon
            textLabel.text = describe(value)
            background = if (selected) list.selectionBackground else list.background
            textLabel.foreground = if (selected) list.selectionForeground else list.foreground
            return this
        }
    }
}
