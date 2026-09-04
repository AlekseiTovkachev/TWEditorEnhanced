package app.tweditor

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

class SaveDatabase(val environment: AppEnvironment, file: File) {
    private val file: File = file
    private val saveName: String
    private var savePrefix = ""
    private var dataOffset = 0
    val entries: MutableList<SaveEntry> = ArrayList(160)
    private val entryMap: MutableMap<String, SaveEntry> = HashMap(160)

    init {
        var saveName = file.getName()
        val sep = saveName.lastIndexOf('.')
        if (sep > 0) {
            saveName = saveName.substring(0, sep)
        }
        this.saveName = saveName
    }

    constructor(environment: AppEnvironment, filename: String) : this(environment, File(filename))

    @Throws(DBException::class, IOException::class)
    fun load() {
        RandomAccessFile(this.file, "r").use { input ->
            var buffer = ByteArray(40)
            var count = input.read(buffer, 0, 12)
            if (count != 12) {
                throw DBException("Save header truncated")
            }
            val signature = String(buffer, 0, 4)
            if (signature != "RGMH") {
                throw DBException("Save signature is not valid")
            }
            val version = getInteger(buffer, 4)
            if (version != 1) {
                throw DBException("Save version " + version + " is not supported")
            }
            this.dataOffset = getInteger(buffer, 8)

            input.seek(input.length() - 8L)
            count = input.read(buffer, 0, 8)
            if (count != 8) {
                throw DBException("Save trailer truncated")
            }
            val resourceOffset = getInteger(buffer, 0)
            val resourceCount = getInteger(buffer, 4)
            input.seek(resourceOffset.toLong())

            for (i in 0 until resourceCount) {
                count = input.read(buffer, 0, 4)
                if (count != 4) {
                    throw DBException("Resource table truncated")
                }
                var length = getInteger(buffer, 0)
                if (buffer.size < length) {
                    buffer = ByteArray(length)
                }
                count = input.read(buffer, 0, length)
                if (count != length) {
                    throw DBException("Resource name truncated")
                }
                val name = String(buffer, 0, length, StandardCharsets.UTF_8)
                count = input.read(buffer, 0, 8)
                if (count != 8) {
                    throw DBException("Resource table truncated")
                }
                length = getInteger(buffer, 0)
                val offset = getInteger(buffer, 4)
                val saveEntry = SaveEntry(name, this.file, offset.toLong(), length, environment.fileSeparator)
                this.entries.add(saveEntry)
                this.entryMap[saveEntry.resourceName] = saveEntry
            }
        }
    }

    @Throws(IOException::class)
    fun save() {
        val outputFile = File(this.file.getPath() + ".tmp")
        if (outputFile.exists()) {
            outputFile.delete()
        }
        val buffer = ByteArray(4096)

        var listOffset = this.dataOffset
        try {
            FileInputStream(this.file).use { headerIn ->
                FileOutputStream(outputFile).use { out ->
                    var residualLength = this.dataOffset
                    while (residualLength > 0) {
                        val length = residualLength.coerceAtMost(buffer.size)
                        val count = headerIn.read(buffer, 0, length)
                        if (count != length) {
                            throw IOException("Save game header truncated")
                        }
                        out.write(buffer, 0, count)
                        residualLength -= count
                    }

                    for (entry in this.entries) {
                        if (entry.onDisk) {
                            FileInputStream(entry.resourceFile).use { entryIn ->
                                entryIn.skip(entry.resourceOffset)
                                var residualLength = entry.resourceLength
                                listOffset += residualLength
                                while (residualLength > 0) {
                                    val length = residualLength.coerceAtMost(buffer.size)
                                    val count = entryIn.read(buffer, 0, length)
                                    if (count != length) {
                                        throw IOException("Resource data truncated for " + entry.resourceName)
                                    }
                                    out.write(buffer, 0, count)
                                    residualLength -= count
                                }
                            }
                        } else {
                            val resourceDataList = entry.resourceDataList
                            residualLength = entry.resourceLength
                            listOffset += residualLength
                            var index = 0
                            while (residualLength > 0) {
                                val dataBuffer = resourceDataList!![index]
                                val length = residualLength.coerceAtMost(dataBuffer.size)
                                out.write(dataBuffer, 0, length)
                                residualLength -= length
                                index++
                            }
                        }
                    }

                    var offset = this.dataOffset
                    for (entry in this.entries) {
                        val nameBytes = entry.getResourcePath().toByteArray(StandardCharsets.UTF_8)
                        setInteger(nameBytes.size, buffer, 0)
                        out.write(buffer, 0, 4)
                        out.write(nameBytes)
                        val length = entry.resourceLength
                        setInteger(length, buffer, 0)
                        setInteger(offset, buffer, 4)
                        out.write(buffer, 0, 8)
                        offset += length
                    }

                    setInteger(listOffset, buffer, 0)
                    setInteger(this.entries.size, buffer, 4)
                    out.write(buffer, 0, 8)
                }
            }
        } catch (exc: IOException) {
            outputFile.delete()
            throw exc
        }

        if (this.file.exists() && !this.file.delete()) {
            throw IOException("Unable to delete '" + this.file.getName() + "'")
        }
        if (!outputFile.renameTo(this.file)) {
            throw IOException("Unable to rename '" + outputFile.getName() + "'")
        }
    }

    fun setSavePrefix(savePrefix: String) {
        this.savePrefix = savePrefix
    }

    fun getName(): String = saveName

    fun getPath(): String = file.getPath()

    fun getFile(): File = file

    fun getEntry(resourceName: String): SaveEntry? {
        var entry = entryMap[resourceName.lowercase()]
        if (entry == null) {
            val resourcePath = this.saveName + "\\" + resourceName
            entry = entryMap[resourcePath.lowercase()]
        }
        return entry
    }

    @Throws(IOException::class)
    fun addEntry(pathName: String, file: File) {
        val saveEntry = SaveEntry(this.savePrefix + pathName, environment.fileSeparator)
        saveEntry.readFromFile(file)
        addEntry(saveEntry)
    }

    fun addEntry(entry: SaveEntry) {
        val name = entry.resourceName
        val oldEntry = entryMap[name]
        if (oldEntry != null) {
            this.entries[this.entries.indexOf(oldEntry)] = entry
        } else {
            this.entries.add(entry)
        }

        entryMap[name] = entry
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
}
