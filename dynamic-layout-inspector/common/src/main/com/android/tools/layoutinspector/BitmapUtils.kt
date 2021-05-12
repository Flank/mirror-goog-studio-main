/*
 * Copyright (C) 2021 The Android Open Source Project
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

/**
 * The bitmap header contains the width and height as ints, and the type as a byte.
 * See [BitmapType].
 */
const val BITMAP_HEADER_SIZE = 9

enum class BitmapType(val byteVal: Byte, val pixelSize: Int) {
    RGB_565(1, 2) {
        override fun createImage(bytes: ByteBuffer, width: Int, height: Int): BufferedImage {
            val shortArray = ShortArray(width * height)
            bytes.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortArray)
            val buffer = DataBufferShort(shortArray, width * height)
            val model =
                SinglePixelPackedSampleModel(
                    DataBuffer.TYPE_USHORT,
                    width,
                    height,
                    intArrayOf(0x1f.shl(11), 0x3f.shl(5), 0x1f)
                )
            val raster = Raster.createWritableRaster(model, buffer, Point(0, 0))
            val colorModel = DirectColorModel(
                16, 0x1f.shl(11), 0x3f.shl(5), 0x1f)
            @Suppress("UndesirableClassUsage")
            return BufferedImage(colorModel, raster, false, null)
        }
    },
    ABGR_8888(2, 4) {
        override fun createImage(bytes: ByteBuffer, width: Int, height: Int): BufferedImage {
            val rgbaBytes = intArrayOf(0xff, 0xff00, 0xff0000, 0xff000000.toInt())
            return createImage8888(width, height, bytes, rgbaBytes)
        }
    },
    ARGB_8888(3, 4) {
        override fun createImage(bytes: ByteBuffer, width: Int, height: Int): BufferedImage {
            val rgbaBytes = intArrayOf(0xff0000, 0xff00, 0xff, 0xff000000.toInt())
            return createImage8888(width, height, bytes, rgbaBytes)
        }
    };

    abstract fun createImage(bytes: ByteBuffer, width: Int, height: Int): BufferedImage

    protected fun createImage8888(
        width: Int,
        height: Int,
        bytes: ByteBuffer,
        rgbaBytes: IntArray
    ): BufferedImage {
        val intArray = IntArray(width * height)
        bytes.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(intArray)
        val buffer = DataBufferInt(intArray, width * height)
        val model = SinglePixelPackedSampleModel(DataBuffer.TYPE_INT, width, height, rgbaBytes)
        val raster = Raster.createWritableRaster(model, buffer, Point(0, 0))
        val colorModel = DirectColorModel(
            ColorSpace.getInstance(ColorSpace.CS_sRGB),
            32, rgbaBytes[0], rgbaBytes[1], rgbaBytes[2], rgbaBytes[3], false, DataBuffer.TYPE_INT
        )
        @Suppress("UndesirableClassUsage")
        return BufferedImage(colorModel, raster, false, null)
    }

    companion object {
        fun fromByteVal(byte: Byte) = values().find { it.byteVal == byte } ?:
        throw UnknownBitmapTypeException(byte)
    }
}

class UnknownBitmapTypeException(type: Byte): Exception("Unknown bitmap type $type")
