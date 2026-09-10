package app.tweditor

/** Save-format seam for the global innkeeper storage chest. */
object StorageAccess {
    fun findStorageRecord(smmList: DBList): DBList? {
        val storeList = smmList.getElement("StoreList")?.getValue() as? DBList ?: return null
        var fallback: DBList? = null
        for (element in storeList) {
            val record = element.getValue() as? DBList ?: continue
            if (runCatching { record.getInteger("IsStorage") }.getOrDefault(0) == 1) {
                return record
            }
            if (fallback == null) fallback = record
        }
        return fallback
    }

    fun itemList(session: GameSession): DBList? {
        val top = session.smmDatabase?.getTopLevelStruct()?.getValue() as? DBList ?: return null
        return findStorageRecord(top)?.getElement("ItemList")?.getValue() as? DBList
    }

    fun snapshot(session: GameSession): DBElement =
        requireNotNull(session.smmDatabase?.getTopLevelStruct()) { "No save metadata database is open" }.clone()

    fun restore(session: GameSession, snapshot: DBElement) {
        requireNotNull(session.smmDatabase) { "No save metadata database is open" }.setTopLevelStruct(snapshot.clone())
    }

    /** Storage display order: item family, localized name, resource name, stack. */
    fun categoryOf(element: DBElement): Int {
        val baseItem = (element.getValue() as? DBList)?.let {
            runCatching { it.getInteger("BaseItem") }.getOrDefault(-1)
        } ?: -1
        return when {
            baseItem == 1 -> 13 // steel sword
            baseItem == 2 -> 14 // silver sword
            baseItem in setOf(3, 4, 5, 6, 7, 9) -> 15 // big weapon
            baseItem in setOf(8, 12, 17, 19) -> 16 // short weapon
            baseItem == 29 -> 17 // armor
            baseItem == 10 || baseItem == 11 || baseItem == 22 -> 9 // potion
            baseItem == 20 || baseItem == 23 || baseItem == 38 -> 7 // jewelry
            baseItem == 21 || baseItem == 35 || baseItem == 37 -> 8 // magical
            baseItem == 32 -> 4 // gem
            baseItem == 33 -> 6 // ingredient
            baseItem == 34 -> 11 // upgrade
            baseItem == 40 -> 10 // quest
            baseItem == 47 -> 0 // bomb
            baseItem == 48 -> 2 // drink
            baseItem == 44 -> 3 // food
            baseItem == 46 -> 5 // grease
            baseItem == 30 -> 1 // book
            else -> 12
        }
    }

    fun displayName(element: DBElement): String {
        val fields = element.getValue() as? DBList ?: return ""
        return runCatching { fields.getString("LocalizedName") }.getOrDefault("")
            .ifEmpty { runCatching { fields.getString("TemplateResRef") }.getOrDefault("") }
    }

    fun resref(element: DBElement): String =
        (element.getValue() as? DBList)?.let { runCatching { it.getString("TemplateResRef") }.getOrDefault("") } ?: ""

    fun stackSize(element: DBElement): Int =
        (element.getValue() as? DBList)?.let { runCatching { it.getInteger("StackSize") }.getOrDefault(0) } ?: 0

    fun sortedElements(itemList: DBList): List<DBElement> = itemList.toList().sortedWith(
        compareBy({ categoryOf(it) }, { displayName(it) }, { resref(it) }, { stackSize(it) })
    )
}

class SortStorageCommand : EditorCommand {
    override fun validate(session: GameSession): List<LocalizedText> =
        if (StorageAccess.itemList(session) == null) {
            listOf(LocalizedText("storage.command.noRecord"))
        } else {
            emptyList()
        }

    override fun apply(session: GameSession): AppliedEditorCommand {
        val before = StorageAccess.snapshot(session)
        val itemList = requireNotNull(StorageAccess.itemList(session)) { "This save has no storage chest record" }
        val sorted = StorageAccess.sortedElements(itemList)
        sorted.forEachIndexed { index, element -> itemList.setElement(index, element) }
        return AppliedEditorCommand(
            PendingChange(LocalizedText("storage.pending.sort"), EvidenceLevel.STRUCTURALLY_VERIFIED)
        ) { StorageAccess.restore(session, before) }
    }
}
