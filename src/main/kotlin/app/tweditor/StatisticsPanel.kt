package app.tweditor

import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.GridLayout
import java.awt.Insets
import java.awt.Graphics
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.table.AbstractTableModel

class StatisticsPanel(private val session: GameSession) : JPanel(BorderLayout()) {
    private var data: StatisticsData? = null
    private val cards = JPanel(CardLayout())
    private val placeholder = JLabel("Open a save to see statistics.")
    private val content = JPanel(BorderLayout())
    private val killsTable = ReadOnlyTable()
    private val killsHeader = JLabel()
    private val moreKills = JLabel()
    private val actsTable = ReadOnlyTable()
    private val journalHeader = JLabel()
    private val journalChart = JournalChart()

    init {
        placeholder.horizontalAlignment = javax.swing.SwingConstants.CENTER

        val columns = JPanel(GridLayout(0, 1, 0, 12))
        val killsScroll = JScrollPane(killsTable)
        killsScroll.viewport.preferredSize = Dimension(420, 11 * 22)
        columns.add(titled("Kills", killsSection(killsScroll)))
        val actsScroll = JScrollPane(actsTable)
        actsScroll.viewport.preferredSize = Dimension(420, 8 * 22)
        columns.add(titled("Quests by act", actsScroll))
        columns.add(titled("Journal timeline", journalSection()))
        content.add(columns, BorderLayout.CENTER)
        cards.add(placeholder, PLACEHOLDER_CARD)
        cards.add(JScrollPane(content), CONTENT_CARD)
        add(cards, BorderLayout.CENTER)
        showPlaceholder(true)
    }

    fun statistics(): StatisticsData? = data

    fun killsTableModel(): AbstractTableModel = killsTable.model as AbstractTableModel

    fun actsTableModel(): AbstractTableModel = actsTable.model as AbstractTableModel

    fun setFields(list: DBList) {
        val killTags = ArrayList<String>()
        if (session.playerDatabase != null) {
            val playerTop = session.playerDatabase!!.getTopLevelStruct()!!.getValue() as DBList
            val kills = playerTop.getElement("KillList")?.getValue() as? DBList
            if (kills != null) {
                for (kill in kills) {
                    val fields = kill.getValue() as? DBList ?: continue
                    val tag = fields.getString("OppTag")
                    if (tag.isNotEmpty()) {
                        killTags.add(tag)
                    }
                }
            }
        }

        val quests = session.getQuests() ?: emptyList()
        val journal = session.getJournalData()?.entries ?: emptyList()
        val computed = StatisticsData.compute(killTags, quests, journal)
        data = computed
        refresh(computed)
    }

    fun getFields(list: DBList) {
    }

    private fun refresh(data: StatisticsData) {
        showPlaceholder(false)

        killsHeader.text = "${data.totalKills} kills across ${data.distinctOpponents} opponents"
        killsTable.model = KillTableModel(data.topKills, KILL_TOP_LIMIT)
        moreKills.text = if (data.distinctOpponents > KILL_TOP_LIMIT) {
            "…and ${data.distinctOpponents - KILL_TOP_LIMIT} more opponents"
        } else {
            " "
        }

        actsTable.model = ActTableModel(data.acts)

        journalHeader.text = when {
            data.journalEntries == 0 -> "No journal entries yet"
            data.firstEntryTOD > 0 ->
                "${data.journalEntries} entries · in-game time ${formatTOD(data.firstEntryTOD)} to ${formatTOD(data.lastEntryTOD)}"
            else -> "${data.journalEntries} entries (no in-game time recorded)"
        }
        journalChart.days = data.days
        journalChart.revalidate()
        journalChart.repaint()
    }

    private fun showPlaceholder(placeholderOnly: Boolean) {
        (cards.layout as CardLayout).show(cards, if (placeholderOnly) PLACEHOLDER_CARD else CONTENT_CARD)
        revalidate()
        repaint()
    }

    private fun titled(title: String, body: Component): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(title),
            BorderFactory.createEmptyBorder(4, 4, 4, 4))
        panel.add(body, BorderLayout.CENTER)
        return panel
    }

    private fun killsSection(scrollPane: JScrollPane): JPanel {
        val panel = JPanel(BorderLayout())
        panel.add(killsHeader, BorderLayout.NORTH)
        panel.add(scrollPane, BorderLayout.CENTER)
        panel.add(moreKills, BorderLayout.SOUTH)
        return panel
    }

    private fun journalSection(): JPanel {
        val panel = JPanel(GridBagLayout())
        val constraints = GridBagConstraints()
        constraints.gridx = 0
        constraints.gridy = 0
        constraints.weightx = 1.0
        constraints.fill = GridBagConstraints.HORIZONTAL
        constraints.insets = Insets(0, 0, 4, 0)
        panel.add(journalHeader, constraints)
        constraints.gridy = 1
        constraints.weighty = 1.0
        constraints.fill = GridBagConstraints.BOTH
        panel.add(journalChart, constraints)
        return panel
    }

    companion object {
        private const val KILL_TOP_LIMIT = 10
        private const val PLACEHOLDER_CARD = "placeholder"
        private const val CONTENT_CARD = "content"

        fun formatTOD(tod: Long): String {
            val day = tod / StatisticsData.SECONDS_PER_DAY + 1
            val hour = tod % StatisticsData.SECONDS_PER_DAY / 3600
            val minute = tod % 3600 / 60
            return "Day $day, %02d:%02d".format(hour, minute)
        }
    }

    private class ReadOnlyTable : JTable() {
        init {
            setShowGrid(false)
            rowHeight = 22
        }

        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }

    private class KillTableModel(kills: List<KillCount>, limit: Int) : AbstractTableModel() {
        private val rows = kills.take(limit)

        override fun getRowCount(): Int = rows.size

        override fun getColumnCount(): Int = 2

        override fun getColumnName(column: Int): String = if (column == 0) "Opponent" else "Kills"

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any =
            if (columnIndex == 0) rows[rowIndex].tag else rows[rowIndex].count
    }

    private class ActTableModel(private val acts: List<ActQuests>) : AbstractTableModel() {
        override fun getRowCount(): Int = acts.size

        override fun getColumnCount(): Int = 4

        override fun getColumnName(column: Int): String = when (column) {
            0 -> "Act"
            1 -> "In save"
            2 -> "Begun"
            else -> "Completed"
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val act = acts[rowIndex]
            return when (columnIndex) {
                0 -> actName(act.act)
                1 -> act.known
                2 -> act.begun
                else -> act.completed
            }
        }

        fun actName(act: String): String = when (act) {
            "prologue1" -> "Prologue"
            "act1" -> "Act I"
            "act2" -> "Act II"
            "act3" -> "Act III"
            "act4" -> "Act IV"
            "act5" -> "Act V"
            "epilogue" -> "Epilogue"
            else -> "Other"
        }
    }

    private class JournalChart : JPanel(null) {
        var days: List<DayCount> = emptyList()

        init {
            preferredSize = Dimension(300, 130)
            minimumSize = Dimension(200, 100)
            isOpaque = false
        }

        override fun paintComponent(graphics: Graphics) {
            super.paintComponent(graphics)
            if (days.isEmpty() || width < 60 || height < 40) {
                return
            }
            val maxDay = days.last().day
            val maxCount = days.maxOf { it.count }
            val left = 36
            val bottom = height - 20
            val barWidth = 8
            val scale = (width - left - 8).toDouble() / (maxDay + 1).coerceAtLeast(1)
            for (day in days) {
                val x = left + (day.day * scale).toInt()
                val barHeight = (day.count.toDouble() / maxCount * (bottom - 12)).toInt().coerceAtLeast(2)
                graphics.fillRect(x, bottom - barHeight, barWidth, barHeight)
            }
            graphics.fillRect(left, bottom, width - left - 8, 1)
            graphics.drawString(days.first().day.toString(), 4, bottom + 4)
            val lastLabel = (maxDay + 1).toString()
            graphics.drawString(lastLabel, width - 8 - graphics.fontMetrics.stringWidth(lastLabel), bottom + 4)
        }
    }
}
