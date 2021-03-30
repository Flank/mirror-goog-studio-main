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
package com.android.tools.layoutinspector

import com.android.testutils.ImageDiffUtil.assertImageSimilar
import com.android.tools.idea.layoutinspector.proto.SkiaParser
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.layoutinspector.LayoutInspectorUtils.getSkpVersion
import com.google.common.truth.Truth.assertThat
import junit.framework.TestCase
import org.junit.Test
import java.awt.Color
import java.awt.Point
import java.awt.Transparency
import java.awt.color.ColorSpace
import java.awt.image.BufferedImage
import java.awt.image.ComponentColorModel
import java.awt.image.DataBuffer
import java.awt.image.DataBufferByte
import java.awt.image.PixelInterleavedSampleModel
import java.awt.image.Raster

class LayoutInspectorUtilsTest {
    @Test
    fun testBuildTree() {
        val (node1Image, node1Bytes) = createImage(Color.GREEN, 100, 200)
        val (node2Image, node2Bytes) = createImage(Color.BLUE, 50, 150)
        val (node3Image, node3Bytes) = createImage(Color.RED, 30, 20)

        val root = SkiaParser.InspectorView.newBuilder().apply {
            id = 1
            width = 100
            height = 200
            addChildrenBuilder().apply {
                id = 1
                width = 100
                height = 200
                imageId = 1
            }
            addChildrenBuilder().apply {
                id = 2
                width = 50
                height = 150
                addChildrenBuilder().apply {
                    id = 2
                    width = 50
                    height = 150
                    imageId = 2
                }
            }
            addChildrenBuilder().apply {
                id = 4
                width = 30
                height = 20
                addChildrenBuilder().apply {
                    id = 3
                    width = 30
                    height = 20
                    addChildrenBuilder().apply {
                        id = 3
                        width = 30
                        height = 20
                        image = node3Bytes
                    }
                }
            }
        }.build()

        val result = LayoutInspectorUtils.buildTree(
            root, mapOf(1 to node1Bytes, 2 to node2Bytes), {false},
            mapOf(1L to SkiaParser.RequestedNodeInfo.newBuilder().apply {
                x = 0
                y = 0
                width = 100
                height = 200
            }.build(),
                  2L to SkiaParser.RequestedNodeInfo.newBuilder().apply {
                      x = 0
                      y = 0
                      width = 50
                      height = 150
                  }.build(),
                  3L to SkiaParser.RequestedNodeInfo.newBuilder().apply {
                      x = 100
                      y = 100
                      width = 30
                      height = 20
                  }.build()))!!

        val expected = SkiaViewNode(1, listOf(
            SkiaViewNode(1, node1Image),
            SkiaViewNode(2, listOf(SkiaViewNode(2, node2Image))),
            SkiaViewNode(4, listOf(
                SkiaViewNode(3, listOf(SkiaViewNode(3, node3Image))))),
            ))
        assertTreesEqual(result, expected)
    }

    @Test
    fun testGetSkpVersion() {
        val version = getSkpVersion(
            "skiapict".toByteArray()
                .plus(byteArrayOf(10, 0, 1, 0))
                .plus("blah".toByteArray()))
        TestCase.assertEquals(65546, version)
    }

    private fun assertTreesEqual(actual: SkiaViewNode, expected: SkiaViewNode) {
        assertThat(actual.id).isEqualTo(expected.id)
        if (expected.image != null) {
            assertImageSimilar("image", expected.image as BufferedImage,
                               actual.image as BufferedImage, 0.0)
        }
        else {
            assertThat(actual.image).isNull()
        }
        assertThat(actual.children.size).isEqualTo(expected.children.size)
        actual.children.zip(expected.children).forEach { (actual, expected) ->
            assertTreesEqual(actual,expected)
        }
    }

    private fun createImage(desiredColor: Color, width: Int, height: Int): Pair<BufferedImage, ByteString> {
        val byteArray = ByteArray(width * height * 4)
        val buffer = DataBufferByte(byteArray, width * height * 4)
        val model = PixelInterleavedSampleModel(
            DataBuffer.TYPE_BYTE, width, height, 4, width * 4,
            intArrayOf(2, 1, 0, 3))
        val raster = Raster.createWritableRaster(model, buffer, Point(0, 0))
        val colorModel = ComponentColorModel(
            ColorSpace.getInstance(ColorSpace.CS_sRGB), true, false, Transparency.TRANSLUCENT,
            DataBuffer.TYPE_BYTE)
        @Suppress("UndesirableClassUsage")
        val image = BufferedImage(colorModel, raster, false, null)
        image.graphics.apply {
            color = desiredColor
            fillRect(0, 0, image.width, image.height)
        }
        return image to ByteString.copyFrom(byteArray)
    }
}
