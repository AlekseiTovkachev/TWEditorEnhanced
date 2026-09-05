package app.tweditor

import java.awt.GridLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTabbedPane

class SignsPanel(private val session: GameSession, private val environment: AppEnvironment) :
    JPanel(), ActionListener {
    private val tabbedPane = JTabbedPane()
    private val fields: Array<Array<Array<JCheckBox?>>>
    private val signLevels = Array(tabNames.size) { IntArray(2) }
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
                        val field = AbilityCheckBox(name, abilityLabel, ABILITY_ICON_SIZE) { label, size ->
                            environment.icons.abilityIcon(label, size)
                        }
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
                var addSign = true

                if (row == 0) {
                    if (col > signLevels[tab][0] + 1) {
                        JOptionPane.showMessageDialog(this, "Lower sign level must be obtained first", "Missing level", 0)
                        addSign = false
                    }
                } else if (row == 1) {
                    if (col > signLevels[tab][1] + 1) {
                        JOptionPane.showMessageDialog(this, "Lower sign level powerup must be obtained first", "Missing powerup", 0)
                        addSign = false
                    }
                } else if (col > signLevels[tab][0]) {
                    JOptionPane.showMessageDialog(this, "The sign level must be obtained first", "Missing level", 0)
                    addSign = false
                }

                if (addSign) {
                    var fieldList = DBList(environment, 2)
                    fieldList.addElement(DBElement(10, 0, "RnAbName", abilityLabel))
                    fieldList.addElement(DBElement(0, 0, "RnAbStk", 0))
                    list.addElement(DBElement(14, 48879, "", fieldList))

                    if (row < 2 && col > signLevels[tab][row]) {
                        var updatedSpell = false
                        val low = associatedSpells[tab] * 10
                        val high = associatedSpells[tab] * 10 + 9
                        var spellList: DBList? = null
                        val element = playerList.getElement("KnownList0")
                        if (element != null) {
                            spellList = element.getValue() as DBList
                            val count = spellList.getElementCount()
                            for (i in 0 until count) {
                                fieldList = spellList.getElement(i).getValue() as DBList
                                val spell = fieldList.getInteger("Spell")
                                if (spell >= low && spell <= high && spell and 0x1 == row) {
                                    fieldList.setInteger("Spell", low + 2 * col + row)
                                    updatedSpell = true
                                    break
                                }
                            }
                        }

                        if (!updatedSpell) {
                            if (spellList == null) {
                                spellList = DBList(environment, 1)
                                playerList.addElement(DBElement(15, 0, "KnownList0", spellList))
                            }

                            fieldList = DBList(environment, 1)
                            val element = DBElement(2, 0, "Spell", low + 2 * col + row)
                            fieldList.addElement(element)
                            spellList.addElement(DBElement(14, 2, "", fieldList))
                        }

                        signLevels[tab][row] = col
                    }

                    session.setDataModified(true)
                } else {
                    session.setDataChanging(true)
                    field.isSelected = false
                    session.setDataChanging(false)
                }
            } else {
                var removeSign = true

                if (row == 0) {
                    if (col < signLevels[tab][0]) {
                        JOptionPane.showMessageDialog(this, "All higher sign levels must be removed first", "Higher level", 0)
                        removeSign = false
                    } else {
                        for (i in 1 until fields[0].size) {
                            val checkField = fields[tab][i][col]
                            if (checkField != null && checkField.isSelected) {
                                JOptionPane.showMessageDialog(this, "All sign level upgrades must be removed first", "Sign upgrades", 0)
                                removeSign = false
                                break
                            }
                        }
                    }
                } else if (row == 1 && col < signLevels[tab][1]) {
                    JOptionPane.showMessageDialog(this, "All higher sign powerups must be removed first", "Higher powerup", 0)
                    removeSign = false
                }

                if (removeSign) {
                    var count = list.getElementCount()
                    for (i in 0 until count) {
                        val fieldList = list.getElement(i).getValue() as DBList
                        val name = fieldList.getString("RnAbName")
                        if (abilityLabel == name) {
                            list.removeElement(i)
                            session.setDataModified(true)
                            break
                        }
                    }

                    if (row < 2 && col == signLevels[tab][row]) {
                        val low = associatedSpells[tab] * 10
                        val high = associatedSpells[tab] * 10 + 9
                        val element = playerList.getElement("KnownList0")
                        if (element != null) {
                            val spellList = element.getValue() as DBList
                            count = spellList.getElementCount()
                            for (i in 0 until count) {
                                val fieldList = spellList.getElement(i).getValue() as DBList
                                val spell = fieldList.getInteger("Spell")
                                if (spell >= low && spell <= high && spell and 0x1 == row) {
                                    if (col == 0) {
                                        spellList.removeElement(i)
                                        break
                                    }
                                    fieldList.setInteger("Spell", spell - 2)

                                    break
                                }
                            }
                        }

                        signLevels[tab][row] = col - 1
                        session.setDataModified(true)
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

            signLevels[tab][0] = -1
            signLevels[tab][1] = -1
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
                if (row < 2 && col > signLevels[tab][row]) {
                    signLevels[tab][row] = col
                }
            }
        }
    }

    fun getFields(list: DBList) {
    }

    companion object {
        private const val ABILITY_ICON_SIZE = 18

        private val tabNames = arrayOf("Aard", "Igni", "Quen", "Axii", "Yrden")

        private val fieldNames = arrayOf(
            arrayOf(arrayOf("Level 1", "Level 2", "Level 3", "Level 4", "Level 5"), arrayOf("Student", "Apprentice", "Specialist", "Expert", "Master"), arrayOf("Stun", "Disarm", "Blasting Fist", "Extended Duration", "Gale"), arrayOf("", "Gust", "Thunder", "Added Efficiency", "")),
            arrayOf(arrayOf("Level 1", "Level 2", "Level 3", "Level 4", "Level 5"), arrayOf("Student", "Apprentice", "Specialist", "Expert", "Master"), arrayOf("Harm's Way I", "Harm's Way II", "Burning Blade", "Inferno", "Extended Duration"), arrayOf("", "Incineration", "Wall of Fire", "Added Efficiency", "")),
            arrayOf(arrayOf("Level 1", "Level 2", "Level 3", "Level 4", "Level 5"), arrayOf("Student", "Apprentice", "Specialist", "Expert", "Master"), arrayOf("Barrier I", "Barrier II", "Barrier III", "Survival Zone", "Resonance"), arrayOf("", "Extended Duration", "Added Intensity", "Added Efficiency", "")),
            arrayOf(arrayOf("Level 1", "Level 2", "Level 3", "Level 4", "Level 5"), arrayOf("Student", "Apprentice", "Specialist", "Expert", "Master"), arrayOf("Spell", "Hypnosis", "Faze", "Terror", "Ally"), arrayOf("", "Extended Duration I", "Extended Duration II", "Added Efficiency", "")),
            arrayOf(arrayOf("Level 1", "Level 2", "Level 3", "Level 4", "Level 5"), arrayOf("Student", "Apprentice", "Specialist", "Expert", "Master"), arrayOf("Pain Sign", "Prowess", "Stupor Sign", "Blinding Sign", "Circle of Death"), arrayOf("", "Inscriptions", "Crippling Sign", "Added Efficiency", ""))
        )

        private val databaseLabels = arrayOf(
            arrayOf(arrayOf("Aard1", "Aard2", "Aard3", "Aard4", "Aard5"), arrayOf("Aard1 Powerup", "Aard2 Powerup", "Aard3 Powerup", "Aard4 Powerup", "Aard5 Powerup"), arrayOf("Aard1 Upgrade1", "Aard2 Upgrade1", "Aard3 Upgrade1", "Aard4 Upgrade1", "Aard5 Upgrade1"), arrayOf("", "Aard2 Upgrade2", "Aard3 Upgrade2", "Aard4 Upgrade2", "")),
            arrayOf(arrayOf("Igni1", "Igni2", "Igni3", "Igni4", "Igni5"), arrayOf("Igni1 Powerup", "Igni2 Powerup", "Igni3 Powerup", "Igni4 Powerup", "Igni5 Powerup"), arrayOf("Igni1 Upgrade1", "Igni2 Upgrade1", "Igni3 Upgrade1", "Igni4 Upgrade1", "Igni5 Upgrade1"), arrayOf("", "Igni2 Upgrade2", "Igni3 Upgrade2", "Igni4 Upgrade2", "")),
            arrayOf(arrayOf("Quen1", "Quen2", "Quen3", "Quen4", "Quen5"), arrayOf("Quen1 Powerup", "Quen2 Powerup", "Quen3 Powerup", "Quen4 Powerup", "Quen5 Powerup"), arrayOf("Quen1 Upgrade1", "Quen2 Upgrade1", "Quen3 Upgrade1", "Quen4 Upgrade1", "Quen5 Upgrade1"), arrayOf("", "Quen2 Upgrade2", "Quen3 Upgrade2", "Quen4 Upgrade2", "")),
            arrayOf(arrayOf("Axi1", "Axi2", "Axi3", "Axi4", "Axi5"), arrayOf("Axi1 Powerup", "Axi2 Powerup", "Axi3 Powerup", "Axi4 Powerup", "Axi5 Powerup"), arrayOf("Axi1 Upgrade1", "Axi2 Upgrade1", "Axi3 Upgrade1", "Axi4 Upgrade1", "Axi5 Upgrade1"), arrayOf("", "Axi2 Upgrade2", "Axi3 Upgrade2", "Axi4 Upgrade2", "")),
            arrayOf(arrayOf("Yrden1", "Yrden2", "Yrden3", "Yrden4", "Yrden5"), arrayOf("Yrden1 Powerup", "Yrden2 Powerup", "Yrden3 Powerup", "Yrden4 Powerup", "Yrden5 Powerup"), arrayOf("Yrden1 Upgrade1", "Yrden2 Upgrade1", "Yrden3 Upgrade1", "Yrden4 Upgrade1", "Yrden5 Upgrade1"), arrayOf("", "Yrden2 Upgrade2", "Yrden3 Upgrade2", "Yrden4 Upgrade2", ""))
        )

        private val associatedSpells = intArrayOf(0, 3, 1, 4, 2)

        /** The ability names the panel's checkboxes represent, for icon priming and tests. */
        fun abilityLabels(): List<String> =
            databaseLabels.flatMap { tab -> tab.flatMap { row -> row.filter { it.isNotEmpty() } } }
    }
}
