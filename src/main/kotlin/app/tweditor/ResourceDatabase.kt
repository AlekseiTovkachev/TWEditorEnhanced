package app.tweditor

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.Arrays
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar

class ResourceDatabase(file: File) {
    private var file: File = file
    private var databaseType: String = "ERF "
    private var databaseVersion: String = "V1.0"
    private var description: LocalizedString = LocalizedString(-1)
    private var entries: MutableList<ResourceEntry> = ArrayList(64)
    private var entryMap: MutableMap<String, ResourceEntry> = HashMap(64)

    constructor(filePath: String) : this(File(filePath))

    @Throws(DBException::class, IOException::class)
    fun load() {
        RandomAccessFile(this.file, "r").use { input ->
            val header = ByteArray(160)
            var count = input.read(header)
            if (count != 160) {
                throw DBException("Database header is too short")
            }
            this.databaseType = String(header, 0, 4)
            if (!Arrays.asList(*databaseTypes).contains(this.databaseType)) {
                throw DBException("Database type '" + this.databaseType + "' is not supported")
            }
            this.databaseVersion = String(header, 4, 4)
            if (!Arrays.asList(*databaseVersions).contains(this.databaseVersion)) {
                throw DBException("Database version '" + this.databaseVersion + "' is not supported")
            }
            val stringCount = getInteger(header, 8)
            val stringSize = getInteger(header, 12)
            val entryCount = getInteger(header, 16)
            val stringOffset = getInteger(header, 20)
            val keyOffset = getInteger(header, 24)
            val resourceOffset = getInteger(header, 28)
            val stringReference = getInteger(header, 40)

            this.description = LocalizedString(stringReference)
            this.entries = ArrayList(entryCount.coerceAtLeast(10))
            this.entryMap = HashMap(entryCount.coerceAtLeast(10))

            if (stringCount > 0) {
                input.seek(stringOffset.toLong())
                var buffer = ByteArray(128)
                for (i in 0 until stringCount) {
                    count = input.read(buffer, 0, 8)
                    if (count != 8) {
                        throw DBException("String list truncated")
                    }
                    var language = getInteger(buffer, 0)
                    val stringLength = getInteger(buffer, 4)
                    val gender = language and 0x1
                    language = language shr 1
                    val string: String
                    if (stringLength > 0) {
                        if (stringLength > buffer.size) {
                            buffer = ByteArray(stringLength)
                        }
                        count = input.read(buffer, 0, stringLength)
                        if (count != stringLength) {
                            throw DBException("String list truncated")
                        }
                        var value = String(buffer, 0, stringLength, StandardCharsets.UTF_8)
                        if (value.isNotEmpty() && value[value.length - 1].toInt() == 0) {
                            value = value.substring(0, value.length - 1)
                        }
                        string = value
                    } else {
                        string = ""
                    }

                    this.description.addSubstring(LocalizedSubstring(string, language, gender))
                }
            }

            val resourceNames = ArrayList<String>(entryCount)
            val resourceTypes = ArrayList<Int>(entryCount)
            if (entryCount > 0) {
                input.seek(keyOffset.toLong())
                val nameLength: Int
                val keyLength: Int
                if (this.databaseVersion == "V1.0") {
                    keyLength = 24
                    nameLength = 16
                } else {
                    keyLength = 40
                    nameLength = 32
                }

                val key = ByteArray(keyLength)
                for (i in 0 until entryCount) {
                    count = input.read(key)
                    if (count != keyLength) {
                        throw DBException("Key list truncated")
                    }
                    var nameEnd = 0
                    while (nameEnd < nameLength && key[nameEnd].toInt() != 0) {
                        nameEnd++
                    }
                    resourceNames.add(String(key, 0, nameEnd))
                    resourceTypes.add(getShort(key, nameLength + 4))
                }
            }

            if (entryCount > 0) {
                input.seek(resourceOffset.toLong())
                val element = ByteArray(8)
                for (i in 0 until entryCount) {
                    count = input.read(element)
                    if (count != 8) {
                        throw DBException("Resource list truncated")
                    }
                    val offset = getInteger(element, 0).toLong()
                    val length = getInteger(element, 4)
                    val resourceName = resourceNames[i]
                    val resourceType = resourceTypes[i]
                    if (resourceName.isNotEmpty() && resourceType != 65535) {
                        val entry = ResourceEntry(resourceName, resourceType, this.file, offset, length)
                        this.entries.add(entry)
                        this.entryMap[entry.getName()] = entry
                    }
                }
            }
        }
    }

    @Throws(DBException::class, IOException::class)
    fun save() {
        val outputFile = File(this.file.getPath() + ".tmp")
        if (outputFile.exists()) {
            outputFile.delete()
        }
        var saved = false
        try {
            RandomAccessFile(outputFile, "rw").use { out ->
                val header = ByteArray(160)
                out.write(header)

                var buffer = ByteArray(128)
                val stringOffset = out.filePointer.toInt()
                var stringSize = 0
                val stringCount = this.description.getSubstringCount()
                for (substring in this.description.getSubstrings()) {
                    val string = substring.string
                    val stringBytes = string.toByteArray()
                    val length = stringBytes.size
                    if (length + 8 > buffer.size) {
                        buffer = ByteArray(length + 8)
                    }
                    setInteger(substring.language * 2 + substring.gender, buffer, 0)
                    setInteger(length, buffer, 4)
                    for (j in 0 until length) {
                        buffer[j + 8] = stringBytes[j]
                    }
                    out.write(buffer, 0, length + 8)
                    stringSize += length + 8
                }

                val entryCount = this.entries.size
                val keyOffset = out.filePointer.toInt()
                var resourceID = 0
                val entryLength: Int
                val nameLength: Int
                if (this.databaseVersion == "V1.1") {
                    nameLength = 32
                    entryLength = 40
                } else {
                    nameLength = 16
                    entryLength = 24
                }

                val keyBuffer = ByteArray(entryLength)
                for (entry in this.entries) {
                    val nameBytes = entry.resourceName.toByteArray()
                    if (nameBytes.size > nameLength) {
                        throw DBException("Resource name '" + entry.resourceName + "' is too long")
                    }
                    var index = 0
                    while (index < nameBytes.size) {
                        keyBuffer[index] = nameBytes[index]
                        index++
                    }
                    while (index < nameLength) {
                        keyBuffer[index] = 0
                        index++
                    }
                    setInteger(resourceID, keyBuffer, nameLength)
                    setShort(entry.resourceType, keyBuffer, nameLength + 4)
                    setShort(0, keyBuffer, nameLength + 6)
                    out.write(keyBuffer)
                    resourceID++
                }

                val resourceOffset = out.filePointer.toInt()
                var dataOffset = resourceOffset + entryCount * 8

                for (entry in this.entries) {
                    val length = entry.length
                    setInteger(dataOffset, buffer, 0)
                    setInteger(length, buffer, 4)
                    out.write(buffer, 0, 8)
                    dataOffset += length
                }

                buffer = ByteArray(4096)
                for (entry in this.entries) {
                    RandomAccessFile(entry.file, "r").use { input ->
                        input.seek(entry.offset)
                        var residualLength = entry.length
                        while (residualLength > 0) {
                            val length = residualLength.coerceAtMost(buffer.size)
                            val count = input.read(buffer, 0, length)
                            if (count != length) {
                                throw DBException("Data truncated for resource " + entry.getName())
                            }
                            out.write(buffer, 0, count)
                            residualLength -= count
                        }
                    }
                }

                val calendar = GregorianCalendar()
                calendar.time = Date()

                val typeBytes = this.databaseType.toByteArray()
                for (i in 0 until 4) {
                    header[i] = typeBytes[i]
                }
                val versionBytes = this.databaseVersion.toByteArray()
                for (i in 0 until 4) {
                    header[i + 4] = versionBytes[i]
                }
                setInteger(stringCount, header, 8)
                setInteger(stringSize, header, 12)
                setInteger(entryCount, header, 16)
                setInteger(stringOffset, header, 20)
                setInteger(keyOffset, header, 24)
                setInteger(resourceOffset, header, 28)
                setInteger(calendar.get(Calendar.YEAR) - 1970, header, 32)
                setInteger(calendar.get(Calendar.DAY_OF_YEAR) - 1, header, 36)
                setInteger(this.description.stringReference, header, 40)

                out.seek(0L)
                out.write(header, 0, 44)
                saved = true
            }
        } finally {
            if (!saved) {
                outputFile.delete()
            }
        }

        if (this.file.exists() && !this.file.delete()) {
            outputFile.delete()
            throw IOException("Unable to delete " + this.file.getName())
        }
        if (!outputFile.renameTo(this.file)) {
            outputFile.delete()
            throw IOException("Unable to rename " + outputFile.getName() + " to " + this.file.getName())
        }
    }

    fun getName(): String = file.getName()

    fun getPath(): String = file.getPath()

    fun getType(): String = databaseType

    fun setType(type: String) {
        if (!Arrays.asList(*databaseTypes).contains(type)) {
            throw IllegalArgumentException("Database type '" + type + "' is not supported")
        }
        this.databaseType = type
    }

    fun getVersion(): String = databaseVersion

    fun setVersion(version: String) {
        if (!Arrays.asList(*databaseVersions).contains(version)) {
            throw IllegalArgumentException("Database version '" + version + "' is not supported")
        }
        this.databaseVersion = version
    }

    fun getDescription(): LocalizedString = description

    fun getEntryCount(): Int = entries.size

    fun getEntries(): List<ResourceEntry> = entries

    fun getEntry(index: Int): ResourceEntry? = if (index < entries.size) entries[index] else null

    fun getEntry(entryName: String): ResourceEntry? = entryMap[entryName.lowercase()]

    fun addEntry(entry: ResourceEntry): Int {
        val oldEntry = entryMap[entry.getName()]
        val index: Int
        if (oldEntry != null) {
            index = entries.indexOf(oldEntry)
            entries[index] = entry
        } else {
            index = entries.size
            entries.add(entry)
        }

        entryMap[entry.getName()] = entry
        return index
    }

    fun removeEntry(entry: ResourceEntry): Int {
        val oldEntry = entryMap[entry.getName()]
        val index: Int
        if (oldEntry == null) {
            index = -1
        } else {
            index = entries.indexOf(oldEntry)
            entries.removeAt(index)
            entryMap.remove(entry.getName())
        }

        return index
    }

    fun removeEntry(index: Int) {
        val entry = entries.removeAt(index)
        entryMap.remove(entry.getName())
    }

    private fun getShort(buffer: ByteArray, offset: Int): Int {
        return buffer[offset].toInt() and 0xFF or (buffer[offset + 1].toInt() and 0xFF shl 8)
    }

    private fun setShort(number: Int, buffer: ByteArray, offset: Int) {
        buffer[offset] = number.toByte()
        buffer[offset + 1] = (number ushr 8).toByte()
    }

    private fun getInteger(buffer: ByteArray, offset: Int): Int {
        return buffer[offset].toInt() and 0xFF or
            (buffer[offset + 1].toInt() and 0xFF shl 8) or
            (buffer[offset + 2].toInt() and 0xFF shl 16) or
            (buffer[offset + 3].toInt() and 0xFF shl 24)
    }

    private fun setInteger(number: Int, buffer: ByteArray, offset: Int) {
        buffer[offset] = number.toByte()
        buffer[offset + 1] = (number ushr 8).toByte()
        buffer[offset + 2] = (number ushr 16).toByte()
        buffer[offset + 3] = (number ushr 24).toByte()
    }

    override fun toString(): String = file.getPath()

    companion object {
        val databaseTypes = arrayOf("ERF ", "HAK ", "MOD ", "NWM ", "SAV ")

        val databaseVersions = arrayOf("V1.0", "V1.1")
    }
}
