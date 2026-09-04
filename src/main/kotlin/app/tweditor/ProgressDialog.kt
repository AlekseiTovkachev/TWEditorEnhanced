package app.tweditor

import java.awt.Frame
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.SwingUtilities

class ProgressDialog(private val parent: JFrame?, message: String) : JDialog(parent as Frame, "The Witcher Save Editor", true) {
    private val progressBar = JProgressBar(0, 100)
    private var deferredProgress = 0
    private var success = false

    init {
        val progressPane = JPanel()
        progressPane.layout = BoxLayout(progressPane, BoxLayout.PAGE_AXIS)
        progressPane.add(Box.createVerticalStrut(15))
        progressPane.add(JLabel("<html><b>" + message + "</b></html>"))
        progressPane.add(Box.createVerticalStrut(15))
        progressBar.setStringPainted(true)
        progressPane.add(progressBar)
        progressPane.add(Box.createVerticalStrut(15))

        val contentPane = JPanel()
        contentPane.add(progressPane)
        setContentPane(contentPane)
    }

    fun showDialog(): Boolean {
        pack()
        setLocationRelativeTo(parent)
        isVisible = true
        return success
    }

    fun closeDialog(success: Boolean) {
        this.success = success
        isVisible = false
        dispose()
    }

    fun updateProgress(progress: Int) {
        if (SwingUtilities.isEventDispatchThread()) {
            progressBar.value = progress
        } else {
            deferredProgress = progress
            SwingUtilities.invokeLater {
                progressBar.value = deferredProgress
            }
        }
    }
}
