package app.tweditor

import java.io.File
import java.io.IOException
import java.util.ArrayList

/** Resource-to-template scan used by the Compose shell and headless tests. */
object LoadTemplates {
        /**
         * Parses one resource-map entry into an item template, or null when the
         * entry is not a named .uti. The app runs this on the loader thread;
         * tests call the whole scan directly.
         */
    fun processEntry(entryObject: Any, environment: AppEnvironment): ItemTemplate? {
            val name = ResourceAccess.name(entryObject)?.lowercase() ?: return null
            val sep = name.lastIndexOf('.')
            if (sep <= 0 || name.substring(sep) != ".uti") {
                return null
            }
            val resourceName = when (entryObject) {
                is File -> name.substring(0, sep)
                is KeyEntry -> entryObject.resourceName
                is ResourceEntry -> entryObject.resourceName
                else -> return null
            }
            val input = ResourceAccess.open(entryObject) ?: return null

            val database = Database(environment)
            input.use { database.load(it) }
            val fieldList = database.getTopLevelStruct()!!.getValue() as DBList
            val fullResourceName = name
            val preferredModule = environment.resourceOrigin(fullResourceName)?.moduleName
            val itemName = fieldList.getString("LocalizedName", preferredModule)
            // Description is often empty (books, gems); only a name is required.
            if (itemName.isEmpty()) {
                return null
            }
            val resourceElement = DBElement(11, 0, "TemplateResRef", resourceName)
            fieldList.setElement("TemplateResRef", resourceElement)
            return ItemTemplate(fieldList, preferredModule)
        }

        /** The app's template scan, callable from tests (no progress dialog). */
    fun loadItemTemplates(environment: AppEnvironment) {
            environment.itemTemplates = ArrayList(environment.resourceFiles.size)
            for (mapEntry in environment.resourceFiles.entries) {
                val template = processEntry(mapEntry.value, environment)
                if (template != null) {
                    environment.itemTemplates.add(template)
                }
            }
            environment.icons.primeTemplates(environment.itemTemplates)
    }
}
