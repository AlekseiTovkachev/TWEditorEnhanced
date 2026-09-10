package app.tweditor

import java.io.File
import java.io.IOException

/** An installed-save failure that keeps the generated candidate available for diagnosis. */
class SaveWriteException(
    message: String,
    val candidateFile: File,
    cause: Throwable
) : IOException(message, cause)
