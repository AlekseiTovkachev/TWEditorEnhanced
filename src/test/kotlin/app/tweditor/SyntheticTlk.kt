package app.tweditor

import java.io.ByteArrayOutputStream

/** Builds minimal valid TLK bytes for test installs: header language id, strref entries, UTF-8 data. */
object SyntheticTlk {
    fun build(languageId: Int, strings: Map<Int, String>, count: Int = (strings.keys.maxOrNull() ?: -1) + 1): ByteArray {
        val stringOffset = 20 + count * 40
        val entries = ByteArrayOutputStream(count * 40)
        val data = ByteArrayOutputStream()
        for (index in 0 until count) {
            val value = strings[index]?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
            val entry = ByteArray(40)
            if (value.isNotEmpty()) {
                entry[0] = 1
                setInt(entry, 28, data.size())
                setInt(entry, 32, value.size)
                data.write(value)
            }
            entries.write(entry)
        }

        val header = ByteArray(20)
        "TLK V3.0".toByteArray(Charsets.US_ASCII).copyInto(header)
        setInt(header, 8, languageId)
        setInt(header, 12, count)
        setInt(header, 16, stringOffset)
        return header + entries.toByteArray() + data.toByteArray()
    }

    fun setInt(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
        bytes[offset + 2] = (value ushr 16).toByte()
        bytes[offset + 3] = (value ushr 24).toByte()
    }
}
