package app.tweditor

import java.io.File
import java.nio.charset.Charset
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

@Tag("local")
class EquipmentDumpTest {
    @Test
    fun dumpEquippedAndCarried(@TempDir tempDir: java.nio.file.Path) {
        val saves = SaveSeamSupport.localSaves()
        assumeTrue(saves.isNotEmpty(), "no local saves")
        val newest = saves.maxByOrNull { it.lastModified() } ?: return
        val work = SaveSeamSupport.tempCopy(newest)
        val environment = SaveSeamSupport.createEnvironment()
        val loaded = SaveSeamSupport.load(environment, work, tempDir)

        val out = StringBuilder()
        out.appendLine("save: " + newest.getName())

        val player = loaded.player
        if (player != null) {
            val equipped = player.getElement("Equip_ItemList")?.getValue() as? DBList
            out.appendLine("== Equip_ItemList: " + (equipped?.getElementCount() ?: 0))
            equipped?.forEachIndexed { index, element ->
                val fields = element.getValue() as? DBList ?: return@forEachIndexed
                val resref = runCatching { fields.getString("TemplateResRef") }.getOrDefault("")
                val slot = runCatching { fields.getInteger("WeaponSlot") }.getOrDefault(-1)
                val tag = runCatching { fields.getString("Tag") }.getOrDefault("")
                out.appendLine("  equipped[$index]: resref=$resref slot=$slot tag=$tag")
            }
            val carried = player.getElement("ItemList")?.getValue() as? DBList
            if (carried != null) {
                out.appendLine("== ItemList: " + carried.getElementCount())
                carried.forEachIndexed { index, element ->
                    val fields = element.getValue() as? DBList ?: return@forEachIndexed
                    val resref = runCatching { fields.getString("TemplateResRef") }.getOrDefault("")
                    if (resref.contains("swd", ignoreCase = true) || resref.contains("sword", ignoreCase = true) ||
                        resref.contains("leather", ignoreCase = true) || resref.contains("armor", ignoreCase = true) ||
                        resref.contains("jacket", ignoreCase = true)
                    ) {
                        val tag = runCatching { fields.getString("Tag") }.getOrDefault("")
                        out.appendLine("  carried[$index]: resref=$resref tag=$tag")
                    }
                }
            }
        }

        val enc = Charset.forName("ISO-8859-1")
        val modFile = loaded.modDatabase!!.getPath()!!
        val modStr = String(File(modFile).readBytes(), enc)
        out.appendLine("== module file: " + modFile)
        val fxHits = Regex("[\\x20-\\x7E]{3,}").findAll(modStr).map { it.value }.toList().distinct()
            .filter { it.matches(Regex(".*fx_stl001.*|.*ph_stl_001.*|.*pochwy.*|.*stlscab.*")) }
        if (fxHits.isNotEmpty()) {
            fxHits.take(20).forEach { out.appendLine("  modfx: " + it) }
        } else {
            out.appendLine("  modfx: (none)")
        }

        val smmList = loaded.smmDatabase?.getTopLevelStruct()?.getValue() as? DBList
        val store = smmList?.let { StorageAccess.findStorageRecord(it) }
        val storageList = store?.getElement("ItemList")?.getValue() as? DBList
        if (storageList != null) {
            out.appendLine("== storage: " + storageList.getElementCount())
            storageList.forEachIndexed { index, element ->
                val fields = element.getValue() as? DBList ?: return@forEachIndexed
                val resref = runCatching { fields.getString("TemplateResRef") }.getOrDefault("")
                if (resref.contains("swd", ignoreCase = true) || resref.contains("sword", ignoreCase = true) ||
                    resref.contains("leather", ignoreCase = true) || resref.contains("armor", ignoreCase = true) ||
                    resref.contains("jacket", ignoreCase = true)
                ) {
                    val tag = runCatching { fields.getString("Tag") }.getOrDefault("")
                    out.appendLine("  stored[$index]: resref=$resref tag=$tag")
                }
            }
        }

        val dump = File("build/equipment-dump.txt")
        dump.parentFile.mkdirs()
        dump.writeText(out.toString())
        println(out.toString())
    }
}
