package app.tweditor

import java.awt.GridLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTabbedPane

class AttributesPanel(private val session: GameSession, private val environment: AppEnvironment) :
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
        for (tab in 0 until tabs) {
            val panel = JPanel(GridLayout(0, cols, 5, 5))
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    if (fieldNames[tab][row][col].isNotEmpty()) {
                        val field = JCheckBox(fieldNames[tab][row][col])
                        field.actionCommand = Integer.toString(tab * 100 + row * 10 + col)
                        field.addActionListener(this)
                        this.fields[tab][row][col] = field
                        panel.add(field)
                        labelMap[databaseLabels[tab][row][col]] = field
                    } else {
                        panel.add(JLabel())
                    }
                }
            }

            tabbedPane.addTab(tabNames[tab], panel)
        }

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
                    var fieldList = DBList(environment, 2)
                    fieldList.addElement(DBElement(10, 0, "RnAbName", abilityLabel))
                    fieldList.addElement(DBElement(0, 0, "RnAbStk", 0))
                    list.addElement(DBElement(14, 48879, "", fieldList))

                    for (associated in associatedLabels) {
                        if (abilityLabel == associated[0]) {
                            val associatedLabel = associated[1]
                            fieldList = DBList(environment, 2)
                            fieldList.addElement(DBElement(10, 0, "RnAbName", associatedLabel))
                            fieldList.addElement(DBElement(0, 0, "RnAbStk", 0))
                            list.addElement(DBElement(14, 48879, "", fieldList))
                            break
                        }
                    }

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

                    for (associated in associatedLabels) {
                        if (abilityLabel == associated[0]) {
                            val associatedLabel = associated[1]
                            count = list.getElementCount()
                            for (j in 0 until count) {
                                val fieldList = list.getElement(j).getValue() as DBList
                                val name = fieldList.getString("RnAbName")
                                if (name == associatedLabel) {
                                    list.removeElement(j)
                                    session.setDataModified(true)
                                    break
                                }
                            }

                            break
                        }
                    }

                    if (row == 0 && levels[tab] == col) {
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
        private val tabNames = arrayOf("Strength", "Dexterity", "Stamina", "Intelligence")

        private val fieldNames = arrayOf(
            arrayOf(arrayOf("Level 1", "Level 2", "Level 3", "Level 4", "Level 5"), arrayOf("Buzz", "Position", "Vigor", "Bleeding Resistance", "Wound Resistance"), arrayOf("True Grit", "Regeneration", "Knockdown Resistance", "Stone Skin", "Added Vitality"), arrayOf("", "Brawl", "Survival Instinct", "Aggression", "")),
            arrayOf(arrayOf("Level 1", "Level 2", "Level 3", "Level 4", "Level 5"), arrayOf("Flaying", "Deflect Arrows", "Bleeding Resistance", "Finesse", "Vigilance"), arrayOf("Predator", "Repel", "Agility", "Feint", "Precision"), arrayOf("", "Fistfight", "Limit Incineration", "Incineration Resistance", "")),
            arrayOf(arrayOf("Level 1", "Level 2", "Level 3", "Level 4", "Level 5"), arrayOf("Heavyweight", "Absorption", "Endurance Regeneration", "Stun Resistance", "Potion Tolerance"), arrayOf("Mutation", "Poison Resistance", "Pain Resistance", "Brawn", "Added Endurance"), arrayOf("", "Endurance Regeneration", "Revive", "Altered Metabolism", "")),
            arrayOf(arrayOf("Level 1", "Level 2", "Level 3", "Level 4", "Level 5"), arrayOf("Potion Brewing", "Herbalism", "Cleansing Ritual", "Focus", "Mental Endurance"), arrayOf("Rising Moon", "Monster Lore", "Ingredient Extraction", "Life Ritual", "Intensity"), arrayOf("", "Oil Preparation", "Bomb Preparation", "Magic Frenzy", ""))
        )

        private val databaseLabels = arrayOf(
            arrayOf(arrayOf("Strength1", "Strength2", "Strength3", "Strength4", "Strength5"), arrayOf("Strength1 Upgrade1", "Strength2 Upgrade1", "Strength3 Upgrade1", "Strength4 Upgrade1", "Strength5 Upgrade1"), arrayOf("Strength1 Upgrade2", "Strength2 Upgrade2", "Strength3 Upgrade2", "Strength4 Upgrade2", "Strength5 Upgrade2"), arrayOf("", "Strength2 Upgrade3", "Strength3 Upgrade3", "Strength4 Upgrade3", "")),
            arrayOf(arrayOf("Dexterity1", "Dexterity2", "Dexterity3", "Dexterity4", "Dexterity5"), arrayOf("Dexterity1 Upgrade1", "Dexterity2 Upgrade1", "Dexterity3 Upgrade1", "Dexterity4 Upgrade1", "Dexterity5 Upgrade1"), arrayOf("Dexterity1 Upgrade2", "Dexterity2 Upgrade2", "Dexterity3 Upgrade2", "Dexterity4 Upgrade2", "Dexterity5 Upgrade2"), arrayOf("", "Dexterity2 Upgrade3", "Dexterity3 Upgrade3", "Dexterity4 Upgrade3", "")),
            arrayOf(arrayOf("Endurance1", "Endurance2", "Endurance3", "Endurance4", "Endurance5"), arrayOf("Endurance1 Upgrade1", "Endurance2 Upgrade1", "Endurance3 Upgrade1", "Endurance4 Upgrade1", "Endurance5 Upgrade1"), arrayOf("Endurance1 Upgrade2", "Endurance2 Upgrade2", "Endurance3 Upgrade2", "Endurance4 Upgrade2", "Endurance5 Upgrade2"), arrayOf("", "Endurance2 Upgrade3", "Endurance3 Upgrade3", "Endurance4 Upgrade3", "")),
            arrayOf(arrayOf("Intelligence1", "Intelligence2", "Intelligence3", "Intelligence4", "Intelligence5"), arrayOf("Intelligence1 Upgrade1", "Intelligence2 Upgrade1", "Intelligence3 Upgrade1", "Intelligence4 Upgrade1", "Intelligence5 Upgrade1"), arrayOf("Intelligence1 Upgrade2", "Intelligence2 Upgrade2", "Intelligence3 Upgrade2", "Intelligence4 Upgrade2", "Intelligence5 Upgrade2"), arrayOf("", "Intelligence2 Upgrade3", "Intelligence3 Upgrade3", "Intelligence4 Upgrade3", ""))
        )

        private val associatedLabels = arrayOf(
            arrayOf("Dexterity1 Upgrade1", "Skinning"),
            arrayOf("Intelligence2 Upgrade1", "HerbGathering"),
            arrayOf("Intelligence2 Upgrade3", "GreaseMaking"),
            arrayOf("Intelligence3 Upgrade1", "RitualOfPurify"),
            arrayOf("Intelligence3 Upgrade2", "Anatomy"),
            arrayOf("Intelligence3 Upgrade3", "BombMaking"),
            arrayOf("Intelligence4 Upgrade2", "RitualOfLife")
        )
    }
}
