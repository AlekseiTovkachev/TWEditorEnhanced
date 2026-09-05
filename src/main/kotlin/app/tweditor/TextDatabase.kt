package app.tweditor

import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.InputStream
import java.io.InputStreamReader

class TextDatabase {
    val columns: MutableList<String> = ArrayList(16)
    private val columnMap: MutableMap<String, Int> = HashMap(16)
    private val resources: MutableList<Array<String>> = ArrayList(100)

    constructor(filePath: String) {
        BufferedReader(FileReader(filePath)).use { input ->
            readDefinitions(input)
        }
    }

    constructor(file: File) {
        BufferedReader(FileReader(file)).use { input ->
            readDefinitions(input)
        }
    }

    constructor(inputStream: InputStream) {
        BufferedReader(InputStreamReader(inputStream)).use { input ->
            readDefinitions(input)
        }
    }

    private fun readDefinitions(input: BufferedReader) {
        var headerDone = false
        var columnsDone = false

        var values: Array<String>? = null
        var line: String?
        while (input.readLine().also { line = it } != null) {
            val text = line!!
            val lineLength = text.length
            if (lineLength != 0 && text[0] != '#') {
                var skipIndex = true
                var index = 0
                var value = 0
                if (columnsDone) {
                    values = Array(columns.size) { "" }
                }

                while (index < lineLength) {
                    if (Character.isWhitespace(text[index])) {
                        index++
                    } else {
                        val quoted = if (text[index] == '"') {
                            index++
                            true
                        } else {
                            false
                        }

                        val start = index
                        if (start >= lineLength) {
                            break
                        }
                        while (index < lineLength &&
                            (if (quoted) text[index] != '"' else !Character.isWhitespace(text[index]))
                        ) {
                            index++
                        }
                        val token: String = if (start == index) {
                            ""
                        } else {
                            text.substring(start, index)
                        }
                        if (index < lineLength && text[index] == '"') {
                            index++
                        }

                        if (!headerDone) {
                            if (value == 0) {
                                if (token != "2DA") {
                                    throw DBException("File format '" + token + "' is not supported")
                                }
                            } else if (value == 1 && token != "V2.0") {
                                throw DBException("File version '" + token + "' is not supported")
                            }
                        } else if (!columnsDone) {
                            columnMap[token.lowercase()] = value
                            columns.add(token)
                        } else if (skipIndex) {
                            skipIndex = false
                            value--
                        } else if (value < values!!.size) {
                            values[value] = token
                        }

                        value++
                    }
                }

                if (value > 0) {
                    if (columnsDone) {
                        resources.add(values!!)
                    } else if (headerDone) {
                        columnsDone = true
                    } else {
                        headerDone = true
                    }
                }
            }
        }
    }

    fun getResourceCount(): Int = resources.size

    fun getString(resourceIndex: Int, valueIndex: Int): String {
        if (resourceIndex >= resources.size) {
            throw IllegalArgumentException("Resource index is not valid")
        }
        if (valueIndex >= columns.size) {
            throw IllegalArgumentException("Value index is not valid")
        }
        return resources[resourceIndex][valueIndex]
    }

    fun getString(resourceIndex: Int, valueLabel: String): String {
        if (resourceIndex >= resources.size) {
            throw IllegalArgumentException("Resource index is not valid")
        }
        val valueIndex = columnMap[valueLabel.lowercase()] ?: return ""
        var string = resources[resourceIndex][valueIndex]
        if (string.length >= 4 && string.substring(0, 4) == "****") {
            string = ""
        }
        return string
    }

    fun getInteger(resourceIndex: Int, valueLabel: String): Int {
        if (resourceIndex >= resources.size) {
            throw IllegalArgumentException("Resource index is not valid")
        }
        val valueIndex = columnMap[valueLabel.lowercase()] ?: return 0

        val string = resources[resourceIndex][valueIndex]
        return if (string.length >= 4 && string.substring(0, 4) == "****") {
            0
        } else {
            parseNumber(string)
        }
    }

    /** 2DA values may be decimal or 0x-prefixed hexadecimal (may exceed Int.MaxValue). */
    private fun parseNumber(string: String): Int {
        return if (string.length > 2 && (string.startsWith("0x") || string.startsWith("0X"))) {
            java.lang.Integer.parseUnsignedInt(string.substring(2), 16)
        } else {
            string.toInt()
        }
    }
}
