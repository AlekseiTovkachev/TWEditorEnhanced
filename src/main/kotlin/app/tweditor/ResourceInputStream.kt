package app.tweditor

import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

class ResourceInputStream @Throws(IOException::class) constructor(private var entry: ResourceEntry?) : InputStream() {
    private var input: FileInputStream?
    private var residualLength: Int

    init {
        val entry = entry!!
        residualLength = entry.length
        input = FileInputStream(entry.file)
        input!!.skip(entry.offset)
    }

    @Throws(IOException::class)
    override fun close() {
        input?.close()
        input = null

        entry = null
        residualLength = 0
    }

    override fun available(): Int = residualLength

    @Throws(IOException::class)
    override fun read(): Int {
        val fileInput = input ?: throw IOException("Input stream closed")
        val b: Int
        if (residualLength > 0) {
            b = fileInput.read()
            if (b != -1) {
                residualLength -= 1
            }
        } else {
            b = -1
        }

        return b
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, bufferOffset: Int, bufferLength: Int): Int {
        val fileInput = input ?: throw IOException("Input stream closed")
        val count: Int
        if (residualLength > 0) {
            count = fileInput.read(buffer, bufferOffset, bufferLength.coerceAtMost(residualLength))
            if (count != -1) {
                residualLength -= count
            }
        } else {
            count = -1
        }

        return count
    }

    @Throws(IOException::class)
    override fun skip(count: Long): Long {
        val fileInput = input ?: throw IOException("Input stream closed")
        val skipped = fileInput.skip(count.coerceAtMost(residualLength.toLong()))
        residualLength = (residualLength - skipped).toInt()
        return skipped
    }

    protected fun finalize() {
        try {
            close()
        } catch (exc: Throwable) {
            Main.logException("Exception while finalizing input stream", exc)
        }
    }
}
