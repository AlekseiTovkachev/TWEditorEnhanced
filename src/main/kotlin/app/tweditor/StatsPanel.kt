package app.tweditor

import java.awt.GridLayout
import javax.swing.Box
import javax.swing.JLabel
import javax.swing.JPanel

class StatsPanel(private val session: GameSession) : JPanel(GridLayout(0, 3, 40, 0)) {
    private val statFields: Array<Array<NumericField?>> = Array(fieldNames.size) { arrayOfNulls(3) }

    init {
        val listener = DatabaseUpdateListener(session)

        add(Box.createVerticalStrut(5))
        add(Box.createVerticalStrut(5))
        add(Box.createVerticalStrut(5))

        for (i in fieldNames.indices) {
            for (j in 0 until 3) {
                if (fieldNames[i][j].isNotEmpty()) {
                    add(JLabel(fieldNames[i][j]))
                } else {
                    add(JLabel())
                }
            }
            for (j in 0 until 3) {
                if (fieldNames[i][j].isNotEmpty()) {
                    val field = NumericField(5)
                    field.document.addDocumentListener(listener)
                    add(field)
                    statFields[i][j] = field
                }
            }

            add(Box.createVerticalStrut(5))
            add(Box.createVerticalStrut(5))
            add(Box.createVerticalStrut(5))
        }
    }

    fun setFields(list: DBList) {
        for (i in databaseNames.indices) {
            for (j in 0 until 3) {
                statFields[i][j]?.setValue(list.getInteger(databaseNames[i][j]))
            }
        }
    }

    fun getFields(list: DBList) {
        for (i in databaseNames.indices) {
            for (j in 0 until 3) {
                statFields[i][j]?.let { list.setInteger(databaseNames[i][j], it.getValue()) }
            }
        }
    }

    companion object {
        private val fieldNames = arrayOf(
            arrayOf("Level", "Vitality", "Bronze Talents"),
            arrayOf("Experience", "Endurance", "Silver Talents"),
            arrayOf("Gold", "Toxicity", "Gold Talents")
        )

        private val databaseNames = arrayOf(
            arrayOf("ExpLevel", "CurrentHitPoints", "TalentBronze"),
            arrayOf("Experience", "CurrentEndurance", "TalentSilver"),
            arrayOf("Gold", "CurrentToxicity", "TalentGold")
        )
    }
}
