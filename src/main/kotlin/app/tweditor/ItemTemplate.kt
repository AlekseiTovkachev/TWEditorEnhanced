package app.tweditor

class ItemTemplate(val fieldList: DBList, private val preferredModule: String? = null) : Comparable<ItemTemplate> {
    val baseItem: Int
    val resourceName: String
    val iconResref: String?

    /**
     * The display name resolves on every read so a language switch re-renders
     * picker entries through the active content TLK instead of a frozen copy.
     */
    val itemName: String
        get() = fieldList.getString("LocalizedName", preferredModule)

    init {
        this.baseItem = fieldList.getInteger("BaseItem")
        this.resourceName = fieldList.getString("TemplateResRef")
        this.iconResref = fieldList.environment.icons.itemIconResref(fieldList)
    }

    override fun equals(other: Any?): Boolean {
        return other is ItemTemplate && other.itemName == itemName
    }

    override fun compareTo(other: ItemTemplate): Int = itemName.compareTo(other.itemName)

    override fun toString(): String = "$itemName ($resourceName)"
}
