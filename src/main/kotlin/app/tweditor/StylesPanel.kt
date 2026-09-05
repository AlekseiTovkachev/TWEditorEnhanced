package app.tweditor

import java.awt.GridLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTabbedPane

class StylesPanel(private val session: GameSession, private val environment: AppEnvironment) :
    JPanel(), ActionListener {
    private val tabbedPane = JTabbedPane()
    private val fields: Array<Array<Array<JCheckBox?>>>
    private val levels = IntArray(tabNames.size)
    private val labelMap: MutableMap<String, JCheckBox> = HashMap(tabNames.size * fieldNames[0].size * fieldNames[0][0].size)

    init {
        val tabs = fieldNames.size
        val rows = fieldNames[0].size
        val cols = fieldNames[0][0].size
        fields = Array(tabs) { Array(rows) { arrayOfNulls(cols) } }
        val abilityLabels = ArrayList<String>()
        for (tab in 0 until tabs) {
            val panel = JPanel(GridLayout(0, cols, 5, 5))
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    if (fieldNames[tab][row][col].isNotEmpty()) {
                        val name = fieldNames[tab][row][col]
                        val abilityLabel = databaseLabels[tab][row][col]
                        val field = JCheckBox(name)
                        field.icon = environment.icons.abilityIcon(abilityLabel, ABILITY_ICON_SIZE)
                        field.actionCommand = Integer.toString(tab * 100 + row * 10 + col)
                        field.addActionListener(this)
                        this.fields[tab][row][col] = field
                        panel.add(field)
                        labelMap[abilityLabel] = field
                        abilityLabels.add(abilityLabel)
                    } else {
                        panel.add(JLabel())
                    }
                }
            }

            tabbedPane.addTab(tabNames[tab], panel)
        }

        environment.icons.primeAbilities(abilityLabels)
        add(tabbedPane)
    }

    override fun actionPerformed(ae: ActionEvent?) {
        if (ae!!.source !is JCheckBox || session.isDataChanging()) {
            return
        }

        try {
            val value = ae.actionCommand!!.toInt()
            val tab = value / 100
            val row = value % 100 / 10
            val col = value % 10
            val field = fields[tab][row][col]!!
            val abilityLabel = databaseLabels[tab][row][col]

            var list = session.database!!.getTopLevelStruct()!!.getValue() as DBList
            list = list.getElement("Mod_PlayerList")!!.getValue() as DBList
            val playerList = list.getElement(0).getValue() as DBList
            list = playerList.getElement("CharAbilities")!!.getValue() as DBList
            if (field.isSelected) {
                var addAbility = true

                if (row == 0) {
                    if (col > levels[tab] + 1) {
                        JOptionPane.showMessageDialog(this, "Lower ability level must be obtained first", "Missing level", 0)
                        addAbility = false
                    }
                } else if (col > levels[tab]) {
                    JOptionPane.showMessageDialog(this, "The ability level must be obtained first", "Missing level", 0)
                    addAbility = false
                }

                if (addAbility) {
                    val fieldList = DBList(environment, 2)
                    fieldList.addElement(DBElement(10, 0, "RnAbName", abilityLabel))
                    fieldList.addElement(DBElement(0, 0, "RnAbStk", 0))
                    list.addElement(DBElement(14, 48879, "", fieldList))

                    if (row == 0 && col > levels[tab]) {
                        levels[tab] = col
                    }
                    session.setDataModified(true)
                } else {
                    session.setDataChanging(true)
                    field.isSelected = false
                    session.setDataChanging(false)
                }
            } else {
                var removeAbility = true

                if (row == 0) {
                    if (col < levels[tab]) {
                        JOptionPane.showMessageDialog(this, "All higher ability levels must be removed first", "Higher level", 0)
                        removeAbility = false
                    } else {
                        for (i in 1 until fields[0].size) {
                            val checkField = fields[tab][i][col]
                            if (checkField != null && checkField.isSelected) {
                                JOptionPane.showMessageDialog(this, "All ability level upgrades must be removed first", "Ability upgrades", 0)
                                removeAbility = false
                                break
                            }
                        }
                    }
                }

                if (removeAbility) {
                    val count = list.getElementCount()
                    for (i in 0 until count) {
                        val fieldList = list.getElement(i).getValue() as DBList
                        val name = fieldList.getString("RnAbName")
                        if (abilityLabel == name) {
                            list.removeElement(i)
                            session.setDataModified(true)
                            break
                        }
                    }

                    if (row == 0 && col == levels[tab]) {
                        levels[tab] = col - 1
                    }
                } else {
                    session.setDataChanging(true)
                    field.isSelected = true
                    session.setDataChanging(false)
                }
            }
        } catch (exc: DBException) {
            Main.logException("Unable to update database field", exc)
        } catch (exc: Throwable) {
            Main.logException("Exception while processing action event", exc)
        }
    }

    fun setFields(list: DBList) {
        for (tab in fields.indices) {
            for (row in fields[0].indices) {
                for (col in 0 until fields[0][0].size) {
                    fields[tab][row][col]?.isSelected = false
                }
            }

            levels[tab] = -1
        }

        val element = list.getElement("CharAbilities")
        if (element == null) {
            throw DBException("CharAbilities field not found")
        }
        val abilityList = element.getValue() as DBList
        for (abilityElement in abilityList) {
            val fieldList = abilityElement.getValue() as DBList
            val abilityName = fieldList.getString("RnAbName")
            val field = labelMap[abilityName]
            if (field != null) {
                field.isSelected = true
                val value = field.actionCommand!!.toInt()
                val tab = value / 100
                val row = value % 100 / 10
                val col = value % 10
                if (row == 0 && col > levels[tab]) {
                    levels[tab] = col
                }
            }
        }
    }

    fun getFields(list: DBList) {
    }

    companion object {
        private const val ABILITY_ICON_SIZE = 18

        private val tabNames = arrayOf("Strong Steel", "Fast Steel", "Group Steel", "Strong Silver", "Fast Silver", "Group Silver")

        private val fieldNames = arrayOf(
            arrayOf(arrayOf("Level 1", "Level 2", "Level 3", "Level 4", "Level 5"), arrayOf("Cut at the Jugular I", "Cut at the Jugular II", "Cut at the Jugular III", "", ""), arrayOf("Crushing Blow I", "Crushing Blow II", "Crushing Blow III", "", ""), arrayOf("Bloody Rage I", "Bloody Rage II", "Bloody Rage III", "", "")),
            arrayOf(arrayOf("Level 1", "Level 2", "Level 3", "Level 4", "Level 5"), arrayOf("Paralysis I", "Paralysis II", "Paralysis III", "", ""), arrayOf("Hail of Blows I", "Hail of Blows II", "Hail of Blows III", "", ""), arrayOf("Sever Sinews I", "Sever Sinews II", "Sever Sinews III", "", "")),
            arrayOf(arrayOf("Level 1", "Level 2", "Level 3", "Level 4", "Level 5"), arrayOf("Precise Hit I", "Precise Hit II", "Precise Hit III", "", ""), arrayOf("Half-Spin I", "Half-Spin II", "Half-Spin III", "", ""), arrayOf("Trip I", "Trip II", "Trip III", "", "")),
            arrayOf(arrayOf("Level 1", "Level 2", "Level 3", "Level 4", "Level 5"), arrayOf("Deep Cut I", "Deep Cut II", "Deep Cut III", "", ""), arrayOf("Mortal Blow I", "Mortal Blow II", "Mortal Blow III", "", ""), arrayOf("Patinado I", "Patinado II", "Patinado III", "", "")),
            arrayOf(arrayOf("Level 1", "Level 2", "Level 3", "Level 4", "Level 5"), arrayOf("Crippling Pain I", "Crippling Pain II", "Crippling Pain III", "", ""), arrayOf("Flash Cuts I", "Flash Cuts II", "Flash Cuts III", "", ""), arrayOf("Sinister I", "Sinister II", "Sinister III", "", "")),
            arrayOf(arrayOf("Level 1", "Level 2", "Level 3", "Level 4", "Level 5"), arrayOf("Critical Hit I", "Critical Hit II", "Critical Hit III", "", ""), arrayOf("Tempest I", "Tempest II", "Tempest III", "", ""), arrayOf("Tempest I", "Tempest II", "Tempest III", "", ""))
        )

        private val databaseLabels = arrayOf(
            arrayOf(arrayOf("StyleSteelStrong1", "StyleSteelStrong2", "StyleSteelStrong3", "StyleSteelStrong4", "StyleSteelStrong5"), arrayOf("StyleSteelStrong1 Upgrade1", "StyleSteelStrong2 Upgrade1", "StyleSteelStrong3 Upgrade1", "", ""), arrayOf("StyleSteelStrong1 Upgrade2", "StyleSteelStrong2 Upgrade2", "StyleSteelStrong3 Upgrade2", "", ""), arrayOf("StyleSteelStrong1 Upgrade3", "StyleSteelStrong2 Upgrade3", "StyleSteelStrong3 Upgrade3", "", "")),
            arrayOf(arrayOf("StyleSteelFast1", "StyleSteelFast2", "StyleSteelFast3", "StyleSteelFast4", "StyleSteelFast5"), arrayOf("StyleSteelFast1 Upgrade1", "StyleSteelFast2 Upgrade1", "StyleSteelFast3 Upgrade1", "", ""), arrayOf("StyleSteelFast1 Upgrade2", "StyleSteelFast2 Upgrade2", "StyleSteelFast3 Upgrade2", "", ""), arrayOf("StyleSteelFast1 Upgrade3", "StyleSteelFast2 Upgrade3", "StyleSteelFast3 Upgrade3", "", "")),
            arrayOf(arrayOf("StyleSteelGroup1", "StyleSteelGroup2", "StyleSteelGroup3", "StyleSteelGroup4", "StyleSteelGroup5"), arrayOf("StyleSteelGroup1 Upgrade1", "StyleSteelGroup2 Upgrade1", "StyleSteelGroup3 Upgrade1", "", ""), arrayOf("StyleSteelGroup1 Upgrade2", "StyleSteelGroup2 Upgrade2", "StyleSteelGroup3 Upgrade2", "", ""), arrayOf("StyleSteelGroup1 Upgrade3", "StyleSteelGroup2 Upgrade3", "StyleSteelGroup3 Upgrade3", "", "")),
            arrayOf(arrayOf("StyleSilverStrong1", "StyleSilverStrong2", "StyleSilverStrong3", "StyleSilverStrong4", "StyleSilverStrong5"), arrayOf("StyleSilverStrong1 Upgrade1", "StyleSilverStrong2 Upgrade1", "StyleSilverStrong3 Upgrade1", "", ""), arrayOf("StyleSilverStrong1 Upgrade2", "StyleSilverStrong2 Upgrade2", "StyleSilverStrong3 Upgrade2", "", ""), arrayOf("StyleSilverStrong1 Upgrade3", "StyleSilverStrong2 Upgrade3", "StyleSilverStrong3 Upgrade3", "", "")),
            arrayOf(arrayOf("StyleSilverFast1", "StyleSilverFast2", "StyleSilverFast3", "StyleSilverFast4", "StyleSilverFast5"), arrayOf("StyleSilverFast1 Upgrade1", "StyleSilverFast2 Upgrade1", "StyleSilverFast3 Upgrade1", "", ""), arrayOf("StyleSilverFast1 Upgrade2", "StyleSilverFast2 Upgrade2", "StyleSilverFast3 Upgrade2", "", ""), arrayOf("StyleSilverFast1 Upgrade3", "StyleSilverFast2 Upgrade3", "StyleSilverFast3 Upgrade3", "", "")),
            arrayOf(arrayOf("StyleSilverGroup1", "StyleSilverGroup2", "StyleSilverGroup3", "StyleSilverGroup4", "StyleSilverGroup5"), arrayOf("StyleSilverGroup1 Upgrade1", "StyleSilverGroup2 Upgrade1", "StyleSilverGroup3 Upgrade1", "", ""), arrayOf("StyleSilverGroup1 Upgrade2", "StyleSilverGroup2 Upgrade2", "StyleSilverGroup3 Upgrade2", "", ""), arrayOf("StyleSilverGroup1 Upgrade3", "StyleSilverGroup2 Upgrade3", "StyleSilverGroup3 Upgrade3", "", ""))
        )

        /** The ability names the panel's checkboxes represent, for icon priming and tests. */
        fun abilityLabels(): List<String> =
            databaseLabels.flatMap { tab -> tab.flatMap { row -> row.filter { it.isNotEmpty() } } }
    }
}
