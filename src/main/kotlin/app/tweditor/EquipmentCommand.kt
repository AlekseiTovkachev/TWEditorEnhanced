package app.tweditor

import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicLong

enum class EquipmentCompatibilityStatus {
    COMPATIBLE,
    UNCERTAIN,
    INCOMPATIBLE
}

data class EquipmentCompatibility(
    val status: EquipmentCompatibilityStatus,
    val message: LocalizedText
)

data class EquipmentPickerItem(
    val resourceName: String,
    val name: String,
    val baseItem: Int,
    val iconResref: String?,
    val legalSlots: List<Int>,
    val warning: LocalizedText? = null
)

/**
 * The small catalog boundary used by the Inventory picker and drag targets.
 * Known illegal templates are filtered out; a missing baseitems.2da mapping is
 * retained as an explicitly warned choice instead of being guessed away.
 */
object EquipmentCatalog {
    fun pickerItems(
        environment: AppEnvironment,
        targetSlot: Int,
        query: String = ""
    ): List<EquipmentPickerItem> {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        val masks = WeaponSlots.equipableSlots(environment)
        return environment.itemTemplates.asSequence()
            .filter { it.itemName.isNotBlank() }
            .mapNotNull { template ->
                val mask = masks[template.baseItem]
                if (mask == null) {
                    EquipmentPickerItem(
                        resourceName = template.resourceName,
                        name = template.itemName,
                        baseItem = template.baseItem,
                        iconResref = template.iconResref,
                        legalSlots = emptyList(),
                        warning = LocalizedText("equipment.compat.unknownBaseItem", template.baseItem)
                    )
                } else {
                    val legalSlots = WeaponSlots.slotsFor(mask)
                        .filter { InventoryViewState.ACCEPTED_EQUIPMENT_SLOTS.any { accepted -> accepted.first == it } }
                    if (targetSlot !in legalSlots) {
                        null
                    } else {
                    EquipmentPickerItem(
                        resourceName = template.resourceName,
                        name = template.itemName,
                        baseItem = template.baseItem,
                        iconResref = template.iconResref,
                        legalSlots = legalSlots
                    )
                    }
                }
            }
            .filter { item ->
                normalizedQuery.isEmpty() ||
                    item.name.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                    item.resourceName.lowercase(Locale.ROOT).contains(normalizedQuery)
            }
            .sortedWith(compareBy<EquipmentPickerItem, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.resourceName })
            .toList()
    }

    fun compatibility(environment: AppEnvironment, item: InventoryItemView, targetSlot: Int): EquipmentCompatibility =
        compatibility(environment, item.baseItem, targetSlot, item.name)

    fun compatibility(
        environment: AppEnvironment,
        baseItem: Int,
        targetSlot: Int,
        itemName: Any = "item"
    ): EquipmentCompatibility {
        if (InventoryViewState.ACCEPTED_EQUIPMENT_SLOTS.none { it.first == targetSlot }) {
            return EquipmentCompatibility(
                EquipmentCompatibilityStatus.INCOMPATIBLE,
                LocalizedText("equipment.compat.unknownDestination", InventoryViewState.slotNameArg(targetSlot))
            )
        }

        val mask = WeaponSlots.equipableSlots(environment)[baseItem]
            ?: return EquipmentCompatibility(
                EquipmentCompatibilityStatus.UNCERTAIN,
                LocalizedText("equipment.compat.unknownEquipability", itemName, baseItem)
            )
        if (targetSlot !in WeaponSlots.slotsFor(mask)) {
            return EquipmentCompatibility(
                EquipmentCompatibilityStatus.INCOMPATIBLE,
                LocalizedText("equipment.compat.cannotGo", itemName, InventoryViewState.slotNameArg(targetSlot))
            )
        }
        return EquipmentCompatibility(
            EquipmentCompatibilityStatus.COMPATIBLE,
            LocalizedText("equipment.compat.compatible", InventoryViewState.slotNameArg(targetSlot))
        )
    }
}

enum class EquipmentRecordSource {
    EQUIPMENT,
    INVENTORY,
    STORAGE
}

internal data class EquipmentRecordRef(
    val id: String,
    val source: EquipmentRecordSource,
    val list: DBList,
    val element: DBElement,
    val fields: DBList,
    val index: Int
)

internal object EquipmentAccess {
    private val nextRecordToken = AtomicLong(1)
    private val recordTokens = WeakHashMap<DBElement, Long>()

    private fun recordToken(element: DBElement): Long = synchronized(recordTokens) {
        recordTokens[element] ?: nextRecordToken.getAndIncrement().also { recordTokens[element] = it }
    }

    fun recordId(source: String, index: Int, element: DBElement): String =
        "$source-$index-${recordToken(element)}"

    fun record(session: GameSession, id: String): EquipmentRecordRef? {
        val separator = id.indexOf('-')
        if (separator <= 0) return null
        val source = when (id.substring(0, separator)) {
            "equipment" -> EquipmentRecordSource.EQUIPMENT
            "inventory" -> EquipmentRecordSource.INVENTORY
            "storage" -> EquipmentRecordSource.STORAGE
            else -> return null
        }
        val recordPart = id.substring(separator + 1)
        val index = recordPart.substringBefore('-').toIntOrNull() ?: return null
        val identity = recordPart.substringAfter('-', missingDelimiterValue = "")
        val list = when (source) {
            EquipmentRecordSource.EQUIPMENT -> equipmentList(session, create = false)
            EquipmentRecordSource.INVENTORY -> playerList(session)?.getElement("ItemList")?.getValue() as? DBList
            EquipmentRecordSource.STORAGE -> storageList(session)
        } ?: return null
        if (index !in 0 until list.getElementCount()) return null
        val element = list.getElement(index)
        if (identity.isNotEmpty() && identity != recordToken(element).toString()) {
            return null
        }
        val fields = element.getValue() as? DBList ?: return null
        return EquipmentRecordRef(id, source, list, element, fields, index)
    }

    fun playerList(session: GameSession): DBList? = HeroData.playerFields(session)

    fun equipmentList(session: GameSession, create: Boolean): DBList? {
        val player = playerList(session) ?: return null
        val existing = player.getElement("Equip_ItemList")
        if (existing != null) {
            return existing.getValue() as? DBList
        }
        if (!create) return null
        val created = DBList(player.environment, 10)
        player.addElement(DBElement(DBElement.LIST, 0, "Equip_ItemList", created))
        return created
    }

    fun storageList(session: GameSession): DBList? {
        val top = session.smmDatabase?.getTopLevelStruct()?.getValue() as? DBList ?: return null
        val store = StorageAccess.findStorageRecord(top) ?: return null
        return store.getElement("ItemList")?.getValue() as? DBList
    }

    fun destinationItems(session: GameSession, slot: Int): List<DBElement> {
        val list = equipmentList(session, create = false) ?: return emptyList()
        return list.filter { element ->
            val fields = element.getValue() as? DBList ?: return@filter false
            runCatching { fields.getInteger("WeaponSlot") == slot }.getOrDefault(false)
        }
    }

    fun snapshot(session: GameSession): EquipmentSessionSnapshot {
        val ifo = requireNotNull(session.database?.getTopLevelStruct()) { "No module database is open" }.clone()
        val smm = session.smmDatabase?.getTopLevelStruct()?.clone()
        return EquipmentSessionSnapshot(ifo, smm)
    }

    fun restore(session: GameSession, snapshot: EquipmentSessionSnapshot) {
        requireNotNull(session.database) { "No module database is open" }.setTopLevelStruct(snapshot.ifo.clone())
        if (snapshot.smm != null) {
            requireNotNull(session.smmDatabase) { "No save metadata database is open" }.setTopLevelStruct(snapshot.smm.clone())
        }
    }
}

internal data class EquipmentSessionSnapshot(
    val ifo: DBElement,
    val smm: DBElement?
)

class EquipItemCommand(
    private val environment: AppEnvironment,
    private val sourceId: String,
    private val targetSlot: Int,
    private val replaceExisting: Boolean = false,
    private val confirmUncertain: Boolean = false
) : EditorCommand {
    override fun validate(session: GameSession): List<LocalizedText> {
        val problems = validateDestination(targetSlot).toMutableList()
        val source = EquipmentAccess.record(session, sourceId)
            ?: return problems + LocalizedText("equipment.command.itemGone")
        val item = itemView(source)
        val compatibility = EquipmentCatalog.compatibility(environment, item, targetSlot)
        if (compatibility.status == EquipmentCompatibilityStatus.INCOMPATIBLE) {
            problems.add(compatibility.message)
        } else if (compatibility.status == EquipmentCompatibilityStatus.UNCERTAIN && !confirmUncertain) {
            problems.add(compatibility.message)
        }

        val destination = EquipmentAccess.destinationItems(session, targetSlot)
            .filterNot { it === source.element }
        if (destination.isNotEmpty() && !replaceExisting) {
            problems.add(LocalizedText("equipment.command.slotOccupied", InventoryViewState.slotNameArg(targetSlot)))
        }
        if (source.source == EquipmentRecordSource.EQUIPMENT) {
            val current = runCatching { source.fields.getInteger("WeaponSlot") }.getOrDefault(-1)
            if (current == targetSlot) {
                problems.add(
                    LocalizedText("equipment.command.alreadyInSlot", item.name, InventoryViewState.slotNameArg(targetSlot))
                )
            }
        }
        return problems
    }

    override fun apply(session: GameSession): AppliedEditorCommand {
        val snapshot = EquipmentAccess.snapshot(session)
        val source = requireNotNull(EquipmentAccess.record(session, sourceId)) { "The selected item is no longer available" }
        val item = itemView(source)
        val equipment = requireNotNull(EquipmentAccess.equipmentList(session, create = true)) { "Equipment list is unavailable" }
        val destination = EquipmentAccess.destinationItems(session, targetSlot).filterNot { it === source.element }
        if (replaceExisting) {
            destination.forEach(equipment::removeElement)
        }

        if (source.source != EquipmentRecordSource.EQUIPMENT) {
            source.list.removeElement(source.element)
            equipment.addElement(source.element)
        }
        source.fields.setInteger("WeaponSlot", targetSlot)

        val uncertain = EquipmentCatalog.compatibility(environment, item, targetSlot).status == EquipmentCompatibilityStatus.UNCERTAIN
        val replacing = replaceExisting && destination.isNotEmpty()
        val key = when {
            uncertain && replacing -> "equipment.pending.replace.unverified"
            replacing -> "equipment.pending.replace"
            uncertain -> "equipment.pending.equip.unverified"
            else -> "equipment.pending.equip"
        }
        return AppliedEditorCommand(
            PendingChange(
                LocalizedText(key, item.name, InventoryViewState.slotNameArg(targetSlot)),
                if (uncertain) EvidenceLevel.UNVERIFIED else EvidenceLevel.STRUCTURALLY_VERIFIED
            )
        ) {
            EquipmentAccess.restore(session, snapshot)
        }
    }
}

class EquipTemplateCommand(
    private val environment: AppEnvironment,
    private val resourceName: String,
    private val targetSlot: Int,
    private val replaceExisting: Boolean = false,
    private val confirmUncertain: Boolean = false
) : EditorCommand {
    override fun validate(session: GameSession): List<LocalizedText> {
        val problems = validateDestination(targetSlot).toMutableList()
        val template = findTemplate(environment, resourceName)
            ?: return problems + LocalizedText("equipment.command.templateGone")
        val compatibility = EquipmentCatalog.compatibility(
            environment,
            template.baseItem,
            targetSlot,
            template.itemName
        )
        if (compatibility.status == EquipmentCompatibilityStatus.INCOMPATIBLE) {
            problems.add(compatibility.message)
        } else if (compatibility.status == EquipmentCompatibilityStatus.UNCERTAIN && !confirmUncertain) {
            problems.add(compatibility.message)
        }
        if (EquipmentAccess.destinationItems(session, targetSlot).isNotEmpty() && !replaceExisting) {
            problems.add(LocalizedText("equipment.command.slotOccupied", InventoryViewState.slotNameArg(targetSlot)))
        }
        return problems
    }

    override fun apply(session: GameSession): AppliedEditorCommand {
        val snapshot = EquipmentAccess.snapshot(session)
        val template = requireNotNull(findTemplate(environment, resourceName)) { "The selected item template is unavailable" }
        val equipment = requireNotNull(EquipmentAccess.equipmentList(session, create = true)) { "Equipment list is unavailable" }
        val destination = EquipmentAccess.destinationItems(session, targetSlot)
        if (replaceExisting) {
            destination.forEach(equipment::removeElement)
        }
        val fields = template.fieldList.clone()
        fields.setInteger("Dropable", 1, DBElement.BYTE)
        fields.setInteger("Identified", 1, DBElement.BYTE)
        fields.setInteger("StackSize", runCatching { template.fieldList.getInteger("MaxStack") }.getOrDefault(1).coerceAtLeast(1), DBElement.WORD)
        fields.setInteger("WeaponSlot", targetSlot, DBElement.BYTE)
        equipment.addElement(DBElement(DBElement.STRUCT, 0, "", fields))

        val uncertain = EquipmentCatalog.compatibility(environment, template.baseItem, targetSlot, template.itemName).status == EquipmentCompatibilityStatus.UNCERTAIN
        val replacing = replaceExisting && destination.isNotEmpty()
        val key = when {
            uncertain && replacing -> "equipment.pending.replaceWith.unverified"
            replacing -> "equipment.pending.replaceWith"
            uncertain -> "equipment.pending.equip.unverified"
            else -> "equipment.pending.equip"
        }
        return AppliedEditorCommand(
            PendingChange(
                LocalizedText(key, template.itemName, InventoryViewState.slotNameArg(targetSlot)),
                if (uncertain) EvidenceLevel.UNVERIFIED else EvidenceLevel.STRUCTURALLY_VERIFIED
            )
        ) {
            EquipmentAccess.restore(session, snapshot)
        }
    }
}

class RemoveEquipmentCommand(
    private val sourceId: String
) : EditorCommand {
    override fun validate(session: GameSession): List<LocalizedText> {
        val source = EquipmentAccess.record(session, sourceId)
            ?: return listOf(LocalizedText("equipment.command.equippedGone"))
        if (source.source != EquipmentRecordSource.EQUIPMENT) {
            return listOf(LocalizedText("equipment.command.onlyEquippedRemove"))
        }
        return emptyList()
    }

    override fun apply(session: GameSession): AppliedEditorCommand {
        val snapshot = EquipmentAccess.snapshot(session)
        val source = requireNotNull(EquipmentAccess.record(session, sourceId)) { "The selected equipped item is unavailable" }
        val name = itemView(source).name
        val equipment = requireNotNull(EquipmentAccess.equipmentList(session, create = false)) { "Equipment list is unavailable" }
        equipment.removeElement(source.element)
        return AppliedEditorCommand(
            PendingChange(LocalizedText("equipment.pending.remove", name), EvidenceLevel.STRUCTURALLY_VERIFIED)
        ) {
            EquipmentAccess.restore(session, snapshot)
        }
    }
}

data class EquipmentEditValues(
    val selfAbilities: List<WeaponAbility>,
    val opponentAbilities: List<WeaponAbility>,
    val modelPart1: Int,
    val quality: Int,
    val customCost: Int,
    val weaponType: String
)

fun readEquipmentEditValues(session: GameSession, sourceId: String): EquipmentEditValues? {
    val source = EquipmentAccess.record(session, sourceId) ?: return null
    if (source.source != EquipmentRecordSource.EQUIPMENT) return null
    return readItemEditValues(source)
}

fun readInventoryEditValues(session: GameSession, sourceId: String): EquipmentEditValues? {
    val source = EquipmentAccess.record(session, sourceId) ?: return null
    if (source.source == EquipmentRecordSource.EQUIPMENT) return null
    return readItemEditValues(source)
}

private fun readItemEditValues(source: EquipmentRecordRef): EquipmentEditValues {
    return EquipmentEditValues(
        selfAbilities = ItemEdit.readAbilities(source.fields, "WpnAbilitySelf"),
        opponentAbilities = ItemEdit.readAbilities(source.fields, "WpnAbilityOpp"),
        modelPart1 = runCatching { source.fields.getInteger("ModelPart1") }.getOrDefault(0),
        quality = runCatching { source.fields.getInteger("Quality") }.getOrDefault(0),
        customCost = runCatching { source.fields.getInteger("CustomCost") }.getOrDefault(0),
        weaponType = runCatching { source.fields.getString("WeaponType") }.getOrDefault("")
    )
}

class EditEquipmentCommand(
    private val environment: AppEnvironment,
    private val sourceId: String,
    private val values: EquipmentEditValues
) : EditorCommand {
    override fun validate(session: GameSession): List<LocalizedText> {
        val source = EquipmentAccess.record(session, sourceId)
            ?: return listOf(LocalizedText("equipment.command.equippedGone"))
        if (source.source != EquipmentRecordSource.EQUIPMENT) {
            return listOf(LocalizedText("equipment.command.onlyEquippedEdit"))
        }
        if (values.modelPart1 !in 0..255 || values.quality !in 0..255 || values.customCost < 0) {
            return listOf(LocalizedText("equipment.command.fieldsOutOfRange"))
        }
        return emptyList()
    }

    override fun apply(session: GameSession): AppliedEditorCommand {
        val snapshot = EquipmentAccess.snapshot(session)
        val source = requireNotNull(EquipmentAccess.record(session, sourceId)) { "The selected equipped item is unavailable" }
        val name = itemView(source).name
        val edit = ItemEdit(environment, source.fields)
        edit.setWeaponAbilities(values.selfAbilities, values.opponentAbilities)
        edit.setModelPart1(values.modelPart1)
        edit.setQuality(values.quality)
        edit.setCustomCost(values.customCost)
        edit.setWeaponType(values.weaponType)
        return AppliedEditorCommand(
            PendingChange(LocalizedText("equipment.pending.edit", name), EvidenceLevel.STRUCTURALLY_VERIFIED)
        ) {
            EquipmentAccess.restore(session, snapshot)
        }
    }
}

private fun validateDestination(targetSlot: Int): List<LocalizedText> {
    return if (InventoryViewState.ACCEPTED_EQUIPMENT_SLOTS.any { it.first == targetSlot }) {
        emptyList()
    } else {
        listOf(LocalizedText("equipment.compat.unknownDestination", InventoryViewState.slotNameArg(targetSlot)))
    }
}

private fun findTemplate(environment: AppEnvironment, resourceName: String): ItemTemplate? =
    environment.itemTemplates.firstOrNull { it.resourceName.equals(resourceName, ignoreCase = true) }

private fun itemView(source: EquipmentRecordRef, messages: EditorMessages = EditorMessages.english()): InventoryItemView {
    val localized = runCatching { source.fields.getString("LocalizedName") }.getOrDefault("")
    val template = runCatching { source.fields.getString("TemplateResRef") }.getOrDefault("")
    return InventoryItemView(
        id = source.id,
        name = localized.ifEmpty { template.ifEmpty { messages.get("item.unfamiliar") } },
        templateResRef = template,
        baseItem = runCatching { source.fields.getInteger("BaseItem") }.getOrDefault(0),
        modelPart = runCatching { source.fields.getInteger("ModelPart1") }.getOrDefault(0),
        count = runCatching { source.fields.getInteger("StackSize") }.getOrDefault(0),
        category = "Equipment",
        warning = if (localized.isEmpty() && template.isEmpty()) messages.get("item.warning.noName") else null
    )
}
