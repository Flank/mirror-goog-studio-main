/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.lint.client.api

import com.android.manifmerger.Actions
import com.android.manifmerger.ManifestModel
import com.android.manifmerger.XmlNode
import com.android.utils.Pair
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.google.common.io.Files
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

class BlameFile internal constructor(
    private val nodes: MutableMap<String, BlameNode>,
    private val actions: Actions?
) {

    private fun findBlameNode(element: Element): BlameNode? {
        val key = getNodeKey(element)
        var blameNode: BlameNode? = nodes[key]

        if (blameNode == null && actions != null) {
            val nodeKey = XmlNode.NodeKey.fromXml(element, model)
            val records = actions.getNodeRecords(nodeKey)
            for (record in records) {
                val actionType = record.actionType
                if (actionType == Actions.ActionType.ADDED || actionType == Actions.ActionType.MERGED) {
                    if (blameNode == null) {
                        blameNode = BlameNode(key)
                        nodes[key] = blameNode
                    }
                    val actionLocation = record.actionLocation
                    val sourceFile = actionLocation.file.sourceFile
                    if (sourceFile != null) {
                        blameNode.elementLocation = " from " + sourceFile.path
                    }
                }
            }
            for (nodeName in actions.getRecordedAttributeNames(nodeKey)) {
                for (
                    record in actions
                        .getAttributeRecords(nodeKey, nodeName)
                ) {
                    val actionType = record.actionType
                    if (actionType == Actions.ActionType.ADDED || actionType == Actions.ActionType.MERGED) {
                        if (blameNode == null) {
                            blameNode = BlameNode(key)
                            nodes[key] = blameNode
                        }
                        val actionLocation = record.actionLocation
                        val sourceFile = actionLocation.file.sourceFile
                        if (sourceFile != null) {
                            blameNode.setAttributeLocations(
                                nodeName.localName,
                                " from " + sourceFile.path
                            )
                        }
                    }
                }
            }
        }

        return blameNode
    }

    fun findSourceNode(client: LintClient, node: Node): Pair<File, out Node>? {
        return when (node) {
            is Attr -> findSourceAttribute(client, node)
            is Element -> findSourceElement(client, node)
            else -> null
        }
    }

    fun findSourceElement(
        client: LintClient,
        element: Element
    ): Pair<File, Node>? {
        val source = findElementOrAttribute(client, element, null)
        return if (source != null && source.second is Element) {
            source
        } else null
    }

    fun findSourceAttribute(client: LintClient, attr: Attr): Pair<File, out Node>? {
        val element = attr.ownerElement
        val source = findElementOrAttribute(client, element, attr)
        if (source != null && source.second is Attr) {
            return source
        } else if (source != null && source.second is Element) {
            val sourceElement = source.second as Element
            return if (attr.prefix != null) {
                val namespace = attr.namespaceURI
                val localName = attr.localName
                val sourceAttribute = sourceElement.getAttributeNodeNS(namespace, localName)
                if (sourceAttribute != null) {
                    Pair.of(source.first, sourceAttribute)
                } else null
            } else {
                val sourceAttribute = sourceElement.getAttributeNode(attr.name)
                if (sourceAttribute != null) {
                    Pair.of(source.first, sourceAttribute)
                } else null
            }
        }

        return null
    }

    private fun findElementOrAttribute(
        client: LintClient,
        element: Element,
        attribute: Attr?
    ): Pair<File, Node>? {
        val blameNode = findBlameNode(element) ?: return null

        var location: String? = null
        if (attribute != null) {
            location = blameNode.getAttributeLocation(attribute.name)
            if (location == null) {
                location = blameNode.getAttributeLocation(attribute.localName)
            }
            // If null use element location instead
        }

        if (location == null) {
            location = blameNode.elementLocation
        }
        if (location == null) {
            return null
        }

        var index = location.indexOf(" from ")
        if (index == -1) {
            return null
        }
        index += " from ".length

        if (location.startsWith("[", index)) {
            // Library name included
            index = location.indexOf("] ")
            if (index == -1) {
                return null
            }
            index += 2
        }

        var range = location.length
        while (range > 0) {
            val c = location[range - 1]
            if (c != ':' && c != '-' && !Character.isDigit(c)) {
                break
            }
            range--
        }

        val path = location.substring(index, range)
        val manifest = File(path)
        if (!manifest.isFile) {
            return null
        }

        // We're using lint's XML parser here, not something simple
        // like XmlUtils#parseDocument since we'll typically be queried
        // for locations; it's the main use case for resolving
        // merged nodes back to their sources
        val parser = client.xmlParser
        val document: Document?
        try {
            document = parser.parseXml(manifest)
            if (document == null) {
                return null
            }
        } catch (ignore: Throwable) {
            return null
        }

        val targetKey = blameNode.key

        // We have several options here; one is to use the location ranges
        // listed by the manifest merger. The big downside with that is that
        // it's not very accurate, and only gives line numbers and offsets.
        // We typically want to find the *actual* DOM node, not just its general
        // offset range (such that we can perform additional range math on
        // the source node, such as producing sub ranges (just the name portion
        // etc.)
        //
        // The alternative is to visit the source document and match up the node
        // keys. That's what we're doing below.

        val reference = AtomicReference<Element>()
        XmlVisitor.accept(
            document,
            object : XmlVisitor() {
                override fun visitTag(element: Element, tag: String): Boolean {
                    val key = getNodeKey(element)
                    if (targetKey == key) {
                        reference.set(element)
                        return true
                    }

                    return false
                }
            }
        )
        return Pair.of(manifest, reference.get())
    }

    // TODO: Make tag visitor, node visitor, attribute visitor, etc
    // such that I don't need to visit all attributes when not considered etc
    abstract class XmlVisitor {

        open fun visitTag(element: Element, tag: String): Boolean {
            return false
        }

        open fun visitAttribute(attribute: Attr): Boolean {
            return false
        }

        private fun visit(node: Node): Boolean {
            if (node.nodeType == Node.ELEMENT_NODE) {
                val tag = node as Element
                if (visitTag(tag, tag.localName)) {
                    return true
                }

                val attributes = tag.attributes
                var i = 0
                val n = attributes.length
                while (i < n) {
                    val attr = attributes.item(i)
                    if (visitAttribute(attr as Attr)) {
                        return true
                    }
                    i++
                }
            }

            var child: Node? = node.firstChild
            while (child != null) {
                if (visit(child)) {
                    return true
                }
                child = child.nextSibling
            }

            return false
        }

        companion object {

            fun accept(node: Node, visitor: XmlVisitor) {
                visitor.visit(node)
            }
        }
    }

    /** Represents a node in a manifest merger blame file (for example, in a typical
     * Gradle project, `app/build/outputs/logs/manifest-merger-debug-report.txt`.  */
    internal class BlameNode(val key: String) {
        var elementLocation: String? = null
        private var attributeLocations: MutableList<Pair<String, String>>? = null

        fun getAttributeLocation(name: String): String? {
            if (attributeLocations != null) {
                for (pair in attributeLocations!!) {
                    if (name == pair.first) {
                        return pair.second
                    }
                }
            }
            return null
        }

        fun setAttributeLocations(name: String, location: String) {
            if (attributeLocations != null) {
                // Locations are always adjacent, so it will always be the last one
                if (name == attributeLocations!![attributeLocations!!.size - 1].first) {
                    attributeLocations!!.removeAt(attributeLocations!!.size - 1)
                }
            } else {
                attributeLocations = Lists.newArrayList()
            }

            attributeLocations!!.add(Pair.of(name, location))
        }
    }

    companion object {
        val NONE = BlameFile(mutableMapOf(), null)
        private val model = ManifestModel()

        private fun getNodeKey(element: Element): String {
            // This unfortunately doesn't work well because in the merged manifest we'll
            // have fully qualified names, e.g.
            //    activity#com.google.myapplication.MainActivity
            // and in the source manifest files we may not, e.g.
            //    activity#.MainActivity
            //
            // (I've actually just patched the key lookup to produce
            // qualified names. If that's not acceptable in the manifest merger,
            // the alternative is to duplicate the naming logic here.)
            return XmlNode.NodeKey.fromXml(element, model).toString()
        }

        @Throws(IOException::class)
        fun parse(file: File): BlameFile {
            val lines = Files.readLines(file, Charsets.UTF_8)
            return parse(lines)
        }

        fun parse(mergerActions: Actions): BlameFile {
            val nodes = Maps.newHashMapWithExpectedSize<String, BlameNode>(80)
            return BlameFile(nodes, mergerActions)
        }

        fun parse(lines: List<String>): BlameFile {
            val nodes = Maps.newHashMapWithExpectedSize<String, BlameNode>(80)

            var last: BlameNode? = null
            var attributeName: String? = null
            for (line in lines) {
                if (line.isEmpty()) {
                    continue
                }
                val indent = getIndent(line)
                if (line.startsWith("INJECTED ", indent)) {
                    // Ignore injected attributes: coming from Gradle or merger: no corresponding
                    // source location (at least not in the manifest, and the merger doesn't model
                    // Gradle source code)
                    continue
                }
                if (line.startsWith("ADDED ", indent) || line.startsWith("MERGED ", indent)) {
                    if (last != null) {
                        if (indent > 0) {
                            // Indented: it's an attribute
                            assert(attributeName != null)
                            last.setAttributeLocations(attributeName!!, line.trim { it <= ' ' })
                        } else if (last.elementLocation == null) {
                            last.elementLocation = line.trim { it <= ' ' }
                        }
                    }
                    continue
                } else if (line.startsWith("--")) {
                    continue
                }

                if (indent > 0) {
                    attributeName = line.trim { it <= ' ' }
                    continue
                }

                val key = line.trim { it <= ' ' }
                val node = BlameNode(key)
                nodes[key] = node
                attributeName = null
                last = node
            }

            return BlameFile(nodes, null)
        }

        private fun getIndent(line: String): Int {
            for (i in 0 until line.length) {
                val c = line[i]
                if (c != '\t') {
                    return i
                }
            }
            return line.length
        }
    }
}
