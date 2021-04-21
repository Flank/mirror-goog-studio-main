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

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream


internal fun byteArrayOf(vararg chars: Char) = ByteArray(chars.size) { chars[it].toByte() }

/** Serialization encoding for an inline cache which misses type definitions.  */
private const val INLINE_CACHE_MISSING_TYPES_ENCODING = 6

/** Serialization encoding for a megamorphic inline cache.  */
private const val INLINE_CACHE_MEGAMORPHIC_ENCODING = 7

private val MAGIC = byteArrayOf('p', 'r', 'o', '\u0000')

/** Profile versions are from 000 to 499.  */
internal val VERSION_0_1_0_P = byteArrayOf('0', '1', '0', '\u0000')

class ArtProfile internal constructor(
    internal val version: ByteArray,
    internal val profileData: Map<DexFile, DexFileData>,
) {
    fun print(os: PrintStream, obf: ObfuscationMap) {
        for ((dexFile, data) in profileData) {
            for (typeIndex in data.classes) {
                val type = dexFile.typePool[typeIndex]
                obf.deobfuscate(type).forEach { os.println(it) }
            }
            for ((methodIndex, methodData) in data.methods) {
                val method = dexFile.methodPool[methodIndex]
                val deobfuscated = obf.deobfuscate(method)
                methodData.print(os)
                deobfuscated.print(os)
                os.println()
            }
        }
    }

    /**
     * Serializes the profile in the given output stream.
     * {#link [.load]} describes the serialization format.
     *
     * @param os the output stream
     */
    fun save(os: OutputStream) {
        // Write the profile data in a byte array first. The array will need to be compressed before
        // writing it in the final output stream.
        val profileBytes = writeProfileData()
        with(os) {
            write(MAGIC)
            write(version)
            writeUInt8(profileData.size) // number of dex files
            writeUInt32(profileBytes.size.toLong())
            writeCompressed(profileBytes)
        }
    }

    /**
     * Serializes the profile data in a byte array. This methods only serializes the actual
     * profile content and not the necessary headers.
     */
    private fun writeProfileData(): ByteArray {
        // Start by creating a couple of caches for the data we re-use during serialization.

        // The required capacity in bytes for the uncompressed profile data.
        var requiredCapacity = 0
        // Maps dex files to their index in the profile. It help to speed-up a dex file index when
        // computing the class reference serialization in the inline cache. The owning dex for an
        // inline cache class is encoded as its index in the profile.
        val dexFileToProfileIndex: MutableMap<DexFile, Int> = HashMap()
        // Maps dex file to the size their method region will occupy. We need this when computing the
        // overall size requirements and for serializing the dex file data. The computation is
        // expensive as it walks all methods recorded in the profile.
        val dexFileToHotMethodRegionSize: MutableMap<DexFile, Int> = HashMap()
        var dexFileIndex = 0
        for ((dexFile, dexFileData) in profileData) {
            val hotMethodRegionSize: Int = getHotMethodRegionSize(dexFileData)
            val lineHeaderSize =
                    ( UINT_16_SIZE // classes set size
                    + UINT_16_SIZE // dex location size
                    + UINT_32_SIZE // method map size
                    + UINT_32_SIZE // checksum
                    + UINT_32_SIZE) // number of method ids
            requiredCapacity += (lineHeaderSize
                    + dexFile.profileKey.utf8Length
                    + dexFileData.classes.size * UINT_16_SIZE + hotMethodRegionSize
                    + getMethodBitmapStorageSize(dexFile))
            dexFileToProfileIndex[dexFile] = dexFileIndex++
            dexFileToHotMethodRegionSize[dexFile] = hotMethodRegionSize
        }

        // Start serializing the data.
        val dataBos = ByteArrayOutputStream(requiredCapacity)

        // Dex files must be written in the order of their profile index. This
        // avoids writing the index in the output file and simplifies the parsing logic.
        // Write profile line headers.

        // Write dex file line headers.
        for ((dexFile, dexFileData) in profileData) {
            dataBos.writeDexDataHeader(
                dexFile,
                dexFileData,
                dexFileToHotMethodRegionSize[dexFile]!!
            )
        }

        // Write dex file data.
        for ((dexFile, dexFileData) in profileData) {
            dataBos.writeDexFileData(
                dexFile,
                dexFileData,
                dexFileToProfileIndex
            )
        }

        check(dataBos.size() == requiredCapacity) {
            ("The bytes saved do not match expectation. actual="
                    + dataBos.size() + " expected=" + requiredCapacity)
        }
        return dataBos.toByteArray()
    }

    /**
     * Writes the dex data header for the given dex file into the output stream.
     *
     * @param dexFile the dex file to which the data belongs
     * @param dexData the dex data to which the header belongs
     * @param hotMethodRegionSize the size (in bytes) for the method region that will be serialized as
     * part of the dex data
     */
    private fun OutputStream.writeDexDataHeader(
        dexFile: DexFile,
        dexData: DexFileData,
        hotMethodRegionSize: Int,
    ) {
        writeUInt16(dexFile.profileKey.utf8Length)
        writeUInt16(dexData.classes.size)
        writeUInt32(hotMethodRegionSize.toLong())
        writeUInt32(dexFile.dexChecksum)
        writeUInt32(dexFile.header.methodIds.size.toLong())
        writeString(dexFile.profileKey)
    }

    /**
     * Writes the given dex file data into the stream.
     *
     * Note that we allow dex files without any methods or classes, so that
     * inline caches can refer to valid dex files.
     *
     * @param dexFile the dex file to which the data belongs
     * @param dexFileData the dex data that should be serialized
     * @param dexFileToProfileIndex a mapping from dex files to their profile index which is used
     * for serializing the inline cache type ids
     */
    private fun OutputStream.writeDexFileData(
        dexFile: DexFile,
        dexFileData: DexFileData,
        dexFileToProfileIndex: Map<DexFile, Int>
    ) {
        writeMethodsWithInlineCaches(dexFileData, dexFileToProfileIndex)
        writeClasses(dexFileData)
        writeMethodBitmap(dexFile, dexFileData)
    }

    /**
     * Writes the methods with inline caches to the output stream.
     *
     * @param dexFileData the dex data containing the methods that should be serialized
     * @param dexFileToProfileIndex a mapping from dex files to their profile index which is used
     * for serializing the inline cache type ids* @param dexFileToProfileIndex
     */
    private fun OutputStream.writeMethodsWithInlineCaches(
        dexFileData: DexFileData,
        dexFileToProfileIndex: Map<DexFile, Int>
    ) {
        // The profile stores the first method index, then the remainder are relative
        // to the previous value.
        var lastMethodIndex = 0
        for ((methodIndex, methodData) in dexFileData.methods) {
            if (!methodData.isHot) {
                continue
            }
            val diffWithTheLastMethodIndex = methodIndex - lastMethodIndex
            writeUInt16(diffWithTheLastMethodIndex)
            writeInlineCaches(methodData.inlineCaches, dexFileToProfileIndex)
            lastMethodIndex = methodIndex
        }
    }

    /**
     * Writes the dex file classes to the output stream.
     *
     * @param dexFileData the dex data containing the classes that should be serialized
     */
    private fun OutputStream.writeClasses(dexFileData: DexFileData) {
        // The profile stores the first class index, then the remainder are relative
        // to the previous value.
        var lastClassIndex = 0
        for (classIndex in dexFileData.classes) {
            val diffWithTheLastClassIndex = classIndex - lastClassIndex
            writeUInt16(diffWithTheLastClassIndex)
            lastClassIndex = classIndex
        }
    }

    /**
     * Writes the methods flags as a bitmap to the output stream.
     *
     * @param dexFile the dex file to which the data belongs
     * @param dexFileData the dex data that should be serialized
     */
    private fun OutputStream.writeMethodBitmap(
        dexFile: DexFile,
        dexFileData: DexFileData,
    ) {
        val lastFlag = MethodFlags.LAST_FLAG_REGULAR
        val bitmap = ByteArray(getMethodBitmapStorageSize(dexFile))
        for ((methodIndex, methodData) in dexFileData.methods) {
            var flag = MethodFlags.FIRST_FLAG
            while (flag <= lastFlag) {
                if (flag == MethodFlags.HOT) {
                    flag = flag shl 1
                    continue
                }
                if (methodData.isFlagSet(flag)) {
                    setMethodBitmapBit(bitmap, flag, methodIndex, dexFile)
                }
                flag = flag shl 1
            }
        }
        write(bitmap)
    }


    /**
     * Writes inline caches into the output stream.
     *
     * @param inlineCaches the inline caches to write
     * @param dexFileToProfileIndex a mapping from dex files to their profile index which is used
     * for serializing the inline cache type ids
     */
    private fun OutputStream.writeInlineCaches(
        inlineCaches: Map<Int, DexPcData>,
        dexFileToProfileIndex: Map<DexFile, Int>
    ) {
        writeUInt16(inlineCaches.size)
        for ((dexPc, dexPcData) in inlineCaches) {
            writeUInt16(dexPc)

            // Add the megamorphic/missing_types encoding if needed and continue.
            // In either cases we don't add any classes to the profiles and so there's
            // no point to continue.
            if (dexPcData.isMissingTypes) {
                writeUInt8(INLINE_CACHE_MISSING_TYPES_ENCODING)
                continue
            }
            if (dexPcData.isMegamorphic) {
                writeUInt8(INLINE_CACHE_MEGAMORPHIC_ENCODING)
                continue
            }
            val dexToClassesMap = dexPcData.classes.groupBy { it.owningDex }
            writeUInt8(dexToClassesMap.size)

            // Note that as opposed to the list of startup classes we do not delta-encode the classes in
            // the inline cache.
            for ((key, classes) in dexToClassesMap) {
                val profileIndex = dexFileToProfileIndex.getValue(key)
                writeUInt8(profileIndex)
                writeUInt8(classes.size)
                for (classReference in classes) {
                    writeUInt16(classReference.classDexIndex)
                }
            }
        }
    }

    /**
     * Returns the size necessary to encode the region of methods with inline caches.
     */
    private fun getHotMethodRegionSize(dexFileData: DexFileData): Int {
        var size = 0
        for (method in dexFileData.methods.values) {
            if (!method.isHot) continue
            size += 2 * UINT_16_SIZE // method index + inline cache size;
            size += UINT_16_SIZE * method.inlineCaches.size // dex pc counts
            for (dexPcData in method.inlineCaches.values) {
                size += UINT_8_SIZE // dex to classes map size
                // Megamorphic inline caches or the ones missing types do not have any class.
                if (dexPcData.isMissingTypes || dexPcData.isMegamorphic) {
                    continue
                }
                val inlineCacheDexFiles = HashSet<DexFile>()
                for (ref in dexPcData.classes) {
                    inlineCacheDexFiles.add(ref.owningDex)
                }
                size += inlineCacheDexFiles.size * UINT_8_SIZE // number of groups
                size += inlineCacheDexFiles.size * UINT_8_SIZE // dex profile index
                size += dexPcData.classes.size * UINT_16_SIZE // the actual classes
            }
        }
        return size
    }

    /**
     * Sets the bit corresponding to the {@param isStartup} flag in the method bitmap.
     *
     * @param bitmap the method bitmap
     * @param flag whether or not this is the startup bit
     * @param methodIndex the method index in the dex file
     * @param dexFile the method dex file
     */
    private fun setMethodBitmapBit(
        bitmap: ByteArray,
        flag: Int,
        methodIndex: Int,
        dexFile: DexFile,
    ) {
        val bitIndex = methodFlagBitmapIndex(flag, methodIndex, dexFile)
        val bitmapIndex = bitIndex / Byte.SIZE_BITS
        bitmap[bitmapIndex] = (
                bitmap[bitmapIndex].toInt() or 1 shl bitIndex % Byte.SIZE_BITS
        ).toByte()
    }

    /**
     * Returns the size needed for the method bitmap storage of the given dex file.
     */
    // TODO(lmr): bitsPerMethod here is going to be a constant number so lets simplify this method by constantizing it
    private fun getMethodBitmapStorageSize(dexFile: DexFile): Int {
        val bitsPerMethod: Int = 1 + flagBitmapIndex(MethodFlags.LAST_FLAG_REGULAR)
        val methodBitmapBits = dexFile.header.methodIds.size * bitsPerMethod
        return roundUpUsingAPowerOf2(methodBitmapBits, java.lang.Byte.SIZE) / java.lang.Byte.SIZE
    }
}

fun ArtProfile(hrp: HumanReadableProfile, obf: ObfuscationMap, apk: Apk): ArtProfile {
    val dexes = apk.dexes
    val profileData = HashMap<DexFile, DexFileData>()
    for (iDex in dexes.indices) {
        val dex = dexes[iDex]
        val methods = dex.methodPool
        val types = dex.typePool
        val classDefs = dex.classDefPool

        val profileClasses = mutableSetOf<Int>()
        val profileMethods = mutableMapOf<Int, MethodData>()

        for (iMethod in methods.indices) {
            val method = methods[iMethod]
            val deobfuscated = obf.deobfuscate(method)
            val flags = hrp.match(deobfuscated)
            if (flags != 0) {
                profileMethods[iMethod] = MethodData(flags)
            }
        }

        for (typeIndex in classDefs) {
            val type = types[typeIndex]
            if (obf.deobfuscate(type).any { hrp.match(it) != 0 }) {
                profileClasses.add(typeIndex)
            }
        }

        if (profileClasses.isNotEmpty() || profileMethods.isNotEmpty()) {
            profileData[dex] = DexFileData(profileClasses, profileMethods)
        }
    }
    return ArtProfile(
        version = VERSION_0_1_0_P,
        profileData = profileData,
    )
}

internal class DexFileData(
    val classes: Set<Int>,
    val methods: Map<Int, MethodData>,
)

internal class MethodData(
    val flags: Int,
    val inlineCaches: Map<Int, DexPcData> = emptyMap(),
) {
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

internal class DexPcData(
    val isMegamorphic: Boolean,
    val isMissingTypes: Boolean,
    val classes: Set<ClassReference>,
)

internal class ClassReference(
    val classDexIndex: Int,
    val owningDex: DexFile,
)

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
}

/**
 * Computes the length of the string's UTF-8 encoding.
 */
internal val String.utf8Length: Int get() = toByteArray(StandardCharsets.UTF_8).size

internal fun roundUpUsingAPowerOf2(value: Int, powerOfTwo: Int): Int {
    return value + powerOfTwo - 1 and -powerOfTwo
}

internal fun isPowerOfTwo(x: Int): Boolean {
    return x > 0 && x and x - 1 == 0
}

/** Returns the position on which the flag is encoded in the bitmap.  */
private fun flagBitmapIndex(methodFlag: Int): Int {
    require(isValidMethodFlag(methodFlag))
    require(methodFlag != MethodFlags.HOT)
    // We arrange the method flags in order, starting with the startup flag.
    // The kFlagHot is not encoded in the bitmap and thus not expected as an
    // argument here. Since all the other flags start at 1 we have to subtract
    // one from the power of 2.
    return whichPowerOf2(methodFlag) - 1
}

/** Returns the absolute index in the flags bitmap of a method.  */
private fun methodFlagBitmapIndex(flag: Int, methodIndex: Int, dexFile: DexFile): Int {
    // The format is [startup bitmap][post startup bitmap][AmStartup][...]
    // This compresses better than ([startup bit][post startup bit])*
    return methodIndex + flagBitmapIndex(flag) * dexFile.header.methodIds.size
}

/** Returns true iff the methodFlag is valid and encodes a single value flag  */
private fun isValidMethodFlag(methodFlag: Int): Boolean {
    return (isPowerOfTwo(methodFlag)
            && methodFlag >= MethodFlags.FIRST_FLAG && methodFlag <= MethodFlags.LAST_FLAG_REGULAR)
}

/**
 * Returns ln2(x) assuming tha x is a power of 2.
 */
internal fun whichPowerOf2(x: Int): Int {
    require(isPowerOfTwo(x))
    return Integer.numberOfTrailingZeros(x)
}


internal const val UINT_8_SIZE = 1
internal const val UINT_16_SIZE = 2
internal const val UINT_32_SIZE = 4

internal fun OutputStream.writeUInt(value: Long, numberOfBytes: Int) {
    val buffer = ByteArray(numberOfBytes)
    for (i in 0 until numberOfBytes) {
        buffer[i] = (value shr i * java.lang.Byte.SIZE and 0xff).toByte()
    }
    write(buffer)
}

/**
 * Writes the value as an 8 bit unsigned integer (uint8_t in c++).
 */
internal fun OutputStream.writeUInt8(value: Int) = writeUInt(value.toLong(), UINT_8_SIZE)

/**
 * Writes the value as a 16 bit unsigned integer (uint16_t in c++).
 */
internal fun OutputStream.writeUInt16(value: Int) = writeUInt(value.toLong(), UINT_16_SIZE)

/**
 * Writes the value as a 32 bit unsigned integer (uint32_t in c++).
 */
internal fun OutputStream.writeUInt32(value: Long) = writeUInt(value, UINT_32_SIZE)

/**
 * Writes a string in the stream using UTF-8 encoding.
 *
 * @param s the string to be written
 * @throws IOException in case of IO errors
 */
internal fun OutputStream.writeString(s: String) {
    write(s.toByteArray(StandardCharsets.UTF_8))
}

/**
 * Compresses data the using [DeflaterOutputStream] before writing it to the stream.
 * The method will write the size of the compressed data (32 bits, [.writeUInt32]) before
 * the actual compressed bytes.
 *
 * @param data the data to be compressed and written.
 * @throws IOException in case of IO errors
 */
internal fun OutputStream.writeCompressed(data: ByteArray) {
    val compressor = Deflater(Deflater.BEST_SPEED)
    val bos = ByteArrayOutputStream()
    DeflaterOutputStream(bos, compressor).use { it.write(data) }
    writeUInt32(bos.size().toLong())
    // TODO(calin): we can get rid of the multiple byte array copy using a custom stream.
    write(bos.toByteArray())
}
