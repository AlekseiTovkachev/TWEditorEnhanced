package app.tweditor

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

class StringsDatabase(val file: File) {
    private val input = RandomAccessFile(file, "r")
    private val languageID: Int
    private val stringCount: Int
    private val entryOffset = 20
    private val stringOffset: Int

    constructor(filePath: String) : this(File(filePath))

    init {
        val buffer = ByteArray(20)
        val count = input.read(buffer)
        if (count != buffer.size) {
            throw DBException("TLK header truncated")
        }
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
                input.seek(entryOffset + refid * 40L)
                val count = input.read(buffer)
                if (count != buffer.size) {
                    throw DBException("String entry truncated for reference " + refid)
                }

                if (buffer[0].toInt() and 0x1 != 0) {
                    val offset = getInteger(buffer, 28)
                    val length = getInteger(buffer, 32)
                    val data = ByteArray(length)
                    input.seek(stringOffset + offset.toLong())
                    val dataCount = input.read(data)
                    if (dataCount != length) {
                        throw DBException("String data truncated for reference " + refid)
                    }
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
            input.close()
        } catch (exc: IOException) {
        }
    }

    private fun getInteger(buffer: ByteArray, offset: Int): Int {
        return buffer[offset].toInt() and 0xFF or
            (buffer[offset + 1].toInt() and 0xFF shl 8) or
            (buffer[offset + 2].toInt() and 0xFF shl 16) or
            (buffer[offset + 3].toInt() and 0xFF shl 24)
    }
}
