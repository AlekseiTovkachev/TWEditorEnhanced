package app.tweditor

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

class Database(val environment: AppEnvironment) {
    private var file: File? = null
    private var name = ""
    private var fileType: String? = null
    private var fileVersion: String? = null
    private var topLevelStruct: DBElement? = null
    private var structBuffer: ByteArray? = null
    private var structArraySize = 0
    private var structArrayCount = 0
    private var fieldBuffer: ByteArray? = null
    private var fieldArraySize = 0
    private var fieldArrayCount = 0
    private var labelBuffer: ByteArray? = null
    private var labelArraySize = 0
    private var labelArrayCount = 0
    private var fieldDataBuffer: ByteArray? = null
    private var fieldDataSize = 0
    private var fieldDataLength = 0
    private var fieldIndicesBuffer: ByteArray? = null
    private var fieldIndicesSize = 0
    private var fieldIndicesLength = 0
    private var listIndicesBuffer: ByteArray? = null
    private var listIndicesSize = 0
    private var listIndicesLength = 0

    constructor(environment: AppEnvironment, filePath: String) : this(environment) {
        this.file = File(filePath)
        this.name = this.file!!.getName()
    }

    constructor(environment: AppEnvironment, file: File) : this(environment) {
        this.file = file
        this.name = file.getName()
    }

    fun getFile(): File? = file

    fun getName(): String = name

    fun setName(name: String) {
        this.name = name
    }

    fun getType(): String? = fileType

    fun setType(type: String) {
        if (type.length != 4) {
            throw IllegalArgumentException("The file type is not 4 characters")
        }
        this.fileType = type
    }

    fun getVersion(): String? = fileVersion

    fun setVersion(version: String) {
        if (version != "V3.2" && version != "V3.3") {
            throw IllegalArgumentException("File version " + version + " is not supported")
        }
        this.fileVersion = version
    }

    fun getTopLevelStruct(): DBElement? = topLevelStruct

    fun setTopLevelStruct(struct: DBElement) {
        if (struct.getType() != 14) {
            throw IllegalArgumentException("Database element is not a structure")
        }
        this.topLevelStruct = struct
    }

    @Throws(DBException::class, IOException::class)
    fun load() {
        if (file == null) {
            throw IllegalStateException("No database file is available")
        }
        FileInputStream(file!!).use { input ->
            load(input)
        }
    }

    @Throws(DBException::class, IOException::class)
    fun load(input: InputStream) {
        try {
            val headerBuffer = ByteArray(56)
            var count = input.read(headerBuffer)
            if (count != 56) {
                throw DBException(name + ": GFF header is too short")
            }

            this.fileType = String(headerBuffer, 0, 4)
            this.fileVersion = String(headerBuffer, 4, 4)
            if (fileVersion != "V3.2" && fileVersion != "V3.3") {
                throw DBException(name + ": GFF version " + fileVersion + " is not supported")
            }
            val structBaseOffset = getInteger(headerBuffer, 8)
            this.structArrayCount = getInteger(headerBuffer, 12)
            this.structArraySize = this.structArrayCount
            val fieldBaseOffset = getInteger(headerBuffer, 16)
            this.fieldArrayCount = getInteger(headerBuffer, 20)
            this.fieldArraySize = this.fieldArrayCount
            val labelBaseOffset = getInteger(headerBuffer, 24)
            this.labelArrayCount = getInteger(headerBuffer, 28)
            this.labelArraySize = this.labelArrayCount
            val fieldDataOffset = getInteger(headerBuffer, 32)
            this.fieldDataLength = getInteger(headerBuffer, 36)
            this.fieldDataSize = this.fieldDataLength
            val fieldIndicesOffset = getInteger(headerBuffer, 40)
            this.fieldIndicesLength = getInteger(headerBuffer, 44)
            this.fieldIndicesSize = this.fieldIndicesLength
            val listIndicesOffset = getInteger(headerBuffer, 48)
            this.listIndicesLength = getInteger(headerBuffer, 52)
            this.listIndicesSize = this.listIndicesLength

            if (this.structArrayCount < 1) {
                throw DBException(name + ": GFF file contains no structures")
            }
            var size = 12 * this.structArraySize
            this.structBuffer = ByteArray(size)
            count = input.read(this.structBuffer!!)
            if (count != size) {
                throw DBException(name + ": Structure array data truncated")
            }

            if (this.fieldArrayCount > 0) {
                size = 12 * this.fieldArraySize
                this.fieldBuffer = ByteArray(size)
                count = input.read(this.fieldBuffer!!)
                if (count != size) {
                    throw DBException(name + ": Field array data truncated")
                }
            }

            if (this.labelArrayCount > 0) {
                size = 16 * this.labelArraySize
                this.labelBuffer = ByteArray(size)
                count = input.read(this.labelBuffer!!)
                if (count != size) {
                    throw DBException(name + ": Label array data truncated")
                }
            }

            if (this.fieldDataLength > 0) {
                this.fieldDataBuffer = ByteArray(this.fieldDataSize)
                count = input.read(this.fieldDataBuffer!!)
                if (count != this.fieldDataSize) {
                    throw DBException(name + ": Field data truncated")
                }
            }

            if (this.fieldIndicesLength > 0) {
                this.fieldIndicesBuffer = ByteArray(this.fieldIndicesSize)
                count = input.read(this.fieldIndicesBuffer!!)
                if (count != this.fieldIndicesSize) {
                    throw DBException(name + ": Field indices truncated")
                }
            }

            if (this.listIndicesLength > 0) {
                this.listIndicesBuffer = ByteArray(this.listIndicesSize)
                count = input.read(this.listIndicesBuffer!!)
                if (count != this.listIndicesSize) {
                    throw DBException(name + ": List indices truncated")
                }
            }

            this.topLevelStruct = decodeStruct("", 0)
        } finally {
            this.structBuffer = null
            this.fieldBuffer = null
            this.labelBuffer = null
            this.fieldDataBuffer = null
            this.fieldIndicesBuffer = null
            this.listIndicesBuffer = null
        }
    }

    @Throws(DBException::class)
    private fun decodeField(index: Int): DBElement {
        if (index >= this.fieldArrayCount) {
            throw DBException(name + ": Field index " + index + " exceeds array size")
        }

        val fieldData = this.fieldBuffer!!
        val fieldValues = this.fieldDataBuffer
        val labelData = this.labelBuffer!!
        var offset = 12 * index
        val fieldType = getInteger(fieldData, offset)
        var labelIndex = getInteger(fieldData, offset + 4)
        var dataOffset = getInteger(fieldData, offset + 8)
        if (labelIndex >= this.labelArrayCount) {
            throw DBException(name + ": Label index " + labelIndex + " exceeds array size")
        }

        val labelOffset = 16 * labelIndex
        var labelLength: Int = 16
        while (labelLength > 0 && labelData[labelOffset + labelLength - 1].toInt() == 0) {
            labelLength--
        }
        val label = String(labelData, labelOffset, labelLength)
        val element: DBElement
        when (fieldType) {
            15 -> element = decodeList(label, dataOffset)
            14 -> element = decodeStruct(label, dataOffset)
            0 -> element = DBElement(fieldType, 0, label, dataOffset and 0xFF)
            1 -> element = DBElement(fieldType, 0, label, dataOffset.toChar())
            2 -> element = DBElement(fieldType, 0, label, dataOffset and 0xFFFF)
            3 -> {
                dataOffset = dataOffset and 65535
                if (dataOffset > 32767) {
                    dataOffset = dataOffset or -65536
                }
                element = DBElement(fieldType, 0, label, dataOffset)
            }
            4 -> element = DBElement(fieldType, 0, label, dataOffset.toLong())
            5 -> element = DBElement(fieldType, 0, label, dataOffset)
            6, 7 -> {
                if (dataOffset + 8 > this.fieldDataLength) {
                    throw DBException(name + ": Field data offset " + dataOffset + " exceeds field data")
                }
                var longValue = (fieldValues!![dataOffset].toLong() and 0xFF) or
                    ((fieldValues[dataOffset + 1].toLong() and 0xFF) shl 8) or
                    ((fieldValues[dataOffset + 2].toLong() and 0xFF) shl 16) or
                    ((fieldValues[dataOffset + 3].toLong() and 0xFF) shl 24) or
                    ((fieldValues[dataOffset + 4].toLong() and 0xFF) shl 32) or
                    ((fieldValues[dataOffset + 5].toLong() and 0xFF) shl 40) or
                    ((fieldValues[dataOffset + 6].toLong() and 0xFF) shl 48) or
                    ((fieldValues[dataOffset + 7].toLong() and 0xFF) shl 56)

                if (fieldType == 6 && longValue < 0L) {
                    throw DBException("DWORD64 value is too large for Java representation")
                }
                element = DBElement(fieldType, 0, label, longValue)
            }
            8 -> element = DBElement(fieldType, 0, label, Float.fromBits(dataOffset))
            9 -> {
                if (dataOffset + 8 > this.fieldDataLength) {
                    throw DBException(name + ": Field data offset " + dataOffset + " exceeds field data")
                }
                val longBits = (fieldValues!![dataOffset].toLong() and 0xFF) or
                    ((fieldValues[dataOffset + 1].toLong() and 0xFF) shl 8) or
                    ((fieldValues[dataOffset + 2].toLong() and 0xFF) shl 16) or
                    ((fieldValues[dataOffset + 3].toLong() and 0xFF) shl 24) or
                    ((fieldValues[dataOffset + 4].toLong() and 0xFF) shl 32) or
                    ((fieldValues[dataOffset + 5].toLong() and 0xFF) shl 40) or
                    ((fieldValues[dataOffset + 6].toLong() and 0xFF) shl 48) or
                    ((fieldValues[dataOffset + 7].toLong() and 0xFF) shl 56)

                element = DBElement(fieldType, 0, label, Double.fromBits(longBits))
            }
            13 -> {
                if (dataOffset + 4 > this.fieldDataLength) {
                    throw DBException("Field data offset " + dataOffset + " exceeds field data")
                }
                val byteLength = getInteger(fieldValues!!, dataOffset)
                dataOffset += 4
                if (dataOffset + byteLength > this.fieldDataLength) {
                    throw DBException("Void data length " + byteLength + " exceeds field data")
                }
                val byteData = ByteArray(byteLength)
                if (byteLength > 0) {
                    System.arraycopy(fieldValues, dataOffset, byteData, 0, byteLength)
                }
                element = DBElement(fieldType, 0, label, byteData)
            }
            11 -> {
                if (dataOffset + 1 > this.fieldDataLength) {
                    throw DBException(name + ": Field data offset " + dataOffset + " exceeds field data")
                }
                val resourceLength = fieldValues!![dataOffset].toInt() and 0xFF
                dataOffset++
                if (dataOffset + resourceLength > this.fieldDataLength) {
                    throw DBException(name + ": Resource length " + resourceLength + " exceeds field data")
                }
                val resourceString: String = if (resourceLength > 0) {
                    String(fieldValues, dataOffset, resourceLength, StandardCharsets.UTF_8)
                } else {
                    ""
                }

                element = DBElement(fieldType, 0, label, resourceString)
            }
            10 -> {
                if (dataOffset + 4 > this.fieldDataLength) {
                    throw DBException(name + ": Field data offset " + dataOffset + " exceeds field data")
                }
                val stringLength = getInteger(fieldValues!!, dataOffset)
                dataOffset += 4
                if (dataOffset + stringLength > this.fieldDataLength) {
                    throw DBException(name + ": String length " + stringLength + " exceeds field data")
                }
                val string: String = if (stringLength > 0) {
                    String(fieldValues, dataOffset, stringLength, StandardCharsets.UTF_8)
                } else {
                    ""
                }

                element = DBElement(fieldType, 0, label, string)
            }
            12 -> {
                if (dataOffset + 12 > this.fieldDataLength) {
                    throw DBException(name + ": Field data offset " + dataOffset + " exceeds field data")
                }
                var localizedLength = getInteger(fieldValues!!, dataOffset)
                val stringReference = getInteger(fieldValues, dataOffset + 4)
                val substringCount = getInteger(fieldValues, dataOffset + 8)
                dataOffset += 12
                localizedLength -= 8
                val localizedString = LocalizedString(stringReference)

                for (i in 0 until substringCount) {
                    if (dataOffset + 8 > this.fieldDataLength) {
                        throw DBException(name + ": Localized substring " + i + " exceeds field data")
                    }
                    if (localizedLength < 8) {
                        throw DBException(name + ": Localized substring " + i + " exceeds localized string")
                    }
                    val stringID = getInteger(fieldValues, dataOffset)
                    val substringLength = getInteger(fieldValues, dataOffset + 4)
                    dataOffset += 8
                    localizedLength -= 8
                    if (dataOffset + substringLength > this.fieldDataLength) {
                        throw DBException(name + ": Localized substring " + i + " exceeds field data")
                    }
                    if (substringLength > localizedLength) {
                        throw DBException(name + ": Localized substring " + i + " exceeds localized string")
                    }
                    val substring: String = if (substringLength > 0) {
                        String(fieldValues, dataOffset, substringLength, StandardCharsets.UTF_8)
                    } else {
                        ""
                    }

                    localizedString.addSubstring(LocalizedSubstring(substring, stringID / 2, stringID and 0x1))
                    dataOffset += substringLength
                    localizedLength -= substringLength
                }

                element = DBElement(fieldType, 0, label, localizedString)
            }
            else -> throw DBException(name + ": Unrecognized field type " + fieldType)
        }

        return element
    }

    @Throws(DBException::class)
    private fun decodeStruct(label: String, index: Int): DBElement {
        if (index >= this.structArrayCount) {
            throw DBException(name + ": Structure index " + index + " exceeds array size")
        }

        val structData = this.structBuffer!!
        val fieldIndicesData = this.fieldIndicesBuffer
        var offset = 12 * index
        val id = getInteger(structData, offset)
        var fieldIndex = getInteger(structData, offset + 4)
        val fieldCount = getInteger(structData, offset + 8)
        val list = DBList(environment, fieldCount)
        if (fieldCount == 1) {
            val field = decodeField(fieldIndex)
            list.addElement(field)
        } else if (fieldCount > 1) {
            offset = fieldIndex
            for (i in 0 until fieldCount) {
                if (offset + 4 > this.fieldIndicesLength) {
                    throw DBException("Field indices offset " + offset + " exceeds indices size")
                }
                fieldIndex = getInteger(fieldIndicesData!!, offset)
                offset += 4
                val field = decodeField(fieldIndex)
                list.addElement(field)
            }
        }

        return DBElement(14, id, label, list)
    }

    @Throws(DBException::class)
    private fun decodeList(label: String, offset: Int): DBElement {
        if (offset + 4 > this.listIndicesLength) {
            throw DBException(name + ": List indices offset " + offset + " exceeds indices size")
        }

        val listIndicesData = this.listIndicesBuffer!!
        val structCount = getInteger(listIndicesData, offset)
        val list = DBList(environment, structCount)
        var listOffset = offset + 4
        for (i in 0 until structCount) {
            if (listOffset + 4 > this.listIndicesLength) {
                throw DBException(name + ": List indices offset " + listOffset + " exceeds indices size")
            }
            val structIndex = getInteger(listIndicesData, listOffset)
            listOffset += 4
            list.addElement(decodeStruct("", structIndex))
        }

        return DBElement(15, 0, label, list)
    }

    @Throws(DBException::class, IOException::class)
    fun save() {
        if (file == null) {
            throw IllegalStateException("No database file is available")
        }
        val tmpFile = File(file!!.getPath() + ".new")
        var saved = false
        try {
            FileOutputStream(tmpFile).use { out ->
                save(out)
            }
            saved = true
        } finally {
            if (!saved) {
                tmpFile.delete()
            }
        }

        if (file!!.exists() && !file!!.delete()) {
            tmpFile.delete()
            throw IOException("Unable to delete " + file!!.getName())
        }
        if (!tmpFile.renameTo(file!!)) {
            tmpFile.delete()
            throw IOException("Unable to rename " + tmpFile.getName() + " to " + file!!.getName())
        }
    }

    @Throws(DBException::class, IOException::class)
    fun save(out: OutputStream) {
        try {
            this.structBuffer = ByteArray(48000)
            this.structArraySize = 4000
            this.structArrayCount = 0

            this.fieldBuffer = ByteArray(144000)
            this.fieldArraySize = 12000
            this.fieldArrayCount = 0

            this.labelBuffer = ByteArray(16000)
            this.labelArraySize = 1000
            this.labelArrayCount = 0

            this.fieldDataBuffer = ByteArray(20000)
            this.fieldDataSize = 20000
            this.fieldDataLength = 0

            this.fieldIndicesBuffer = ByteArray(36000)
            this.fieldIndicesSize = 36000
            this.fieldIndicesLength = 0

            this.listIndicesBuffer = ByteArray(8000)
            this.listIndicesSize = 8000
            this.listIndicesLength = 0

            if (this.topLevelStruct == null) {
                throw DBException(name + ": No top-level structure")
            }
            if (this.fileType == null || this.fileType!!.length != 4) {
                throw DBException(name + ": File type is not set")
            }
            if (this.fileVersion == null || this.fileVersion!!.length != 4) {
                throw DBException(name + ": File version is not set")
            }
            encodeStruct(this.topLevelStruct!!)

            val headerBuffer = ByteArray(56)
            var buffer = this.fileType!!.toByteArray()
            System.arraycopy(buffer, 0, headerBuffer, 0, 4)
            buffer = this.fileVersion!!.toByteArray()
            System.arraycopy(buffer, 0, headerBuffer, 4, 4)
            var offset = 56
            val structLength = 12 * this.structArrayCount
            setInteger(offset, headerBuffer, 8)
            setInteger(this.structArrayCount, headerBuffer, 12)
            offset += structLength
            val fieldLength = 12 * this.fieldArrayCount
            setInteger(offset, headerBuffer, 16)
            setInteger(this.fieldArrayCount, headerBuffer, 20)
            offset += fieldLength
            val labelLength = 16 * this.labelArrayCount
            setInteger(offset, headerBuffer, 24)
            setInteger(this.labelArrayCount, headerBuffer, 28)
            offset += labelLength
            setInteger(offset, headerBuffer, 32)
            setInteger(this.fieldDataLength, headerBuffer, 36)
            offset += this.fieldDataLength
            setInteger(offset, headerBuffer, 40)
            setInteger(this.fieldIndicesLength, headerBuffer, 44)
            offset += this.fieldIndicesLength
            setInteger(offset, headerBuffer, 48)
            setInteger(this.listIndicesLength, headerBuffer, 52)

            out.write(headerBuffer)
            out.write(this.structBuffer!!, 0, structLength)
            if (fieldLength != 0) {
                out.write(this.fieldBuffer!!, 0, fieldLength)
            }
            if (labelLength != 0) {
                out.write(this.labelBuffer!!, 0, labelLength)
            }
            if (this.fieldDataLength != 0) {
                out.write(this.fieldDataBuffer!!, 0, this.fieldDataLength)
            }
            if (this.fieldIndicesLength != 0) {
                out.write(this.fieldIndicesBuffer!!, 0, this.fieldIndicesLength)
            }
            if (this.listIndicesLength != 0) {
                out.write(this.listIndicesBuffer!!, 0, this.listIndicesLength)
            }
        } finally {
            this.structBuffer = null
            this.fieldBuffer = null
            this.labelBuffer = null
            this.fieldDataBuffer = null
            this.fieldIndicesBuffer = null
            this.listIndicesBuffer = null
        }
    }

    @Throws(DBException::class)
    private fun encodeField(element: DBElement): Int {
        val fieldType = element.getType()

        val fieldLabel = element.getLabel()
        if (fieldLabel.isEmpty()) {
            throw DBException("Field does not have a label")
        }
        val labelBytes = fieldLabel.toByteArray()
        val label = ByteArray(16)
        System.arraycopy(labelBytes, 0, label, 0, labelBytes.size.coerceAtMost(16))
        var match = false
        var labelIndex: Int
        val labelData = this.labelBuffer!!
        run {
            labelIndex = 0
            while (labelIndex < this.labelArrayCount) {
                val labelOffset = labelIndex * 16
                match = true
                for (i in 0 until 16) {
                    if (labelData[labelOffset + i] != label[i]) {
                        match = false
                        break
                    }
                }

                if (match) {
                    break
                }
                labelIndex++
            }
        }
        if (!match) {
            if (this.labelArrayCount == this.labelArraySize) {
                this.labelArraySize += 1000
                val buffer = ByteArray(16 * this.labelArraySize)
                System.arraycopy(this.labelBuffer!!, 0, buffer, 0, this.labelArrayCount * 16)
                this.labelBuffer = buffer
            }

            labelIndex = this.labelArrayCount++
            val labelOffset = labelIndex * 16
            System.arraycopy(label, 0, this.labelBuffer!!, labelOffset, 16)
        }

        val fieldValue = element.getValue()
        val dataOffset: Int
        when (fieldType) {
            15 -> dataOffset = encodeList(element)
            14 -> dataOffset = encodeStruct(element)
            0 -> dataOffset = numericInt(fieldValue!!) and 0xFF
            1 -> dataOffset = (fieldValue as Char).code and 0xFFFF
            2, 3 -> dataOffset = numericInt(fieldValue!!) and 0xFFFF
            4 -> dataOffset = numericLong(fieldValue!!).toInt()
            5 -> dataOffset = numericInt(fieldValue!!)
            6, 7 -> dataOffset = setFieldData(numericLong(fieldValue!!))
            8 -> dataOffset = java.lang.Float.floatToIntBits(fieldValue as Float)
            9 -> dataOffset = setFieldData(java.lang.Double.doubleToLongBits(fieldValue as Double))
            13 -> {
                val voidData = fieldValue as ByteArray
                val voidLength = voidData.size
                val voidBuffer = ByteArray(4 + voidLength)
                setInteger(voidLength, voidBuffer, 0)
                System.arraycopy(voidData, 0, voidBuffer, 4, voidLength)
                dataOffset = setFieldData(voidBuffer)
            }
            11 -> {
                val resourceString = fieldValue as String
                val resourceData = resourceString.toByteArray(StandardCharsets.UTF_8)

                val resourceLength = resourceData.size
                if (resourceLength > 255) {
                    throw DBException("Resource length is greater than 255")
                }
                val resourceBuffer = ByteArray(1 + resourceLength)
                resourceBuffer[0] = resourceLength.toByte()
                System.arraycopy(resourceData, 0, resourceBuffer, 1, resourceLength)
                dataOffset = setFieldData(resourceBuffer)
            }
            10 -> {
                val string = fieldValue as String
                val stringBuffer: ByteArray
                if (string.isNotEmpty()) {
                    val stringData = string.toByteArray(StandardCharsets.UTF_8)

                    val stringLength = stringData.size
                    stringBuffer = ByteArray(4 + stringLength)
                    setInteger(stringLength, stringBuffer, 0)
                    System.arraycopy(stringData, 0, stringBuffer, 4, stringLength)
                } else {
                    stringBuffer = ByteArray(4)
                    setInteger(0, stringBuffer, 0)
                }

                dataOffset = setFieldData(stringBuffer)
            }
            12 -> {
                val localizedString = fieldValue as LocalizedString
                val substringCount = localizedString.getSubstringCount()
                var localizedLength = 8
                val substringList = ArrayList<ByteArray>(substringCount)

                for (localizedSubstring in localizedString.getSubstrings()) {
                    val substring = localizedSubstring.string
                    val substringData: ByteArray = if (substring.isNotEmpty()) {
                        substring.toByteArray(StandardCharsets.UTF_8)
                    } else {
                        ByteArray(0)
                    }

                    substringList.add(substringData)
                    localizedLength += 8 + substringData.size
                }

                val localizedBuffer = ByteArray(4 + localizedLength)
                setInteger(localizedLength, localizedBuffer, 0)
                setInteger(localizedString.stringReference, localizedBuffer, 4)
                setInteger(substringCount, localizedBuffer, 8)
                var substringOffset = 12

                val substringDataIterator = substringList.listIterator()
                for (localizedSubstring in localizedString.getSubstrings()) {
                    val substringData = substringDataIterator.next()
                    val substringLength = substringData.size
                    setInteger(localizedSubstring.language * 2 + localizedSubstring.gender, localizedBuffer, substringOffset)

                    setInteger(substringLength, localizedBuffer, substringOffset + 4)
                    if (substringLength > 0) {
                        System.arraycopy(substringData, 0, localizedBuffer, substringOffset + 8, substringLength)
                    }
                    substringOffset += 8 + substringLength
                }

                dataOffset = setFieldData(localizedBuffer)
            }
            else -> throw DBException(name + ": Unrecognized field type " + fieldType)
        }

        if (this.fieldArrayCount == this.fieldArraySize) {
            this.fieldArraySize += 4000
            val buffer = ByteArray(12 * this.fieldArraySize)
            System.arraycopy(this.fieldBuffer!!, 0, buffer, 0, this.fieldArrayCount * 12)
            this.fieldBuffer = buffer
        }

        val fieldIndex = this.fieldArrayCount++
        val fieldOffset = fieldIndex * 12
        setInteger(fieldType, this.fieldBuffer!!, fieldOffset)
        setInteger(labelIndex, this.fieldBuffer!!, fieldOffset + 4)
        setInteger(dataOffset, this.fieldBuffer!!, fieldOffset + 8)
        return fieldIndex
    }

    @Throws(DBException::class)
    private fun encodeStruct(element: DBElement): Int {
        val list = element.getValue() as DBList
        val fieldCount = list.getElementCount()
        var fieldOffset = 0

        if (this.structArrayCount == this.structArraySize) {
            this.structArraySize += 2000
            val buffer = ByteArray(12 * this.structArraySize)
            System.arraycopy(this.structBuffer!!, 0, buffer, 0, this.structArrayCount * 12)
            this.structBuffer = buffer
        }

        val structIndex = this.structArrayCount++

        if (fieldCount == 1) {
            fieldOffset = encodeField(list.getElement(0))
        } else if (fieldCount > 1) {
            val indexLength = 4 * fieldCount
            if (this.fieldIndicesLength + indexLength > this.fieldIndicesSize) {
                val increment = indexLength.coerceAtLeast(8000)
                this.fieldIndicesSize += increment
                val buffer = ByteArray(this.fieldIndicesSize)
                System.arraycopy(this.fieldIndicesBuffer!!, 0, buffer, 0, this.fieldIndicesLength)
                this.fieldIndicesBuffer = buffer
            }

            fieldOffset = this.fieldIndicesLength
            this.fieldIndicesLength += indexLength
            for (i in 0 until fieldCount) {
                val fieldIndex = encodeField(list.getElement(i))
                setInteger(fieldIndex, this.fieldIndicesBuffer!!, fieldOffset + 4 * i)
            }
        }

        val structOffset = structIndex * 12
        setInteger(element.getID(), this.structBuffer!!, structOffset)
        setInteger(fieldOffset, this.structBuffer!!, structOffset + 4)
        setInteger(fieldCount, this.structBuffer!!, structOffset + 8)
        return structIndex
    }

    @Throws(DBException::class)
    private fun encodeList(element: DBElement): Int {
        val list = element.getValue() as DBList
        val listCount = list.getElementCount()
        val listLength = (listCount + 1) * 4
        if (this.listIndicesLength + listLength > this.listIndicesSize) {
            val increment = listLength.coerceAtLeast(2000)
            this.listIndicesSize += increment
            val buffer = ByteArray(this.listIndicesSize)
            System.arraycopy(this.listIndicesBuffer!!, 0, buffer, 0, this.listIndicesLength)
            this.listIndicesBuffer = buffer
        }

        val listOffset = this.listIndicesLength
        this.listIndicesLength += listLength
        setInteger(listCount, this.listIndicesBuffer!!, listOffset)

        for (i in 0 until listCount) {
            val structIndex = encodeStruct(list.getElement(i))
            setInteger(structIndex, this.listIndicesBuffer!!, listOffset + 4 * (i + 1))
        }

        return listOffset
    }

    private fun setFieldData(data: ByteArray): Int {
        val dataLength = data.size
        if (this.fieldDataLength + dataLength > this.fieldDataSize) {
            val increment = dataLength.coerceAtLeast(8000)
            this.fieldDataSize += increment
            val buffer = ByteArray(this.fieldDataSize)
            System.arraycopy(this.fieldDataBuffer!!, 0, buffer, 0, this.fieldDataLength)
            this.fieldDataBuffer = buffer
        }

        val dataOffset = this.fieldDataLength
        this.fieldDataLength += dataLength
        System.arraycopy(data, 0, this.fieldDataBuffer!!, dataOffset, dataLength)
        return dataOffset
    }

    private fun setFieldData(data: Long): Int {
        if (this.fieldDataLength + 8 > this.fieldDataSize) {
            this.fieldDataSize += 8000
            val buffer = ByteArray(this.fieldDataSize)
            System.arraycopy(this.fieldDataBuffer!!, 0, buffer, 0, this.fieldDataLength)
            this.fieldDataBuffer = buffer
        }

        val dataOffset = this.fieldDataLength
        this.fieldDataLength += 8
        this.fieldDataBuffer!![dataOffset] = data.toInt().toByte()
        this.fieldDataBuffer!![dataOffset + 1] = (data.toInt() shr 8).toByte()
        this.fieldDataBuffer!![dataOffset + 2] = (data.toInt() shr 16).toByte()
        this.fieldDataBuffer!![dataOffset + 3] = (data.toInt() shr 24).toByte()
        this.fieldDataBuffer!![dataOffset + 4] = (data ushr 32).toInt().toByte()
        this.fieldDataBuffer!![dataOffset + 5] = (data ushr 40).toInt().toByte()
        this.fieldDataBuffer!![dataOffset + 6] = (data ushr 48).toInt().toByte()
        this.fieldDataBuffer!![dataOffset + 7] = (data ushr 56).toInt().toByte()
        return dataOffset
    }

    private fun numericLong(value: Any): Long = when (value) {
        is Long -> value
        is Int -> value.toLong()
        else -> throw DBException("Field value is not an integer number")
    }

    private fun numericInt(value: Any): Int = when (value) {
        is Int -> value
        is Long -> value.toInt()
        else -> throw DBException("Field value is not an integer number")
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
