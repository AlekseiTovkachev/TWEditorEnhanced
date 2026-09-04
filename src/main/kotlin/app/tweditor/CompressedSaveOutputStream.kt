package app.tweditor

import java.io.IOException
import java.io.OutputStream
import java.util.zip.GZIPOutputStream

class CompressedSaveOutputStream @Throws(IOException::class) constructor(outputStream: SaveOutputStream) :
    GZIPOutputStream(outputStream, 4096) {
    private var saveOutputStream: OutputStream? = outputStream

    @Throws(IOException::class)
    override fun close() {
        if (saveOutputStream != null) {
            super.close()
            saveOutputStream!!.close()
            saveOutputStream = null
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
