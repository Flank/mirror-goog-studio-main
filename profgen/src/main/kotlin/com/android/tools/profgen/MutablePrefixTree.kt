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

internal class MutablePrefixTree<T> {
    private val root = Node<T>()

    fun put(prefix: String, value: T) {
        var node: Node<T> = root
        for (char in prefix) {
            node = node.children.getOrPut(char) { Node() }
        }
        node.values.add(value)
    }

    fun firstOrNull(key: String, fn: (T) -> Boolean): T? {
        var node: Node<T>? = root
        var i = 0
        while (node != null && i < key.length) {
            val value = node.values.firstOrNull(fn)
            if (value != null) return value
            node = node.children[key[i]]
            i++
        }
        // the node might not be null, we might have just hit the end of the prefix.
        // we still want to ensure that we hit all of the "values" below it.
        return node?.firstOrNull(fn)
    }

    private class Node<T> {
        val children = mutableMapOf<Char, Node<T>>()
        val values = mutableListOf<T>()

        fun firstOrNull(fn: (T) -> Boolean): T? {
            val stack = mutableListOf(this)
            while (stack.isNotEmpty()) {
                val node = stack.removeAt(0)
                val value = node.values.firstOrNull(fn)
                if (value != null) return value
                stack.addAll(node.children.values)
            }
            return null
        }
    }
}

