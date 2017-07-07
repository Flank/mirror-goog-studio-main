/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.builder.symbols;

import static com.android.SdkConstants.DOT_XML;

import com.android.annotations.NonNull;
import com.android.ide.common.res2.FileResourceNameValidator;
import com.android.ide.common.res2.MergingException;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.utils.SdkUtils;
import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * Parser that scans a resource directory for all resources and builds a {@link SymbolTable}. The
 * parser expects a resource directory that contains sub-directories with a name in the format
 * {@code X(-Y)*} where {@code X} is a <i>pseudo</i> resource type (see below) and {@code Y} are
 * optional qualifiers. Inside each directory, only resource files should exist.
 *
 * <p>The <i>pseudo</i> resource type is either the resource type or the value {@code values}. If
 * the first segment of the directory name is {@code values}, files in the directory are treated as
 * resource XML values, parsed using {@link ResourceValuesXmlParser} and the generated symbol tables
 * are merged to form the resulting one.
 *
 * <p>The qualifiers are irrelevant as far as the directory parser is concerned and are ignored.
 *
 * <p>One symbol will be generated per resource file, with the exception of resource files in the
 * {@code values} directory and resources declared inside other XML files (e.g. "@+string/my_string"
 * declared inside {@code layout/activity_name.xml} would generate a new Symbol of type {@code
 * String} and name {@code my_string}).
 *
 * <p>For files the symbol's name is the resource file name with optional extension removed. The
 * only characters allowed in the symbol's name are lowercase letters, digits and the underscore
 * character.
 *
 * <p>For values declared inside XML files the symbol's name is the element's {@code name} XML tag
 * value. Dots and colons are allowed, but deprecated and therefore support for them will end soon.
 *
 * <p>Subdirectories in the resource directories are ignored.
 *
 * <p>For testing purposes, it is guaranteed that the resource directories are processed by name
 * with all the {@code values} directories being processed last. Inside each directory, files are
 * processed in alphabetical order. All symbols are assigned an ID, even if they are duplicated.
 * Duplicated symbols are not present in the final symbol table. So, if for example, the following
 * resources are defined {@code drawable/a.png}, {@code drawable-hdpi/a.png} and {@code
 * layout/b.xml}, two symbols will exist, {@code drawable/a} and {@code layout/b} with IDs {@code 1}
 * and {@code 3}, respectively. This behavior, that ensures ID assignment is deterministic, should
 * not be relied upon except for testing.
 */
public class ResourceDirectoryParser {

    private ResourceDirectoryParser() {}

    /**
     * Parses a resource directory.
     *
     * @param directory the directory to parse, must be an existing directory
     * @param idProvider the provider of IDs for the resources
     * @return the generated symbol table
     * @throws ResourceDirectoryParseException failed to parse the resource directory
     */
    @NonNull
    public static SymbolTable parseDirectory(
            @NonNull File directory,
            @NonNull IdProvider idProvider,
            @NonNull SymbolTable platformAttrSymbols)
            throws ResourceDirectoryParseException {
        Preconditions.checkArgument(directory.isDirectory(), "!directory.isDirectory()");

        SymbolTable.Builder builder = SymbolTable.builder();

        File[] resourceDirectories = directory.listFiles();
        assert resourceDirectories != null;

        Arrays.sort(resourceDirectories, Comparator.comparing(File::getName));

        DocumentBuilder documentBuilder;
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        try {
            documentBuilder = documentBuilderFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new ResourceDirectoryParseException("Failed to instantiate DOM parser", e);
        }

        for (File resourceDirectory : resourceDirectories) {
            if (!resourceDirectory.isDirectory()) {
                throw new ResourceDirectoryParseException(
                        "'"
                                + resourceDirectory.getAbsolutePath()
                                + "' is not a directory");
            }

            parseResourceDirectory(
                    resourceDirectory, builder, idProvider, documentBuilder, platformAttrSymbols);
        }

        return builder.build();
    }

    /**
     * Parses a resource directory.
     *
     * @param resourceDirectory the resource directory to parse
     * @param builder the builder to add symbols to
     * @param idProvider the ID provider to get IDs from
     * @param platformAttrSymbols platform attr values
     * @throws ResourceDirectoryParseException failed to parse the resource directory
     */
    private static void parseResourceDirectory(
            @NonNull File resourceDirectory,
            @NonNull SymbolTable.Builder builder,
            @NonNull IdProvider idProvider,
            @NonNull DocumentBuilder documentBuilder,
            @NonNull SymbolTable platformAttrSymbols)
            throws ResourceDirectoryParseException {
        assert resourceDirectory.isDirectory();

        // Compute the pseudo resource type from the resource directory name, discarding any
        // qualifiers. If the directory name is "foo", then the pseudo resource type is "foo". If
        // the directory name is "foo-bar-blah", then the pseudo resource type is "foo".
        String directoryName = resourceDirectory.getName();
        ResourceFolderType folderResourceType = ResourceFolderType.getFolderType(directoryName);

        // Iterate all files in the resource directory and handle each one.
        File[] resourceFiles = resourceDirectory.listFiles();
        assert resourceFiles != null;
        Arrays.sort(resourceFiles, Comparator.comparing(File::getName));
        for (File maybeResourceFile : resourceFiles) {
            if (maybeResourceFile.isDirectory()) {
                continue;
            }

            if (!maybeResourceFile.isFile()) {
                throw new ResourceDirectoryParseException(
                        "'"
                                + maybeResourceFile.getAbsolutePath()
                                + "' is not a file nor directory");
            }

            // Check if this is a resource values directory or not. If it is, then individual files
            // are not treated as resources but rather resource value XML files.
            if (folderResourceType == ResourceFolderType.VALUES) {
                Document domTree;
                try {
                    domTree = documentBuilder.parse(maybeResourceFile);
                    SymbolTable parsedXml =
                            ResourceValuesXmlParser.parse(domTree, idProvider, platformAttrSymbols);
                    parsedXml.getSymbols().values().forEach(s -> addIfNotExisting(builder, s));
                } catch (SAXException | IOException | IllegalArgumentException e) {
                    throw new ResourceDirectoryParseException(
                            "Failed to parse XML resource file '"
                                    + maybeResourceFile.getAbsolutePath()
                                    + "'", e);
                }
            } else {
                // We do not need to validate the filenames of files inside the {@code values}
                // directory as they do not get parsed into Symbols; but we need to validate the
                // filenames of files inside non-values directories.
                try {
                    FileResourceNameValidator.validate(maybeResourceFile, folderResourceType);
                } catch (MergingException e) {
                    throw new ResourceDirectoryParseException("Failed file name validation", e);
                }

                String fileName = maybeResourceFile.getName();

                // Get name without extension.
                String symbolName = getNameWithoutExtensions(fileName);

                ResourceType resourceType =
                        FolderTypeRelationship.getNonIdRelatedResourceType(folderResourceType);
                addIfNotExisting(
                        builder,
                        Symbol.createAndValidateSymbol(
                                resourceType,
                                symbolName,
                                SymbolJavaType.INT,
                                idProvider.next(resourceType),
                                Symbol.NO_CHILDREN));

                if (FolderTypeRelationship.isIdGeneratingFolderType(folderResourceType)
                        && SdkUtils.endsWithIgnoreCase(fileName, DOT_XML)) {
                    // If we are parsing an XML file (but not in values directories), parse the file
                    // in search of lazy constructions like {@code "@+id/name"} that also declare
                    // resources.
                    Document domTree;
                    try {
                        domTree = documentBuilder.parse(maybeResourceFile);
                    } catch (SAXException | IOException e) {
                        throw new ResourceDirectoryParseException(
                                "Failed to parse XML resource file '"
                                        + maybeResourceFile.getAbsolutePath()
                                        + "'",
                                e);
                    }
                    SymbolTable extraSymbols = ResourceExtraXmlParser.parse(domTree, idProvider);
                    extraSymbols.getSymbols().values().forEach(s -> addIfNotExisting(builder, s));
                }
            }
        }
    }

    /**
     * Removes the optional extensions from the filename. This method should be only called on names
     * verified by {@link FileResourceNameValidator#validate}.
     *
     * <p>As opposed to {@link Files#getNameWithoutExtension}, removes all extensions from the given
     * filename, for example:
     *
     * <p>{@code "foo.xml"} -> {@code "foo"} and {code "foo.9.png"} -> {@code "foo"}.
     *
     * @param filename the filename with optional extension
     * @return filename without any extensions
     */
    @NonNull
    private static String getNameWithoutExtensions(@NonNull String filename) {
        // Find the *first* dot.
        int dotIndex = filename.indexOf('.');
        return (dotIndex > 0) ? filename.substring(0, dotIndex) : filename;
    }

    /**
     * Adds a symbol to a {@link SymbolTable} builder, if a symbol with the same resource type and
     * name is not already there.
     *
     * @param builder the builder
     * @param symbol the symbol
     */
    private static void addIfNotExisting(
            @NonNull SymbolTable.Builder builder, @NonNull Symbol symbol) {
        if (!builder.contains(symbol)) {
            builder.add(symbol);
        }
    }
}
