package app.tweditor

class ItemTemplate(val fieldList: DBList) : Comparable<ItemTemplate> {
    val baseItem: Int
    val itemName: String
    val resourceName: String
    val iconResref: String?

    init {
        this.baseItem = fieldList.getInteger("BaseItem")
        this.itemName = fieldList.getString("LocalizedName")
        this.resourceName = fieldList.getString("TemplateResRef")
        this.iconResref = fieldList.environment.icons.itemIconResref(fieldList)
    }

    override fun equals(other: Any?): Boolean {
        return other is ItemTemplate && other.itemName == itemName
    }

    override fun compareTo(other: ItemTemplate): Int = itemName.compareTo(other.itemName)

    override fun toString(): String = "$itemName ($resourceName)"
}
