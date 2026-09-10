package app.tweditor

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

class StringsDatabase private constructor(
    val file: File,
    private val inMemory: ByteArray?
) : AutoCloseable {
    private val input: RandomAccessFile? = if (inMemory == null) RandomAccessFile(file, "r") else null
    private val languageID: Int
    private val stringCount: Int
    private val entryOffset = 20
    private val stringOffset: Int

    constructor(file: File) : this(file, null)

    constructor(filePath: String) : this(File(filePath))

    /** Reads an archive entry once, allowing packed module TLKs to be layered. */
    constructor(inputStream: InputStream, name: String = "module.tlk") :
        this(File(name), inputStream.use { it.readBytes() })

    init {
        val buffer = readBytes(0L, 20, "TLK header")
        val type = String(buffer, 0, 4)
        val version = String(buffer, 4, 4)
        if (type != "TLK ") {
            throw DBException("File type '" + type + "' is not supported")
        }
        if (version != "V3.0") {
            throw DBException("File version '" + version + "' is not supported")
        }
        this.languageID = getInteger(buffer, 8)
        this.stringCount = getInteger(buffer, 12)
        this.stringOffset = getInteger(buffer, 16)
    }

    fun getName(): String = file.getName()

    fun getLanguageID(): Int = languageID

    fun getString(stringRef: Int): String {
        var string: String? = null
        try {
            val refid = stringRef and 0xFFFFFF
            if (refid < stringCount) {
                val buffer = ByteArray(40)
                val entry = readBytes(entryOffset + refid * 40L, buffer.size, "String entry for reference " + refid)
                entry.copyInto(buffer)

                if (buffer[0].toInt() and 0x1 != 0) {
                    val offset = getInteger(buffer, 28)
                    val length = getInteger(buffer, 32)
                    if (length < 0) {
                        throw DBException("String data length is invalid for reference " + refid)
                    }
                    val data = readBytes(
                        stringOffset + offset.toLong(),
                        length,
                        "String data for reference " + refid
                    )
                    string = String(data, StandardCharsets.UTF_8)
                }
            }
        } catch (exc: DBException) {
            Main.logException("String database format error", exc)
        } catch (exc: IOException) {
            Main.logException("Unable to read string database", exc)
        }

        return string ?: ""
    }

    fun getLabel(stringRef: Int): String {
        val string = StringBuilder(getString(stringRef).trim())

        var sep = string.length - 1
        if (sep > 0) {
            val c = string[sep]
            if (c == '.' || c == ':') {
                string.deleteCharAt(sep)
            }
        }

        var index = 0
        while (true) {
            sep = string.indexOf("<", index)
            if (sep < 0) {
                break
            }
            index = sep
            sep = string.indexOf(">", index)
            if (sep < 0) {
                break
            }
            string.delete(index, sep + 1)
        }

        index = 0
        while (true) {
            sep = string.indexOf("{", index)
            if (sep < 0) {
                break
            }
            index = sep
            sep = string.indexOf("}", index)
            if (sep < 0) {
                break
            }
            string.delete(index, sep + 1)
        }

        return string.toString()
    }

    fun getHeading(stringRef: Int): String {
        var heading: String? = null
        val text = getString(stringRef).trim()
        var start = text.indexOf("<cHEADER>")
        if (start < 0) start = text.indexOf("<cHeader>")
        if (start < 0) start = text.indexOf("<cBOLD>")
        if (start < 0) start = text.indexOf("<cBold>")
        if (start >= 0) {
            start = text.indexOf('>', start) + 1
            val stop = text.indexOf("</c>", start)
            if (stop > start) {
                heading = text.substring(start, stop)
            }
        }
        return heading ?: text
    }

    @Suppress("removal")
    protected fun finalize() {
        try {
            close()
        } catch (exc: IOException) {
        }
    }

    override fun close() {
        input?.close()
    }

    private fun readBytes(offset: Long, length: Int, label: String): ByteArray {
        if (offset < 0L || length < 0) {
            throw DBException(label + " is invalid")
        }
        val bytes = inMemory
        if (bytes != null) {
            if (offset > bytes.size.toLong() || length.toLong() > bytes.size.toLong() - offset) {
                throw DBException(label + " truncated")
            }
            return bytes.copyOfRange(offset.toInt(), offset.toInt() + length)
        }

        val fileInput = input ?: throw DBException("TLK input is closed")
        val result = ByteArray(length)
        try {
            fileInput.seek(offset)
            fileInput.readFully(result)
        } catch (exc: IOException) {
            throw DBException(label + " truncated", exc)
        }
        return result
    }

    private fun getInteger(buffer: ByteArray, offset: Int): Int {
        return buffer[offset].toInt() and 0xFF or
            (buffer[offset + 1].toInt() and 0xFF shl 8) or
            (buffer[offset + 2].toInt() and 0xFF shl 16) or
            (buffer[offset + 3].toInt() and 0xFF shl 24)
    }
}
