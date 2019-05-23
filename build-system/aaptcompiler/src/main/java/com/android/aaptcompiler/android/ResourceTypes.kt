/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.aaptcompiler.android

import com.android.aaptcompiler.StringPool
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.text.Charsets

/**
 * Definitions of resource data structures.
 * <p>Transliterated from: *
 * https://android.googlesource.com/platform/frameworks/base/+/android-9.0.0_r12/libs/androidfw/ResourceTypes.cpp
 */

/**
 * Header that appears at the front of every data chunk in a resource.
 */
class ResChunkHeader(
  // Type identifier for this chunk. The meaning of this value depends on the containing chunk.
  val typeId: Short,
  // Size of the chunk header (in bytes). Adding this value to the address of the chunk will be
  // the address of the associated data if any.
  val headerSize: Short) {

  constructor(): this(0, 0)

  // Total size of this chunk (in bytes). This is the chunkSize plus the size of any data
  // associated with this chunk. Adding the this to the address of this chunk will completely skip
  // its contents (including any child chunks).
  var size: Int = 0
}

enum class ChunkType(val id: Short) {
  NULL_TYPE(0x0000),
  STRING_POOL_TYPE(0x0001),
  TABLE_TYPE(0x0002),
  XML_TYPE(0x0003),

  // Marker for the beginning of XML types.
  XML_FIRST_CHUNK_TYPE(0x0100),
  // Child types of XML_TYPE
  XML_START_NAMESPACE_TYPE(0x0100),
  XML_END_NAMESPACE_TYPE(0x0101),
  XML_START_ELEMENT_TYPE(0x0102),
  XML_END_ELEMENT_TYPE(0x0103),
  XML_CDATA_TYPE(0x0104),
  XML_LAST_CHUNK_TYPE(0x017f),
  // This contains a uint32_t array mapping strings in the string
  // pool back to resource identifiers.  It is optional.
  XML_RESOURCE_MAP_TYPE(0x0180),

  // Child types of TABLE_TYPE
  TABLE_PACKAGE_TYPE(0x0200),
  TABLE_TYPE_TYPE(0x0201),
  TABLE_TYPE_SPEC_TYPE(0x0202),
  TABLE_LIBRARY_TYPE(0x0203),
  TABLE_OVERLAYABLE_TYPE(0x0204),
  TABLE_OVERLAYABLE_POLICY_TYPE(0x0205),
}

/**
 * Reference to a string in a string pool
 */
data class ResStringPoolRef(
  // Index into the string pool table (unsigned 32 bit offset from the indices immediately after
  // ResStringPoolHeader) at which to find the location of the string data in the pool
  val index: Int)

/**
 * Definition of a pool of strings.
 *
 * <p> The data of this chunk is an array of 32 bit unsigned integers providing indices into the
 * pool, relative to the start of the string values. This start, {@code stringStart}, are of all the
 * UTF-16 Strings concatenated together; each starts with a short representing the string's length
 * and ends in a terminating 0x0000u character. If a string is > 32767 characters, the size will be
 * stored as two 16 bit values, a high word and a low word, with the high bit set in the first value
 * to indicate this format is being used.
 *
 * <p> If {@code styleCount} is not zero, then immediately following the array of indices into the
 * string table is another array of indices into a style table starting at {@code stylesStart}.
 * Each entry in the style table is an array of {@code ResStringPoolSpan} structures.
 */
data class ResStringPoolHeader(
  val header: ResChunkHeader,
  // Number of strings in this pool (number of 32 bit indices that follow in the data).
  val stringCount: Int,
  // Number of style span arrays in the pool (number of 32 bit indices that follow the string
  // indices).
  val styleCount: Int) {

  constructor(): this(ResChunkHeader(), 0, 0)

  var flags: Int = 0
  // Index from header of the string data.
  var stringsStart: Int = 0
  // Index from header of the style data.
  var stylesStart: Int = 0

  companion object {
    const val SORTED_FLAG = 1 shl 0
    const val UTF8_FLAG = 1 shl 8

    const val SIZE = 28.toShort()
  }
}

/**
 * This structure defines a span of style information associated with a string in the pool.
 */
data class ResStringPoolSpan(
  // this is the name of the span  -- that is, the name of the XML tag that defined it. The special
  // value END indicates the end of an array of spans.
  val name: ResStringPoolRef,
  // The range of characters in the string that this span applies to.
  val firstChar: Int,
  val lastChar: Int
) {
  companion object {
    const val END = -1
    const val SIZE = 12
  }
}

/** Convenience class for accessing data in a String Pool Flattened Resource */
class ResStringPool private constructor(
  val data: ByteBuffer,
  val header: ResStringPoolHeader,
  val stringPoolSize: Int,
  val strings: List<String>,
  val stylesPoolSize: Int,
  val styles: List<List<ResStringPoolSpan>>) {

  companion object {
    fun get(buffer: ByteBuffer, length: Int): ResStringPool {
      buffer.order(ByteOrder.nativeOrder())

      if (length < ResStringPoolHeader.SIZE) {
        error("Invalid StringPool: buffer too small to store a string pool.")
      }

      val typeId = buffer.getShort(0).deviceToHost()
      val headerSize = buffer.getShort(2).deviceToHost()
      val resourceSize = buffer.getInt(4).deviceToHost()

      if (typeId != ChunkType.STRING_POOL_TYPE.id ||
        headerSize != ResStringPoolHeader.SIZE ||
        resourceSize < headerSize ||
        resourceSize > length
      ) {
        error("Invalid StringPool: Header has invalid format.")
      }

      // The size has been checked, so we can start reading the ResStringPoolHeader Fields.
      val chunkHeader = ResChunkHeader(typeId, headerSize)
      chunkHeader.size = resourceSize

      val header =
        ResStringPoolHeader(
          chunkHeader, buffer.getInt(8).deviceToHost(), buffer.getInt(12).deviceToHost())
      header.flags = buffer.getInt(16).deviceToHost()
      header.stringsStart = buffer.getInt(20).deviceToHost()
      header.stylesStart = buffer.getInt(24).deviceToHost()

      var stringPoolSize = 0
      val strings = mutableListOf<String>()
      if (header.stringCount != 0) {
        // we need to check overflow and ensure the string indexes can fit.
        if (header.stringCount*4 < header.stringCount ||
          (header.header.headerSize + (header.stringCount*4)) > resourceSize) {
          error("Invalid StringPool: Buffer not large enough for string indices.")
        }

        val charSize = if ((header.flags and ResStringPoolHeader.UTF8_FLAG) != 0) 1 else 2

        // There should at least be enough space for the smallest string.
        // (2 bytes length, null terminator)
        if (header.stringsStart+2 >= resourceSize) {
          error("Invalid StringPool: Buffer not large enough for strings.")
        }

        if (header.styleCount == 0) {
          stringPoolSize = (resourceSize - header.stringsStart) / charSize
        } else {
          // check invariant: styles starts before end of data
          if (header.stylesStart >= resourceSize - 2) {
            error("Invalid StringPool: Style start specified in header too large.")
          }
          // check invariant: styles follow strings
          if (header.stylesStart <= header.stringsStart) {
            error("Invalid StringPool: ")
          }
          stringPoolSize = (header.stylesStart - header.stringsStart) / charSize
        }

        if (stringPoolSize ==0) {
          error("Invalid StringPool: Space for strings in header is too small.")
        }

        var currentStringIndex = headerSize.toInt()
        for (i in 0.until(header.stringCount)) {
          val stringLocation =
            buffer.getInt(currentStringIndex).deviceToHost() + header.stringsStart
          currentStringIndex += 4

          val string = decodeString(buffer, stringLocation, charSize == 1)
          strings.add(string)
        }
      }

      var stylePoolSize = 0
      val styles = mutableListOf<List<ResStringPoolSpan>>()
      if (header.styleCount != 0) {

        var currentStyleIndex = headerSize.toInt() + header.stringCount*4
        // invariant: integer overflow in calculating styles
        if (currentStyleIndex < headerSize.toInt()) {
          error("Invalid StringPool: Integer overflow encountered while decoding styles.")
        }

        stylePoolSize = (resourceSize - header.stylesStart)/4

        for (i in 0.until(header.styleCount)) {
          val styleLocation = buffer.getInt(currentStyleIndex).deviceToHost() + header.stylesStart
          currentStyleIndex += 4

          val style = decodeStyle(buffer, styleLocation)
          styles.add(style)
        }
      }
      return ResStringPool(buffer, header, stringPoolSize, strings, stylePoolSize, styles)
    }

    private fun decodeString(buffer: ByteBuffer, location: Int, utf8: Boolean): String {
      var stringPosition = location

      if (utf8) {
        // In UTF8 mode, the length of UTF16 comes first)
        val firstByteUTF16 = buffer.get(stringPosition)
        ++stringPosition

        val utf16Length = when {
          (firstByteUTF16.toInt() and StringPool.TWO_BYTE_UTF8_LENGTH_SIGNIFIER) != 0 -> {
            val secondByte = buffer.get(stringPosition).toInt() and 0xff
            ++stringPosition

            ((firstByteUTF16.toInt() shl 8) + secondByte) and StringPool.UTF8_ENCODE_LENGTH_MAX
          }
          else -> firstByteUTF16.toInt()
        }

        // In UTF8 mode, the length in UTF8 comes next
        val firstByteUTF8 = buffer.get(stringPosition)
        ++stringPosition

        val utf8Length = when {
          (firstByteUTF8.toInt() and StringPool.TWO_BYTE_UTF8_LENGTH_SIGNIFIER) != 0 -> {
            val secondByte = buffer.get(stringPosition).toInt() and 0xff
            ++stringPosition

            ((firstByteUTF8.toInt() shl 8) + secondByte) and StringPool.UTF8_ENCODE_LENGTH_MAX
          }
          else -> firstByteUTF8.toInt()
        }

        // pull the bytes out of the buffer.
        val array = ByteArray(utf8Length)
        for (i in 0.until(utf8Length)) {
          array[i] = buffer.get(stringPosition)
          ++stringPosition
        }

        // ensure the string is null terminated
        if (buffer.get(stringPosition) != 0.toByte()) {
          error("Invalid StringPool: UTF8 string value is not null-terminated.")
        }

        val utf16String = String(array, Charsets.UTF_8)
        // ensure the utf16 size is correct
        if (utf16String.length != utf16Length) {
          error("Invalid StringPool: specified UTF16 does not match actual string length.")
        }

        return utf16String
      } else {
        // first get the utf16 length, as that is the first component regardless of mode.
        val firstShort = buffer.getShort(stringPosition).deviceToHost()
        stringPosition += 2

        val utf16Length = when {
          (firstShort.toInt() and StringPool.TWO_CHAR_UTF16_LENGTH_SIGNIFIER) != 0 -> {
            val secondShort = buffer.getShort(stringPosition).deviceToHost().toInt() and 0xffff
            stringPosition += 2
            ((firstShort.toInt() shl 16) + secondShort) and StringPool.UTF16_ENCODE_LENGTH_MAX
          }
          else -> firstShort.toInt()
        }

        // pull the chars out of the buffer
        val array = CharArray(utf16Length)
        for (i in 0.until(utf16Length)) {
          array[i] = buffer.getChar(stringPosition).deviceToHost()
          stringPosition += 2
        }

        // ensure the string is null terminated
        if (buffer.getChar(stringPosition) != 0.toChar()) {
          error("Invalid StringPool: UTF16 string value is not null-terminated.")
        }

        return String(array)
      }
    }

    private fun decodeStyle(buffer: ByteBuffer, location: Int): List<ResStringPoolSpan> {
      var currentIndex = location

      val result = mutableListOf<ResStringPoolSpan>()
      while (true) {
        val refIndex = buffer.getInt(currentIndex).deviceToHost()
        if (refIndex == ResStringPoolSpan.END) {
          break
        }
        val firstChar = buffer.getInt(currentIndex + 4).deviceToHost()
        val lastChar = buffer.getInt(currentIndex + 8).deviceToHost()
        currentIndex += 12
        result.add(ResStringPoolSpan(ResStringPoolRef(refIndex), firstChar, lastChar))
      }
      return result
    }
  }
}

