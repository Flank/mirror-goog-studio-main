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

package com.android.layoutinspector.parser

import com.android.layoutinspector.ProtocolVersion
import com.android.layoutinspector.model.ViewNode
import com.google.common.base.Charsets
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.Stack

object ViewNodeParser {
    /** Parses the flat string representation of a view node and returns the root node.  */
    @JvmStatic
    @JvmOverloads
    fun parse(
        bytes: ByteArray,
        version: ProtocolVersion = ProtocolVersion.Version1
    ): ViewNode? {
        return when (version) {
            ProtocolVersion.Version1 -> parseV1ViewNode(bytes)
            ProtocolVersion.Version2 -> parseV2ViewNode(bytes)
        }
    }

    private fun parseV2ViewNode(bytes: ByteArray): ViewNode? {
        return ViewNodeV2Parser().parse(bytes)
    }

    private fun parseV1ViewNode(bytes: ByteArray): ViewNode? {
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
                lastNode = createViewNode(parent, line.trim { it <= ' ' })
                if (root == null) {
                    root = lastNode
                }
            }
        } catch (e: IOException) {
            return null
        }

        root?.updateNodeDrawn(true)
        return root
    }

    private fun createViewNode(parent: ViewNode?, data: String): ViewNode {
        var data = data
        var delimIndex = data.indexOf('@')
        if (delimIndex < 0) {
            throw IllegalArgumentException("Invalid format for ViewNode, missing @: " + data)
        }
        var name = data.substring(0, delimIndex)
        data = data.substring(delimIndex + 1)
        delimIndex = data.indexOf(' ')
        val hash = data.substring(0, delimIndex)
        val node = ViewNode(parent, name, hash)
        node.index = if (parent == null) 0 else parent!!.children.size

        if (data.length > delimIndex + 1) {
            loadProperties(node, data.substring(delimIndex + 1).trim { it <= ' ' })
            node.id = node.getProperty("mID", "id")!!.value
        }
        node.setup()
        parent?.let {
            it.children.add(node)
        }
        return node
    }

    private fun loadProperties(node: ViewNode, data: String) {
        var start = 0
        var stop: Boolean

        do {
            val index = data.indexOf('=', start)
            val fullName = data.substring(start, index)

            val index2 = data.indexOf(',', index + 1)
            val length = Integer.parseInt(data.substring(index + 1, index2))
            start = index2 + 1 + length

            val value = data.substring(index2 + 1, index2 + 1 + length)
            val property = ViewPropertyParser.parse(fullName, value)

            node.properties.add(property)
            node.namedProperties[property.fullName] = property

            node.addPropertyToGroup(property)

            stop = start >= data.length
            if (!stop) {
                start += 1
            }
        } while (!stop)

        node.properties.sort()
    }
}
