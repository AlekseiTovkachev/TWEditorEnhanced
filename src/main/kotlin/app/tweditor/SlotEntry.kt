package app.tweditor

/**
 * One paperdoll row in the Equipment tab: a named equipment slot and whatever
 * (if anything) is equipped in it.
 */
class SlotEntry(val slotName: String, val slot: Int, val item: InventoryItem?) {
    override fun toString(): String {
        val filled = item?.toString() ?: "(empty)"
        return slotName + ": " + filled
    }
}
