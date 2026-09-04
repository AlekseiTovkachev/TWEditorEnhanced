package app.tweditor

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

class KeyDatabase(val environment: AppEnvironment, private val file: File) {
    private var keyEntries: MutableList<KeyEntry>? = null
    private var keyEntriesMap: MutableMap<String, KeyEntry>? = null
    private var archiveNames: MutableList<String>? = null

    constructor(environment: AppEnvironment, filePath: String) : this(environment, File(filePath))

    init {
        readFile()
    }

    @Throws(DBException::class, IOException::class)
    private fun readFile() {
        RandomAccessFile(this.file, "r").use { input ->
            val header = ByteArray(68)
            var count = input.read(header)
            if (count != header.size) {
                throw DBException("KEY header length is incorrect")
            }
            val signature = String(header, 0, 4)
            if (signature != "KEY ") {
                throw DBException("KEY header signature is incorrect")
            }
            val version = String(header, 4, 4)
            if (version != "V1.1") {
                throw DBException("KEY header version " + version + " is not supported")
            }
            val fileCount = getInteger(header, 8)
            var fileOffset = getInteger(header, 20).toLong()
            val keyCount = getInteger(header, 12)
            var keyOffset = getInteger(header, 24).toLong()

            val archiveNames = ArrayList<String>(fileCount)
            val fileBuffer = ByteArray(12)
            var nameBuffer = ByteArray(256)
            for (i in 0 until fileCount) {
                input.seek(fileOffset)
                count = input.read(fileBuffer)
                if (count != fileBuffer.size) {
                    throw DBException("File table truncated")
                }
                val nameOffset = getInteger(fileBuffer, 4).toLong()
                val nameLength = getInteger(fileBuffer, 8)
                if (nameLength > nameBuffer.size) {
                    nameBuffer = ByteArray(nameLength)
                }
                input.seek(nameOffset)
                input.read(nameBuffer, 0, nameLength)
                val fileName = String(nameBuffer, 0, nameLength)
                archiveNames.add(fileName)

                fileOffset += 12L
            }
            this.archiveNames = archiveNames

            val keyEntries = ArrayList<KeyEntry>(keyCount)
            val keyEntriesMap = HashMap<String, KeyEntry>(keyCount)
            val keyBuffer = ByteArray(26)
            for (i in 0 until keyCount) {
                input.seek(keyOffset)
                count = input.read(keyBuffer)
                if (count != keyBuffer.size) {
                    throw DBException("Key table truncated")
                }
                var nameLength = 1
                while (nameLength < 16 && keyBuffer[nameLength].toInt() != 0) {
                    nameLength++
                }
                val resourceName = String(keyBuffer, 0, nameLength)
                val resourceType = getShort(keyBuffer, 16)
                val resourceID = getInteger(keyBuffer, 18)
                val index = getInteger(keyBuffer, 22) ushr 20
                if (index >= archiveNames.size) {
                    throw DBException("BIF index for resource " + resourceName + " is too large")
                }
                val archivePath = this.file.getParent() + environment.fileSeparator + archiveNames[index]
                val keyEntry = KeyEntry(resourceName, resourceType, resourceID, archivePath)
                keyEntries.add(keyEntry)
                keyEntriesMap[keyEntry.fileName.lowercase()] = keyEntry
                keyOffset += 26L
            }
            this.keyEntries = keyEntries
            this.keyEntriesMap = keyEntriesMap
        }
    }

    fun getName(): String = file.getName()

    fun getEntries(): List<KeyEntry> = keyEntries ?: emptyList()

    fun getEntry(fileName: String): KeyEntry? = keyEntriesMap!![fileName.lowercase()]

    private fun getShort(buffer: ByteArray, offset: Int): Int {
        var value = buffer[offset].toInt() and 0xFF or (buffer[offset + 1].toInt() and 0xFF shl 8)
        if (value >= 32768) {
            value = value or -65536
        }
        return value
    }

    private fun getInteger(buffer: ByteArray, offset: Int): Int {
        return buffer[offset].toInt() and 0xFF or
            (buffer[offset + 1].toInt() and 0xFF shl 8) or
            (buffer[offset + 2].toInt() and 0xFF shl 16) or
            (buffer[offset + 3].toInt() and 0xFF shl 24)
    }
}
