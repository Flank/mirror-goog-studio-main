/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.res.shrinker.util

import com.android.aapt.Resources
import java.nio.file.Files
import java.nio.file.Path

internal fun xmlElement(tag: String, namespaceUri: String = "") =
    Resources.XmlElement.newBuilder()
        .setName(tag)
        .setNamespaceUri(namespaceUri)

internal fun Resources.XmlElement.Builder.setText(text: String) =
    this.addChild(Resources.XmlNode.newBuilder().setText(text))

internal fun Resources.XmlElement.Builder.addChild(child: Resources.XmlElement.Builder) =
    this.addChild(Resources.XmlNode.newBuilder().setElement(child))

internal fun Resources.XmlElement.Builder.addNamespace(prefix: String, uri: String) =
    this.addNamespaceDeclaration(
        Resources.XmlNamespace.newBuilder()
            .setPrefix(prefix)
            .setUri(uri)
    )

internal fun Resources.XmlElement.Builder.addAttribute(
    name: String,
    namespaceUri: String = "",
    value: String = "",
    refId: Long? = null
): Resources.XmlElement.Builder {
    val attribute = Resources.XmlAttribute.newBuilder()
        .setNamespaceUri(namespaceUri)
        .setName(name)
        .setValue(value)
    refId?.let {
        val name = value.trimStart('@', '+')
        attribute.setCompiledItem(
            Resources.Item.newBuilder()
                .setRef(Resources.Reference.newBuilder().setName(name).setId(it.toInt()))
        )
    }
    return this.addAttribute(attribute)
}

internal fun Resources.XmlElement.Builder.addAttributeWithRefNameOnly(
    name: String,
    refName: String = ""
) =
    this.addAttribute(
        Resources.XmlAttribute.newBuilder()
            .setNamespaceUri(namespaceUri)
            .setName(name)
            .setValue(refName)
            .setCompiledItem(
                Resources.Item.newBuilder()
                    .setRef(Resources.Reference.newBuilder().setName(refName))
            )
    )

internal fun Resources.XmlElement.Builder.buildNode() =
    Resources.XmlNode.newBuilder().setElement(this).build()

internal fun Resources.XmlElement.Builder.writeToFile(path: Path, createDirs: Boolean = true) {
    Files.createDirectories(path.parent)
    Files.write(path, buildNode().toByteArray())
}

internal fun Resources.XmlNode.toXmlString(): String {
    val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
    renderXml(
        this,
        mapOf("" to ""),
        sb
    )
    return sb.toString()
}

private fun renderXml(node: Resources.XmlNode, namespaces: Map<String, String>, sb: StringBuilder) {
    when {
        node.hasElement() -> renderXml(
            node.element,
            namespaces,
            sb
        )
        else -> sb.append(node.text)
    }
}

private fun renderXml(element: Resources.XmlElement, previousNamespaces: Map<String, String>, sb: StringBuilder) {
    val namespaces = previousNamespaces +
            element.namespaceDeclarationList.map { Pair(it.uri, it.prefix) }

    sb.append("<")
    renderName(
        element.namespaceUri,
        element.name,
        namespaces,
        sb
    )
    sb.append(" ")
    for (namespace in element.namespaceDeclarationList) {
        sb.append("xmlns:").append(namespace.prefix).append("=\"").append(namespace.uri).append("\" ")
    }
    for (attribute in element.attributeList) {
        renderName(
            attribute.namespaceUri,
            attribute.name,
            namespaces,
            sb
        )
        sb.append("=\"").append(attribute.value).append("\" ")
    }
    sb.append(">")

    element.childList.forEach {
        renderXml(
            it,
            namespaces,
            sb
        )
    }

    sb.append("</")
    renderName(
        element.namespaceUri,
        element.name,
        namespaces,
        sb
    )
    sb.append(">")
}

private fun renderName(namespaceUri: String, name: String, namespaces: Map<String, String>, sb: StringBuilder) {
    sb.append(namespaces[namespaceUri])
    if (namespaceUri.isNotEmpty()) {
        sb.append(":")
    }
    sb.append(name)
}
