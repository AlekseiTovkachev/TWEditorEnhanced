package app.tweditor

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField

class ItemEditDialog(
    parent: JFrame?,
    private val session: GameSession,
    environment: AppEnvironment,
    label: String,
    fields: DBList
) : JDialog(parent, "Edit Item: $label", true), ActionListener {
    private val itemEdit = ItemEdit(environment, fields)
    private val selfModel = DefaultListModel<String>()
    private val oppModel = DefaultListModel<String>()
    private val selfField = JList(selfModel)
    private val oppField = JList(oppModel)
    private val selfInput = JComboBox<String>()
    private val oppInput = JComboBox<String>()
    private val templateInput = JComboBox<ItemTemplate>()
    private val modelPart1Field = NumericField(4)
    private val qualityField = NumericField(4)
    private val customCostField = NumericField(8)
    private val weaponTypeField = JTextField(16)
    private val originalStacks: MutableMap<String, Int>
    private val originalModelPart1: Int
    private val originalQuality: Int
    private val originalCustomCost: Int
    private val originalWeaponType: String

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE

        selfField.selectionMode = 0
        oppField.selectionMode = 0

        originalStacks = HashMap()
        for (ability in itemEdit.weaponAbilitiesSelf) {
            selfModel.addElement(ability.name)
            originalStacks[ability.name] = ability.stack
        }
        for (ability in itemEdit.weaponAbilitiesOpp) {
            oppModel.addElement(ability.name)
            originalStacks[ability.name] = ability.stack
        }

        val knownAbilities = sortedSetOf<String>()
        knownAbilities.addAll(selfModel.toArray().map { it as String })
        knownAbilities.addAll(oppModel.toArray().map { it as String })
        for (template in environment.itemTemplates) {
            ItemEdit.readAbilities(template.fieldList, "WpnAbilitySelf").forEach { knownAbilities.add(it.name) }
            ItemEdit.readAbilities(template.fieldList, "WpnAbilityOpp").forEach { knownAbilities.add(it.name) }
        }

        selfInput.isEditable = true
        selfInput.model = DefaultComboBoxModel(knownAbilities.toTypedArray())
        oppInput.isEditable = true
        oppInput.model = DefaultComboBoxModel(knownAbilities.toTypedArray())

        modelPart1Field.setValue(itemEdit.modelPart1)
        qualityField.setValue(itemEdit.quality)
        customCostField.setValue(itemEdit.customCost)
        weaponTypeField.text = itemEdit.weaponType
        originalModelPart1 = itemEdit.modelPart1
        originalQuality = itemEdit.quality
        originalCustomCost = itemEdit.customCost
        originalWeaponType = itemEdit.weaponType

        templateInput.model = DefaultComboBoxModel(
            environment.itemTemplates
                .filter {
                    ItemEdit.readAbilities(it.fieldList, "WpnAbilitySelf").isNotEmpty() ||
                        ItemEdit.readAbilities(it.fieldList, "WpnAbilityOpp").isNotEmpty()
                }
                .toTypedArray()
        )

        val contentPane = JPanel()
        contentPane.layout = BoxLayout(contentPane, BoxLayout.PAGE_AXIS)
        contentPane.border = BorderFactory.createEmptyBorder(15, 15, 15, 15)

        val abilityPane = JPanel(GridLayout(1, 2, 10, 0))
        abilityPane.add(buildAbilityPane("Self abilities (wielder)", selfField, selfInput, "add self ability", "remove self ability"))
        abilityPane.add(buildAbilityPane("Opponent abilities (on hit)", oppField, oppInput, "add opp ability", "remove opp ability"))
        abilityPane.maximumSize = Dimension(Int.MAX_VALUE.toInt(), abilityPane.preferredSize.height)
        contentPane.add(abilityPane)
        contentPane.add(Box.createVerticalStrut(10))

        val statPane = JPanel(GridLayout(2, 4, 5, 5))
        statPane.add(JLabel("ModelPart1", 2))
        statPane.add(modelPart1Field)
        statPane.add(JLabel("Quality", 2))
        statPane.add(qualityField)
        statPane.add(JLabel("CustomCost", 2))
        statPane.add(customCostField)
        statPane.add(JLabel("WeaponType", 2))
        statPane.add(weaponTypeField)
        statPane.maximumSize = Dimension(Int.MAX_VALUE.toInt(), statPane.preferredSize.height)
        contentPane.add(statPane)
        contentPane.add(Box.createVerticalStrut(10))

        val copyPane = JPanel()
        copyPane.add(JLabel("Copy power from template", 2))
        copyPane.add(templateInput)
        val copyButton = JButton("Copy Abilities")
        copyButton.addActionListener(this)
        copyButton.actionCommand = "copy abilities"
        copyPane.add(copyButton)
        copyPane.maximumSize = Dimension(Int.MAX_VALUE.toInt(), copyPane.preferredSize.height)
        contentPane.add(copyPane)
        contentPane.add(Box.createVerticalStrut(10))

        val buttonPane = JPanel()
        val applyButton = JButton("Apply")
        applyButton.addActionListener(this)
        applyButton.actionCommand = "apply"
        buttonPane.add(applyButton)
        val cancelButton = JButton("Cancel")
        cancelButton.addActionListener(this)
        cancelButton.actionCommand = "cancel"
        buttonPane.add(cancelButton)
        buttonPane.maximumSize = Dimension(Int.MAX_VALUE.toInt(), buttonPane.preferredSize.height)
        contentPane.add(buttonPane)

        setContentPane(contentPane)
    }

    private fun buildAbilityPane(title: String, list: JList<String>, input: JComboBox<String>, addAction: String, removeAction: String): JPanel {
        val pane = JPanel(BorderLayout())
        pane.add(JLabel(title, 0), "North")
        pane.add(JScrollPane(list), "Center")

        val buttonPane = JPanel()
        val addButton = JButton("Add")
        addButton.addActionListener(this)
        addButton.actionCommand = addAction
        buttonPane.add(addButton)
        val removeButton = JButton("Remove")
        removeButton.addActionListener(this)
        removeButton.actionCommand = removeAction
        buttonPane.add(removeButton)
        buttonPane.add(input)
        pane.add(buttonPane, "South")
        return pane
    }

    override fun actionPerformed(ae: ActionEvent?) {
        try {
            when (ae!!.actionCommand) {
                "add self ability" -> addAbility(selfModel, selfInput)
                "add opp ability" -> addAbility(oppModel, oppInput)
                "remove self ability" -> removeAbility(selfModel, selfField)
                "remove opp ability" -> removeAbility(oppModel, oppField)
                "copy abilities" -> copyAbilities()
                "apply" -> applyEdits()
                "cancel" -> dispose()
            }
        } catch (exc: DBException) {
            Main.logException("Unable to update database field", exc)
        } catch (exc: Throwable) {
            Main.logException("Exception while processing action event", exc)
        }
    }

    private fun addAbility(model: DefaultListModel<String>, input: JComboBox<String>) {
        val name = (input.editor.item as String).trim()
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "You must enter an ability name", "No ability", 0)
            return
        }
        if (model.contains(name)) {
            JOptionPane.showMessageDialog(this, "This ability is already in the list", "Duplicate ability", 0)
            return
        }
        model.addElement(name)
    }

    private fun removeAbility(model: DefaultListModel<String>, list: JList<String>) {
        val sel = list.selectedIndex
        if (sel < 0) {
            JOptionPane.showMessageDialog(this, "You must select an ability to remove", "No ability selected", 0)
            return
        }
        model.removeElementAt(sel)
    }

    private fun copyAbilities() {
        val template = templateInput.selectedItem
        if (template !is ItemTemplate) {
            JOptionPane.showMessageDialog(this, "You must select a template to copy from", "No template selected", 0)
            return
        }

        selfModel.clear()
        oppModel.clear()
        for (ability in ItemEdit.readAbilities(template.fieldList, "WpnAbilitySelf")) {
            selfModel.addElement(ability.name)
        }
        for (ability in ItemEdit.readAbilities(template.fieldList, "WpnAbilityOpp")) {
            oppModel.addElement(ability.name)
        }
    }

    private fun applyEdits() {
        val self = ArrayList<WeaponAbility>()
        for (i in 0 until selfModel.size()) {
            val name = selfModel.getElementAt(i)
            self.add(WeaponAbility(name, originalStacks[name] ?: 1))
        }
        val opp = ArrayList<WeaponAbility>()
        for (i in 0 until oppModel.size()) {
            val name = oppModel.getElementAt(i)
            opp.add(WeaponAbility(name, originalStacks[name] ?: 1))
        }
        itemEdit.setWeaponAbilities(self, opp)

        if (modelPart1Field.getValue() != originalModelPart1) {
            itemEdit.setModelPart1(modelPart1Field.getValue())
        }
        if (qualityField.getValue() != originalQuality) {
            itemEdit.setQuality(qualityField.getValue())
        }
        if (customCostField.getValue() != originalCustomCost) {
            itemEdit.setCustomCost(customCostField.getValue())
        }

        val weaponType = weaponTypeField.text.trim()
        if (weaponType != originalWeaponType && weaponType.isNotEmpty()) {
            itemEdit.setWeaponType(weaponType)
        }

        session.setDataModified(true)
        Main.mainWindow?.setTitle(null)
        dispose()
    }

    companion object {
        fun showDialog(parent: JFrame?, session: GameSession, environment: AppEnvironment, label: String, fields: DBList) {
            val dialog = ItemEditDialog(parent, session, environment, label, fields)
            dialog.pack()
            dialog.setLocationRelativeTo(parent)
            dialog.isVisible = true
        }
    }
}
