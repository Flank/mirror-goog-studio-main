/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

@file: JvmName("SymbolUtils")

package com.android.ide.common.symbols

import com.android.SdkConstants
import com.android.ide.common.xml.AndroidManifestParser
import com.android.ide.common.xml.ManifestData
import com.android.io.FileWrapper
import com.android.resources.ResourceType
import com.android.xml.AndroidManifest
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.CharMatcher
import com.google.common.base.Splitter
import com.google.common.collect.HashMultimap
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Lists
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import java.util.HashMap
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

/** Helper methods related to Symbols and resource processing. */

private val NORMALIZED_VALUE_NAME_CHARS =
    CharMatcher.anyOf(".:").precomputed()

fun mergeAndRenumberSymbols(
    mainPackageName: String,
    librarySymbols: SymbolTable,
    dependencySymbols: Collection<SymbolTable>,
    platformSymbols: SymbolTable
): SymbolTable {

    /*
     For most symbol types, we are simply going to loop on all the symbols, and merge them in
     the final table while renumbering them.
     For Styleable arrays we will handle things differently. We cannot rely on the array values,
     as some R.txt were published with dummy values. We are instead simply going to merge
     the children list from all the styleable, and create the symbol from this list.
    */

    // Merge the library symbols into the same collection as the dependencies. There's no
    // order or preference, and this allows just looping on them all
    val tables = ArrayList<SymbolTable>(dependencySymbols.size + 1)
    tables.add(librarySymbols)
    tables.addAll(dependencySymbols)

    // the ID value provider.
    val idProvider = IdProvider.sequential()

    // first pass, we use two different multi-map to record all symbols.
    // 1. resourceType -> name. This is for all by the Styleable symbols
    // 2. styleable name -> children. This is for styleable only.
    val newSymbolMap = HashMultimap.create<ResourceType, String>()
    val arrayToAttrs = HashMap<String, MutableSet<String>>()

    tables.forEach { table ->
        table.symbols.values().forEach { symbol ->
            when (symbol) {
                is Symbol.NormalSymbol -> newSymbolMap.put(symbol.resourceType, symbol.name)
                is Symbol.StyleableSymbol -> {
                    arrayToAttrs
                        .getOrPut(symbol.name) { HashSet() }
                        .addAll(symbol.children)
                }
                else -> throw IOException("Unexpected symbol $symbol")
            }
        }
    }

    // the builder for the table
    val tableBuilder = SymbolTable.builder().tablePackage(mainPackageName)

    // let's keep a map of the new ATTR names to symbol so that we can find them easily later
    // when we process the styleable
    val attrToValue = HashMap<String, Symbol.NormalSymbol>()

    // process the normal symbols
    for (resourceType in newSymbolMap.keySet()) {
        val symbolNames = Lists.newArrayList(newSymbolMap.get(resourceType))
        symbolNames.sort()

        for (symbolName in symbolNames) {
            val value = idProvider.next(resourceType)
            val newSymbol =
                    Symbol.NormalSymbol(
                            resourceType = resourceType,
                            name = symbolName,
                            intValue = value
                    )
            tableBuilder.add(newSymbol)

            if (resourceType == ResourceType.ATTR) {
                // store the new ATTR value in the map
                attrToValue[symbolName] = newSymbol
            }
        }
    }

    // process the arrays.
    arrayToAttrs.forEach { arrayName, children ->
        val attributes = children.sorted()

        // now get the attributes values using the new symbol map
        val attributeValues = ImmutableList.builder<Int>()
        for (attribute in attributes) {
            if (attribute.startsWith(SdkConstants.ANDROID_NS_NAME_PREFIX)) {
                val name = attribute.substring(SdkConstants.ANDROID_NS_NAME_PREFIX_LEN)

                val platformSymbol =
                    platformSymbols.symbols.get(ResourceType.ATTR, name) as Symbol.NormalSymbol?
                if (platformSymbol != null) {
                    attributeValues.add(platformSymbol.intValue)
                }
            } else {
                val symbol = attrToValue[attribute]
                if (symbol != null) {
                    // symbol can be null if the symbol table is broken. This is possible
                    // some non-final AAR built with non final Gradle.
                    // e.g.  com.android.support:appcompat-v7:26.0.0-beta2
                    attributeValues.add(symbol.intValue)
                }
            }
        }

        tableBuilder.add(
            Symbol.StyleableSymbol(
                arrayName,
                attributeValues.build(),
                ImmutableList.copyOf(attributes)
            )
        )
    }

    return tableBuilder.build()
}

/**
 * Load symbol tables of each library on which the main library/application depends on.
 *
 * @param libraries libraries which the main library/application depends on
 * @return a set of of symbol table for each library
 */
@Throws(IOException::class)
fun loadDependenciesSymbolTables(libraries: Iterable<File>): ImmutableSet<SymbolTable> {
    return  ImmutableSet.builder<SymbolTable>().apply {
        for (dependency in libraries) {
            add(SymbolIo.readSymbolListWithPackageName(dependency.toPath()))
        }
    }.build()
}

/**
 * Pulls out the package name from the given android manifest.
 *
 * @param manifestFile manifest file of the library
 * @return package name held in the manifest
 * @throws IOException if there is a problem reading the manifest or if the manifest does not
 *     contain a package name
 */
@Throws(IOException::class)
fun getPackageNameFromManifest(manifestFile: File): String {
    val manifestData = try {
        AndroidManifestParser.parse(FileWrapper(manifestFile))
    } catch (e: SAXException) {
        throw IOException(
            "Failed to parse android manifest XML file at path: '${manifestFile.absolutePath}'",
            e
        )
    } catch (e: IOException) {
        throw IOException(
            "Failed to parse android manifest XML file at path: '${manifestFile.absolutePath}'",
            e
        )
    }
    return manifestData.`package`
}

/**
 * Pulls out the package name from the given parsed android manifest.
 *
 * @param manifest the parsed manifest of the library
 * @return package name held in the manifest
 */
fun getPackageNameFromManifest(manifest: ManifestData): String = manifest.`package`

/**
 * Generates keep rules based on the nodes declared in the manifest file.
 *
 * <p>Used in the new resource processing, since aapt is not used in processing libraries'
 * resources and the {@code aapt_rules.txt} file and its rules are required by minify.
 *
 * <p>Goes through all {@code application}, {@code instrumentation}, {@code activity}, {@code
 * service}, {@code provider} and {@code receiver} keep class data in the manifest, generates
 * keep rules for each of them and returns them as a list.
 *
 * <p>For examples refer to {@code SymbolUtilsTest.java}.
 *
 * @param manifest containing keep class data
 */
fun generateMinifyKeepRules(manifest: ManifestData, mergedResources: File?): List<String> {
    return generateKeepRules(manifest, false, mergedResources)
}

@VisibleForTesting
fun generateKeepRules(
    manifest: ManifestData,
    isMainDex: Boolean,
    mergedResources: File?
): List<String> {
    val rules = ArrayList<String>()
    rules.add("# Generated by the gradle plugin")

    // Find all the rules based on the AndroidManifest
    for (keepClass in manifest.keepClasses) {
        if (isMainDex) {
            // When creating keep rules for Dex, we should sometimes omit some activity, service
            // provider and receiver nodes. It is based on the process declared in their node or,
            // if none was specified or was empty, on the default process of the application.
            // If the process was not declared, was empty or starts with a colon symbol (last
            // case meaning private process), we do not need to keep that class.
            val type = keepClass.type
            val process = keepClass.process
            if ((type == AndroidManifest.NODE_ACTIVITY
                        || type == AndroidManifest.NODE_SERVICE
                        || type == AndroidManifest.NODE_PROVIDER
                        || type == AndroidManifest.NODE_RECEIVER)
                && (process == null || process.isEmpty() || process.startsWith(":"))) {
                continue
            }
        }
        rules.add(String.format("-keep class %s { <init>(...); }", keepClass.name))
    }

    // Now go through all the layout files and find classes that need to be kept
    if (mergedResources != null) {
        try {
            val documentBuilderFactory =
                DocumentBuilderFactory.newInstance()
            val documentBuilder = documentBuilderFactory.newDocumentBuilder()

            for (typeDir in mergedResources.listFiles()) {
                if (typeDir.isDirectory && typeDir.name.startsWith("layout")) {
                    for (layoutXml in typeDir.listFiles()) {
                        if (layoutXml.isFile) {
                            generateKeepRulesFromLayoutXmlFile(
                                layoutXml, documentBuilder, rules
                            )
                        }
                    }
                }
            }
        } catch (e: ParserConfigurationException) {
            throw IOException("Failed to read merged resources", e)
        }
    }

    rules.sort()
    return rules
}

@Throws(IOException::class)
fun generateKeepRulesFromLayoutXmlFile(
    layout: File,
    documentBuilder: DocumentBuilder,
    rules: MutableList<String>
) {
    try {
        val xmlDocument = documentBuilder.parse(layout)
        val root = xmlDocument.documentElement
        if (root != null) {
            generateKeepRulesFromXmlNode(root, rules)
        }
    } catch (e: SAXException) {
        throw IOException(
            "Failed to parse XML resource file " + layout.absolutePath, e
        )
    } catch (e: IOException) {
        throw IOException(
            "Failed to parse XML resource file " + layout.absolutePath, e
        )
    }
}

private fun generateKeepRulesFromXmlNode(node: Element, rules: MutableList<String>) {
    val tag = node.tagName
    if (tag.contains(".")) {
        rules.add(String.format("-keep class %s { <init>(...); }", tag))
    }

    var current: Node? = node.firstChild
    while (current != null) {
        if (current.nodeType == Node.ELEMENT_NODE) {
            // handle its children
            generateKeepRulesFromXmlNode(current as Element, rules)
        }
        current = current.nextSibling
    }
}

@Throws(IOException::class)
fun parseManifest(manifestFile: File): ManifestData {
    return try {
        AndroidManifestParser.parse(FileWrapper(manifestFile))
    } catch (e: SAXException) {
        throw IOException(
            "Failed to parse android manifest XML file at path: '"
                    + manifestFile.absolutePath,
            e
        )
    } catch (e: IOException) {
        throw IOException(
            "Failed to parse android manifest XML file at path: '"
                    + manifestFile.absolutePath,
            e
        )
    }
}

/**
 * Updates the value resource name to mimic aapt's behaviour - replaces all dots and colons with
 * underscores.
 *
 * <p>If the name contains whitespaces or other illegal characters, they are not checked in this
 * method, but caught in the Symbol constructor call to {@link
 * Symbol#createAndValidateSymbol(ResourceType, String, SymbolJavaType, String, List)}.
 *
 * @param name the resource name to be updated
 * @return a valid resource name
 */
fun canonicalizeValueResourceName(name: String): String =
    NORMALIZED_VALUE_NAME_CHARS.replaceFrom(name, '_')

private val VALUE_ID_SPLITTER = Splitter.on(',').trimResults()

fun valueStringToInt(valueString: String) =
    if (valueString.startsWith("0x")) {
        Integer.parseUnsignedInt(valueString.substring(2), 16)
    } else {
        Integer.parseInt(valueString)
    }

fun parseArrayLiteral(size: Int, valuesString: String): ImmutableList<Int> {
    if (size == 0) {
        if (!valuesString.subSequence(1, valuesString.length-1).isBlank()) {
            failParseArrayLiteral(size, valuesString)
        }
        return ImmutableList.of()
    }
    val ints = ImmutableList.builder<Int>()

    val values = VALUE_ID_SPLITTER.split(valuesString.subSequence(1,
        valuesString.length - 1)).iterator()
    for (i in 0 until size) {
        if (!values.hasNext()) {
            failParseArrayLiteral(size, valuesString)
        }
        ints.add(valueStringToInt(values.next()))
    }
    if (values.hasNext()) {
        failParseArrayLiteral(size, valuesString)
    }

    return ints.build()
}

fun failParseArrayLiteral(size: Int, valuesString: String): Nothing {
    throw IOException("""Values string $valuesString should have $size item(s).""")
}
