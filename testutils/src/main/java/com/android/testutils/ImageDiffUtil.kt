/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.testutils

import com.android.io.readImage
import com.android.io.writeImage
import org.junit.Assert
import java.awt.Color
import java.awt.Image
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

object ImageDiffUtil {
    /**
     * Converts a BufferedImage type to [TYPE_INT_ARGB],
     * which is the only type accepted by [ImageDiffUtil.assertImageSimilar].
     */
    @JvmStatic
    fun convertToARGB(inputImg: Image): BufferedImage {
        if (inputImg is BufferedImage && inputImg.type == TYPE_INT_ARGB) {
            return inputImg // Early return in case the image has already the correct type
        }
        val outputImg =
            BufferedImage(inputImg.getWidth(null), inputImg.getHeight(null), TYPE_INT_ARGB)
        val g2d = outputImg.createGraphics()
        g2d.drawImage(inputImg, 0, 0, null)
        g2d.dispose()
        return outputImg
    }

    /**
     * Asserts that the given image is similar to the golden one contained in the given file.
     * If the golden image file does not exist, it is created and the test fails.
     */
    @Throws(IOException::class)
    @JvmStatic
    fun assertImageSimilar(goldenFile: Path, actual: BufferedImage, maxPercentDifferent: Double) {
        if (Files.notExists(goldenFile)) {
            val converted = convertToARGB(actual)
            Files.createDirectories(goldenFile.parent)
            val outFile = TestUtils.getTestOutputDir().resolve(goldenFile.fileName.toString())
            // This will show up in undeclared outputs when running on a test server
            converted.writeImage("PNG", outFile)
            // This will copy the file to its designated location. Useful when running locally.
            converted.writeImage("PNG", goldenFile)
            throw AssertionError("File did not exist, created here: $goldenFile and in undeclared outputs")
        }
        val goldenImage = goldenFile.readImage()
        assertImageSimilar(goldenFile.fileName.toString(), goldenImage, actual, maxPercentDifferent)
    }

    @Throws(IOException::class)
    @JvmStatic
    fun assertImageSimilar(
        imageName: String,
        goldenImage: BufferedImage,
        image: Image,
        maxPercentDifferent: Double
    ) {
        // If we get exactly the same object, no need to check--and they might be mocks anyway.
        if (goldenImage === image) {
            return
        }
        val bufferedImage = convertToARGB(image)
        val imageWidth = min(goldenImage.width, bufferedImage.width)
        val imageHeight = min(goldenImage.height, bufferedImage.height)

        val width = 3 * imageWidth
        @Suppress("UnnecessaryVariable")
        val height = imageHeight // makes code more readable
        val deltaImage = BufferedImage(width, height, TYPE_INT_ARGB)
        val g = deltaImage.graphics

        // Compute delta map
        var delta = 0.0
        for (y in 0 until imageHeight) {
            for (x in 0 until imageWidth) {
                val goldenRgb = goldenImage.getRGB(x, y)
                val rgb = bufferedImage.getRGB(x, y)
                if (goldenRgb == rgb) {
                    deltaImage.setRGB(imageWidth + x, y, 0x00808080)
                    continue
                }

                // If the pixels have no opacity, don't delta colors at all.
                if (goldenRgb and -0x1000000 == 0 && rgb and -0x1000000 == 0) {
                    deltaImage.setRGB(imageWidth + x, y, 0x00808080)
                    continue
                }
                val deltaA = (rgb and -0x1000000 ushr 24) - (goldenRgb and -0x1000000 ushr 24)
                val newA = 128 + deltaA and 0xFF
                val deltaR = (rgb and 0xFF0000 ushr 16) - (goldenRgb and 0xFF0000 ushr 16)
                val newR = 128 + deltaR and 0xFF
                val deltaG = (rgb and 0x00FF00 ushr 8) - (goldenRgb and 0x00FF00 ushr 8)
                val newG = 128 + deltaG and 0xFF
                val deltaB = (rgb and 0x0000FF) - (goldenRgb and 0x0000FF)
                val newB = 128 + deltaB and 0xFF
                val newRGB = newA shl 24 or (newR shl 16) or (newG shl 8) or newB
                deltaImage.setRGB(imageWidth + x, y, newRGB)
                val dA = deltaA / 255.0
                val dR = deltaR / 255.0
                val dG = deltaG / 255.0
                val dB = deltaB / 255.0
                // Notice that maximum difference per pixel is 1, which is realized for completely
                // opaque black and white colors.
                delta += sqrt((dA * dA + dR * dR + dG * dG + dB * dB) / 4.0)
            }
        }
        val maxDiff = imageHeight * imageWidth.toDouble()
        val percentDifference = delta / maxDiff * 100
        var error: String? = null
        when {
            percentDifference > maxPercentDifferent -> {
                error = String.format("Images differ (by %.2g%%)", percentDifference)
            }
            abs(goldenImage.width - bufferedImage.width) >= 2 -> {
                error =
                    "Widths differ too much for $imageName: " +
                            "${goldenImage.width}x${goldenImage.height} vs " +
                            "${bufferedImage.width}x${bufferedImage.height}"
            }
            abs(goldenImage.height - bufferedImage.height) >= 2 -> {
                error =
                    "Heights differ too much for $imageName: " +
                            "${goldenImage.width}x${goldenImage.height} vs " +
                            "${bufferedImage.width}x${bufferedImage.height}"
            }
        }
        if (error != null) {
            // Expected on the left
            // Golden on the right
            g.drawImage(goldenImage, 0, 0, null)
            g.drawImage(bufferedImage, 2 * imageWidth, 0, null)

            // Labels
            if (imageWidth > 80) {
                g.color = Color.RED
                g.drawString("Expected", 10, 20)
                g.drawString("Actual", 2 * imageWidth + 10, 20)
            }

            // Write image diff to undeclared outputs dir so ResultStore archives.
            val output =
                    TestUtils.getTestOutputDir().resolve(
                            "delta-" + imageName.replace(File.separatorChar, '_'))
            Files.createDirectories(output.parent)
            deltaImage.writeImage("PNG", output)
            error += " - see details in archived file $output"
            println(error)
            Assert.fail(error)
        }
        g.dispose()
    }
}
