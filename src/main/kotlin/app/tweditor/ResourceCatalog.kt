package app.tweditor

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

/** Where a resource was found in the layered game data search. */
enum class ResourceOriginKind {
    BASE_ARCHIVE,
    PACKED_MODULE,
    LOOSE_OVERRIDE
}

/** Provenance kept beside the existing resource map so callers can explain ownership. */
data class ResourceOrigin(
    val kind: ResourceOriginKind,
    val label: String,
    val path: File? = null
) {
    /** The packed module name, when this resource came from a module archive. */
    val moduleName: String?
        get() = if (kind == ResourceOriginKind.PACKED_MODULE) label else null
}

/** A parsed module archive and the name shown to users as its owner. */
data class PackedModuleInfo(
    val moduleName: String,
    val file: File,
    val database: ResourceDatabase
)

/** A module TLK kept in the same deterministic order as module discovery. */
data class ModuleStringsDatabase(
    val moduleName: String,
    val resourceName: String,
    val database: StringsDatabase
)

/** Opens all resource handles accepted by AppEnvironment.resourceFiles. */
object ResourceAccess {
    fun open(resource: Any): InputStream? {
        return try {
            when (resource) {
                is File -> FileInputStream(resource)
                is KeyEntry -> resource.getInputStream()
                is ResourceEntry -> resource.getInputStream()
                else -> null
            }
        } catch (_: IOException) {
            null
        } catch (_: DBException) {
            null
        }
    }

    fun name(resource: Any): String? {
        return when (resource) {
            is File -> resource.name
            is KeyEntry -> resource.fileName
            is ResourceEntry -> resource.getName()
            else -> null
        }
    }
}
