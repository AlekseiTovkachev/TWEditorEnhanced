package app.tweditor

/** Immutable, presentation-safe inventory facts read from the current session. */
data class InventoryItemView(
    val id: String,
    val name: String,
    val templateResRef: String,
    val baseItem: Int,
    val modelPart: Int,
    val count: Int,
    val category: String,
    val warning: String? = null
)

data class InventoryContainerView(
    val title: String,
    val maxCells: Int,
    val cells: List<InventoryItemView?>,
    val overflow: List<InventoryItemView>
)

data class EquipmentDestinationView(
    val slot: Int,
    val name: String,
    val items: List<InventoryItemView>,
    val warning: String? = null
) {
    val item: InventoryItemView? get() = items.firstOrNull()
}

data class InventoryViewState(
    val storage: List<InventoryItemView>,
    val equipment: List<EquipmentDestinationView>,
    val questItems: List<InventoryItemView>,
    val satchel: InventoryContainerView,
    val alchemy: InventoryContainerView,
    val unplacedItems: List<InventoryItemView>,
    val warnings: List<String>
) {
    val allItems: List<InventoryItemView>
        get() = storage + equipment.flatMap { it.items } + questItems +
            satchel.cells.filterNotNull() + alchemy.cells.filterNotNull() +
            satchel.overflow + alchemy.overflow + unplacedItems

    companion object {
        internal val ACCEPTED_EQUIPMENT_SLOTS = listOf(
            WeaponSlots.BACK_NORMAL to "equipment.slot.steelSword",
            WeaponSlots.BACK_SILVER to "equipment.slot.silverSword",
            WeaponSlots.SHORT_1 to "equipment.slot.shortWeapon1",
            WeaponSlots.SHORT_2 to "equipment.slot.shortWeapon2",
            WeaponSlots.BIG_WEAPON to "equipment.slot.bigWeapon",
            WeaponSlots.ARMOR to "equipment.slot.armor",
            WeaponSlots.TROPHY to "equipment.slot.trophy",
            WeaponSlots.FOREARM_RIGHT to "equipment.slot.ringRight",
            WeaponSlots.FOREARM_LEFT to "equipment.slot.ringLeft",
            WeaponSlots.ELIXIR_1 to "equipment.slot.elixir1",
            WeaponSlots.ELIXIR_2 to "equipment.slot.elixir2",
            WeaponSlots.ELIXIR_3 to "equipment.slot.elixir3"
        )

        fun slotNameKey(slot: Int): String? = ACCEPTED_EQUIPMENT_SLOTS.firstOrNull { it.first == slot }?.second

        /**
         * A renderable slot-name argument for command messages: a nested
         * LocalizedText for the editor-translated destinations, the game's own
         * weaponslots.2da name for the rest.
         */
        fun slotNameArg(slot: Int): Any =
            slotNameKey(slot)?.let { LocalizedText(it) } ?: WeaponSlots.name(slot)

        fun from(session: GameSession, messages: EditorMessages = EditorMessages.english()): InventoryViewState {
            val warnings = ArrayList<String>()
            val player = HeroData.playerFields(session)
            val quest = ArrayList<InventoryItemView>()
            val satchelCells = arrayOfNulls<InventoryItemView>(42)
            val alchemyCells = arrayOfNulls<InventoryItemView>(42)
            val satchelOverflow = ArrayList<InventoryItemView>()
            val alchemyOverflow = ArrayList<InventoryItemView>()
            val unplaced = ArrayList<InventoryItemView>()

            val itemList = player?.getElement("ItemList")?.getValue() as? DBList
            if (itemList != null) {
                for ((index, element) in itemList.withIndex()) {
                    val fields = element.getValue() as? DBList ?: continue
                    val item = itemView(EquipmentAccess.recordId("inventory", index, element), fields, messages, warnings)
                    if (fields.integerOrZero("QuestItem") != 0) {
                        quest.add(item)
                        continue
                    }
                    val x = fields.integerOrZero("Repos_PosX")
                    val y = fields.integerOrZero("Repos_PosY")
                    when {
                        y in 0..2 && x in 0..13 -> place(item, satchelCells, y * 14 + x, satchelOverflow, warnings, "Satchel")
                        y in 3..5 && x in 0..13 -> place(item, alchemyCells, (y - 3) * 14 + x, alchemyOverflow, warnings, "Alchemy")
                        else -> {
                            unplaced.add(item.copy(warning = item.warning ?: "Unfamiliar inventory position preserved"))
                            warnings.add("Preserved ${item.name} with an unfamiliar inventory position.")
                        }
                    }
                }
            }

            val equipmentGroups = LinkedHashMap<Int, MutableList<InventoryItemView>>()
            val unknownEquipment = ArrayList<InventoryItemView>()
            val equipList = player?.getElement("Equip_ItemList")?.getValue() as? DBList
            if (equipList != null) {
                for ((index, element) in equipList.withIndex()) {
                    val fields = element.getValue() as? DBList ?: continue
                    if (fields.integerOrZero("BaseItem") == 36) continue
                    val item = itemView(EquipmentAccess.recordId("equipment", index, element), fields, messages, warnings)
                    val slot = fields.integerOrZero("WeaponSlot")
                    if (ACCEPTED_EQUIPMENT_SLOTS.any { it.first == slot }) {
                        equipmentGroups.getOrPut(slot) { ArrayList() }.add(item)
                    } else {
                        unknownEquipment.add(item.copy(warning = item.warning ?: "Unfamiliar equipment slot preserved"))
                        warnings.add("Preserved ${item.name} outside the accepted paperdoll destinations.")
                    }
                }
            }

            val equipment = ACCEPTED_EQUIPMENT_SLOTS.map { (slot, nameKey) ->
                val items = equipmentGroups[slot]?.toList().orEmpty()
                EquipmentDestinationView(
                    slot,
                    messages.get(nameKey),
                    items,
                    if (items.size > 1) "Multiple records share this destination; all are preserved" else items.firstOrNull()?.warning
                )
            }

            val storage = ArrayList<InventoryItemView>()
            val smmList = session.smmDatabase?.getTopLevelStruct()?.getValue() as? DBList
            val storageRecord = smmList?.let(StorageAccess::findStorageRecord)
            val storageItems = storageRecord?.getElement("ItemList")?.getValue() as? DBList
            if (storageItems != null) {
                for ((index, element) in storageItems.withIndex()) {
                    val fields = element.getValue() as? DBList ?: continue
                    storage.add(itemView(EquipmentAccess.recordId("storage", index, element), fields, messages, warnings))
                }
            } else if (session.saveDatabase != null) {
                warnings.add("This save has no storage chest record; no storage items were normalized.")
            }

            val unplacedAll = unplaced + unknownEquipment
            return InventoryViewState(
                storage = storage.toList(),
                equipment = equipment,
                questItems = quest.toList(),
                satchel = InventoryContainerView(messages.get("inventory.container.satchel"), 42, satchelCells.toList(), satchelOverflow.toList()),
                alchemy = InventoryContainerView(messages.get("inventory.container.alchemy"), 42, alchemyCells.toList(), alchemyOverflow.toList()),
                unplacedItems = unplacedAll,
                warnings = warnings.distinct()
            )
        }

        private fun place(
            item: InventoryItemView,
            cells: Array<InventoryItemView?>,
            index: Int,
            overflow: MutableList<InventoryItemView>,
            warnings: MutableList<String>,
            container: String
        ) {
            if (cells[index] == null) {
                cells[index] = item
            } else {
                val preserved = item.copy(warning = item.warning ?: "Duplicate cell record preserved")
                overflow.add(preserved)
                warnings.add("Preserved a duplicate $container cell record for ${item.name}.")
            }
        }

        private fun itemView(id: String, fields: DBList, messages: EditorMessages, warnings: MutableList<String>): InventoryItemView {
            val localized = fields.stringOrEmpty("LocalizedName")
            val template = fields.stringOrEmpty("TemplateResRef")
            val name = localized.ifEmpty { template.ifEmpty { messages.get("item.unfamiliar") } }
            val warning = if (localized.isEmpty() && template.isEmpty()) {
                warnings.add("An unfamiliar item record was preserved without a display name.")
                messages.get("item.warning.noName")
            } else null
            val category = when {
                fields.integerOrZero("QuestItem") != 0 -> messages.get("item.category.quest")
                fields.integerOrZero("AlchIngredient") != 0 -> messages.get("item.category.alchemy")
                else -> messages.get("item.category.item")
            }
            return InventoryItemView(
                id = id,
                name = name,
                templateResRef = template,
                baseItem = fields.integerOrZero("BaseItem"),
                modelPart = fields.integerOrZero("ModelPart1"),
                count = fields.integerOrZero("StackSize"),
                category = category,
                warning = warning
            )
        }

        private fun DBList.integerOrZero(label: String): Int = runCatching { getInteger(label) }.getOrDefault(0)
        private fun DBList.stringOrEmpty(label: String): String = runCatching { getString(label) }.getOrDefault("")
    }
}

enum class InventoryLayout { WIDE, NARROW }

fun inventoryLayoutFor(widthDp: Int): InventoryLayout =
    if (widthDp < 1080) InventoryLayout.NARROW else InventoryLayout.WIDE
