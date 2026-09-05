package app.tweditor

import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DdsDecoderTest {

    @Test
    fun decodesDxt5WithInterpolatedAlpha() {
        val pixels = DdsDecoder.decode(
            dds(width = 4, height = 4, fourCC = "DXT5") {
                // alpha block: alpha0, alpha1, then 48 bits of 3-bit indices
                byte(0)               // alpha0 = 0
                byte(255)             // alpha1 = 255
                val code = 2          // 6-code mode: (4*0 + 1*255) / 5 = 51
                var bits = 0L
                for (pixel in 0 until 16) {
                    bits = bits or (code.toLong() shl (3 * pixel))
                }
                for (i in 0 until 6) {
                    byte(((bits ushr (8 * i)) and 0xFF).toInt())
                }
                // color block: color0 = red, color1 = green, all indices 0
                short(0xF800)
                short(0x07E0)
                int(0)
            }
        )
        assertEquals(4, pixels.width)
        assertEquals(4, pixels.height)
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                assertEquals(0x33FF0000.toInt(), pixels.argb[y * 4 + x])
            }
        }
    }

    @Test
    fun decodesDxt5SixValueModeReservedCodes() {
        val pixels = DdsDecoder.decode(
            dds(width = 4, height = 4, fourCC = "DXT5") {
                // alpha block: alpha0 <= alpha1 selects the 6-value mode where
                // code 6 decodes to fully transparent and code 7 to fully opaque
                byte(0)               // alpha0 = 0
                byte(255)             // alpha1 = 255
                val codes = intArrayOf(0, 1, 2, 6, 7, 0, 1, 2, 6, 7, 0, 1, 2, 6, 7, 0)
                var bits = 0L
                for (pixel in 0 until 16) {
                    bits = bits or (codes[pixel].toLong() shl (3 * pixel))
                }
                for (i in 0 until 6) {
                    byte(((bits ushr (8 * i)) and 0xFF).toInt())
                }
                // color block: uniform white, all color indices 0
                short(0xFFFF)
                short(0xFFFF)
                int(0)
            }
        )
        // codes: 0 -> alpha0=0, 1 -> alpha1=255, 2 -> (4*0+1*255)/5=51,
        // 6 -> transparent, 7 -> opaque
        assertEquals(0x00FFFFFF.toInt(), pixels.argb[0])
        assertEquals(0xFFFFFFFF.toInt(), pixels.argb[1])
        assertEquals(0x33FFFFFF.toInt(), pixels.argb[2])
        assertEquals(0x00FFFFFF.toInt(), pixels.argb[3])
        assertEquals(0xFFFFFFFF.toInt(), pixels.argb[4])
    }

    @Test
    fun decodesDxt5WithSeparateBlocksPerRow() {
        val pixels = DdsDecoder.decode(
            dds(width = 4, height = 8, fourCC = "DXT5") {
                // row 1: fully opaque red
                byte(255)
                byte(255)
                int(0)
                byte(0)
                byte(0)
                short(0xF800)
                short(0xF800)
                int(0)
                // row 2: fully transparent green (alpha0=0 selects the 6-value
                // mode; code 6 decodes to fully transparent)
                byte(0)
                byte(255)
                var bits = 0L
                for (pixel in 0 until 16) {
                    bits = bits or (6L shl (3 * pixel))
                }
                for (i in 0 until 6) {
                    byte(((bits ushr (8 * i)) and 0xFF).toInt())
                }
                short(0x07E0)
                short(0x07E0)
                int(0)
            }
        )
        for (x in 0 until 4) {
            assertEquals(0xFFFF0000.toInt(), pixels.argb[x])
            assertEquals(0x0000FF00, pixels.argb[4 * 4 + x])
        }
    }

    @Test
    fun decodesDxt3ExplicitAlpha() {
        val pixels = DdsDecoder.decode(
            dds(width = 4, height = 4, fourCC = "DXT3") {
                // 16 4-bit alphas: pixel0=0xF, pixel1=0x0, rest 0x8
                byte(0x0F)
                for (i in 1 until 8) {
                    byte(0x88)
                }
                short(0xF800)
                short(0xF800)
                int(0)
            }
        )
        assertEquals(0xFFFF0000.toInt(), pixels.argb[0])
        assertEquals(0x00FF0000, pixels.argb[1])
        assertEquals(0x88FF0000.toInt(), pixels.argb[2])
    }

    @Test
    fun decodesDxt1WithTransparency() {
        val pixels = DdsDecoder.decode(
            dds(width = 4, height = 4, fourCC = "DXT1") {
                // color0=black < color1=red -> 3-color mode with transparent code 3
                short(0x0000)
                short(0xF800)
                int(0b11_10_01_00) // pixel0=black, pixel1=red, pixel2=mix, pixel3=transparent
            }
        )
        assertEquals(0xFF000000.toInt(), pixels.argb[0])
        assertEquals(0xFFFF0000.toInt(), pixels.argb[1])
        val mixed = 0xFF7F0000.toInt()
        assertEquals(mixed, pixels.argb[2])
        assertEquals(0, pixels.argb[3])
    }

    @Test
    fun decodesDxt1FourColorMode() {
        val pixels = DdsDecoder.decode(
            dds(width = 4, height = 4, fourCC = "DXT1") {
                // color0=red > color1=black -> 4-color mode
                short(0xF800)
                short(0x0000)
                int(0b11_10_01_00)
            }
        )
        assertEquals(0xFFFF0000.toInt(), pixels.argb[0])
        assertEquals(0xFF000000.toInt(), pixels.argb[1])
        assertEquals(0xFFAA0000.toInt(), pixels.argb[2])
        assertEquals(0xFF550000.toInt(), pixels.argb[3])
    }

    @Test
    fun decodesUncompressedRgba() {
        val pixels = DdsDecoder.decode(
            dds(width = 2, height = 2, fourCC = null, bitCount = 32, redMask = 0xFF0000, greenMask = 0xFF00, blueMask = 0xFF, alphaMask = -0x1000000) {
                int(0xFFFF0000.toInt())       // row 0 pixel 0: opaque red
                int(0x00FF0000)               // row 0 pixel 1: transparent red
                int(0xFF00FF00.toInt())       // row 1 pixel 0: green
                int(0xFF0000FF.toInt())       // row 1 pixel 1: blue
            }
        )
        assertEquals(0xFFFF0000.toInt(), pixels.argb[0])
        assertEquals(0x00FF0000, pixels.argb[1])
        assertEquals(0xFF00FF00.toInt(), pixels.argb[2])
        assertEquals(0xFF0000FF.toInt(), pixels.argb[3])
    }

    @Test
    fun decodesUncompressedRgb() {
        val pixels = DdsDecoder.decode(
            dds(width = 2, height = 2, fourCC = null, bitCount = 24, redMask = 0xFF0000, greenMask = 0xFF00, blueMask = 0xFF, alphaMask = 0) {
                // little-endian BGR triplets; rows pad from 6 to 8 bytes
                byte(0x00); byte(0x00); byte(0xFF) // row 0 pixel 0: red
                byte(0x00); byte(0xFF); byte(0x00) // row 0 pixel 1: green
                byte(0x00); byte(0x00)             // row 0 padding
                byte(0xFF); byte(0x00); byte(0x00) // row 1 pixel 0: blue
                byte(0x00); byte(0x00); byte(0x00) // row 1 pixel 1: black
                byte(0x00); byte(0x00)             // row 1 padding
            }
        )
        assertEquals(0xFFFF0000.toInt(), pixels.argb[0])
        assertEquals(0xFF00FF00.toInt(), pixels.argb[1])
        assertEquals(0xFF0000FF.toInt(), pixels.argb[2])
        assertEquals(0xFF000000.toInt(), pixels.argb[3])
    }

    @Test
    fun rejectsBadMagicAndTruncation() {
        assertThrows(IOException::class.java) {
            DdsDecoder.decode(ByteArray(128))
        }
        val header = dds(width = 4, height = 4, fourCC = "DXT1") {
            short(0xF800)
            short(0xF800)
            int(0)
        }
        assertThrows(IOException::class.java) {
            DdsDecoder.decode(header.copyOf(header.size - 1))
        }
        val badCompression = dds(width = 4, height = 4, fourCC = "BC5 ") {
            int(0)
        }
        assertThrows(IOException::class.java) {
            DdsDecoder.decode(badCompression)
        }
    }

    /** Builds a minimal 128-byte DDS header followed by the given pixel data. */
    private fun dds(
        width: Int,
        height: Int,
        fourCC: String?,
        bitCount: Int = 0,
        redMask: Int = 0,
        greenMask: Int = 0,
        blueMask: Int = 0,
        alphaMask: Int = 0,
        data: BlockBuilder.() -> Unit
    ): ByteArray {
        val out = ByteArrayOutputStream(128 + 32)
        fun leInt(value: Int) = byteArrayOf(
            value.toByte(),
            (value ushr 8).toByte(),
            (value ushr 16).toByte(),
            (value ushr 24).toByte()
        )
        out.writeBytes("DDS ".toByteArray(Charsets.US_ASCII))
        out.writeBytes(leInt(124))
        out.writeBytes(leInt(0x1 or 0x2 or 0x4 or 0x1000))
        out.writeBytes(leInt(height))
        out.writeBytes(leInt(width))
        out.writeBytes(leInt(0))
        out.writeBytes(leInt(0))
        out.writeBytes(leInt(0))
        repeat(11) { out.writeBytes(leInt(0)) }
        out.writeBytes(leInt(32))
        if (fourCC != null) {
            out.writeBytes(leInt(0x4))
            out.writeBytes(fourCC.toByteArray(Charsets.US_ASCII))
            out.writeBytes(leInt(0))
            repeat(4) { out.writeBytes(leInt(0)) }
        } else {
            out.writeBytes(leInt(0x40 or 0x1))
            out.writeBytes(leInt(0))
            out.writeBytes(leInt(bitCount))
            out.writeBytes(leInt(redMask))
            out.writeBytes(leInt(greenMask))
            out.writeBytes(leInt(blueMask))
            out.writeBytes(leInt(alphaMask))
        }
        out.writeBytes(leInt(0x1000))
        out.writeBytes(leInt(0))
        out.writeBytes(leInt(0))
        out.writeBytes(leInt(0))
        out.writeBytes(leInt(0))
        val builder = BlockBuilder()
        builder.data()
        out.writeBytes(builder.bytes())
        return out.toByteArray()
    }

    private class BlockBuilder {
        private val out = ByteArrayOutputStream()

        fun byte(value: Int) {
            out.write(value and 0xFF)
        }

        fun short(value: Int) {
            out.write(value and 0xFF)
            out.write((value ushr 8) and 0xFF)
        }

        fun int(value: Int) {
            out.write(value and 0xFF)
            out.write((value ushr 8) and 0xFF)
            out.write((value ushr 16) and 0xFF)
            out.write((value ushr 24) and 0xFF)
        }

        fun bytes(): ByteArray = out.toByteArray()
    }
}
