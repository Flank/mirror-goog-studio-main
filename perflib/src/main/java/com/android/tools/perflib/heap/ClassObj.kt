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
import gnu.trove.TIntObjectHashMap

open class ClassObj(
    id: Long, stack: StackTrace?, val className: String,
    private val staticFieldsOffset: Long
) : Instance(id, stack), Comparable<ClassObj> {
    class HeapData {
        var shallowSize = 0
        var instances: MutableList<Instance> = ArrayList()
    }

    var superClassId: Long = 0
    var classLoaderId: Long = 0
    var fields: Array<Field> = Array(0) { throw IllegalStateException() }
    var staticFields: Array<Field> = Array(0) { throw IllegalStateException() }
    var instanceSize = 0
    override var isSoftReference = false
    var heapData = TIntObjectHashMap<HeapData>()

    fun addSubclass(subclass: ClassObj) = subclasses.add(subclass)

    val subclasses: MutableSet<ClassObj> = mutableSetOf()

    fun dumpSubclasses() {
        for (subclass in subclasses) {
            println("     " + subclass.className)
        }
    }

    override fun toString() = className.replace('/', '.')

    fun addInstance(heapId: Int, instance: Instance) {
        if (instance is ClassInstance) {
            instance.size = instanceSize
        }
        val data = heapData[heapId] ?: HeapData().also { heapData.put(heapId, it) }
        data.instances.add(instance)
        data.shallowSize += instance.size
    }

    val allFieldsCount: Int get() = fields.size + (superClassObj?.allFieldsCount ?: 0)

    fun getShallowSize(heapId: Int): Int = heapData[heapId]?.shallowSize ?: 0

    fun setIsSoftReference() {
        isSoftReference = true
    }

    open val staticFieldValues: Map<Field, Any?>
        get() {
            val result: MutableMap<Field, Any?> = HashMap()
            buffer.setPosition(staticFieldsOffset)
            val numEntries = readUnsignedShort()
            for (i in 0 until numEntries) {
                val f = staticFields[i]
                readId()
                readUnsignedByte()
                val value = readValue(f.type)
                result[f] = value
            }
            return result
        }

    override fun resolveReferences() {
        for ((key, value) in staticFieldValues) {
            if (value is Instance) {
                value.addReverseReference(key, this)
                hardForwardReferences.add(value)
            }
        }
    }

    override fun accept(visitor: Visitor) {
        visitor.visitClassObj(this)
        for (instance in hardForwardReferences) {
            visitor.visitLater(this, instance)
        }
    }

    override fun compareTo(o: ClassObj): Int {
        if (id == o.id) {
            return 0
        }
        val nameCompareResult = className.compareTo(o.className)
        return when {
            nameCompareResult != 0 -> nameCompareResult
            id - o.id > 0 -> 1
            else -> -1
        }
    }

    override fun equals(o: Any?) = o is ClassObj && compareTo(o) == 0
    override fun hashCode(): Int = className.hashCode()

    @VisibleForTesting
    fun getStaticField(type: Type, name: String): Any? = staticFieldValues[Field(type, name)]

    val superClassObj: ClassObj? get() = heap!!.mSnapshot.findClass(superClassId)
    val classLoader: Instance? get() = heap!!.mSnapshot.findInstance(classLoaderId)
    val instancesList: List<Instance> get() = heapData.keys().flatMap(::getHeapInstances)

    fun getHeapInstances(heapId: Int): List<Instance> = heapData[heapId]?.instances ?: listOf()
    fun getHeapInstancesCount(heapId: Int): Int = heapData[heapId]?.instances?.size ?: 0

    val instanceCount: Int get() = heapData.values.sumOf { (it as HeapData).instances.size }
    val shallowSize: Int get() = heapData.values.sumOf { (it as HeapData).shallowSize }

    val descendantClasses: Sequence<ClassObj>
        get() = sequenceOf(this) + subclasses.flatMap { it.descendantClasses }

    companion object {
        val referenceClassName: String get() = "java.lang.ref.Reference"
    }
}
