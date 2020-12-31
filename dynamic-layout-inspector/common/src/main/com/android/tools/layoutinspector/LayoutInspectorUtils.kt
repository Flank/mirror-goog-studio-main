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

import com.android.tools.idea.layoutinspector.proto.SkiaParser
import com.android.tools.idea.protobuf.ByteString
import com.google.common.annotations.VisibleForTesting
import java.awt.Image
import java.awt.Point
import java.awt.color.ColorSpace
import java.awt.image.BufferedImage
import java.awt.image.DataBuffer
import java.awt.image.DataBufferInt
import java.awt.image.DirectColorModel
import java.awt.image.Raster
import java.awt.image.SinglePixelPackedSampleModel
import java.nio.ByteBuffer
import java.nio.ByteOrder

object LayoutInspectorUtils {
    private fun createImage(bytes: ByteBuffer, width: Int, height: Int): BufferedImage {
        val intArray = IntArray(width * height)
        bytes.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(intArray)
        val buffer = DataBufferInt(intArray, width * height)
        val model = SinglePixelPackedSampleModel(DataBuffer.TYPE_INT, width, height, intArrayOf(0xff0000, 0xff00, 0xff, 0xff000000.toInt()))
        val raster = Raster.createWritableRaster(model, buffer, Point(0, 0))
        val colorModel = DirectColorModel(
            ColorSpace.getInstance(ColorSpace.CS_sRGB),
            32, 0xff0000, 0xff00, 0xff, 0xff000000.toInt(), false, DataBuffer.TYPE_INT)
        @Suppress("UndesirableClassUsage")
        return BufferedImage(colorModel, raster, false, null)
    }

    fun buildTree(
      node: SkiaParser.InspectorView,
      images: Map<Int, ByteString>,
      isInterrupted: () -> Boolean,
      drawIdToRequest: Map<Long, SkiaParser.RequestedNodeInfo>
    ): SkiaViewNode? {
        if (isInterrupted()) {
            throw InterruptedException()
        }
        val image = if (node.image.isEmpty) images[node.imageId] else node.image
        return if (image?.isEmpty == false) {
            val width = if (node.width > 0) node.width else drawIdToRequest[node.id]?.width ?: return null
            val height = if (node.height > 0) node.height else drawIdToRequest[node.id]?.height ?: return null
            SkiaViewNode(node.id, createImage(image.asReadOnlyByteBuffer(), width, height))
        }
        else {
            SkiaViewNode(node.id, node.childrenList.mapNotNull { buildTree(it, images, isInterrupted, drawIdToRequest) })
        }
    }

    fun makeRequestedNodeInfo(drawId: Long, x: Int, y: Int, width: Int, height: Int): SkiaParser.RequestedNodeInfo? {
        return SkiaParser.RequestedNodeInfo.newBuilder()
            .setId(drawId)
            .setX(x)
            .setY(y)
            .setWidth(width)
            .setHeight(height)
            .build()
    }

}

/**
 * A view as seen in a Skia image.
 * We can only have children or an image, not both.
 */
class SkiaViewNode private constructor(val id: Long, var image: Image?, val children: List<SkiaViewNode>) {

    constructor(id: Long, children: List<SkiaViewNode> = listOf()) : this(id, null, children)

    constructor(id: Long, image: Image) : this(id, image, listOf())

    var imageGenerationTime: Long? = null

    fun flatten(): Sequence<SkiaViewNode> {
        return children.asSequence().flatMap { it.flatten() }.plus(this)
    }
}
