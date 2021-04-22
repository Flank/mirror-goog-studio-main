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

package com.android.tools.profgen

import java.io.File
import java.io.PrintStream
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream


class Apk internal constructor(internal val dexes: List<DexFile>)

fun Apk(file: File): Apk {
    return Apk(file.readBytes(), file.name)
}

fun Apk(bytes: ByteArray, name: String): Apk {
    return ZipInputStream(bytes.inputStream()).use { zis ->
        val dexes = mutableListOf<DexFile>()
        var zipEntry: ZipEntry? = zis.nextEntry
        while (zipEntry != null) {
            val fileName = zipEntry.name
            if (!fileName.startsWith("classes") || !fileName.endsWith(".dex")) {
                zipEntry = zis.nextEntry
                continue
            }
            val dex = parseDexFile(zis.readBytes(), fileName, name)
            dexes.add(dex)
            zipEntry = zis.nextEntry
        }
        Apk(dexes)
    }
}

/**
 * Slimmed-down in-memory representation of a Dex file. This data structure contains the minimal amount of information
 * that profgen needs in order to generate a profile. This means that a lot of information is missing, such as the field
 * pool, all code points, and various bits of information of the class defs.
 */
internal class DexFile(
    val header: DexHeader,
    val dexChecksum: Long,
    fileName: String,
    apkFileName: String,
) {
    val profileKey = if (fileName == "classes.dex") {
        apkFileName
    } else {
        "$apkFileName!$fileName"
    }

    val stringPool = ArrayList<String>(header.stringIds.size)
    val typePool = ArrayList<String>(header.typeIds.size)
    val protoPool = ArrayList<DexPrototype>(header.prototypeIds.size)
    val methodPool = ArrayList<DexMethod>(header.methodIds.size)
    // we don't really care about any of the details of classes, just what index it corresponds to in the
    // type pool, and we can use the type pool to determine its descriptor, so in this case we only need an IntArray.
    val classDefPool = IntArray(header.classDefs.size)
}

internal class DexHeader(
    val magic: ByteArray,
    val checksum: Int,
    val signature: ByteArray,
    val fileSize: Int,
    val headerSize: Int,
    val endianTag: Endian,
    val link: Span,
    val mapOffset: Int,
    val stringIds: Span,
    val typeIds: Span,
    val prototypeIds: Span,
    val fieldIds: Span,
    val methodIds: Span,
    val classDefs: Span,
    val data: Span,
)

internal class DexMethod(
    val parent: String,
    val name: String,
    val prototype: DexPrototype,
) {
    val returnType: String get() = prototype.returnType
    val parameters: String = prototype.parameters.joinToString("")
    fun print(os: PrintStream) = with(os) {
        print(parent)
        print("->")
        print(name)
        print('(')
        print(parameters)
        print(')')
        print(returnType)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DexMethod

        if (parent != other.parent) return false
        if (name != other.name) return false
        if (prototype.returnType != other.prototype.returnType) return false
        if (parameters != other.parameters) return false

        return true
    }

    override fun hashCode(): Int {
        var result = parent.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + prototype.returnType.hashCode()
        result = 31 * result + parameters.hashCode()
        return result
    }

    override fun toString(): String = buildString {
        append(parent)
        append("->")
        append(name)
        append('(')
        append(parameters)
        append(')')
        append(returnType)
    }
}

/**
 * Dex files store the "prototype" or signature of a function separate from the function itself to save on space. As a
 * result, we allocate this data structure separately from the [DexMethod].
 */
internal class DexPrototype(
    val returnType: String,
    val parameters: List<String>,
)

/**
 * A simple tuple of Integers indicating a range of data in a binary file.
 */
internal class Span(
    /**
     * The size of the span, in bytes.
     */
    val size: Int,
    /**
     * The offset/location of the span, in bytes.
     */
    val offset: Int
) {
    fun includes(value: Long): Boolean {
        return value >= offset && value < offset + size
    }
}
