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
import com.google.common.primitives.UnsignedBytes
import java.util.Arrays

abstract class Instance internal constructor(
    val id: Long, //  The stack in which this object was allocated
    val stack: StackTrace?
) {

    //  Id of the ClassObj of which this object is an instance
    var classId: Long = 0

    open val uniqueId: Long get() = id and heap!!.mSnapshot.idSizeMask

    //  The heap in which this object was allocated (app, zygote, etc)
    open var heap: Heap? = null

    //  Returns the instrinsic size of a given object
    //  The size of this object
    open var size = 0

    //  O+ format only. The registered native size associated with this object.
    var nativeSize: Long = 0

    //  The retained size of this object, indexed by heap (default, image, app, zygote).
    //  Intuitively, this represents the amount of memory that could be reclaimed in each heap if
    //  the instance were removed.
    //  To save space, we only keep a primitive array here following the order in mSnapshot.mHeaps.
    private var retainedSizes: LongArray? = null
    internal var _hardFwdRefs: InstanceList = InstanceList.Empty
    val hardForwardReferences: Sequence<Instance> get() = _hardFwdRefs.asInstanceSequence()

    //  List of all objects that hold a live reference to this object
    internal var _hardRevRefs: InstanceList = InstanceList.Empty
    open val hardReverseReferences: Sequence<Instance> get() = _hardRevRefs.asInstanceSequence()

    //  List of all objects that hold a soft/weak/phantom reference to this object.
    //  Don't create an actual list until we need to.
    var _softRevRefs: InstanceList = InstanceList.Empty
    open val softReverseReferences: Sequence<Instance> get() = _softRevRefs.asInstanceSequence()

    open val classObj: ClassObj? get() = heap!!.mSnapshot.findClass(classId)

    val compositeSize: Int
        get() {
            val visitor = object : NonRecursiveVisitor() {
                var compositeSize = 0
                override fun defaultAction(node: Instance) {
                    compositeSize += (node.size + node.nativeSize).toInt()
                }
            }
            visitor.doVisit(listOf(this))
            return visitor.compositeSize
        }

    open var distanceToGcRoot: Int = Int.MAX_VALUE
        set(newDistance) {
            assert(newDistance < field)
            field = newDistance
        }

    /**
     * Determines if this instance is reachable via hard references from any GC root.
     * The results are only valid after ShortestDistanceVisitor has been run.
     */
    val isReachable get() = distanceToGcRoot != Int.MAX_VALUE

    /**
     * There is an underlying assumption that a class that is a soft reference will only have one
     * referent.
     *
     * true if the instance is a soft reference type, or false otherwise
     */
    open val isSoftReference: Boolean get() = false

    protected val buffer: DataBuffer get() = heap!!.mSnapshot.buffer

    /**
     * Resolves all forward/reverse + hard/soft references for this instance.
     */
    abstract fun resolveReferences()
    abstract fun accept(visitor: Visitor)

    fun resetRetainedSize() {
        val allHeaps: List<Heap?> = heap!!.mSnapshot.heapList
        if (retainedSizes == null) {
            retainedSizes = LongArray(allHeaps.size)
        } else {
            Arrays.fill(retainedSizes!!, 0)
        }
        retainedSizes!![allHeaps.indexOf(heap)] = size + nativeSize
    }

    fun addRetainedSizes(other: Instance) {
        for (i in retainedSizes!!.indices) {
            retainedSizes!![i] += other.retainedSizes!![i]
        }
    }

    fun getRetainedSize(heapIndex: Int): Long = retainedSizes!![heapIndex]

    val totalRetainedSize: Long get() = retainedSizes?.sum() ?: 0

    /**
     * Add to the list of objects that references this Instance.
     *
     * @param field     the named variable in #reference pointing to this instance. If the name of
     * the field is "referent", and #reference is a soft reference type, then
     * reference is counted as a soft reference instead of the usual hard
     * reference.
     * @param reference another instance that references this instance
     */
    fun addReverseReference(field: Field?, reference: Instance) {
        if (field != null && field.name == "referent" && reference.isSoftReference) {
            _softRevRefs += reference
        } else {
            _hardRevRefs += reference
        }
    }

    protected fun readValue(type: Type): Any? = when (type) {
        Type.OBJECT -> heap!!.mSnapshot.findInstance(readId())
        Type.BOOLEAN -> buffer.readByte().toInt() != 0
        Type.CHAR -> buffer.readChar()
        Type.FLOAT -> buffer.readFloat()
        Type.DOUBLE -> buffer.readDouble()
        Type.BYTE -> buffer.readByte()
        Type.SHORT -> buffer.readShort()
        Type.INT -> buffer.readInt()
        Type.LONG -> buffer.readLong()
    }

    protected fun readId(): Long =
        // As long as we don't interpret IDs, reading signed values here is fine.
        when (heap!!.mSnapshot.getTypeSize(Type.OBJECT)) {
            1 -> buffer.readByte().toLong()
            2 -> buffer.readShort().toLong()
            4 -> buffer.readInt().toLong()
            8 -> buffer.readLong()
            else -> 0
        }

    protected fun readUnsignedByte(): Int = UnsignedBytes.toInt(buffer.readByte())
    protected fun readUnsignedShort(): Int = buffer.readShort().toInt() and 0xffff
}
