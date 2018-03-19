/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.layoutinspector.model

import com.android.layoutinspector.LayoutInspectorCaptureOptions
import com.android.layoutinspector.parser.ViewNodeParser
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.ObjectInputStream
import javax.imageio.ImageIO

class LayoutFileData @Throws(IOException::class)
constructor(file: File) {
    val bufferedImage: BufferedImage?
    var node: ViewNode? = null

    init {
        var previewBytes = ByteArray(0)
        val bytes = ByteArray(file.length().toInt())

        FileInputStream(file).use { stream -> stream.read(bytes) }

        ObjectInputStream(ByteArrayInputStream(bytes)).use { input ->
            // Parse options
            val options = LayoutInspectorCaptureOptions()
            options.parse(input.readUTF())

            // Parse view node
            val nodeBytes = ByteArray(input.readInt())
            input.readFully(nodeBytes)
            node = ViewNodeParser.parse(nodeBytes)
            if (node == null) {
                throw IOException("Error parsing view node")
            }

            // Preview image
            previewBytes = ByteArray(input.readInt())
            input.readFully(previewBytes)
        }

        bufferedImage = ImageIO.read(ByteArrayInputStream(previewBytes))
    }
}
