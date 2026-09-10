package app.tweditor

/** One inspectable effect attached to an item record. */
data class ItemEffectView(
    val groupKey: String,
    val name: String,
    val stack: Int = 0
)

/** Immutable item information safe for the Compose shell to render. */
data class ItemDetailsView(
    val description: String,
    val effects: List<ItemEffectView>
) {
    val hasContent: Boolean get() = description.isNotBlank() || effects.isNotEmpty()
}

/**
 * Reads description and effect data without exposing DBList traversal to the
 * Compose shell. Save-record values win; the matching game template fills in
 * fields a Save does not carry.
 */
fun readItemDetails(environment: AppEnvironment, session: GameSession, sourceId: String): ItemDetailsView? {
    val source = EquipmentAccess.record(session, sourceId) ?: return null
    val template = source.fields.stringOrEmpty("TemplateResRef").takeIf { it.isNotEmpty() }?.let { resref ->
        environment.itemTemplates.firstOrNull { it.resourceName.equals(resref, ignoreCase = true) }?.fieldList
    }

    fun text(label: String): String = source.fields.stringOrEmpty(label)
        .ifEmpty { template?.stringOrEmpty(label).orEmpty() }

    val primaryDescription = text("DescIdentified").ifEmpty { text("Description") }
    val description = listOf(primaryDescription, text("ExtraDesc"))
        .map { ItemDescriptionText.toPlainText(it) { ref -> environment.getString(ref) } }
        .filter { it.isNotBlank() }
        .joinToString("\n\n")

    fun abilities(label: String): List<WeaponAbility> {
        val saved = ItemEdit.readAbilities(source.fields, label)
        return if (saved.isNotEmpty()) saved else template?.let { ItemEdit.readAbilities(it, label) }.orEmpty()
    }

    val effects = buildList {
        abilities("WpnAbilitySelf").forEach { add(ItemEffectView("wielder", it.name, it.stack)) }
        abilities("WpnAbilityOpp").forEach { add(ItemEffectView("onHit", it.name, it.stack)) }
        alchemySubstances(environment, source.fields, template).forEach { add(ItemEffectView("alchemy", it)) }
    }
    return ItemDetailsView(description, effects)
}

/** Converts the game's small custom-markup dialect to readable Compose text. */
internal object ItemDescriptionText {
    private val stringRef = Regex("<strref:(\\d+)>", RegexOption.IGNORE_CASE)
    private val breakTag = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
    private val listItemTag = Regex("<li\\s*>", RegexOption.IGNORE_CASE)
    private val blockEndTag = Regex("</(?:p|div|ul|ol)\\s*>", RegexOption.IGNORE_CASE)
    private val anyTag = Regex("<[^>]*>")

    fun toPlainText(raw: String, resolveStringRef: (Int) -> String): String {
        if (raw.isBlank()) return ""
        return raw
            .replace(stringRef) { match -> resolveStringRef(match.groupValues[1].toInt()) }
            .replace(breakTag, "\n")
            .replace(listItemTag, "\n• ")
            .replace(blockEndTag, "\n")
            .replace(anyTag, "")
            .replace("&nbsp;", " ", ignoreCase = true)
            .replace("&amp;", "&", ignoreCase = true)
            .replace("&lt;", "<", ignoreCase = true)
            .replace("&gt;", ">", ignoreCase = true)
            .replace("&quot;", "\"", ignoreCase = true)
            .replace("&#39;", "'", ignoreCase = true)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
            .joinToString("\n") { it.trim() }
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n+\\s*•"), "\n•")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }
}

private fun alchemySubstances(environment: AppEnvironment, fields: DBList, template: DBList?): List<String> {
    val ingredientId = fields.integerOrZero("AlchIngredient")
        .takeIf { it > 0 }
        ?: template?.integerOrZero("AlchIngredient")?.takeIf { it > 0 }
        ?: return emptyList()
    val resource = environment.resourceFiles["alchemy_ingre.2da"] ?: return emptyList()
    val table = ResourceAccess.open(resource)?.use(::TextDatabase) ?: return emptyList()
    if (ingredientId >= table.getResourceCount()) return emptyList()
    return ALCHEMY_SUBSTANCES.filter { table.getInteger(ingredientId, it) == 1 }
}

private fun DBList.stringOrEmpty(label: String): String = runCatching { getString(label) }.getOrDefault("")
private fun DBList.integerOrZero(label: String): Int = runCatching { getInteger(label) }.getOrDefault(0)

private val ALCHEMY_SUBSTANCES = listOf(
    "Vitriol", "Rebis", "Aether", "Quebirth", "Hydragenum", "Vermilion", "Albedo", "Nigredo", "Rubedo"
)
