package app.tweditor

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Database label ("Aard2 Upgrade1", "StyleSilverFast3", "Strength2 Upgrade2")
 * -> `ui_ab_*` talent-tree icon resref. Signs and styles resolve through the
 * [AbilityIcons] constants; attribute talents follow the same atlas naming.
 */
object AbilityResrefs {
    fun resref(databaseLabel: String): String? {
        AbilityIcons.iconResref(databaseLabel)?.let { return it }
        val match = TALENT_LABEL.find(databaseLabel) ?: return null
        return "ui_ab_" + match.groupValues[1].take(3).lowercase() +
            match.groupValues[2] +
            (if (match.groupValues[3].isEmpty()) "" else "u" + match.groupValues[3])
    }

    private val TALENT_LABEL = Regex("([A-Za-z]+)(\\d+)(?: Upgrade(\\d+))?$")
}

/**
 * Ability label -> `ui_ab_*` icon resref: the mapping spelled out by the string
 * constants of `witcher_sgn_tree.luc` and `witcher_cs_trees.luc`.
 */
object AbilityIcons {
    private val signPrefixes = linkedMapOf(
        "Aard" to "aar",
        "Igni" to "ign",
        "Quen" to "que",
        "Axii" to "axi",
        "Axi" to "axi",
        "Yrden" to "yrd"
    )

    private val stylePrefixes = linkedMapOf(
        "StyleSteelStrong" to "sts",
        "StyleSteelFast" to "stf",
        "StyleSteelGroup" to "stg",
        "StyleSilverStrong" to "svs",
        "StyleSilverFast" to "svf",
        "StyleSilverGroup" to "svg"
    )

    fun iconResref(abilityLabel: String): String? {
        val label = abilityLabel.trim()
        for ((name, prefix) in signPrefixes) {
            if (label.startsWith(name)) {
                val suffix = signSuffix(label.substring(name.length)) ?: return null
                return "ui_ab_" + prefix + suffix
            }
        }
        for ((name, prefix) in stylePrefixes) {
            if (label.startsWith(name)) {
                val suffix = styleSuffix(label.substring(name.length)) ?: return null
                return "ui_ab_" + prefix + suffix
            }
        }
        return null
    }

    private fun signSuffix(rest: String): String? = when {
        rest.matches(Regex("\\d")) -> rest
        rest.matches(Regex("\\d Powerup")) -> rest.substring(0, 1) + "p"
        else -> upgradeSuffix(rest)
    }

    private fun styleSuffix(rest: String): String? = when {
        rest.matches(Regex("\\d")) -> rest
        else -> upgradeSuffix(rest)
    }

    private fun upgradeSuffix(rest: String): String? = when {
        rest.matches(Regex("\\d Upgrade\\d")) -> rest.substring(0, 1) + "u" + rest.substring(rest.length - 1)
        else -> null
    }
}

/**
 * Decode cache for game icons: item textures (`iit_*`) and ability textures
 * (`ui_ab_*`) streamed out of the install's BIF archives. Lookups happen on the
 * event dispatch thread; decodes run on a background thread and repaint through
 * [lateIconListener] when they land, so rows start text-only and gain icons as
 * they arrive.
 */
class IconLibrary(private val environment: AppEnvironment) {
    private val lateIconListeners = CopyOnWriteArrayList<Runnable>()

    /** Panels register here to re-query lazily-decoded icons once they land. */
    fun addLateIconListener(listener: Runnable) {
        lateIconListeners.add(listener)
    }

    fun removeLateIconListener(listener: Runnable) {
        lateIconListeners.remove(listener)
    }

    private val images = ConcurrentHashMap<String, Optional<BufferedImage>>()
    private val baseArchiveImages = ConcurrentHashMap<String, BufferedImage?>()
    private val scaledIcons = ConcurrentHashMap<String, Optional<BufferedImage>>()
    private val fullResolutionItemIcons = ConcurrentHashMap<String, BufferedImage>()
    private val resolutions = ConcurrentHashMap<String, Optional<String>>()
    private val baseItemClasses = ConcurrentHashMap<Int, BaseItem>()
    private val pending = ConcurrentHashMap.newKeySet<String>()
    private val baseItemsLock = Any()

    @Volatile
    private var baseItemsLoaded = false

    private val decodeExecutor: ExecutorService = Executors.newSingleThreadExecutor { runner ->
        Thread(runner, "icon-decoder").apply { isDaemon = true }
    }
    private val repaintExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runner ->
        Thread(runner, "icon-repaint").apply { isDaemon = true }
    }

    fun itemIcon(fields: DBList): BufferedImage? {
        val resref = itemIconResref(fields) ?: return null
        return scaledItemIcon(resref, fields.getInteger("BaseItem"))
    }

    /**
     * Item icon decoded for a view-facing record (BaseItem / ModelPart1 /
     * TemplateResRef), sized to its game inventory-cell footprint. Returns null
     * while the decode is still pending; views re-query via the late-icon
     * listener.
     */
    fun itemViewIcon(baseItem: Int, modelPart: Int, templateResRef: String): BufferedImage? {
        val resref = itemIconResref(baseItem, modelPart, templateResRef.lowercase()) ?: return null
        return scaledItemIcon(resref, baseItem)
    }

    /** Full-resolution item art for large presentation surfaces. */
    fun itemViewIconFullResolution(baseItem: Int, modelPart: Int, templateResRef: String): BufferedImage? {
        val resref = itemIconResref(baseItem, modelPart, templateResRef.lowercase()) ?: return null
        fullResolutionItemIcons[resref]?.let { return it }
        val source = image(resref) ?: return null
        return trimToContent(source).also { fullResolutionItemIcons[resref] = it }
    }

    fun templateIcon(template: ItemTemplate): BufferedImage? {
        val resref = template.iconResref ?: itemIconResref(template.fieldList) ?: return null
        return scaledItemIcon(resref, template.baseItem)
    }

    /** Item icon for an already-resolved resref, sized to the base item's cell footprint. */
    fun itemIcon(resref: String, baseItem: Int): BufferedImage? = scaledItemIcon(resref, baseItem)

    /** The game inventory-cell footprint (width x height) of a baseitems.2da row. */
    fun itemFootprint(baseItem: Int): Pair<Int, Int> {
        ensureBaseItems()
        val base = baseItemClasses[baseItem]
        return (base?.slotWidth ?: 1).coerceAtLeast(1) to (base?.slotHeight ?: 1).coerceAtLeast(1)
    }

    fun abilityIcon(abilityLabel: String, size: Int): BufferedImage? {
        val resref = AbilityIcons.iconResref(abilityLabel) ?: return null
        return scaledIcon(resref, size)
    }

    /**
     * Character-development talent icons from the same atlas as the signs and
     * styles: the save's `RnAbName` ("Strength2 Upgrade1") maps to the texture
     * "ui_ab_str2u1" (attribute, level, optional upgrade). Returns null until
     * the background decode lands; panels re-query through
     * [addLateIconListener].
     */
    fun talentIcon(databaseLabel: String, size: Int): BufferedImage? {
        val match = TALENT_LABEL_PATTERN.find(databaseLabel) ?: return null
        val attribute = match.groupValues[1].take(3).lowercase()
        val level = match.groupValues[2]
        val upgrade = match.groupValues[3]
        val resref = "ui_ab_" + attribute + level + (if (upgrade.isEmpty()) "" else "u" + upgrade)
        return scaledIcon(resref, size)
    }

    fun itemIconResref(fields: DBList): String? {
        val baseItem = fields.getInteger("BaseItem")
        val modelPart = fields.getInteger("ModelPart1")
        val templateResRef = fields.getString("TemplateResRef").lowercase()
        return itemIconResref(baseItem, modelPart, templateResRef)
    }

    fun itemIconResref(baseItem: Int, modelPart: Int, templateResRef: String): String? {
        val key = baseItem.toString() + "/" + modelPart + "/" + templateResRef
        resolutions[key]?.let { return it.orElse(null) }
        ensureBaseItems()
        val resolution = resolveIconResref(baseItem, modelPart, templateResRef)
        resolutions[key] = Optional.ofNullable(resolution)
        return resolution
    }

    private fun resolveIconResref(baseItem: Int, modelPart: Int, templateResRef: String): String? {
        val base = baseItemClasses[baseItem]
        val itemClass = base?.itemClass ?: ""
        val defaultModel = base?.defaultModel ?: ""
        val classBase = itemClass.removePrefix("it_")
        if (classBase.isNotEmpty() && modelPart >= 0) {
            val candidate = "iit_" + classBase + "_" + modelPart.toString().padStart(3, '0')
            if (hasTexture(candidate)) {
                return candidate
            }
        }
        if (templateResRef.isNotEmpty() && hasTexture(templateResRef)) {
            return templateResRef
        }
        val modelBase = defaultModel.removePrefix("it_")
        if (modelBase.isNotEmpty()) {
            val candidate = "iit_" + modelBase
            if (hasTexture(candidate)) {
                return candidate
            }
        }
        return if (hasTexture(PLACEHOLDER)) PLACEHOLDER else null
    }

    /** Queue every icon resref the item templates resolve to for background decode. */
    fun primeTemplates(templates: List<ItemTemplate>) {
        ensureBaseItems()
        val resrefs = HashSet<String>()
        for (template in templates) {
            val resref = itemIconResref(template.fieldList)
            if (resref != null) {
                resrefs.add(resref)
            }
        }
        prime(resrefs)
    }

    /** Queue the ability icons for the labels a Signs/Styles panel shows. */
    fun primeAbilities(labels: Collection<String>) {
        val resrefs = HashSet<String>()
        for (label in labels) {
            val resref = AbilityIcons.iconResref(label)
            if (resref != null && hasTexture(resref)) {
                resrefs.add(resref)
            }
        }
        prime(resrefs)
    }

    /** Queue the talent icons for the labels the Attributes panel shows. */
    fun primeTalentTalents(labels: Collection<String>) {
        val resrefs = HashSet<String>()
        for (label in labels) {
            val match = TALENT_LABEL_PATTERN.find(label) ?: continue
            val resref = "ui_ab_" + match.groupValues[1].take(3).lowercase() + match.groupValues[2] +
                (if (match.groupValues[3].isEmpty()) "" else "u" + match.groupValues[3])
            if (hasTexture(resref)) {
                resrefs.add(resref)
            }
        }
        prime(resrefs)
    }

    fun prime(resrefs: Collection<String>) {
        for (resref in resrefs) {
            if (images.containsKey(resref)) {
                continue
            }
            if (pending.add(resref)) {
                decodeExecutor.execute {
                    val image = try {
                        decodeResref(resref)
                    } catch (exc: IOException) {
                        null
                    } catch (exc: DBException) {
                        null
                    }
                    images[resref] = Optional.ofNullable(image)
                    pending.remove(resref)
                    if (image != null) {
                        requestRepaint()
                    }
                }
            }
        }
    }

    private fun image(resref: String): BufferedImage? {
        val cached = images[resref]
        if (cached != null) {
            return cached.orElse(null)
        }
        prime(listOf(resref))
        return null
    }    /** Raw decoded game texture for declarative UI adapters; null while decoding is pending. */
    fun imageByResref(resref: String): BufferedImage? = image(resref.lowercase())

    /**
     * Decode a texture from the base game archives only, ignoring loose
     * overrides. The editor shell draws game UI art (the HUD button atlas,
     * container emblems) with pixel-coordinate crops, and a user mod such as
     * ClassicUI replaces those textures with differently composed art; crops
     * against overridden copies then land on empty space.
     */
    fun imageByResrefBaseArchive(resref: String): BufferedImage? {
        val name = resref.lowercase()
        return baseArchiveImages.computeIfAbsent(name) { key ->
            for (suffix in listOf(".dds", ".tga")) {
                val resource = environment.baseResourceFiles[key + suffix]
                if (resource != null) {
                    return@computeIfAbsent decodeResource(resource, key)
                }
            }
            imageByResref(key)
        }
    }

    private fun decodeResref(resref: String): BufferedImage? {
        val resource = environment.resourceFiles[resref + ".dds"]
            ?: environment.resourceFiles[resref + ".tga"]
            ?: return null
        return decodeResource(resource, resref)
    }

    private fun decodeResource(resource: Any, resref: String): BufferedImage? {
        val bytes = ResourceAccess.open(resource)?.use { it.readBytes() } ?: return null
        val decoded = if (bytes.size >= 4 && bytes[0] == 0x44.toByte() && bytes[1] == 0x44.toByte() &&
            bytes[2] == 0x53.toByte() && bytes[3] == 0x20.toByte()
        ) {
            val dds = DdsDecoder.decode(bytes)
            toBufferedImage(dds.width, dds.height, dds.argb)
        } else {
            val tga = TgaDecoder.decode(bytes)
            toBufferedImage(tga.width, tga.height, tga.argb)
        }
        return if (needsVerticalFlip(resref)) flipVertical(decoded) else decoded
    }

    private fun scaledIcon(resref: String, size: Int): BufferedImage? {
        val key = resref + "@" + size
        val cached = scaledIcons[key]
        if (cached != null) {
            return cached.orElse(null)
        }
        val image = image(resref) ?: return null
        val icon = scaleToBox(image, size, size)
        scaledIcons[key] = Optional.of(icon)
        return icon
    }

    /** Item icons sized to their game inventory-cell footprint (see baseitems.2da). */
    private fun scaledItemIcon(resref: String, baseItem: Int): BufferedImage? {
        ensureBaseItems()
        val base = baseItemClasses[baseItem]
        val slotWidth = base?.slotWidth ?: 1
        val slotHeight = base?.slotHeight ?: 1
        val key = resref + "@" + slotWidth + "x" + slotHeight
        val cached = scaledIcons[key]
        if (cached != null) {
            return cached.orElse(null)
        }
        val image = image(resref) ?: return null
        val (boxWidth, boxHeight) = displayBox(slotWidth, slotHeight)
        val icon = scaleToBox(trimToContent(image), boxWidth, boxHeight)
        scaledIcons[key] = Optional.of(icon)
        return icon
    }

    private fun displayBox(slotWidth: Int, slotHeight: Int): Pair<Int, Int> {
        if (slotWidth <= 1 && slotHeight <= 1) {
            return SQUARE_ICON to SQUARE_ICON
        }
        val unit = LARGE_ICON_SIZE.toDouble() / max(slotWidth, slotHeight)
        return (slotWidth * unit).roundToInt() to (slotHeight * unit).roundToInt()
    }

    private fun toBufferedImage(width: Int, height: Int, argb: IntArray): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, width, height, argb, 0, width)
        return image
    }

    /**
     * The game stores every texture bottom-up (D3D UV convention) except the
     * ability atlas: item icons (`iit_*`/`it_*`) and interface art (the HUD
     * button atlas, the character/inventory tab medallions, the inventory
     * category emblems) all render inverted as-decoded — verified by matching
     * the owner's in-game screenshots against the raw textures (scroll crops
     * correlate ~0.8 with the flipped variant and ~0.0 with the stored one;
     * the HUD round buttons and the tab medallions showed the same). The
     * `ui_ab_*` talent coins are authored top-down, as is the placeholder, so
     * they are exempt.
     */
    internal fun needsVerticalFlip(resref: String): Boolean {
        return !resref.startsWith("ui_ab_") && resref != PLACEHOLDER
    }

    private fun flipVertical(image: BufferedImage): BufferedImage {
        val width = image.width
        val height = image.height
        val source = image.getRGB(0, 0, width, height, null, 0, width)
        val target = IntArray(source.size)
        for (y in 0 until height) {
            System.arraycopy(source, (height - 1 - y) * width, target, y * width, width)
        }
        val flipped = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        flipped.setRGB(0, 0, width, height, target, 0, width)
        return flipped
    }

    /** Crop the fully transparent margin so scaled art fills its box. */
    private fun trimToContent(image: BufferedImage): BufferedImage {
        val width = image.width
        val height = image.height
        val argb = image.getRGB(0, 0, width, height, null, 0, width)
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if (argb[row + x] ushr 24 > ALPHA_EPSILON) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < 0) {
            return image
        }
        minX = (minX - 1).coerceAtLeast(0)
        minY = (minY - 1).coerceAtLeast(0)
        maxX = (maxX + 1).coerceAtMost(width - 1)
        maxY = (maxY + 1).coerceAtMost(height - 1)
        return image.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1)
    }

    private fun scaleToBox(image: BufferedImage, boxWidth: Int, boxHeight: Int): BufferedImage {
        val aspect = image.width.toDouble() / image.height.toDouble()
        val boxAspect = boxWidth.toDouble() / boxHeight.toDouble()
        val scale = if (aspect > boxAspect) boxWidth.toDouble() / image.width else boxHeight.toDouble() / image.height
        val drawWidth = (image.width * scale).roundToInt().coerceIn(1, boxWidth)
        val drawHeight = (image.height * scale).roundToInt().coerceIn(1, boxHeight)
        val scaled = BufferedImage(boxWidth, boxHeight, BufferedImage.TYPE_INT_ARGB)
        val graphics = scaled.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.drawImage(image, (boxWidth - drawWidth) / 2, (boxHeight - drawHeight) / 2, drawWidth, drawHeight, null)
        } finally {
            graphics.dispose()
        }
        return scaled
    }

    private fun hasTexture(resref: String): Boolean {
        return environment.resourceFiles.containsKey(resref + ".dds") || environment.resourceFiles.containsKey(resref + ".tga")
    }

    private fun ensureBaseItems() {
        if (baseItemsLoaded) {
            return
        }
        synchronized(baseItemsLock) {
            if (baseItemsLoaded) {
                return
            }
            val resource = environment.resourceFiles["baseitems.2da"]
            val input: InputStream? = resource?.let(ResourceAccess::open)
            if (input != null) {
                try {
                    val table = TextDatabase(input)
                    for (row in 0 until table.getResourceCount()) {
                        baseItemClasses[row] = BaseItem(
                            table.getString(row, "ItemClass"),
                            table.getString(row, "DefaultModel"),
                            table.getInteger(row, "InvSlotWidth"),
                            table.getInteger(row, "InvSlotHeight")
                        )
                    }
                } finally {
                    input.close()
                }
            }
            baseItemsLoaded = true
        }
    }

    /**
     * Coalesced repaint requests: the first late arrival paints immediately,
     * further bursts repaint at most every 250ms with one trailing repaint
     * after the queue quiets down.
     */
    private fun requestRepaint() {
        synchronized(repaintLock) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastPaintAt
            if (elapsed >= REPAINT_INTERVAL) {
                lastPaintAt = now
                invokeListener()
            } else if (!trailingScheduled) {
                trailingScheduled = true
                repaintExecutor.schedule({
                    synchronized(repaintLock) {
                        trailingScheduled = false
                        lastPaintAt = System.currentTimeMillis()
                    }
                    invokeListener()
                }, REPAINT_INTERVAL - elapsed, TimeUnit.MILLISECONDS)
            }
        }
    }

    private fun invokeListener() {
        for (listener in lateIconListeners) {
            repaintExecutor.execute(listener)
        }
    }

    companion object {
        const val PLACEHOLDER = "question_mark"
        private const val SQUARE_ICON = 32
        private const val LARGE_ICON_SIZE = 48
        private const val ALPHA_EPSILON = 16
        private const val REPAINT_INTERVAL = 250L
        private val repaintLock = Any()
        private var lastPaintAt = 0L
        private var trailingScheduled = false
        private val TALENT_LABEL_PATTERN = Regex("([A-Za-z]+)(\\d+)(?: Upgrade(\\d+))?$")
    }

    private class BaseItem(val itemClass: String, val defaultModel: String, val slotWidth: Int, val slotHeight: Int)
}
