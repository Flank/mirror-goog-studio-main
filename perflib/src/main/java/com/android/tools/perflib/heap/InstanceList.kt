/*
 * Copyright (C) 2022 The Android Open Source Project
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

/**
 * Internally, an InstanceList is either an instance (for length=1 case) or an array of instances.
 * This class is used to reduce overhead from indirection when there are lots of lists.
 *
 * This list only supports addition. Except for cases of lengths 0 and 1, operands to addition are
 * not meant to be aliased afterwards. For example, after `val list2 = list1 + instance`,
 * `list1` is not meant to be referenced and used.
 *
 * Because Kotlin doesn't support ad-hoc (untagged) union at the moment, we emulate it by
 * wrapping `Any` in a value type to avoid mis-uses, and export `onCases` as a type-safe
 * eliminator for the union.
 */
@JvmInline
value class InstanceList private constructor(val raw: Any /* Instance | Array<Instance?> */) {
    fun<A> onCases(onInstance: (Instance) -> A, onArray: (Array<Instance?>) -> A): A =
        when (raw) {
            is Instance -> onInstance(raw)
            is Array<*> -> onArray(raw as Array<Instance?>)
            else -> throw IllegalArgumentException("$raw")
        }

    fun asList(): List<Instance> = onCases(::listOf, { it.asInstanceList() })
    fun asInstanceSequence(): Sequence<Instance> = onCases(::sequenceOf, { it.instanceSequence() })

    /**
     * Returns a new list including the element. This instance is (conceptually) destroyed
     * afterwards and should not be used elsewhere.
     */
    operator fun plus(inst: Instance): InstanceList =
        onCases({ if (inst === raw) this else of(arrayOf(it, inst)) },
                { if (it.isEmpty()) of(inst) else of(it.plusElem(inst)) })

    companion object {
        val Empty = InstanceList(arrayOf<Instance>())
        fun of(elem: Instance) = InstanceList(elem)
        fun of(elems: Array<Instance?>) = InstanceList(elems)

        private fun Array<Instance?>.countInstances(): Int = when {
            isEmpty() -> 0
            else -> nextAvailableIndex()
        }

        private fun Array<Instance?>.instanceSequence(): Sequence<Instance> =
            asSequence().take(countInstances()) as Sequence<Instance>

        private fun Array<Instance?>.asInstanceList(): List<Instance> = when {
            isEmpty() -> listOf()
            else -> asList().subList(0, nextAvailableIndex()) as List<Instance>
        }

        private fun Array<Instance?>.plusElem(elem: Instance): Array<Instance?> = when {
            isEmpty() -> arrayOf(elem)
            last() != null -> arrayOfNulls<Instance>(size * 2).also { newElems ->
                copyInto(newElems)
                newElems[this.size] = elem
            }
            else -> this.also { this[nextAvailableIndex()] = elem }
        }

        /**
         * Find the first index to null, or the array's size if it's full
         */
        private fun Array<Instance?>.nextAvailableIndex(): Int {
            require(isNotEmpty()) { "requires non-empty array" }
            tailrec fun search(lo: Int, hi: Int): Int {
                val i = (lo + hi) / 2
                return when (this[i]) {
                    null -> when {
                        i == 0 || this[i-1] != null -> i
                        else -> search(lo, i - 1)
                    }
                    else -> when {
                        i+1 == this.size || this[i+1] == null -> i+1
                        else -> search(i+1, hi)
                    }
                }
            }
            return search(0, this.size - 1)
        }
    }
}
