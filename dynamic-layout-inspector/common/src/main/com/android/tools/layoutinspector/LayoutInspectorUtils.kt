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

import com.android.io.CancellableFileIo
import com.android.tools.idea.layoutinspector.proto.SkiaParser
import com.android.tools.idea.protobuf.ByteString
import java.awt.Image
import java.awt.Point
import java.awt.color.ColorSpace
import java.awt.image.BufferedImage
import java.awt.image.DataBuffer
import java.awt.image.DataBufferInt
import java.awt.image.DataBufferShort
import java.awt.image.DirectColorModel
import java.awt.image.Raster
import java.awt.image.SinglePixelPackedSampleModel
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import javax.xml.bind.JAXBContext
import javax.xml.bind.annotation.XmlAttribute
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement

object LayoutInspectorUtils {
    private val versionMapUnmarshaller =
        JAXBContext.newInstance(VersionMap::class.java).createUnmarshaller()

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
            SkiaViewNode(node.id, BitmapType.ARGB_8888.createImage(image.asReadOnlyByteBuffer(), width, height))
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

    fun getSkpVersion(data: ByteArray): Int {
        // SKPs start with "skiapict" in ascii
        if (data.slice(0..7) != "skiapict".toByteArray(Charsets.US_ASCII).asList() || data.size < 12) {
            throw InvalidPictureException()
        }
        return data.sliceArray(8..11).toInt()
    }

    fun loadSkiaParserVersionMap(path: Path): VersionMap {
        val mapInputStream = CancellableFileIo.newInputStream(path)
        return versionMapUnmarshaller.unmarshal(mapInputStream) as VersionMap
    }
}

/**
 * Convert `this` to bytes using little-endian order. Optionally will populate the given [bytes]
 * at the given [offset].
 */
fun Int.toBytes(bytes: ByteArray = ByteArray(4), offset: Int = 0): ByteArray {
    for (i in 0..3) {
        bytes[offset + i] = (ushr(i * 8) and 0xFF).toByte()
    }
    return bytes
}

/**
 * Convert the first four bytes to an Int, assuming little-endian representation.
 */
fun ByteArray.toInt(): Int {
    var result = 0
    for (i in 0..3) {
        result += (this[i].toInt() and 0xFF).shl(i * 8)
    }
    return result
}

/**
 * Thrown if a request is made to create a server for something that doesn't look like a valid `SkPicture`.
 */
class InvalidPictureException : Exception()

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

@XmlRootElement(name="versionMapping")
class VersionMap {
    @XmlElement(name = "server")
    val servers: MutableList<ServerVersionSpec> = mutableListOf()
}

/**
 * The `SkPicture` versions supported by a given `skiaparser` server version. Used by JAXB to parse `version-map.xml`.
 */
class ServerVersionSpec {
    /** The version of the `skiaparser` package (e.g. the `1` in the sdk package `skiaparser;1` */
    @XmlAttribute(name = "version", required = true)
    val version: Int = 0

    /** The first `SkPicture` version supported by this server. */
    @XmlAttribute(name = "skpStart", required = true)
    val skpStart: Int = 0

    /** The last `SkPicture` version supported by this server. */
    @XmlAttribute(name = "skpEnd", required = false)
    val skpEnd: Int? = null
}
