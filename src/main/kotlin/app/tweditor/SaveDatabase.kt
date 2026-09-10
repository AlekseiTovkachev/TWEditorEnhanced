package app.tweditor

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class SaveDatabase(val environment: AppEnvironment, file: File) {
    private val file: File = file
    private val saveName: String
    private var savePrefix = ""
    private var dataOffset = 0
    val entries: MutableList<SaveEntry> = ArrayList(160)
    private val entryMap: MutableMap<String, SaveEntry> = HashMap(160)

    init {
        var saveName = file.getName()
        val sep = saveName.lastIndexOf('.')
        if (sep > 0) {
            saveName = saveName.substring(0, sep)
        }
        this.saveName = saveName
    }

    constructor(environment: AppEnvironment, filename: String) : this(environment, File(filename))

    @Throws(DBException::class, IOException::class)
    fun load() {
        RandomAccessFile(this.file, "r").use { input ->
            var buffer = ByteArray(40)
            var count = input.read(buffer, 0, 12)
            if (count != 12) {
                throw DBException("Save header truncated")
            }
            val signature = String(buffer, 0, 4)
            if (signature != "RGMH") {
                throw DBException("Save signature is not valid")
            }
            val version = getInteger(buffer, 4)
            if (version != 1) {
                throw DBException("Save version " + version + " is not supported")
            }
            this.dataOffset = getInteger(buffer, 8)

            input.seek(input.length() - 8L)
            count = input.read(buffer, 0, 8)
            if (count != 8) {
                throw DBException("Save trailer truncated")
            }
            val resourceOffset = getInteger(buffer, 0)
            val resourceCount = getInteger(buffer, 4)
            input.seek(resourceOffset.toLong())

            for (i in 0 until resourceCount) {
                count = input.read(buffer, 0, 4)
                if (count != 4) {
                    throw DBException("Resource table truncated")
                }
                var length = getInteger(buffer, 0)
                if (buffer.size < length) {
                    buffer = ByteArray(length)
                }
                count = input.read(buffer, 0, length)
                if (count != length) {
                    throw DBException("Resource name truncated")
                }
                val name = String(buffer, 0, length, StandardCharsets.UTF_8)
                count = input.read(buffer, 0, 8)
                if (count != 8) {
                    throw DBException("Resource table truncated")
                }
                length = getInteger(buffer, 0)
                val offset = getInteger(buffer, 4)
                val saveEntry = SaveEntry(name, this.file, offset.toLong(), length, environment.fileSeparator)
                this.entries.add(saveEntry)
                this.entryMap[saveEntry.resourceName] = saveEntry
            }
        }
    }

    @Throws(IOException::class)
    fun save() {
        saveTransactional()
    }

    /**
     * Writes a complete candidate beside the destination, reopens it through
     * the real archive parser, validates every entry stream, and only then
     * replaces the destination. The function parameters are deliberately
     * injectable so the Save seam can exercise creation, validation, and
     * replacement failures without touching a user's original save.
     */
    @Throws(IOException::class)
    fun saveTransactional(
        afterCandidateWritten: (File) -> Unit = {},
        validator: (SaveDatabase) -> Unit = { candidate -> validateCandidate(candidate) },
        replacer: (Path, Path) -> Unit = ::replaceAtomically
    ) {
        val candidate = createCandidateFile()
        val expectedEntries = this.entries.map { it.resourceName }
        val buffer = ByteArray(4096)

        var listOffset = this.dataOffset
        try {
            FileInputStream(this.file).use { headerIn ->
                FileOutputStream(candidate).use { out ->
                    var residualLength = this.dataOffset
                    while (residualLength > 0) {
                        val length = residualLength.coerceAtMost(buffer.size)
                        val count = headerIn.read(buffer, 0, length)
                        if (count != length) {
                            throw IOException("Save game header truncated")
                        }
                        out.write(buffer, 0, count)
                        residualLength -= count
                    }

                    for (entry in this.entries) {
                        if (entry.onDisk) {
                            FileInputStream(entry.resourceFile).use { entryIn ->
                                entryIn.skip(entry.resourceOffset)
                                var residualLength = entry.resourceLength
                                listOffset += residualLength
                                while (residualLength > 0) {
                                    val length = residualLength.coerceAtMost(buffer.size)
                                    val count = entryIn.read(buffer, 0, length)
                                    if (count != length) {
                                        throw IOException("Resource data truncated for " + entry.resourceName)
                                    }
                                    out.write(buffer, 0, count)
                                    residualLength -= count
                                }
                            }
                        } else {
                            val resourceDataList = entry.resourceDataList
                            residualLength = entry.resourceLength
                            listOffset += residualLength
                            var index = 0
                            while (residualLength > 0) {
                                val dataBuffer = resourceDataList!![index]
                                val length = residualLength.coerceAtMost(dataBuffer.size)
                                out.write(dataBuffer, 0, length)
                                residualLength -= length
                                index++
                            }
                        }
                    }

                    var offset = this.dataOffset
                    for (entry in this.entries) {
                        val nameBytes = entry.getResourcePath().toByteArray(StandardCharsets.UTF_8)
                        setInteger(nameBytes.size, buffer, 0)
                        out.write(buffer, 0, 4)
                        out.write(nameBytes)
                        val length = entry.resourceLength
                        setInteger(length, buffer, 0)
                        setInteger(offset, buffer, 4)
                        out.write(buffer, 0, 8)
                        offset += length
                    }

                    setInteger(listOffset, buffer, 0)
                    setInteger(this.entries.size, buffer, 4)
                    out.write(buffer, 0, 8)
                }
            }
        } catch (exc: Exception) {
            candidate.delete()
            if (exc is IOException) {
                throw exc
            }
            throw IOException("Unable to encode Save candidate", exc)
        }

        try {
            afterCandidateWritten(candidate)
        } catch (exc: Exception) {
            throw SaveWriteException(
                "Candidate post-write stage failed; original save was left untouched. Candidate retained at '" +
                    candidate.getPath() + "'",
                candidate,
                exc
            )
        }

        try {
            val candidateDatabase = reloadCandidate(candidate, expectedEntries)
            validator(candidateDatabase)
        } catch (exc: Exception) {
            throw SaveWriteException(
                "Candidate validation failed; original save was left untouched. Candidate retained at '" +
                    candidate.getPath() + "'",
                candidate,
                exc
            )
        }

        try {
            replacer(candidate.toPath(), this.file.toPath())
        } catch (exc: Exception) {
            throw SaveWriteException(
                "Unable to install candidate; original save was left untouched. Candidate retained at '" +
                    candidate.getPath() + "'",
                candidate,
                exc
            )
        }
    }

    private fun createCandidateFile(): File {
        val parent = this.file.toPath().toAbsolutePath().parent
            ?: throw IOException("Save destination has no parent directory")
        return try {
            Files.createTempFile(parent, this.file.getName() + ".candidate-", ".tmp").toFile()
        } catch (exc: IOException) {
            throw IOException("Unable to create a Save candidate beside '" + this.file.getName() + "'", exc)
        }
    }

    private fun validateCandidate(candidate: SaveDatabase) {
        for (entry in candidate.entries) {
            entry.getInputStream().use { input ->
                val buffer = ByteArray(8192)
                while (input.read(buffer) > 0) {
                    // Reading to EOF validates both raw and compressed entries.
                }
            }
        }
    }

    private fun reloadCandidate(candidate: File, expectedEntries: List<String>): SaveDatabase {
        val candidateDatabase = SaveDatabase(environment, candidate)
        candidateDatabase.load()
        val actualEntries = candidateDatabase.entries.map { it.resourceName }
        if (actualEntries != expectedEntries) {
            throw IOException(
                "Candidate entry identities differ from the source: expected " +
                    expectedEntries.size + ", got " + actualEntries.size
            )
        }
        return candidateDatabase
    }

    private fun replaceAtomically(candidate: Path, destination: Path) {
        try {
            Files.move(candidate, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (unsupported: AtomicMoveNotSupportedException) {
            // The candidate is in the destination directory, so the fallback
            // still replaces in one filesystem operation without deleting the
            // original first.
            Files.move(candidate, destination, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun setSavePrefix(savePrefix: String) {
        this.savePrefix = savePrefix
    }

    fun repathEntries() {
        for (entry in entries) {
            entry.repathTo(saveName, environment.fileSeparator)
        }
    }

    fun renameEntry(oldBaseName: String, newBaseName: String) {
        val entry = getEntry(oldBaseName) ?: return
        entryMap.remove(entry.resourceName)
        entry.renameBaseName(newBaseName, environment.fileSeparator)
        entryMap[entry.resourceName] = entry
    }

    fun getName(): String = saveName

    fun getPath(): String = file.getPath()

    fun getFile(): File = file

    fun getEntry(resourceName: String): SaveEntry? {
        var entry = entryMap[resourceName.lowercase()]
        if (entry == null) {
            val resourcePath = this.saveName + "\\" + resourceName
            entry = entryMap[resourcePath.lowercase()]
        }
        return entry
    }

    @Throws(IOException::class)
    fun addEntry(pathName: String, file: File) {
        val saveEntry = SaveEntry(this.savePrefix + pathName, environment.fileSeparator)
        saveEntry.readFromFile(file)
        addEntry(saveEntry)
    }

    fun addEntry(entry: SaveEntry) {
        val name = entry.resourceName
        val oldEntry = entryMap[name]
        if (oldEntry != null) {
            this.entries[this.entries.indexOf(oldEntry)] = entry
        } else {
            this.entries.add(entry)
        }

        entryMap[name] = entry
    }

    private fun getInteger(buffer: ByteArray, offset: Int): Int {
        return buffer[offset].toInt() and 0xFF or
            (buffer[offset + 1].toInt() and 0xFF shl 8) or
            (buffer[offset + 2].toInt() and 0xFF shl 16) or
            (buffer[offset + 3].toInt() and 0xFF shl 24)
    }

    private fun setInteger(number: Int, buffer: ByteArray, offset: Int) {
        buffer[offset] = number.toByte()
        buffer[offset + 1] = (number ushr 8).toByte()
        buffer[offset + 2] = (number ushr 16).toByte()
        buffer[offset + 3] = (number ushr 24).toByte()
    }
}
