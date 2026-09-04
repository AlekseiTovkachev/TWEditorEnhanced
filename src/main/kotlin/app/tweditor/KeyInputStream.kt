package app.tweditor

import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile

class KeyInputStream @Throws(DBException::class, IOException::class) constructor(keyEntry: KeyEntry) : InputStream() {
    private var input: RandomAccessFile?
    private var dataOffset = 0L
    private var residualLength = 0

    init {
        val file = File(keyEntry.archivePath)
        val randomAccess = RandomAccessFile(file, "r")
        input = randomAccess

        val header = ByteArray(20)
        var count = randomAccess.read(header)
        if (count != header.size) {
            throw DBException("BIF header is too short")
        }
        val type = String(header, 0, 4)
        if (type != "BIFF") {
            throw DBException("BIF signature is not correct")
        }
        val version = String(header, 4, 4)
        if (version != "V1.1") {
            throw DBException("BIF version " + version + " is not supported")
        }
        val resourceCount = getInteger(header, 8)
        val resourceOffset = getInteger(header, 16).toLong()

        val buffer = ByteArray(20)
        randomAccess.seek(resourceOffset)
        val keyID = keyEntry.resourceID
        for (i in 0 until resourceCount) {
            count = randomAccess.read(buffer)
            if (count != buffer.size) {
                throw DBException("Resource table truncated")
            }
            val resourceID = getInteger(buffer, 0)
            if (resourceID == keyID) {
                val resourceType = getShort(buffer, 16)
                if (resourceType != keyEntry.resourceType) {
                    throw DBException("KEY/BIF resource type mismatch")
                }
                dataOffset = getInteger(buffer, 8).toLong()
                residualLength = getInteger(buffer, 12)
                break
            }
        }

        if (dataOffset == 0L) {
            throw DBException("KEY resource '" + keyEntry.fileName + "' not found in BIF")
        }
        input = randomAccess
    }

    @Throws(IOException::class)
    override fun close() {
        input?.close()
        input = null
        residualLength = 0
    }

    override fun available(): Int = residualLength

    @Throws(IOException::class)
    override fun read(): Int {
        val randomAccess = input ?: throw IOException("Input stream is not open")
        val result: Int
        if (residualLength == 0) {
            result = -1
        } else {
            randomAccess.seek(dataOffset)
            result = randomAccess.readByte().toInt() and 0xFF
            dataOffset += 1L
            residualLength -= 1
        }

        return result
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, bufferOffset: Int, bufferLength: Int): Int {
        val randomAccess = input ?: throw IOException("Input stream is not open")
        var count = 0
        if (residualLength == 0) {
            count = -1
        } else {
            randomAccess.seek(dataOffset)
            val length = residualLength.coerceAtMost(bufferLength)
            count = randomAccess.read(buffer, bufferOffset, length)
            if (count < 0) {
                throw EOFException("Unexpected end of stream")
            }
            dataOffset += count
            residualLength -= count
        }

        return count
    }

    protected fun finalize() {
        try {
            close()
        } catch (exc: Throwable) {
            Main.logException("Exception while finalizing input stream", exc)
        }
    }

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
