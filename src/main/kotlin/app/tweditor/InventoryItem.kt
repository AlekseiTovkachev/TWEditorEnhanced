package app.tweditor

class InventoryItem(val name: String, element: DBElement, val detail: String? = null) : Comparable<InventoryItem> {
    val element: DBElement
    var count: Int

    init {
        this.element = element
        this.count = (element.getValue() as DBList).getInteger("StackSize")
    }

    override fun equals(other: Any?): Boolean {
        return other is InventoryItem && other.name == name && other.count == count
    }

    override fun compareTo(other: InventoryItem): Int {
        var diff = name.compareTo(other.name)
        if (diff == 0) {
            diff = count.compareTo(other.count)
        }
        return diff
    }

    override fun toString(): String {
        val label = String.format("%s (%d)", name, count)
        return if (detail == null) label else label + " - " + detail
    }
}
