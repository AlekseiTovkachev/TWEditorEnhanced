package app.tweditor

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

class SaveOutputStream @Throws(IOException::class) constructor(private var entry: SaveEntry?) : OutputStream() {
    private var outputStream: FileOutputStream? = null
    private var resourceDataList: MutableList<ByteArray>? = null
    private var dataIndex = 0
    private var dataOffset = 0
    private var resourceLength = 0

    init {
        val entry = entry!!
        entry.resourceOffset = 0L
        entry.resourceLength = 0
        if (entry.onDisk) {
            val file = entry.resourceFile
            if (file!!.exists()) {
                file.delete()
            }
            outputStream = FileOutputStream(file)
        } else {
            resourceDataList = entry.resourceDataList
            resourceDataList!!.clear()
            resourceDataList!!.add(ByteArray(4096))
        }
    }

    @Throws(IOException::class)
    override fun write(b: Int) {
        if (entry == null) {
            throw IOException("Output stream is not open")
        }
        if (outputStream != null) {
            outputStream!!.write(b)
        } else {
            val dataBuffer = resourceDataList!![dataIndex]
            dataBuffer[dataOffset] = b.toByte()
            dataOffset += 1
            if (dataOffset == dataBuffer.size) {
                resourceDataList!!.add(ByteArray(4096))
                dataIndex += 1
                dataOffset = 0
            }
        }

        resourceLength += 1
    }

    @Throws(IOException::class)
    override fun write(buffer: ByteArray, bufferOffset: Int, bufferLength: Int) {
        if (entry == null) {
            throw IOException("Output stream is not open")
        }
        if (outputStream != null) {
            outputStream!!.write(buffer, bufferOffset, bufferLength)
        } else {
            var count = 0
            while (count < bufferLength) {
                val dataBuffer = resourceDataList!![dataIndex]
                val length = (bufferLength - count).coerceAtMost(dataBuffer.size - dataOffset)
                for (i in 0 until length) {
                    dataBuffer[dataOffset + i] = buffer[bufferOffset + count + i]
                }
                count += length
                dataOffset += length
                if (dataOffset == dataBuffer.size) {
                    resourceDataList!!.add(ByteArray(4096))
                    dataIndex += 1
                    dataOffset = 0
                }
            }
        }

        resourceLength += bufferLength
    }

    @Throws(IOException::class)
    override fun flush() {
        if (entry == null) {
            throw IOException("Output stream is not open")
        }
        outputStream?.flush()
    }

    @Throws(IOException::class)
    override fun close() {
        if (entry != null) {
            outputStream?.close()
            outputStream = null

            entry!!.resourceLength = resourceLength
            entry = null
        }
    }

    protected fun finalize() {
        try {
            close()
        } catch (exc: Throwable) {
            Main.logException("Exception while finalizing output stream", exc)
        }
    }
}
