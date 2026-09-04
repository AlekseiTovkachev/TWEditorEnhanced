package app.tweditor

import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringWriter

class StreamReader(inputStream: InputStream, private val lineSeparator: String) : Thread() {
    private val reader = InputStreamReader(inputStream)
    private val writer = StringWriter(1024)
    private var buffer: StringBuffer? = null
    private var index = 0

    override fun run() {
        try {
            var c: Int
            while (reader.read().also { c = it } != -1) {
                writer.write(c)
            }
            reader.close()
            buffer = writer.buffer
        } catch (exc: IOException) {
            Main.logException("Unable to read from input stream", exc)
        }
    }

    fun getBuffer(): StringBuffer {
        return buffer ?: throw IllegalThreadStateException("Input stream is still open")
    }

    fun getLine(): String? {
        val buffer = buffer ?: throw IllegalThreadStateException("Input stream is still open")
        var line: String? = null
        val length = buffer.length
        if (index < length) {
            val sep = buffer.indexOf(lineSeparator, index)
            if (sep < 0) {
                line = buffer.substring(index)
                index = length
            } else {
                line = buffer.substring(index, sep)
                index = sep + lineSeparator.length
            }
        }

        return line
    }
}
