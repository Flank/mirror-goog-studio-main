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

import com.android.annotations.VisibleForTesting
import java.io.UnsupportedEncodingException
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

open class ClassInstance(id: Long, stack: StackTrace?, private val valuesOffset: Long)
    : Instance(id, stack) {
    open val values: List<FieldValue>
        get() = mutableListOf<FieldValue>().also { result ->
            buffer.setPosition(valuesOffset)
            tailrec fun collect(cl: ClassObj?) {
                if (cl != null) {
                    result.addAll(cl.fields.map { FieldValue(it, readValue(it.type)) })
                    collect(cl.superClassObj)
                }
            }
            collect(classObj)
        }

    override val isSoftReference: Boolean get() = classObj!!.isSoftReference
    val isStringInstance: Boolean get() = "java.lang.String" == classObj?.className
    val asString: String? get() = getAsString(Int.MAX_VALUE)

    @VisibleForTesting
    fun getFields(name: String): List<FieldValue> = values.filter { it.field.name == name }

    override fun resolveReferences() {
        for (fieldValue in values) {
            if (fieldValue.value is Instance) {
                fieldValue.value.addReverseReference(fieldValue.field, this)
                if (!isSoftReference || fieldValue.field.name != "referent") {
                    hardForwardReferences.add(fieldValue.value)
                }
            }
        }
        hardForwardReferences.trimToSize() // Don't wait until the compactMemory stage to trim.
    }

    override fun accept(visitor: Visitor) {
        visitor.visitClassInstance(this)
        for (instance in hardForwardReferences) {
            visitor.visitLater(this, instance)
        }
    }

    override fun toString() =
        String.format(Locale.US, "%s@%d (0x%x)", classObj!!.className, uniqueId, uniqueId)

    fun getAsString(maxDecodeStringLength: Int): String? {
        var count = -1
        var offset = 0
        var charBufferArray: ArrayInstance? = null
        // In later versions of Android, the underlying storage format changed to byte buffers.
        var byteBufferArray: ArrayInstance? = null
        for (entry in values) {
            when {
                charBufferArray == null && "value" == entry.field.name ->
                    if (entry.value is ArrayInstance) {
                        when (entry.value.arrayType) {
                            Type.CHAR -> charBufferArray = entry.value
                            Type.BYTE -> byteBufferArray = entry.value
                        }
                    }
                "count" == entry.field.name && entry.value is Int -> count = entry.value
                "offset" == entry.field.name && entry.value is Int -> offset = entry.value
            }
        }
        fun<T> mkRaw(mk: (Int, Int) -> T) = mk(max(offset, 0),
                                               max(min(count, maxDecodeStringLength), 0))
        return when {
            byteBufferArray != null ->
                try {
                    String(mkRaw(byteBufferArray::asRawByteArray), Charsets.UTF_8)
                } catch (e: UnsupportedEncodingException) {
                    null
                }
            charBufferArray != null -> String(mkRaw(charBufferArray::asCharArray))
            else -> null
        }
    }

    class FieldValue(val field: Field, val value: Any?)
}
