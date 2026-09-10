package app.tweditor

import java.util.Locale

enum class InventoryDestination(val displayName: String, val messageKey: String) {
    SATCHEL("Satchel", "inventory.destination.satchel"),
    ALCHEMY("Alchemy", "inventory.destination.alchemy"),
    QUEST_ITEMS("Quest Items", "inventory.destination.questItems"),
    STORAGE("Storage", "inventory.destination.storage")
}

enum class InventoryPickerCategory(val displayName: String, val messageKey: String) {
    ALL("All", "picker.category.all"),
    WEAPONS("Weapons", "picker.category.weapons"),
    ARMOR("Armor", "picker.category.armor"),
    ALCHEMY("Alchemy", "picker.category.alchemy"),
    QUEST("Quest Items", "picker.category.quest"),
    OTHER("Other", "picker.category.other")
}

data class InventoryPickerItem(
    val resourceName: String,
    val name: String,
    val baseItem: Int,
    val category: InventoryPickerCategory,
    val iconResref: String?,
    val maxStack: Int,
    val details: String,
    val destination: InventoryDestination
)

object InventoryCatalog {
    fun pickerItems(
        environment: AppEnvironment,
        destination: InventoryDestination,
        query: String = "",
        category: InventoryPickerCategory = InventoryPickerCategory.ALL
    ): List<InventoryPickerItem> {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        return environment.itemTemplates.asSequence()
            .map { template -> pickerItem(template, destination) }
            .filter { item -> allowed(item.category, destination) }
            .filter { item -> category == InventoryPickerCategory.ALL || item.category == category }
            .filter { item ->
                normalizedQuery.isEmpty() ||
                    item.name.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                    item.resourceName.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                    item.details.lowercase(Locale.ROOT).contains(normalizedQuery)
            }
            .sortedWith(compareBy<InventoryPickerItem, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.resourceName })
            .toList()
    }

    fun category(template: ItemTemplate): InventoryPickerCategory = category(template.fieldList)

    private fun pickerItem(template: ItemTemplate, destination: InventoryDestination): InventoryPickerItem {
        val fields = template.fieldList
        return InventoryPickerItem(
            resourceName = template.resourceName,
            name = template.itemName,
            baseItem = template.baseItem,
            category = category(fields),
            iconResref = template.iconResref,
            maxStack = runCatching { fields.getInteger("MaxStack") }.getOrDefault(1).coerceAtLeast(1),
            details = runCatching { fields.getString("DescIdentified") }
                .getOrDefault("")
                .ifEmpty { runCatching { fields.getString("Description") }.getOrDefault("") },
            destination = destination
        )
    }

    private fun category(fields: DBList): InventoryPickerCategory {
        if (runCatching { fields.getInteger("QuestItem") }.getOrDefault(0) != 0) {
            return InventoryPickerCategory.QUEST
        }
        if (runCatching { fields.getInteger("AlchIngredient") }.getOrDefault(0) != 0) {
            return InventoryPickerCategory.ALCHEMY
        }
        return when (runCatching { fields.getInteger("BaseItem") }.getOrDefault(-1)) {
            29 -> InventoryPickerCategory.ARMOR
            1, 2, 3, 4, 5, 6, 7, 8, 9, 12, 17, 19 -> InventoryPickerCategory.WEAPONS
            else -> InventoryPickerCategory.OTHER
        }
    }

    private fun allowed(category: InventoryPickerCategory, destination: InventoryDestination): Boolean = when (destination) {
        // Weapons and armor belong in Equipment; the satchel carries the rest.
        InventoryDestination.SATCHEL -> category != InventoryPickerCategory.QUEST && category != InventoryPickerCategory.ALCHEMY &&
            category != InventoryPickerCategory.WEAPONS && category != InventoryPickerCategory.ARMOR
        InventoryDestination.ALCHEMY -> category == InventoryPickerCategory.ALCHEMY
        InventoryDestination.QUEST_ITEMS -> category == InventoryPickerCategory.QUEST
        InventoryDestination.STORAGE -> true
    }
}

class AddInventoryItemCommand(
    private val environment: AppEnvironment,
    private val destination: InventoryDestination,
    private val resourceName: String
) : EditorCommand {
    override fun validate(session: GameSession): List<LocalizedText> {
        val template = findTemplate(environment, resourceName)
            ?: return listOf(LocalizedText("inventory.command.templateGone"))
        val problems = ArrayList<LocalizedText>()
        val category = InventoryCatalog.category(template)
        when (destination) {
            InventoryDestination.SATCHEL -> {
                if (category == InventoryPickerCategory.QUEST) problems.add(LocalizedText("inventory.command.questDestination"))
                if (category == InventoryPickerCategory.ALCHEMY) problems.add(LocalizedText("inventory.command.alchemyDestination"))
                if (category == InventoryPickerCategory.WEAPONS || category == InventoryPickerCategory.ARMOR) {
                    problems.add(LocalizedText("inventory.command.weaponsDestination"))
                }
                if (InventoryViewState.from(session).satchel.cells.none { it == null }) {
                    problems.add(LocalizedText("inventory.command.satchelFull"))
                }
            }
            InventoryDestination.ALCHEMY -> {
                if (category != InventoryPickerCategory.ALCHEMY) problems.add(LocalizedText("inventory.command.onlyIngredients"))
                if (InventoryViewState.from(session).alchemy.cells.none { it == null }) {
                    problems.add(LocalizedText("inventory.command.alchemyFull"))
                }
            }
            InventoryDestination.QUEST_ITEMS -> {
                if (category != InventoryPickerCategory.QUEST) problems.add(LocalizedText("inventory.command.onlyQuestItems"))
            }
            InventoryDestination.STORAGE -> {
                if (EquipmentAccess.storageList(session) == null) {
                    problems.add(LocalizedText("inventory.command.noStorageRecord"))
                }
            }
        }
        if (EquipmentAccess.playerList(session) == null && destination != InventoryDestination.STORAGE) {
            problems.add(LocalizedText("inventory.command.noPlayerInventory"))
        }
        return problems
    }

    override fun apply(session: GameSession): AppliedEditorCommand {
        val snapshot = EquipmentAccess.snapshot(session)
        val template = requireNotNull(findTemplate(environment, resourceName)) { "The selected item template is unavailable" }
        val fields = template.fieldList.clone()
        fields.setInteger("Dropable", 1, DBElement.BYTE)
        fields.setInteger("Identified", 1, DBElement.BYTE)
        fields.setInteger("StackSize", runCatching { template.fieldList.getInteger("MaxStack") }.getOrDefault(1).coerceAtLeast(1), DBElement.WORD)

        val targetList: DBList
        when (destination) {
            InventoryDestination.STORAGE -> {
                targetList = requireNotNull(EquipmentAccess.storageList(session)) { "Storage is not initialized" }
                val itemList = targetList
                itemList.addElement(DBElement(DBElement.STRUCT, 0, "", fields))
            }
            else -> {
                targetList = requireNotNull(playerItemList(session, create = true)) { "The player inventory list is unavailable" }
                when (destination) {
                    InventoryDestination.SATCHEL -> setFirstCell(fields, InventoryViewState.from(session).satchel.cells, 0)
                    InventoryDestination.ALCHEMY -> setFirstCell(fields, InventoryViewState.from(session).alchemy.cells, 3)
                    InventoryDestination.QUEST_ITEMS -> Unit
                    InventoryDestination.STORAGE -> Unit
                }
                targetList.addElement(DBElement(DBElement.STRUCT, 0, "", fields))
            }
        }

        return AppliedEditorCommand(
            PendingChange(
                LocalizedText("inventory.pending.add", template.itemName, LocalizedText(destination.messageKey)),
                EvidenceLevel.STRUCTURALLY_VERIFIED
            )
        ) {
            EquipmentAccess.restore(session, snapshot)
        }
    }
}

class RemoveInventoryItemCommand(
    private val sourceId: String
) : EditorCommand {
    override fun validate(session: GameSession): List<LocalizedText> {
        val source = EquipmentAccess.record(session, sourceId)
            ?: return listOf(LocalizedText("inventory.command.itemGone"))
        return if (source.source == EquipmentRecordSource.EQUIPMENT) {
            listOf(LocalizedText("inventory.command.useEquipmentRemove"))
        } else {
            emptyList()
        }
    }

    override fun apply(session: GameSession): AppliedEditorCommand {
        val snapshot = EquipmentAccess.snapshot(session)
        val source = requireNotNull(EquipmentAccess.record(session, sourceId)) { "The selected inventory item is unavailable" }
        val name = inventoryItemView(source).name
        source.list.removeElement(source.element)
        val destination = if (source.source == EquipmentRecordSource.STORAGE) {
            LocalizedText("inventory.destination.storage")
        } else {
            LocalizedText("inventory.tab.carried")
        }
        return AppliedEditorCommand(
            PendingChange(
                LocalizedText("inventory.pending.remove", name, destination),
                EvidenceLevel.STRUCTURALLY_VERIFIED
            )
        ) {
            EquipmentAccess.restore(session, snapshot)
        }
    }
}

class EditInventoryItemCommand(
    private val environment: AppEnvironment,
    private val sourceId: String,
    private val values: EquipmentEditValues
) : EditorCommand {
    override fun validate(session: GameSession): List<LocalizedText> {
        val source = EquipmentAccess.record(session, sourceId)
            ?: return listOf(LocalizedText("inventory.command.itemGone"))
        if (source.source == EquipmentRecordSource.EQUIPMENT) {
            return listOf(LocalizedText("inventory.command.useEquipmentEdit"))
        }
        if (values.modelPart1 !in 0..255 || values.quality !in 0..255 || values.customCost < 0) {
            return listOf(LocalizedText("equipment.command.fieldsOutOfRange"))
        }
        return emptyList()
    }

    override fun apply(session: GameSession): AppliedEditorCommand {
        val snapshot = EquipmentAccess.snapshot(session)
        val source = requireNotNull(EquipmentAccess.record(session, sourceId)) { "The selected inventory item is unavailable" }
        val name = inventoryItemView(source).name
        val edit = ItemEdit(environment, source.fields)
        edit.setWeaponAbilities(values.selfAbilities, values.opponentAbilities)
        edit.setModelPart1(values.modelPart1)
        edit.setQuality(values.quality)
        edit.setCustomCost(values.customCost)
        edit.setWeaponType(values.weaponType)
        return AppliedEditorCommand(
            PendingChange(
                LocalizedText("inventory.pending.edit", name),
                EvidenceLevel.STRUCTURALLY_VERIFIED
            )
        ) {
            EquipmentAccess.restore(session, snapshot)
        }
    }
}

private fun playerItemList(session: GameSession, create: Boolean): DBList? {
    val player = EquipmentAccess.playerList(session) ?: return null
    val existing = player.getElement("ItemList")
    if (existing != null) return existing.getValue() as? DBList
    if (!create) return null
    val created = DBList(player.environment, 10)
    player.addElement(DBElement(DBElement.LIST, 0, "ItemList", created))
    return created
}

private fun setFirstCell(fields: DBList, cells: List<InventoryItemView?>, yOffset: Int) {
    val index = cells.indexOfFirst { it == null }
    if (index < 0) throw IllegalStateException("No empty inventory cell is available")
    fields.setInteger("Repos_PosX", index % 14, DBElement.WORD)
    fields.setInteger("Repos_PosY", yOffset + index / 14, DBElement.WORD)
}

private fun findTemplate(environment: AppEnvironment, resourceName: String): ItemTemplate? =
    environment.itemTemplates.firstOrNull { it.resourceName.equals(resourceName, ignoreCase = true) }

private fun inventoryItemView(source: EquipmentRecordRef, messages: EditorMessages = EditorMessages.english()): InventoryItemView {
    val localized = runCatching { source.fields.getString("LocalizedName") }.getOrDefault("")
    val template = runCatching { source.fields.getString("TemplateResRef") }.getOrDefault("")
    return InventoryItemView(
        id = source.id,
        name = localized.ifEmpty { template.ifEmpty { messages.get("item.unfamiliar") } },
        templateResRef = template,
        baseItem = runCatching { source.fields.getInteger("BaseItem") }.getOrDefault(0),
        modelPart = runCatching { source.fields.getInteger("ModelPart1") }.getOrDefault(0),
        count = runCatching { source.fields.getInteger("StackSize") }.getOrDefault(0),
        category = if (source.source == EquipmentRecordSource.STORAGE) messages.get("item.category.storage") else messages.get("item.category.item"),
        warning = if (localized.isEmpty() && template.isEmpty()) messages.get("item.warning.noName") else null
    )
}
