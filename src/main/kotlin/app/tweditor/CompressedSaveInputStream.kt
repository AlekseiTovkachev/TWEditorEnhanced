package app.tweditor

import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream

class CompressedSaveInputStream @Throws(IOException::class) constructor(inputStream: SaveInputStream) :
    GZIPInputStream(inputStream, 4096) {
    private var saveInputStream: InputStream? = inputStream

    @Throws(IOException::class)
    override fun close() {
        if (saveInputStream != null) {
            super.close()
            saveInputStream!!.close()
            saveInputStream = null
        }
    }

    protected fun finalize() {
        try {
            close()
        } catch (exc: Throwable) {
            Main.logException("Exception while finalizing input stream", exc)
        }
    }
}
