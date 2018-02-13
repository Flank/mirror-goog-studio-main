/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.layoutinspector.model

import com.google.common.base.Charsets
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import java.awt.Rectangle
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.Collections
import java.util.Enumeration
import java.util.LinkedList
import java.util.Stack
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

class ViewNode// defaults in case properties are not available
(parent: ViewNode?, data: String) : TreeNode {

    val parent: ViewNode? = parent
        @JvmName("getParent_") get() = field // Work around for conflict with TreeNode's getParent method

    val name: String
    val groupedProperties: MutableMap<String, MutableList<ViewProperty>>
    val namedProperties: MutableMap<String, ViewProperty>
    val properties: MutableList<ViewProperty>
    val children: MutableList<ViewNode>
    val index: Int
    val hashCode: String
    val id: String

    val displayInfo: DisplayInfo
    val previewBox: Rectangle

    var isParentVisible: Boolean = false
        private set
    var isDrawn: Boolean = false
        private set

    var forcedState: ForcedState

    // If the force state is set, the preview tries to render/hide the view
    // (depending on the parent's state)
    enum class ForcedState {
        NONE,
        VISIBLE,
        INVISIBLE
    }

    private fun loadProperties(data: String) {
        var start = 0
        var stop: Boolean

        do {
            val index = data.indexOf('=', start)
            val property = ViewProperty(data.substring(start, index))

            val index2 = data.indexOf(',', index + 1)
            val length = Integer.parseInt(data.substring(index + 1, index2))
            start = index2 + 1 + length
            property.value = data.substring(index2 + 1, index2 + 1 + length)

            properties.add(property)
            namedProperties.put(property.fullName, property)

            addPropertyToGroup(property)

            stop = start >= data.length
            if (!stop) {
                start += 1
            }
        } while (!stop)

        Collections.sort(properties)
    }

    private fun addPropertyToGroup(property: ViewProperty) {
        val key = getKey(property)
        val propertiesList = groupedProperties.getOrDefault(
                key,
                LinkedList()
        )
        propertiesList.add(property)
        groupedProperties.put(key, propertiesList)
    }

    private fun getKey(property: ViewProperty): String {
        return property.category ?: if (property.fullName.endsWith("()")) {
            "methods"
        } else {
            "properties"
        }
    }

    fun getProperty(name: String, vararg altNames: String): ViewProperty? {
        var property: ViewProperty? = namedProperties[name]
        var i = 0
        while (property == null && i < altNames.size) {
            property = namedProperties[altNames[i]]
            i++
        }
        return property
    }

    /** Recursively updates all the visibility parameter of the nodes.  */
    fun updateNodeDrawn() {
        updateNodeDrawn(isParentVisible)
    }

    private fun updateNodeDrawn(parentVisible: Boolean) {
        var parentVisible = parentVisible
        isParentVisible = parentVisible
        if (forcedState == ForcedState.NONE) {
            isDrawn = !displayInfo.willNotDraw && parentVisible && displayInfo.isVisible
            parentVisible = parentVisible and displayInfo.isVisible
        } else {
            isDrawn = forcedState == ForcedState.VISIBLE && parentVisible
            parentVisible = isDrawn
        }
        for (child in children) {
            child.updateNodeDrawn(parentVisible)
            isDrawn = isDrawn or (child.isDrawn && child.displayInfo.isVisible)
        }
    }

    override fun toString(): String {
        return name + "@" + hashCode
    }

    override fun getChildAt(childIndex: Int): ViewNode {
        return children[childIndex]
    }

    override fun getChildCount(): Int {
        return children.size
    }

    override fun getParent(): ViewNode? {
        return parent
    }

    override fun getIndex(node: TreeNode): Int {
        return children.indexOf(node as ViewNode)
    }

    override fun getAllowsChildren(): Boolean {
        return true
    }

    override fun isLeaf(): Boolean {
        return childCount == 0
    }

    override fun children(): Enumeration<*> {
        return Collections.enumeration(children)
    }

    companion object {

        /** Parses the flat string representation of a view node and returns the root node.  */
        @JvmStatic
        fun parseFlatString(bytes: ByteArray): ViewNode? {
            var root: ViewNode? = null
            var lastNode: ViewNode? = null
            var lastWhitespaceCount = Integer.MIN_VALUE
            val stack = Stack<ViewNode>()

            val input = BufferedReader(
                    InputStreamReader(ByteArrayInputStream(bytes), Charsets.UTF_8)
            )
            try {
                for (line in input.lines()) {
                    if ("DONE.".equals(line, ignoreCase = true)) {
                        break
                    }
                    var whitespaceCount = 0
                    while (line[whitespaceCount] == ' ') {
                        whitespaceCount++
                    }

                    if (lastWhitespaceCount < whitespaceCount) {
                        stack.push(lastNode)
                    } else if (!stack.isEmpty()) {
                        val count = lastWhitespaceCount - whitespaceCount
                        for (i in 0 until count) {
                            stack.pop()
                        }
                    }

                    lastWhitespaceCount = whitespaceCount
                    var parent: ViewNode? = null
                    if (!stack.isEmpty()) {
                        parent = stack.peek()
                    }
                    lastNode = ViewNode(parent, line.trim { it <= ' ' })
                    if (root == null) {
                        root = lastNode
                    }
                }
            } catch (e: IOException) {
                return null
            }

            if (root != null) {
                root.updateNodeDrawn(true)
            }
            return root
        }

        /** Finds the path from node to the root.  */
        @JvmStatic
        fun getPath(node: ViewNode): TreePath {
            return getPathImpl(node, null)
        }

        /** Finds the path from node to the parent.  */
        @JvmStatic
        fun getPathFromParent(node: ViewNode, root: ViewNode): TreePath {
            return getPathImpl(node, root)
        }

        private fun getPathImpl(node: ViewNode, root: ViewNode?): TreePath {
            var node : ViewNode? = node
            val nodes = Lists.newArrayList<Any>()
            do {
                nodes.add(0, node)
                node = node?.parent
            } while (node != null && node !== root)
            if (root != null && node === root) {
                nodes.add(0, root)
            }
            return TreePath(nodes.toTypedArray())
        }
    }

    init {
        this.groupedProperties = Maps.newHashMap()
        this.namedProperties = Maps.newHashMap()
        this.properties = Lists.newArrayList()
        this.children = Lists.newArrayList()
        this.previewBox = Rectangle()
        this.forcedState = ForcedState.NONE
        var data = data
        index = if (this.parent == null) 0 else this.parent!!.children.size
        if (this.parent != null) {
            this.parent!!.children.add(this)
        }
        var delimIndex = data.indexOf('@')
        if (delimIndex < 0) {
            throw IllegalArgumentException("Invalid format for ViewNode, missing @: " + data)
        }
        name = data.substring(0, delimIndex)
        data = data.substring(delimIndex + 1)
        delimIndex = data.indexOf(' ')
        hashCode = data.substring(0, delimIndex)
        if (data.length > delimIndex + 1) {
            loadProperties(data.substring(delimIndex + 1).trim { it <= ' ' })
            id = getProperty("mID", "id")!!.value!!
        } else {
            // defaults in case properties are not available
            id = "unknown"
        }
        displayInfo = DisplayInfo(this)
    }

}
