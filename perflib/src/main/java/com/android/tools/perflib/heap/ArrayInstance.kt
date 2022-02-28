/*
 * Copyright (C) 2008 Google Inc.
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
package com.android.tools.perflib.heap

import com.android.tools.perflib.captures.DataBuffer
import java.nio.ByteBuffer
import java.util.Locale

open class ArrayInstance(
    id: Long, stack: StackTrace?,
    val arrayType: Type, val length: Int,
    private val valuesOffset: Long
) : Instance(id, stack) {
    open val values: Array<Any?>
        get() {
            buffer.setPosition(valuesOffset)
            return Array(length) { readValue(arrayType) }
        }

    fun asRawByteArray(start: Int, elementCount: Int): ByteArray {
        buffer.setPosition(valuesOffset)
        assert(arrayType != Type.OBJECT)
        assert(start + elementCount <= length)
        val bytes = ByteArray(elementCount * arrayType.size)
        buffer.readSubSequence(bytes, start * arrayType.size, elementCount * arrayType.size)
        return bytes
    }

    fun asCharArray(offset: Int, length: Int): CharArray {
        assert(arrayType == Type.CHAR)
        // TODO: Make this copy less by supporting offset in asRawByteArray.
        val charBuffer = ByteBuffer.wrap(asRawByteArray(offset, length))
            .order(DataBuffer.HPROF_BYTE_ORDER)
            .asCharBuffer()
        val result = CharArray(length)
        charBuffer[result]
        return result
    }

    // TODO: Take the rest of the fields into account: length, type, etc (~16 bytes).
    override var size: Int
        get() = length * heap!!.mSnapshot.getTypeSize(arrayType)
        set(size) {
            super.size = size
        }

    override fun resolveReferences() {
        if (arrayType == Type.OBJECT) {
            for (value in values) {
                if (value is Instance) {
                    value.addReverseReference(null, this)
                    _hardFwdRefs += value
                }
            }
        }
    }

    override fun accept(visitor: Visitor) {
        visitor.visitArrayInstance(this)
        for (instance in hardForwardReferences) {
            visitor.visitLater(this, instance)
        }
    } // We might not be parsing an Android hprof.

    // Primitive arrays don't set their classId, we need to do the lookup manually.
    override val classObj: ClassObj?
        get() = when (arrayType) {
            Type.OBJECT -> super.classObj
            else ->
                // Primitive arrays don't set their classId, we need to do the lookup manually.
                heap!!.mSnapshot.findClass(arrayType.getClassNameOfPrimitiveArray(false)) ?:
                // We might not be parsing an Android hprof.
                heap!!.mSnapshot.findClass(arrayType.getClassNameOfPrimitiveArray(true))
        }

    override fun toString(): String {
        var className = classObj!!.className
        if (className.endsWith("[]")) {
            className = className.substring(0, className.length - 2)
        }
        return String.format(Locale.US, "%s[%d]@%d (0x%x)", className, length, uniqueId, uniqueId)
    }
}
