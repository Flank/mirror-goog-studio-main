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

@file:JvmName("ResourceDirectoryParser")

package com.android.ide.common.symbols

import com.android.SdkConstants.DOT_XML
import com.android.ide.common.resources.FileResourceNameValidator
import com.android.ide.common.resources.MergingException
import com.android.resources.FolderTypeRelationship
import com.android.resources.ResourceFolderType
import com.android.utils.SdkUtils
import com.google.common.base.Preconditions
import com.google.common.io.Files
import org.w3c.dom.Document
import java.io.File
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

/**
 * Parser method that scans a resource directory for all resources and builds a [SymbolTable]. It
 * expects a resource directory that contains sub-directories with a name in the format `X(-Y)*`
 * where `X` is a *pseudo* resource type (see below) and `Y` are optional qualifiers. Inside each
 * directory, only resource files should exist.
 *
 * The *pseudo* resource type is either the resource type or the value `values`. If the first
 * segment of the directory name is `values`, files in the directory are treated as resource XML
 * values, parsed using the [parseValuesResource] and [parseResourceForInlineResources] methods and
 * the generated symbol tables are merged to form the resulting one.
 *
 * The qualifiers are irrelevant as far as the directory parser is concerned and are ignored.
 *
 * One symbol will be generated per resource file, with the exception of resource files in the
 * `values` directory and resources declared inside other XML files (e.g. "@+string/my_string"
 * declared inside `layout/activity_name.xml` would generate a new Symbol of type `String` and name
 * `my_string`).
 *
 * For files the symbol's name is the resource file name with optional extension removed. The only
 * characters allowed in the symbol's name are lowercase letters, digits and the underscore
 * character.
 *
 * For values declared inside XML files the symbol's name is the element's `name` XML tag value.
 * Dots and colons are allowed, but deprecated and therefore support for them will end soon.
 *
 * Subdirectories in the resource directories are ignored.
 *
 * For testing purposes, it is guaranteed that the resource directories are processed by name with
 * all the `values` directories being processed last. Inside each directory, files are processed in
 * alphabetical order. All symbols are assigned an ID, even if they are duplicated. Duplicated
 * symbols are not present in the final symbol table. So, if for example, the following resources
 * are defined `drawable/a.png`, `drawable-hdpi/a.png` and `layout/b.xml`, two symbols will exist,
 * `drawable/a` and `layout/b` with IDs `1` and `3`, respectively. This behavior, that ensures ID
 * assignment is deterministic, should not be relied upon except for testing.
 *
 * @param directory the directory to parse, must be an existing directory
 * @param idProvider the provider of IDs for the resources
 * @return the generated symbol table
 */
fun parseResourceSourceSetDirectory(
        directory: File,
        idProvider: IdProvider,
        platformAttrSymbols: SymbolTable?
): SymbolTable {
    Preconditions.checkArgument(directory.isDirectory, "!directory.isDirectory()")

    val builder = SymbolTable.builder()
    val resourceDirectories = directory.listFiles()!!.sortedBy { it.name }

    val documentBuilderFactory = DocumentBuilderFactory.newInstance()
    val documentBuilder = try {
        documentBuilderFactory.newDocumentBuilder()
    } catch (e: ParserConfigurationException) {
        throw ResourceDirectoryParseException("Failed to instantiate DOM parser", e)
    }

    for (resourceDirectory in resourceDirectories) {
        if (!resourceDirectory.isDirectory) {
            throw ResourceDirectoryParseException(
                    "'${resourceDirectory.absolutePath}' is not a directory")
        }

        parseResourceDirectory(
                resourceDirectory, builder, idProvider, documentBuilder, platformAttrSymbols)
    }

    return builder.build()
}

/**
 * Parses a resource directory.
 *
 * @param resourceDirectory the resource directory to parse
 * @param builder the builder to add symbols to
 * @param idProvider the ID provider to get IDs from
 * @param platformAttrSymbols platform attr values
 */
private fun parseResourceDirectory(
        resourceDirectory: File,
        builder: SymbolTable.Builder,
        idProvider: IdProvider,
        documentBuilder: DocumentBuilder,
        platformAttrSymbols: SymbolTable?
) {
    assert(resourceDirectory.isDirectory)

    // Compute the pseudo resource type from the resource directory name, discarding any qualifiers.
    // If the directory name is "foo", then the pseudo resource type is "foo". If the directory name
    // is "foo-bar-blah", then the pseudo resource type is "foo".
    val directoryName = resourceDirectory.name
    val folderResourceType = ResourceFolderType.getFolderType(directoryName)

    // Iterate all files in the resource directory and handle each one.
    val resourceFiles = resourceDirectory.listFiles()!!.sortedBy { it.name }

    for (maybeResourceFile in resourceFiles) {
        if (maybeResourceFile.isDirectory) {
            continue
        }

        if (!maybeResourceFile.isFile) {
            throw ResourceDirectoryParseException(
                    "'${maybeResourceFile.absolutePath}' is not a file nor directory")
        }

        // Check if this is a resource values directory or not. If it is, then individual files are
        // not treated as resources but rather resource value XML files.
        if (folderResourceType == ResourceFolderType.VALUES) {
            val domTree: Document
            try {
                domTree = documentBuilder.parse(maybeResourceFile)
                val parsedXml = parseValuesResource(domTree, idProvider, platformAttrSymbols)
                parsedXml.symbols.values().forEach { s -> addIfNotExisting(builder, s) }
            } catch (e: Exception) {
                throw ResourceDirectoryParseException(
                        "Failed to parse XML resource file '${maybeResourceFile.absolutePath}'",
                        e)
            }

        } else {
            // We do not need to validate the filenames of files inside the `values` directory as
            // they do not get parsed into Symbols; but we need to validate the filenames of files
            // inside non-values directories.
            try {
                FileResourceNameValidator.validate(maybeResourceFile, folderResourceType!!)
            } catch (e: MergingException) {
                throw ResourceDirectoryParseException(
                        "Failed file name validation for file ${maybeResourceFile.absolutePath}", e)
            }

            val fileName = maybeResourceFile.name

            // Get name without extension.
            val symbolName = getNameWithoutExtensions(fileName)

            val resourceType = FolderTypeRelationship
                    .getNonIdRelatedResourceType(folderResourceType)
            addIfNotExisting(
                    builder,
                    Symbol.createAndValidateSymbol(resourceType, symbolName, idProvider))

            if (FolderTypeRelationship.isIdGeneratingFolderType(folderResourceType)
                    && SdkUtils.endsWithIgnoreCase(fileName, DOT_XML)) {
                // If we are parsing an XML file (but not in values directories), parse the file in
                // search of lazy constructions like `@+id/name` that also declare resources.
                val domTree = try {
                    documentBuilder.parse(maybeResourceFile)
                } catch (e: Exception) {
                    throw ResourceDirectoryParseException(
                            "Failed to parse XML file '${maybeResourceFile.absolutePath}'", e)
                }

                val extraSymbols = parseResourceForInlineResources(domTree, idProvider)
                extraSymbols.symbols.values().forEach { s -> addIfNotExisting(builder, s) }
            }
        }
    }
}

/**
 * Removes the optional extensions from the filename. This method should be only called on names
 * verified by [FileResourceNameValidator.validate].
 *
 * As opposed to [Files.getNameWithoutExtension], removes all extensions from the given filename,
 * for example: `foo.xml` -> `foo` and `foo.9.png` -> `foo`.
 *
 * @param filename the filename with optional extension
 * @return filename without any extensions
 */
private fun getNameWithoutExtensions(filename: String): String {
    // Find the *first* dot.
    val dotIndex = filename.indexOf('.')
    return if (dotIndex > 0) filename.substring(0, dotIndex) else filename
}

/**
 * Adds a symbol to a [SymbolTable] builder, if a symbol with the same resource type and
 * name is not already there.
 *
 * @param builder the builder
 * @param symbol the symbol
 */
private fun addIfNotExisting(
        builder: SymbolTable.Builder, symbol: Symbol) {
    if (!builder.contains(symbol)) {
        builder.add(symbol)
    }
}
