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
import java.io.InputStream
import java.io.PrintStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream


class Apk internal constructor(val dexes: List<DexFile>)

fun Apk(file: File): Apk {
    return Apk(file.readBytes())
}

fun Apk(bytes: ByteArray): Apk {
    return ZipInputStream(bytes.inputStream()).use { zis ->
        val dexes = mutableListOf<DexFile>()
        var zipEntry: ZipEntry? = zis.nextEntry
        while (zipEntry != null) {
            val fileName = zipEntry.name
            if (!fileName.startsWith("classes") || !fileName.endsWith(".dex")) {
                zipEntry = zis.nextEntry
                continue
            }
            val dex = parseDexFile(zis.readBytes(), fileName)
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
class DexFile internal constructor(
    internal val header: DexHeader,
    val dexChecksum: Long,
    val name: String,
) {
    internal val stringPool = ArrayList<String>(header.stringIds.size)
    internal val typePool = ArrayList<String>(header.typeIds.size)
    internal val protoPool = ArrayList<DexPrototype>(header.prototypeIds.size)
    internal val methodPool = ArrayList<DexMethod>(header.methodIds.size)
    // we don't really care about any of the details of classes, just what index it corresponds to in the
    // type pool, and we can use the type pool to determine its descriptor, so in this case we only need an IntArray.
    internal val classDefPool = IntArray(header.classDefs.size)
}

fun DexFile(file: File): DexFile = DexFile(file.inputStream(), file.name)

fun DexFile(src: InputStream, name: String): DexFile = parseDexFile(src.readBytes(), name)

internal class DexHeader(
    val stringIds: Span,
    val typeIds: Span,
    val prototypeIds: Span,
    val methodIds: Span,
    val classDefs: Span,
    val data: Span,
) {
    internal companion object {
        val Empty = DexHeader(
            stringIds = Span.Empty,
            typeIds = Span.Empty,
            prototypeIds = Span.Empty,
            methodIds = Span.Empty,
            classDefs = Span.Empty,
            data = Span.Empty,
        )
    }
}

internal data class DexMethod(
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
internal data class DexPrototype(
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
    internal companion object {
        val Empty = Span(0, 0)
    }
}

internal class DexFileData(
    val classes: Set<Int>,
    val methods: Map<Int, MethodData>,
)

internal class MutableDexFileData(
    val classSetSize: Int,
    val hotMethodRegionSize: Int,
    val numMethodIds: Int,
    val dexFile: DexFile,
    val classes: MutableSet<Int>,
    val methods: MutableMap<Int, MethodData>,
)

internal class MethodData(var flags: Int) {
    inline val isHot: Boolean get() = isFlagSet(MethodFlags.HOT)
    @Suppress("NOTHING_TO_INLINE")
    inline fun isFlagSet(flag: Int): Boolean {
        return flags and flag == flag
    }
    fun print(os: PrintStream) = with(os) {
        if (isFlagSet(MethodFlags.HOT)) print(HOT)
        if (isFlagSet(MethodFlags.STARTUP)) print(STARTUP)
        if (isFlagSet(MethodFlags.POST_STARTUP)) print(POST_STARTUP)
    }
}

// TODO(lmr): refactor to not use iteration and first/last flag strategy for this
internal object MethodFlags {
    // Implementation note: DO NOT CHANGE THESE VALUES without adjusting the parsing.
    // To simplify the implementation we use the MethodHotness flag values as indexes into the
    // internal bitmap representation. As such, they should never change unless the profile version
    // is updated and the implementation changed accordingly.
    /** Marker flag used to simplify iterations.  */
    const val FIRST_FLAG = 1 shl 0

    /** The method is profile-hot (this is implementation specific, e.g. equivalent to JIT-warm)  */
    const val HOT = 1 shl 0

    /** Executed during the app startup as determined by the runtime.  */
    const val STARTUP = 1 shl 1

    /** Executed after app startup as determined by the runtime.  */
    const val POST_STARTUP = 1 shl 2

    /** Marker flag used to simplify iterations.  */
    const val LAST_FLAG_REGULAR = 1 shl 2

    /** Combined value of flags */
    const val ALL = HOT or STARTUP or POST_STARTUP
}

internal fun splitParameters(parameters: String): List<String> {
    val result = mutableListOf<String>()
    val currentParam = StringBuilder(parameters.length)
    var inClassName = false
    for (c in parameters) {
        currentParam.append(c)
        inClassName = if (inClassName) c != ';' else c == 'L'
        if (!inClassName) {
            result.add(currentParam.toString())
            currentParam.clear()
        }
    }
    return result
}
