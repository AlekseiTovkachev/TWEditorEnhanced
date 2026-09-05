package app.tweditor

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/**
 * A decoded image: a row-major, top-left-first grid of ARGB pixels.
 */
class DdsImage(val width: Int, val height: Int, val argb: IntArray)

/**
 * Pure-Kotlin decoder for the DDS texture format shipped in the game archives:
 * the DXT1/DXT2/DXT3/DXT4/DXT5 block compressions and uncompressed RGBA/RGB.
 * Only the top mipmap level is decoded; the rest of the chain is skipped.
 */
object DdsDecoder {
    private const val CAPS2_CUBEMAP = 0x200
    private const val CAPS2_CUBEMAP_POSITIVE_X = 0x400
    private const val PIXELFORMAT_FOURCC = 0x4
    private const val PIXELFORMAT_RGB = 0x40

    fun decode(bytes: ByteArray): DdsImage {
        return decode(ByteArrayInputStream(bytes))
    }

    fun decode(input: InputStream): DdsImage {
        val header = ByteArray(128)
        readFully(input, header, header.size)

        if (getInteger(header, 0) != 0x20534444) {
            throw IOException("DDS signature is not correct")
        }
        val flags = getInteger(header, 8)
        if (flags and 0x2 == 0 || flags and 0x4 == 0 || flags and 0x1000 == 0) {
            throw IOException("DDS header does not declare width, height and pixel format")
        }
        val caps2 = getInteger(header, 112)
        if (caps2 and CAPS2_CUBEMAP != 0 && caps2 and CAPS2_CUBEMAP_POSITIVE_X == 0) {
            throw IOException("DDS cubemaps without the positive-X face are not supported")
        }

        val height = getInteger(header, 12)
        val width = getInteger(header, 16)
        if (width <= 0 || height <= 0 || width > 8192 || height > 8192) {
            throw IOException("DDS image dimensions " + width + "x" + height + " are not supported")
        }

        val pixelFormatFlags = getInteger(header, 80)
        val fourCC = String(header, 84, 4, Charsets.US_ASCII)
        val bitCount = getInteger(header, 88)
        val redMask = getInteger(header, 92)
        val greenMask = getInteger(header, 96)
        val blueMask = getInteger(header, 100)
        val alphaMask = getInteger(header, 104)

        return when {
            pixelFormatFlags and PIXELFORMAT_FOURCC != 0 -> when (fourCC) {
                "DXT1" -> decodeBlockCompression(input, width, height, 8, AlphaMode.DXT1)
                "DXT2", "DXT3" -> decodeBlockCompression(input, width, height, 16, AlphaMode.EXPLICIT)
                "DXT4", "DXT5" -> decodeBlockCompression(input, width, height, 16, AlphaMode.INTERPOLATED)
                else -> throw IOException("DDS compression '" + fourCC + "' is not supported")
            }
            bitCount == 32 || bitCount == 24 -> {
                if (redMask == 0 || greenMask == 0 || blueMask == 0) {
                    throw IOException("DDS color masks are missing")
                }
                decodeUncompressed(input, width, height, bitCount / 8, redMask, greenMask, blueMask, alphaMask)
            }
            else -> throw IOException("DDS pixel format with bit count " + bitCount + " is not supported")
        }
    }

    private enum class AlphaMode { DXT1, EXPLICIT, INTERPOLATED }

    private fun decodeBlockCompression(input: InputStream, width: Int, height: Int, bytesPerBlock: Int, alphaMode: AlphaMode): DdsImage {
        val blocksX = (width + 3) / 4
        val blocksY = (height + 3) / 4
        val argb = IntArray(width * height)
        val block = ByteArray(bytesPerBlock)
        val pixels = IntArray(16)
        for (blockY in 0 until blocksY) {
            for (blockX in 0 until blocksX) {
                readFully(input, block, block.size)
                if (alphaMode == AlphaMode.INTERPOLATED) {
                    val alpha0 = block[0].toInt() and 0xFF
                    val alpha1 = block[1].toInt() and 0xFF
                    var bits = 0L
                    for (i in 0 until 6) {
                        bits = bits or ((block[2 + i].toLong() and 0xFF) shl (8 * i))
                    }
                    for (pixel in 0 until 16) {
                        pixels[pixel] = interpolateAlpha(alpha0, alpha1, ((bits ushr (3 * pixel)) and 0x7).toInt())
                    }
                } else if (alphaMode == AlphaMode.EXPLICIT) {
                    for (pixel in 0 until 16) {
                        val pair = block[pixel / 2].toInt() and 0xFF
                        val nibble = if (pixel % 2 == 0) pair and 0xF else (pair ushr 4) and 0xF
                        val expanded = nibble or (nibble shl 4)
                        pixels[pixel] = expanded shl 24
                    }
                } else {
                    for (pixel in 0 until 16) {
                        pixels[pixel] = 0
                    }
                }
                decodeColorBlock(block, alphaMode.bytesPerAlpha(), pixels)
                for (pixel in 0 until 16) {
                    val x = blockX * 4 + (pixel and 3)
                    val y = blockY * 4 + (pixel shr 2)
                    if (x < width && y < height) {
                        argb[y * width + x] = pixels[pixel]
                    }
                }
            }
        }
        return DdsImage(width, height, argb)
    }

    private fun AlphaMode.bytesPerAlpha(): Int = if (this == AlphaMode.DXT1) 0 else 8

    private fun decodeColorBlock(block: ByteArray, alphaOffset: Int, pixels: IntArray) {
        val colorOffset = if (alphaOffset == 0) 0 else 8
        val color0 = getShort(block, colorOffset)
        val color1 = getShort(block, colorOffset + 2)
        val threeColorMode = color0 <= color1 && alphaOffset == 0
        val colors = IntArray(4)
        colors[0] = expand565(color0)
        colors[1] = expand565(color1)
        if (!threeColorMode) {
            colors[2] = mixColors(colors[0], colors[1], 2, 3)
            colors[3] = mixColors(colors[0], colors[1], 1, 3)
        } else {
            colors[2] = mixColors(colors[0], colors[1], 1, 2)
            colors[3] = 0
        }
        var indices = getInteger(block, colorOffset + 4)
        for (pixel in 0 until 16) {
            val index = indices and 3
            indices = indices ushr 2
            val color = colors[index]
            pixels[pixel] = if (alphaOffset == 0) {
                if (threeColorMode && index == 3) 0 else 0xFF000000.toInt() or color
            } else {
                pixels[pixel] or color
            }
        }
    }

    private fun interpolateAlpha(alpha0: Int, alpha1: Int, code: Int): Int {
        val alpha: Int
        val denominator: Int
        if (code == 0) {
            alpha = alpha0
            denominator = 1
        } else if (code == 1) {
            alpha = alpha1
            denominator = 1
        } else if (alpha0 > alpha1) {
            alpha = (8 - code) * alpha0 + (code - 1) * alpha1
            denominator = 7
        } else if (code <= 5) {
            alpha = (6 - code) * alpha0 + (code - 1) * alpha1
            denominator = 5
        } else {
            // 6-value mode reserves code 6 for fully transparent and
            // code 7 for fully opaque pixels.
            alpha = if (code == 7) 255 else 0
            denominator = 1
        }
        return fullArgb(if (denominator == 1) alpha else (alpha + denominator / 2) / denominator)
    }

    private fun expand565(color: Int): Int {
        val red = (color shr 11) and 0x1F
        val green = (color shr 5) and 0x3F
        val blue = color and 0x1F
        val r = (red shl 3) or (red shr 2)
        val g = (green shl 2) or (green shr 4)
        val b = (blue shl 3) or (blue shr 2)
        return (r shl 16) or (g shl 8) or b
    }

    private fun mixColors(color0: Int, color1: Int, weight0: Int, divisor: Int): Int {
        val red = (color0 ushr 16) and 0xFF
        val green = (color0 ushr 8) and 0xFF
        val blue = color0 and 0xFF
        val otherRed = (color1 ushr 16) and 0xFF
        val otherGreen = (color1 ushr 8) and 0xFF
        val otherBlue = color1 and 0xFF
        val mixedRed = (red * weight0 + otherRed * (divisor - weight0)) / divisor
        val mixedGreen = (green * weight0 + otherGreen * (divisor - weight0)) / divisor
        val mixedBlue = (blue * weight0 + otherBlue * (divisor - weight0)) / divisor
        return (mixedRed shl 16) or (mixedGreen shl 8) or mixedBlue
    }

    private fun fullArgb(value: Int): Int {
        val clamped = value.coerceIn(0, 255)
        return clamped shl 24
    }

    private fun decodeUncompressed(input: InputStream, width: Int, height: Int, bytesPerPixel: Int, redMask: Int, greenMask: Int, blueMask: Int, alphaMask: Int): DdsImage {
        val rowPitch = ((width * bytesPerPixel + 3) / 4) * 4
        val pixels = ByteArray(rowPitch * height)
        readFully(input, pixels, pixels.size)

        val argb = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val offset = y * rowPitch + x * bytesPerPixel
                var value = 0
                for (i in 0 until bytesPerPixel) {
                    value = value or ((pixels[offset + i].toInt() and 0xFF) shl (8 * i))
                }
                val alpha = if (alphaMask != 0) channel(value, alphaMask) else 0xFF
                argb[y * width + x] = (alpha shl 24) or
                    (channel(value, redMask) shl 16) or
                    (channel(value, greenMask) shl 8) or
                    channel(value, blueMask)
            }
        }
        return DdsImage(width, height, argb)
    }

    private fun channel(value: Int, mask: Int): Int {
        if (mask == 0) {
            return 0
        }
        var shift = 0
        var count = 0
        var bits = mask
        while (bits and 1 == 0) {
            shift++
            bits = bits ushr 1
        }
        while (bits and 1 == 1) {
            count++
            bits = bits ushr 1
        }
        val channel = (value ushr shift) and ((1 shl count) - 1)
        return if (count == 8) channel else channel * 255 / ((1 shl count) - 1)
    }

    private fun readFully(input: InputStream, target: ByteArray, count: Int) {
        var read = 0
        while (read < count) {
            val bytesRead = input.read(target, read, count - read)
            if (bytesRead < 0) {
                throw IOException("DDS data truncated")
            }
            read += bytesRead
        }
    }

    private fun getShort(buffer: ByteArray, offset: Int): Int {
        return buffer[offset].toInt() and 0xFF or (buffer[offset + 1].toInt() and 0xFF shl 8)
    }

    private fun getInteger(buffer: ByteArray, offset: Int): Int {
        return buffer[offset].toInt() and 0xFF or
            (buffer[offset + 1].toInt() and 0xFF shl 8) or
            (buffer[offset + 2].toInt() and 0xFF shl 16) or
            (buffer[offset + 3].toInt() and 0xFF shl 24)
    }
}
