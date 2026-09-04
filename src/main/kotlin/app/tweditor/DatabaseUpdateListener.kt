package app.tweditor

import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class DatabaseUpdateListener(private val session: GameSession) : ActionListener, DocumentListener {
    override fun actionPerformed(ae: ActionEvent?) {
        if (session.database != null && !session.isDataChanging()) {
            session.setDataModified(true)
            Main.mainWindow!!.setTitle(null)
        }
    }

    override fun changedUpdate(de: DocumentEvent?) {
        if (session.database != null && !session.isDataChanging()) {
            session.setDataModified(true)
            Main.mainWindow!!.setTitle(null)
        }
    }

    override fun insertUpdate(de: DocumentEvent?) {
        if (session.database != null && !session.isDataChanging()) {
            session.setDataModified(true)
            Main.mainWindow!!.setTitle(null)
        }
    }

    override fun removeUpdate(de: DocumentEvent?) {
        if (session.database != null && !session.isDataChanging()) {
            session.setDataModified(true)
            Main.mainWindow!!.setTitle(null)
        }
    }
}
