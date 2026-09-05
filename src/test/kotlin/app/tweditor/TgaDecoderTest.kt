package app.tweditor

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows

class TgaDecoderTest {
    @Test
    fun decodesUncompressed24BitWithBottomLeftOrigin() {
        val image = TgaDecoder.decode(
            tga(
                imageType = 2, width = 2, height = 2, depth = 24, descriptor = 0,
                body = byteArrayOf(
                    1, 2, 3, 4, 5, 6,   // stored first: bottom row (blue, green, red)
                    7, 8, 9, 10, 11, 12 // stored second: top row
                )
            )
        )

        assertEquals(2, image.width)
        assertEquals(2, image.height)
        assertEquals(0xFF090807.toInt(), image.argb[0]) // top-left: red=9 green=8 blue=7
        assertEquals(0xFF0C0B0A.toInt(), image.argb[1]) // top-right
        assertEquals(0xFF030201.toInt(), image.argb[2]) // bottom-left
        assertEquals(0xFF060504.toInt(), image.argb[3]) // bottom-right
    }

    @Test
    fun decodesUncompressed24BitWithTopLeftOrigin() {
        val image = TgaDecoder.decode(
            tga(
                imageType = 2, width = 2, height = 2, depth = 24, descriptor = 0x20,
                body = byteArrayOf(
                    1, 2, 3, 4, 5, 6,   // stored first: top row
                    7, 8, 9, 10, 11, 12 // stored second: bottom row
                )
            )
        )

        assertEquals(0xFF030201.toInt(), image.argb[0]) // top-left
        assertEquals(0xFF060504.toInt(), image.argb[1]) // top-right
        assertEquals(0xFF090807.toInt(), image.argb[2]) // bottom-left
        assertEquals(0xFF0C0B0A.toInt(), image.argb[3]) // bottom-right
    }

    @Test
    fun decodesRightToLeftImages() {
        val image = TgaDecoder.decode(
            tga(
                imageType = 2, width = 2, height = 1, depth = 24, descriptor = 0x30,
                body = byteArrayOf(
                    1, 2, 3, 4, 5, 6 // top row, stored right to left
                )
            )
        )

        assertEquals(0xFF060504.toInt(), image.argb[0]) // left comes from the last stored pixel
        assertEquals(0xFF030201.toInt(), image.argb[1])
    }

    @Test
    fun decodes32BitWithAlpha() {
        val image = TgaDecoder.decode(
            tga(
                imageType = 2, width = 1, height = 1, depth = 32, descriptor = 0x20,
                body = byteArrayOf(0x10, 0x20, 0x30, 0x80.toByte()) // B, G, R, A
            )
        )

        assertEquals(0x80302010.toInt(), image.argb[0])
    }

    @Test
    fun decodesRunLengthEncodedPixels() {
        val image = TgaDecoder.decode(
            tga(
                imageType = 10, width = 3, height = 2, depth = 24, descriptor = 0x20,
                body = byteArrayOf(
                    0x82.toByte(), 1, 2, 3,             // repeat pixel (1,2,3) three times: the top row
                    0x02.toByte(), 4, 5, 6, 7, 8, 9,    // raw run of three pixels: the bottom row
                    10, 11, 12
                )
            )
        )

        assertEquals(3, image.width)
        assertEquals(2, image.height)
        assertEquals(0xFF030201.toInt(), image.argb[0])
        assertEquals(0xFF030201.toInt(), image.argb[1])
        assertEquals(0xFF030201.toInt(), image.argb[2])
        assertEquals(0xFF060504.toInt(), image.argb[3])
        assertEquals(0xFF090807.toInt(), image.argb[4])
        assertEquals(0xFF0C0B0A.toInt(), image.argb[5])
    }

    @Test
    fun rejectsRunLengthDataThatOverflowsTheImage() {
        assertThrows(IOException::class.java) {
            TgaDecoder.decode(
                tga(
                    imageType = 10, width = 1, height = 1, depth = 24, descriptor = 0x20,
                    body = byteArrayOf(0x81.toByte(), 1, 2, 3) // a repeat of two pixels into a one-pixel image
                )
            )
        }
    }

    @Test
    fun skipsTheImageIdField() {
        val image = TgaDecoder.decode(
            tga(
                imageType = 2, width = 1, height = 1, depth = 24, descriptor = 0x20,
                imageId = byteArrayOf(9, 9, 9),
                body = byteArrayOf(1, 2, 3)
            )
        )

        assertEquals(0xFF030201.toInt(), image.argb[0])
    }

    @Test
    fun rejectsColormappedImages() {
        assertThrows(IOException::class.java) {
            TgaDecoder.decode(
                tga(
                    imageType = 1, width = 1, height = 1, depth = 8, descriptor = 0,
                    body = byteArrayOf(0)
                ).also { it[1] = 1 } // colorMapType = 1
            )
        }
    }

    @Test
    fun rejectsTruncatedPixelData() {
        assertThrows(IOException::class.java) {
            TgaDecoder.decode(
                tga(
                    imageType = 2, width = 2, height = 2, depth = 24, descriptor = 0x20,
                    body = byteArrayOf(1, 2, 3) // one pixel of the four required
                )
            )
        }
    }

    @Test
    fun rejectsTruncatedHeader() {
        assertThrows(IOException::class.java) {
            TgaDecoder.decode(byteArrayOf(0, 0, 2))
        }
    }

    @Test
    fun decodesTheFixtureScreenshot(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val saveDatabase = SaveDatabase(environment, save)
        saveDatabase.load()
        val tgaEntry = saveDatabase.entries.first { it.resourceName.endsWith(".tga") }
        val bytes = tgaEntry.getInputStream().use { it.readBytes() }

        val image = TgaDecoder.decode(bytes)

        assertEquals(64, image.width)
        assertEquals(64, image.height)
        for (argb in image.argb) {
            assertEquals(0xFF000000.toInt(), argb and 0xFF000000.toInt(), "24-bit pixels decode fully opaque")
        }
    }

    @Test
    fun fixtureScreenshotContentEndsAboveThePaddingRows(@TempDir tempDir: Path) {
        val save = SaveSeamSupport.copyFixtureTo(tempDir)
        val saveDatabase = SaveDatabase(environment, save)
        saveDatabase.load()
        val tgaEntry = saveDatabase.entries.first { it.resourceName.endsWith(".tga") }
        val image = TgaDecoder.decode(tgaEntry.getInputStream().use { it.readBytes() })

        assertEquals(36, image.contentHeight(TgaImage.SCREENSHOT_PADDING),
            "the game letterboxes the 16:9 screenshot into the 64x64 buffer; the padding rows must not show")
    }

    private fun tga(
        imageType: Int,
        width: Int,
        height: Int,
        depth: Int,
        descriptor: Int,
        imageId: ByteArray = ByteArray(0),
        body: ByteArray
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(imageId.size)
        out.write(0) // colorMapType = none
        out.write(imageType)
        out.write(byteArrayOf(0, 0, 0, 0, 0)) // color map fields
        out.write(byteArrayOf(0, 0, 0, 0)) // x and y origin
        out.write(leShort(width))
        out.write(leShort(height))
        out.write(depth)
        out.write(descriptor)
        out.write(imageId)
        out.write(body)
        return out.toByteArray()
    }

    private fun leShort(value: Int): ByteArray = byteArrayOf(value.toByte(), (value shr 8).toByte())

    companion object {
        lateinit var environment: AppEnvironment

        @BeforeAll
        @JvmStatic
        fun init() {
            environment = SaveSeamSupport.createEnvironment()
        }
    }
}
