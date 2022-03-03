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
import com.android.tools.perflib.analyzer.Capture
import com.android.tools.perflib.captures.DataBuffer
import com.android.tools.perflib.heap.analysis.LinkEvalDominators
import com.android.tools.perflib.heap.analysis.ShortestDistanceVisitor
import com.android.tools.perflib.heap.ext.NativeRegistryPostProcessor
import com.android.tools.perflib.heap.ext.SnapshotPostProcessor
import com.android.tools.proguard.ProguardMap
import gnu.trove.THashSet
import gnu.trove.TIntObjectHashMap
import gnu.trove.TLongObjectHashMap

/*
 * A snapshot of all of the heaps, and related meta-data, for the runtime at a given instant.
 *
 * There are three possible heaps: default, app and zygote. GC roots are always reported in the
 * default heap, and they are simply references to objects living in the zygote or the app heap.
 * During parsing of the HPROF file HEAP_DUMP_INFO chunks change which heap is being referenced.
 */
class Snapshot @VisibleForTesting constructor(val buffer: DataBuffer) : Capture() {
    @JvmField val heapList = ArrayList<Heap>()
    private var currentHeap: Heap? = null

    //  Root objects such as interned strings, jni locals, etc
    private var roots = ArrayList<RootObj>()

    //  List stack traces, which are lists of stack frames
    private var traces = TIntObjectHashMap<StackTrace>()

    //  List of individual stack frames
    private var frames = TLongObjectHashMap<StackFrame>()
    private var areRetainedSizesComputed = false

    //  The set of all classes that are (sub)class(es) of java.lang.ref.Reference.
    private val referenceClasses = THashSet<ClassObj>()
    private var typeSizes: IntArray? = null
    var idSizeMask = 0x00000000ffffffffL
        private set

    val heaps: Collection<Heap> get() = heapList
    val gcRoots: Collection<RootObj> get() = roots

    init {
        setToDefaultHeap()
    }

    fun dispose() = buffer.dispose()

    fun setToDefaultHeap(): Heap = setHeapTo(DEFAULT_HEAP_ID, "default")

    fun setHeapTo(id: Int, name: String): Heap {
        val heap = getHeap(id)
            ?: Heap(id, name).also {
                it.mSnapshot = this
                heapList.add(it)
            }
        currentHeap = heap
        return heap
    }

    fun getHeapIndex(heap: Heap): Int = heapList.indexOf(heap)
    fun getHeap(id: Int): Heap? = heapList.find { it.id == id }
    fun getHeap(name: String): Heap? = heapList.find { it.name == name }

    fun addStackFrame(theFrame: StackFrame) = frames.put(theFrame.mId, theFrame)
    fun getStackFrame(id: Long): StackFrame? = frames[id]
    fun addStackTrace(theTrace: StackTrace) = traces.put(theTrace.mSerialNumber, theTrace)
    fun getStackTrace(traceSerialNumber: Int): StackTrace? = traces[traceSerialNumber]

    fun getStackTraceAtDepth(traceSerialNumber: Int, depth: Int): StackTrace? =
        traces[traceSerialNumber]?.fromDepth(depth)

    fun addRoot(root: RootObj) {
        roots.add(root)
        root.heap = currentHeap
    }

    fun addThread(thread: ThreadObj?, serialNumber: Int) =
        currentHeap!!.addThread(thread, serialNumber)

    fun getThread(serialNumber: Int): ThreadObj =
        currentHeap!!.getThread(serialNumber)

    fun setIdSize(size: Int) {
        val maxId = Type.values().maxOf { it.typeId }
        // Update this if hprof format ever changes its supported types.
        assert(maxId in 1 .. Type.LONG.typeId)
        typeSizes = IntArray(maxId + 1) { -1 }
        for (i in Type.values().indices) {
            typeSizes!![Type.values()[i].typeId] = Type.values()[i].size
        }
        typeSizes!![Type.OBJECT.typeId] = size
        idSizeMask = -0x1L ushr (8 - size) * 8
    }

    fun getTypeSize(type: Type): Int = typeSizes!![type.typeId]

    fun addInstance(id: Long, instance: Instance) {
        currentHeap!!.addInstance(id, instance)
        instance.heap = currentHeap
    }

    fun addClass(id: Long, theClass: ClassObj) {
        currentHeap!!.addClass(id, theClass)
        theClass.heap = currentHeap
    }

    fun findInstance(id: Long): Instance? =
        heapList.firstNotNullOfOrNull { it.getInstance(id) } ?:
        //  Couldn't find an instance of a class, look for a class object
        findClass(id)

    fun findClass(id: Long): ClassObj? = heapList.firstNotNullOfOrNull { it.getClass(id) }

    /**
     * Finds the first ClassObj with a class name that matches `name`.
     *
     * @param name of the class to find
     * @return the found `ClassObj`, or null if not found
     */
    fun findClass(name: String?): ClassObj? =
        heapList.firstNotNullOfOrNull { it.getClass(name) }

    /**
     * Finds all `ClassObj`s with class name that match the given `name`.
     *
     * @param name of the class to find
     * @return a collection of the found `ClassObj`s, or empty collection if not found
     */
    fun findClasses(name: String?): Collection<ClassObj> = heapList.flatMap { it.getClasses(name) }

    fun resolveClasses() {
        val clazz = findClass(JAVA_LANG_CLASS)
        val javaLangClassSize = clazz?.instanceSize ?: 0
        for (heap in heapList) {
            for (classObj in heap.classes) {
                val superClass = classObj.superClassObj
                superClass?.addSubclass(classObj)
                // We under-approximate the size of the class by including the size of Class.class
                // and the size of static fields, and omitting padding, vtable and imtable sizes.
                var classSize = javaLangClassSize
                for (f in classObj.staticFields) {
                    classSize += getTypeSize(f.type)
                }
                classObj.size = classSize
            }
            val heapId = heap.id
            heap.forEachInstance { instance ->
                val classObj = instance.classObj
                classObj?.addInstance(heapId, instance)
                true
            }
        }
    }

    fun identifySoftReferences() {
        for (classObj in findAllDescendantClasses(ClassObj.referenceClassName)) {
            classObj.setIsSoftReference()
            referenceClasses.add(classObj)
        }
    }

    fun resolveReferences() {
        for (heap in heaps) {
            heap.classes.forEach(ClassObj::resolveReferences)
            heap.forEachInstance { instance ->
                instance.resolveReferences()
                true
            }
        }
    }

    fun compactMemory() = heaps.forEach { heap ->
        heap.forEachInstance { instance ->
            instance.compactMemory()
            true
        }
    }

    fun findAllDescendantClasses(className: String): List<ClassObj> =
        findClasses(className).flatMap { it.descendantClasses }

    fun computeRetainedSizes() {
        if (!areRetainedSizesComputed) {
            prepareComputeRetainedSizes()
            doComputeRetainedSizes()
            areRetainedSizesComputed = true
        }
    }

    private fun prepareComputeRetainedSizes() {
        resolveReferences()
        compactMemory()
        ShortestDistanceVisitor().doVisit(gcRoots)
        forEachReachableInstance(Instance::dedupeReferences)

        // Initialize retained sizes for all classes and objects, including unreachable ones.
        for (heap in heaps) {
            heap.classes.forEach(Instance::resetRetainedSize)
            heap.forEachInstance {
                it.resetRetainedSize()
                true
            }
        }
    }

    private fun doComputeRetainedSizes() {
        val (instances, immDom) = LinkEvalDominators.computeDominators(
            gcRoots.mapNotNullTo(mutableSetOf(), RootObj::referredInstance),
            { it.hardForwardReferences.stream() },
        )

        // We only update the retained sizes of objects in the dominator tree (i.e. reachable).
        // It's important to traverse in reverse topological order
        for (i in instances.indices.reversed()) {
            immDom[i]?.addRetainedSizes(instances[i]!!)
        }
    }

    private inline fun forEachReachableInstance(crossinline visit: (Instance) -> Unit) =
        object : NonRecursiveVisitor() {
            override fun defaultAction(instance: Instance) {
                if (instance.isReachable) {
                    visit(instance)
                }
            }
        }.doVisit(gcRoots)

    fun getReachableInstances(): List<Instance> {
        val result = ArrayList<Instance>()
        forEachReachableInstance(result::add)
        return result
    }

    override fun <T> getRepresentation(asClass: Class<T>): T? = when {
        asClass.isAssignableFrom(javaClass) -> asClass.cast(this)
        else -> null
    }

    override fun getTypeName(): String = TYPE_NAME

    companion object {
        const val TYPE_NAME = "hprof"
        private const val JAVA_LANG_CLASS = "java.lang.Class"

        //  Special root object used in dominator computation for objects reachable via multiple roots.
        @JvmField val SENTINEL_ROOT: Instance = RootObj(RootType.UNKNOWN)
        private const val DEFAULT_HEAP_ID = 0

        @JvmOverloads @JvmStatic
        fun createSnapshot(
            buffer: DataBuffer,
            map: ProguardMap = ProguardMap(),
            postProcessors: List<SnapshotPostProcessor> = listOf(NativeRegistryPostProcessor())
        ): Snapshot =
            try {
                Snapshot(buffer).also { snapshot ->
                    HprofParser.parseBuffer(snapshot, buffer, map)
                    postProcessors.forEach { it.postProcess(snapshot) }
                }
            } catch (e: RuntimeException) {
                buffer.dispose()
                throw e
            }
    }
}
