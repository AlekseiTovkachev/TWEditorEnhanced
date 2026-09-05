package app.tweditor

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class SaveEntry private constructor(
    private var resourcePath: String,
    resourceFile: File?,
    resourceOffset: Long,
    resourceLength: Int,
    onDisk: Boolean,
    fileSeparator: String
) {
    var onDisk: Boolean
    var resourceFile: File?
    var resourceOffset: Long
    var resourceLength: Int
    var resourceDataList: MutableList<ByteArray>?
    val isCompressed: Boolean
    var resourceName: String
        private set

    init {
        this.resourceFile = resourceFile
        this.resourceOffset = resourceOffset
        this.resourceLength = resourceLength
        val index = resourcePath.lastIndexOf(fileSeparator)
        this.resourceName = if (index >= 0) {
            resourcePath.substring(index + 1).lowercase()
        } else {
            resourcePath.lowercase()
        }

        if (onDisk) {
            this.resourceDataList = null
        } else {
            this.resourceDataList = ArrayList()
        }
        this.onDisk = onDisk

        val sep = this.resourceName.lastIndexOf('.')
        this.isCompressed = sep > 0 && this.resourceName.substring(sep) == ".sav"
    }

    constructor(path: String, fileSeparator: String) : this(path, null, 0, 0, false, fileSeparator)

    constructor(path: String, file: File, offset: Long, length: Int, fileSeparator: String) : this(
        path, file, offset, length, true, fileSeparator
    )

    @Throws(IOException::class)
    fun readFromFile(file: File) {
        FileInputStream(file).use { inputStream ->
            getOutputStream().use { outputStream ->
                val buffer = ByteArray(4096)
                var count: Int
                while (inputStream.read(buffer).also { count = it } > 0) {
                    outputStream.write(buffer, 0, count)
                }
            }
        }
    }

    fun getResourcePath(): String = resourcePath

    fun repathTo(saveName: String, fileSeparator: String) {
        val index = resourcePath.indexOf(fileSeparator)
        val suffix = if (index >= 0) resourcePath.substring(index) else fileSeparator + resourcePath
        this.resourcePath = saveName + suffix
        updateName(fileSeparator)
    }

    fun renameBaseName(newBaseName: String, fileSeparator: String) {
        val index = resourcePath.indexOf(fileSeparator)
        this.resourcePath = if (index >= 0) resourcePath.substring(0, index + 1) + newBaseName else newBaseName
        updateName(fileSeparator)
    }

    private fun updateName(fileSeparator: String) {
        val index = resourcePath.lastIndexOf(fileSeparator)
        this.resourceName = if (index >= 0) {
            resourcePath.substring(index + 1).lowercase()
        } else {
            resourcePath.lowercase()
        }
    }

    fun setResourceFile(file: File, offset: Int, length: Int) {
        this.resourceFile = file
        this.resourceOffset = offset.toLong()
        this.resourceLength = length
        this.resourceDataList = null
        this.onDisk = true
    }

    @Throws(IOException::class)
    fun getInputStream(): InputStream {
        return if (isCompressed) {
            CompressedSaveInputStream(SaveInputStream(this))
        } else {
            SaveInputStream(this)
        }
    }

    @Throws(IOException::class)
    fun getOutputStream(): OutputStream {
        return if (isCompressed) {
            CompressedSaveOutputStream(SaveOutputStream(this))
        } else {
            SaveOutputStream(this)
        }
    }
}
