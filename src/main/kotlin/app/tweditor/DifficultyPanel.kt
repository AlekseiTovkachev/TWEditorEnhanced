package app.tweditor

import java.awt.GridLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.ButtonGroup
import javax.swing.JPanel
import javax.swing.JRadioButton

class DifficultyPanel(private val session: GameSession, private val environment: AppEnvironment) :
    JPanel(), ActionListener {
    private val easyButton = JRadioButton(EASY)
    private val mediumButton = JRadioButton(MEDIUM)
    private val hardButton = JRadioButton(HARD)
    private var level: String = ""

    init {
        easyButton.actionCommand = EASY
        easyButton.addActionListener(this)

        mediumButton.actionCommand = MEDIUM
        mediumButton.addActionListener(this)

        hardButton.actionCommand = HARD
        hardButton.addActionListener(this)

        val group = ButtonGroup()
        group.add(easyButton)
        group.add(mediumButton)
        group.add(hardButton)

        val panel = JPanel(GridLayout(0, 3, 5, 5))
        panel.add(easyButton)
        panel.add(mediumButton)
        panel.add(hardButton)

        panel.border = javax.swing.BorderFactory.createTitledBorder("Level")

        add(panel)
    }

    private fun processCharAbilities(list: DBList, cmd: String) {
        try {
            val abilityList = list.getElement("CharAbilities")!!.getValue() as DBList

            if (level == EASY || level == MEDIUM) {
                for (i in 0 until abilityList.getElementCount()) {
                    val fieldList = abilityList.getElement(i).getValue() as DBList

                    val e = fieldList.getElement(0)
                    val value = e.getValue()
                    if (value == EASY_DIFF || value == MEDIUM_DIFF) {
                        if (cmd == EASY) {
                            fieldList.setString("RnAbName", EASY_DIFF)
                        } else if (cmd == MEDIUM) {
                            fieldList.setString("RnAbName", MEDIUM_DIFF)
                        } else {
                            abilityList.removeElement(i)
                        }

                        break
                    }
                }
            } else {
                for (i in 0 until abilityList.getElementCount()) {
                    val fieldList = abilityList.getElement(i).getValue() as DBList

                    val e = fieldList.getElement(0)
                    val value = e.getValue()
                    if (value == "StyleSilverGroup1") {
                        val levelList = DBList(environment, 2)
                        if (cmd == EASY) {
                            levelList.addElement(DBElement(10, 0, "RnAbName", EASY_DIFF))
                        } else if (cmd == MEDIUM) {
                            levelList.addElement(DBElement(10, 0, "RnAbName", MEDIUM_DIFF))
                        }
                        levelList.addElement(DBElement(0, 0, "RnAbStk", 0))

                        abilityList.insertElement(i + 1, DBElement(14, 48879, "", levelList))
                        break
                    }
                }
            }

            level = cmd
            session.setDataModified(true)
        } catch (exc: DBException) {
            Main.logException("Unable to update database field", exc)
        } catch (exc: Throwable) {
            Main.logException("Exception while processing action event", exc)
        }
    }

    private fun processGameDiffSetting(list: DBList, cmd: String) {
        try {
            if (cmd == EASY) {
                list.setInteger("GameDiffSetting", EASY_INT)
            } else if (cmd == MEDIUM) {
                list.setInteger("GameDiffSetting", MEDIUM_INT)
            } else {
                list.setInteger("GameDiffSetting", HARD_INT)
            }
        } catch (exc: DBException) {
            Main.logException("Unable to update database field", exc)
        } catch (exc: Throwable) {
            Main.logException("Exception while processing action event", exc)
        }
    }

    override fun actionPerformed(ae: ActionEvent?) {
        if (ae!!.source !is JRadioButton || session.isDataChanging()) {
            return
        }

        val cmd = ae.actionCommand!!

        if (cmd == level) {
            return
        }

        val top = session.database!!.getTopLevelStruct()!!.getValue() as DBList
        val mod = top.getElement("Mod_PlayerList")!!.getValue() as DBList
        val modPlayerList = mod.getElement(0).getValue() as DBList
        processCharAbilities(modPlayerList, cmd)

        val playerList = session.playerDatabase!!.getTopLevelStruct()!!.getValue() as DBList
        processCharAbilities(playerList, cmd)

        val smm = session.smmDatabase!!.getTopLevelStruct()!!.getValue() as DBList
        processGameDiffSetting(smm, cmd)
    }

    fun setFields(list: DBList) {
        level = HARD
        hardButton.isSelected = true

        val element = list.getElement("CharAbilities")
        if (element == null) {
            throw DBException("CharAbilities field not found")
        }
        val abilityList = element.getValue() as DBList
        for (abilityElement in abilityList) {
            val fieldList = abilityElement.getValue() as DBList

            val e = fieldList.getElement(0)
            val value = e.getValue()
            if (value == EASY_DIFF) {
                level = EASY
                easyButton.isSelected = true
                break
            } else if (value == MEDIUM_DIFF) {
                level = MEDIUM
                mediumButton.isSelected = true
                break
            }
        }
    }

    fun getFields(list: DBList) {
    }

    companion object {
        private const val EASY = "Easy"
        private const val MEDIUM = "Medium"
        private const val HARD = "Hard"

        private const val EASY_DIFF = "Difficulty_easy"
        private const val MEDIUM_DIFF = "Difficulty_normal"

        private const val EASY_INT = 0
        private const val MEDIUM_INT = 1
        private const val HARD_INT = 2
    }
}
