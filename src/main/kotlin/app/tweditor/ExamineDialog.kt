package app.tweditor

import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextPane

class ExamineDialog(parent: JFrame?, environment: AppEnvironment, label: String, description: String) :
    JDialog(parent, label, true), ActionListener {
    private val scrollPane: JScrollPane
    private val textPane: JTextPane

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        val stringBuilder = StringBuilder(description)
        stringBuilder.insert(0, "<html>")
        stringBuilder.append("</html>")

        var start = 6
        while (true) {
            start = stringBuilder.indexOf("<", start)
            if (start < 0) {
                break
            }
            val stop = stringBuilder.indexOf(">", start)
            if (stop < 0) {
                break
            }
            val control = stringBuilder.substring(start + 1, stop).lowercase()
            if (control == "/html") {
                break
            }
            val html: String?
            var strref: String? = null
            if (control == "cbold") {
                html = "b"
            } else if (control == "citalic") {
                html = "i"
            } else if (control.length >= 7 && control.substring(0, 7) == "strref:") {
                strref = try {
                    val refid = control.substring(7).toInt()
                    environment.stringsDatabase.getString(refid)
                } catch (exc: NumberFormatException) {
                    ""
                }
                html = null
            } else {
                html = null
            }

            if (html != null) {
                stringBuilder.replace(start + 1, stop, html)
                start = stringBuilder.indexOf("</c>", stop)
                if (start < 0) {
                    stringBuilder.append("</").append(html).append(">")
                    break
                }

                stringBuilder.replace(start + 2, start + 3, html)
                start += 4
            } else if (strref != null) {
                stringBuilder.replace(start, stop + 1, strref)
            } else {
                stringBuilder.delete(start, stop + 1)
            }
        }

        var sep = 0
        while (stringBuilder.indexOf("\n", sep).also { sep = it } >= 0) {
            stringBuilder.replace(sep, sep + 1, "<br>")
        }

        val contentPane = JPanel()
        contentPane.layout = BoxLayout(contentPane, BoxLayout.PAGE_AXIS)
        contentPane.border = BorderFactory.createEmptyBorder(15, 15, 15, 15)

        textPane = JTextPane()
        textPane.contentType = "text/html"
        textPane.text = stringBuilder.toString()
        textPane.caretPosition = 0

        scrollPane = JScrollPane(textPane)
        scrollPane.preferredSize = Dimension(400, 500)

        val buttonPane = JPanel()
        val button = JButton("OK")
        button.addActionListener(this)
        button.actionCommand = "ok"
        buttonPane.add(button)

        contentPane.add(scrollPane)
        contentPane.add(Box.createVerticalStrut(10))
        contentPane.add(buttonPane)
        setContentPane(contentPane)
    }

    override fun actionPerformed(ae: ActionEvent?) {
        try {
            val action = ae!!.actionCommand
            if (action == "ok") {
                isVisible = false
                dispose()
            }
        } catch (exc: Throwable) {
            Main.logException("Exception while processing action event", exc)
        }
    }

    companion object {
        fun showDialog(parent: JFrame?, environment: AppEnvironment, label: String, description: String) {
            val dialog = ExamineDialog(parent, environment, label, description)
            dialog.pack()
            dialog.setLocationRelativeTo(parent)
            dialog.isVisible = true
        }
    }
}
