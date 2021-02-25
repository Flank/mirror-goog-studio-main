/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_TYPE
import com.android.SdkConstants.NEW_ID_PREFIX
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceMergerItem
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.XmlContext
import com.android.utils.iterator
import com.google.common.annotations.Beta
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import javax.xml.parsers.ParserConfigurationException

/**
 * A wrapper for an XML parser. This allows tools integrating lint
 * to map directly to builtin services, such as already-parsed data
 * structures in XML editors.
 *
 * **NOTE: This is not a public or final API; if you rely on this be
 * prepared to adjust your code for the next tools release.**
 */
@Beta
abstract class XmlParser {
    /**
     * Parse the given XML content and returns as a Document
     *
     * @param file the file to be parsed
     * @return the parsed DOM document
     */
    @Throws(IOException::class, SAXException::class, ParserConfigurationException::class)
    abstract fun parseXml(file: File): Document?

    /**
     * Parse the given XML string and return document, or null if any
     * error occurs (does **not** throw parsing exceptions). Most
     * clients should call [parseXml] instead.
     *
     * @param xml the parsing string
     * @param file the file corresponding to the XML string, **if
     *     known**. May be null.
     * @return the parsed DOM document, or null if parsing fails
     */
    abstract fun parseXml(xml: CharSequence, file: File): Document?

    /**
     * Parse the file pointed to by the given context and return as a
     * Document
     *
     * @param context the context pointing to the file to be parsed,
     *     typically via [Context.getContents] but the file
     *     handle ( [Context.file] can also be used to map to an
     *     existing editor buffer in the surrounding tool, etc)
     * @return the parsed DOM document, or null if parsing fails
     */
    abstract fun parseXml(context: XmlContext): Document?

    /**
     * Returns a [Location] for the given DOM node
     *
     * @param context information about the file being parsed
     * @param node the node to create a location for
     * @return a location for the given node
     */
    abstract fun getLocation(context: XmlContext, node: Node): Location

    /**
     * Attempt to create a location for a given XML node. Note that
     * since DOM does not normally provide offset information for nodes,
     * this doesn't work if you pass in a random DOM node from your own
     * parsing operations; you should only call this method with nodes
     * provided by lint in the first place (internally it uses a special
     * parser which tracks offset information.)
     *
     * @param file the file that contains the node that was parsed
     * @param node the node itself
     * @return a location for the node, if possible
     */
    abstract fun getLocation(file: File, node: Node): Location

    /**
     * Returns a [Location] for the given DOM node. Like [getLocation],
     * but allows a position range that is a subset of the node range.
     *
     * @param context information about the file being parsed
     * @param node the node to create a location for
     * @param start the starting position within the node, inclusive
     * @param end the ending position within the node, exclusive
     * @return a location for the given node
     */
    abstract fun getLocation(
        context: XmlContext,
        node: Node,
        start: Int,
        end: Int
    ): Location

    /**
     * Returns a [Location] for the given DOM node
     *
     * @param context information about the file being parsed
     * @param node the node to create a location for
     * @return a location for the given node
     */
    abstract fun getNameLocation(context: XmlContext, node: Node): Location

    /**
     * Returns a [Location] for the given DOM node
     *
     * @param context information about the file being parsed
     * @param node the node to create a location for
     * @return a location for the given node
     */
    abstract fun getValueLocation(context: XmlContext, node: Attr): Location

    /**
     * Create a location suitable for highlighting an element.
     *
     * In some cases, you want to point to an element (for example where
     * it is missing an attribute, so you can't point to the attribute
     * itself). However, some elements can span multiple lines. When
     * running in the IDE, you don't want the entire element range
     * to be highlighted. For an error on the root tag of a layout
     * for example, it would make the entire editor light up in red.
     *
     * In earlier versions, lint would special case [getLocation] for
     * elements and deliberate treat it as [getNameLocation] instead.
     * However, that's problematic since locations are not just used for
     * error highlighting, but also for features such as quickfixes,
     * where it's Very Very Bad™ to have the range magically change to
     * some subset.
     *
     * This method instead creates error ranges intended for warning
     * display purposes. If [node] is non null, the location for that
     * node will be used. Otherwise, if [attribute] is provided it
     * will highlight the given attribute range if the attribute is
     * specified. A common example of this is the "name" attribute in
     * resource values. If not passed in or not defined on the element,
     * this method will use the element range if it fits on a single
     * line; otherwise it will use just the tag name range.
     */
    fun getElementLocation(
        context: XmlContext,
        element: Element,
        node: Node? = null,
        namespace: String? = null,
        attribute: String? = null
    ): Location {
        if (node != null) {
            return getLocation(context, node)
        }

        if (attribute != null) {
            val attr = if (namespace != null) {
                element.getAttributeNodeNS(namespace, attribute)
            } else {
                element.getAttributeNode(attribute)
            }
            if (attr != null) {
                return getLocation(context, attr)
            }
        }

        val location = getLocation(context, element)
        if (location.isSingleLine()) {
            return location
        }

        return getNameLocation(context, element)
    }

    /**
     * Creates a light-weight handle to a location for the given
     * node. It can be turned into a full fledged location by
     * [com.android.tools.lint.detector.api.Location.Handle.resolve].
     *
     * @param context the context providing the node
     * @param node the node (element or attribute) to create a location
     *     handle for
     * @return a location handle
     */
    abstract fun createLocationHandle(
        context: XmlContext,
        node: Node
    ): Location.Handle

    /**
     * Returns the start offset of the given node, or -1 if not known
     *
     * @param context the context providing the node
     * @param node the node (element or attribute) to create a location
     *     handle for
     * @return the start offset, or -1 if not known
     */
    abstract fun getNodeStartOffset(context: XmlContext, node: Node): Int

    /**
     * Returns the end offset of the given node, or -1 if not known
     *
     * @param context the context providing the node
     * @param node the node (element or attribute) to create a location
     *     handle for
     * @return the end offset, or -1 if not known
     */
    abstract fun getNodeEndOffset(context: XmlContext, node: Node): Int

    /**
     * Returns the leaf node at the given offset (biased towards the
     * right), or null if not found
     *
     * @param offset the offset to search at
     * @return the leaf node, if any
     */
    abstract fun findNodeAt(context: XmlContext, offset: Int): Node?

    /**
     * Returns a [Location] for the given DOM node
     *
     * @return a location for the given node
     */
    abstract fun getLocation(client: LintClient, file: File, node: Node): Location

    /**
     * Returns the start offset of the given node, or -1 if not known
     *
     * @return the start offset, or -1 if not known
     */
    abstract fun getNodeStartOffset(client: LintClient, file: File, node: Node): Int

    /**
     * Returns the end offset of the given node, or -1 if not known
     *
     * @return the end offset, or -1 if not known
     */
    abstract fun getNodeEndOffset(client: LintClient, file: File, node: Node): Int

    /**
     * Returns a [Location] for the given DOM node
     *
     * @return a location for the given node
     */
    abstract fun getNameLocation(client: LintClient, file: File, node: Node): Location

    /**
     * Returns a [Location] for the given DOM node
     *
     * @return a location for the given node
     */
    abstract fun getValueLocation(client: LintClient, file: File, node: Attr): Location

    /**
     * Attempt to find the location of the given resource [item] for
     * the given [client]. This will return the location for the entire
     * element; see [getNameLocation] and [getValueLocation] to pick out
     * more specific ranges. (For file-based resources, the location
     * points to the first line of the file.)
     */
    fun getLocation(
        client: LintClient,
        item: ResourceItem
    ): Location? {
        return getLocation(client, item, false, false)
    }

    private fun getLocation(
        client: LintClient,
        item: ResourceItem,
        nameOnly: Boolean = false,
        valueOnly: Boolean = false
    ): Location? {
        val file = item.getFile() ?: return null
        if (item.isFileBased) {
            // Just point to the file -- that's easy
            return Location.create(file)
        }

        // For elements and attributes we have to work harder
        val text = client.readFile(file)
        val name = item.name
        val type = item.type

        // Id's in non-values files are different
        if (type == ResourceType.ID && text.contains(NEW_ID_PREFIX)) {
            return Location.create(file, text, -1, "$NEW_ID_PREFIX/$name", null, null)
        }

        // Parse XML document and search for the right target element
        val document = client.getXmlDocument(file, text)?.documentElement ?: return null
        for (element in document) {
            if (element.getAttribute(ATTR_NAME) != name) {
                continue
            }

            val tag = element.tagName
            if (tag == type.getName() || element.getAttribute(ATTR_TYPE) == type.getName()) {
                if (nameOnly) {
                    element.getAttributeNode(ATTR_NAME)?.let {
                        return getNameLocation(client, file, it)
                    }
                } else if (valueOnly) {
                    val firstChild = element.firstChild
                    val lastChild = element.lastChild
                    if (firstChild != null) {
                        val startLocation = getLocation(file, firstChild)
                        if (lastChild === firstChild) {
                            // Single child (the common case): we have the whole range
                            // already
                            return startLocation
                        }
                        val endLocation = getLocation(file, lastChild)
                        if (startLocation.start != null && endLocation.end != null) {
                            return Location.create(file, startLocation.start, endLocation.end)
                        }
                    }
                }
                return getLocation(client, file, element)
            }
        }

        return null
    }

    /**
     * Attempt to find the location of the given resource [item] for the
     * given [client], and in particular, just the name part.
     */
    fun getNameLocation(client: LintClient, item: ResourceItem): Location? {
        return getLocation(client, item, nameOnly = true)
    }

    /**
     * Attempt to find the location of the given resource [item] for the
     * given [client], and in particular, just the value part.
     */
    fun getValueLocation(client: LintClient, item: ResourceItem): Location? {
        return getLocation(client, item, valueOnly = true)
    }

    fun ResourceItem.getFile(): File? {
        // TODO: Check how to do this most efficiently in the IDE
        return if (this is ResourceMergerItem) {
            file
        } else {
            source?.toFile()
        }
    }
}
