package app.tweditor

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/**
 * A decoded TGA image: a row-major, top-left-first grid of opaque-when-24-bit ARGB pixels.
 */
class TgaImage(val width: Int, val height: Int, val argb: IntArray)

/**
 * Pure-Kotlin decoder for the uncompressed and run-length-encoded true-color TGA
 * images embedded in Save archives (the screenshot entry).
 */
object TgaDecoder {
    private const val TYPE_COLORMAPPED = 1
    private const val TYPE_TRUE_COLOR = 2
    private const val TYPE_TRUE_COLOR_RLE = 10

    fun decode(bytes: ByteArray): TgaImage {
        return decode(ByteArrayInputStream(bytes))
    }

    fun decode(input: InputStream): TgaImage {
        val header = ByteArray(18)
        readFully(input, header, 18)

        val colorMapType = header[1].toInt() and 0xFF
        if (colorMapType != 0) {
            throw IOException("TGA color-mapped images are not supported")
        }
        val imageType = header[2].toInt() and 0xFF
        if (imageType != TYPE_TRUE_COLOR && imageType != TYPE_TRUE_COLOR_RLE) {
            throw IOException("TGA image type " + imageType + " is not supported")
        }

        val idLength = header[0].toInt() and 0xFF
        if (idLength > 0) {
            val imageId = ByteArray(idLength)
            readFully(input, imageId, idLength)
        }

        val width = unsignedShort(header, 12)
        val height = unsignedShort(header, 14)
        val pixelDepth = header[16].toInt() and 0xFF
        if (pixelDepth != 24 && pixelDepth != 32) {
            throw IOException("TGA pixel depth " + pixelDepth + " is not supported")
        }
        if (width <= 0 || height <= 0) {
            throw IOException("TGA image dimensions " + width + "x" + height + " are not valid")
        }

        val bytesPerPixel = pixelDepth / 8
        val pixelCount = width * height
        val pixels = ByteArray(pixelCount * bytesPerPixel)
        if (imageType == TYPE_TRUE_COLOR_RLE) {
            readRunLengthEncoded(input, pixels, bytesPerPixel)
        } else {
            readFully(input, pixels, pixels.size)
        }

        val descriptor = header[17].toInt() and 0xFF
        val topToBottom = descriptor and 0x20 != 0
        val rightToLeft = descriptor and 0x10 != 0

        val argb = IntArray(pixelCount)
        var source = 0
        for (y in 0 until height) {
            val row = if (topToBottom) y else height - 1 - y
            for (x in 0 until width) {
                val column = if (rightToLeft) width - 1 - x else x
                argb[row * width + column] = pixelToArgb(pixels, source, pixelDepth)
                source += bytesPerPixel
            }
        }
        return TgaImage(width, height, argb)
    }

    private fun readRunLengthEncoded(input: InputStream, pixels: ByteArray, bytesPerPixel: Int) {
        val pixel = ByteArray(bytesPerPixel)
        var written = 0
        while (written < pixels.size) {
            val packet = input.read()
            if (packet < 0) {
                throw IOException("TGA run-length data truncated")
            }
            val runLength = (packet and 0x7F) + 1
            val runBytes = runLength * bytesPerPixel
            if (written + runBytes > pixels.size) {
                throw IOException("TGA run-length data overflows the image")
            }
            if (packet and 0x80 != 0) {
                readFully(input, pixel, bytesPerPixel)
                var index = written
                while (index < written + runBytes) {
                    System.arraycopy(pixel, 0, pixels, index, bytesPerPixel)
                    index += bytesPerPixel
                }
            } else {
                readFully(input, pixels, written, runBytes)
            }
            written += runBytes
        }
    }

    private fun pixelToArgb(pixels: ByteArray, offset: Int, pixelDepth: Int): Int {
        val blue = pixels[offset].toInt() and 0xFF
        val green = pixels[offset + 1].toInt() and 0xFF
        val red = pixels[offset + 2].toInt() and 0xFF
        return if (pixelDepth == 32) {
            val alpha = pixels[offset + 3].toInt() and 0xFF
            alpha shl 24 or (red shl 16) or (green shl 8) or blue
        } else {
            0xFF000000.toInt() or (red shl 16) or (green shl 8) or blue
        }
    }

    private fun readFully(input: InputStream, target: ByteArray, count: Int) {
        readFully(input, target, 0, count)
    }

    private fun readFully(input: InputStream, target: ByteArray, offset: Int, count: Int) {
        var read = 0
        while (read < count) {
            val bytesRead = input.read(target, offset + read, count - read)
            if (bytesRead < 0) {
                throw IOException("TGA data truncated")
            }
            read += bytesRead
        }
    }

    private fun unsignedShort(buffer: ByteArray, offset: Int): Int {
        return buffer[offset].toInt() and 0xFF or (buffer[offset + 1].toInt() and 0xFF shl 8)
    }
}
