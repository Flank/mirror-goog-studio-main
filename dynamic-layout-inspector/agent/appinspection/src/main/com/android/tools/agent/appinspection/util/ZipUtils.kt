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

package com.android.tools.agent.appinspection.util

import androidx.annotation.VisibleForTesting
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

fun ByteArray.compress(): ByteArray {
    val deflater = Deflater(Deflater.BEST_SPEED)
    deflater.setInput(this)
    deflater.finish()

    val baos = ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    while (!deflater.finished()) {
        val count = deflater.deflate(buffer)
        if (count <= 0) break

        baos.write(buffer, 0, count)
    }
    baos.flush()
    return baos.toByteArray()
}

// decompress is the opposite of compress, and while it's not actually used by the inspector
// itself, it will be used by tests to verify that compression worked.
@VisibleForTesting
fun ByteArray.decompress(): ByteArray {
    val inflater = Inflater()
    inflater.setInput(this)

    val baos = ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    while (!inflater.finished()) {
        val count = inflater.inflate(buffer)
        if (count <= 0) break

        baos.write(buffer, 0, count)
    }
    baos.flush()
    return baos.toByteArray()
}

