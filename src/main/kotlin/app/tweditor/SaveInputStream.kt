package app.tweditor

import java.io.EOFException
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

class SaveInputStream @Throws(IOException::class) constructor(private var entry: SaveEntry?) : InputStream() {
    private var inputStream: FileInputStream? = null
    private var resourceDataList: List<ByteArray>? = null
    private var dataIndex = 0
    private var dataOffset = 0
    private var residualLength: Int

    init {
        val entry = entry!!
        residualLength = entry.resourceLength
        if (entry.onDisk) {
            inputStream = FileInputStream(entry.resourceFile)
            inputStream!!.skip(entry.resourceOffset)
        } else {
            resourceDataList = entry.resourceDataList
        }
    }

    @Throws(IOException::class)
    override fun close() {
        inputStream?.close()
        inputStream = null
        entry = null
        residualLength = 0
    }

    override fun available(): Int = residualLength

    @Throws(IOException::class)
    override fun read(): Int {
        val entry = entry ?: throw IOException("Input stream is not open")
        val result: Int
        if (residualLength == 0) {
            result = -1
        } else if (inputStream != null) {
            result = inputStream!!.read()
            if (result == -1) {
                throw EOFException("Unexpected end of stream")
            }
            residualLength -= 1
        } else {
            val dataBuffer = resourceDataList!![dataIndex]
            result = dataBuffer[dataOffset].toInt() and 0xFF
            dataOffset += 1
            residualLength -= 1
            if (dataOffset == dataBuffer.size) {
                dataIndex += 1
                dataOffset = 0
            }
        }

        return result
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, bufferOffset: Int, bufferLength: Int): Int {
        val entry = entry ?: throw IOException("Input stream is not open")
        var count = 0
        if (residualLength == 0) {
            count = -1
        } else if (inputStream != null) {
            val length = residualLength.coerceAtMost(bufferLength)
            count = inputStream!!.read(buffer, bufferOffset, length)
            if (count < 0) {
                throw EOFException("Unexpected end of stream")
            }
            residualLength -= count
        } else {
            count = 0
            val length = residualLength.coerceAtMost(bufferLength)
            while (count < length) {
                val dataBuffer = resourceDataList!![dataIndex]
                val copyLength = (dataBuffer.size - dataOffset).coerceAtMost(length - count)
                for (i in 0 until copyLength) {
                    buffer[bufferOffset + count + i] = dataBuffer[dataOffset + i]
                }
                count += copyLength
                dataOffset += copyLength
                if (dataOffset == dataBuffer.size) {
                    dataIndex += 1
                    dataOffset = 0
                }
            }

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
}
