package app.tweditor

/** Module identity recorded in save metadata, kept separate from editable data. */
data class SaveModuleOwnership(
    val startingModule: String?,
    val modules: List<String>
)

object SaveModuleOwnershipReader {
    fun read(database: Database?): SaveModuleOwnership =
        read(database?.getTopLevelStruct()?.getValue() as? DBList)

    fun read(smmList: DBList?): SaveModuleOwnership {
        if (smmList == null) {
            return SaveModuleOwnership(null, emptyList())
        }

        val starting = smmList.getString("StartingMod").trim().takeIf { it.isNotEmpty() }
        val names = LinkedHashSet<String>()
        starting?.let { names.add(normalize(it)) }

        val moduleList = smmList.getElement("Meta_Mod_list")?.getValue() as? DBList
        if (moduleList != null) {
            for (record in moduleList) {
                val fields = record.getValue() as? DBList ?: continue
                for (field in fields) {
                    val label = field.getLabel()
                    val lowerLabel = label.lowercase()
                    if (!lowerLabel.contains("mod") && !lowerLabel.contains("module")) {
                        continue
                    }
                    val value = runCatching { fields.getString(label).trim() }.getOrNull() ?: continue
                    if (value.isNotEmpty()) {
                        names.add(normalize(value))
                    }
                }
            }
        }

        return SaveModuleOwnership(starting?.let(::normalize), names.toList())
    }

    private fun normalize(value: String): String {
        return value
            .removeSuffix(".sav")
            .removeSuffix(".mod")
            .trim()
    }
}
