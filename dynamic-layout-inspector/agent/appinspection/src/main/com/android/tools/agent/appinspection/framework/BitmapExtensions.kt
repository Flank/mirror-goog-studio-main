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

package com.android.tools.agent.appinspection.framework

import android.graphics.Bitmap
import com.android.tools.layoutinspector.BITMAP_HEADER_SIZE
import com.android.tools.layoutinspector.BitmapType
import com.android.tools.layoutinspector.toBytes
import java.nio.ByteBuffer

fun Bitmap.toByteArray(): ByteArray {
    val bytes = ByteArray(byteCount + BITMAP_HEADER_SIZE)

    width.toBytes(bytes, 0)
    height.toBytes(bytes, 4)
    val bitmapType = config.toBitmapType()
    bytes[8] = bitmapType.byteVal

    val buf = ByteBuffer.wrap(bytes, BITMAP_HEADER_SIZE, byteCount)
    this.copyPixelsToBuffer(buf)
    return bytes
}

fun Bitmap.Config.toBitmapType(): BitmapType =
    when (this) {
        Bitmap.Config.ARGB_8888 -> BitmapType.ABGR_8888
        Bitmap.Config.RGB_565 -> BitmapType.RGB_565
        else -> throw Exception("Unknown bitmap config $this")
    }

fun BitmapType.toBitmapConfig(): Bitmap.Config =
    when (this) {
        BitmapType.RGB_565 -> Bitmap.Config.RGB_565
        BitmapType.ABGR_8888 -> Bitmap.Config.ARGB_8888
        else -> throw Exception("Unknown bitmap type $this")
    }
