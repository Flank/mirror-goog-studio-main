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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.google.common.base.Preconditions;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * <p>The <i>pseudo</i> resource type is either the resource type or the value {@code values}.
 * If the first segment of the directory name is {@code values}, files in the directory are treated
 * as resource XML values, parsed using {@link ResourceValuesXmlParser} and the generated symbol
 * tables merged to form the resulting one.
 *
 * <p>The qualifiers are irrelevant as far as the directory parser is concerned and are ignored.
 *
 * <p>One symbol will be generated per resource file, with the exception of resource files in the
 * {@code values} directory. The symbol's name is the resource file name with optional extension
 * removed. The only characters allowed in the symbol's name are lowercase letters, digits and
 * the underscore character.
 *
 * <p>Subdirectories in the resource directories are ignored.
 *
 * <p>For testing purposes, it is guaranteed that the resource directories are processed by name
 * with all the {@code values} directories being processed last. Inside each directory, files are
 * processed in alphabetical order. All symbols are assigned an ID, even if they are duplicated.
 * Duplicated symbols are not present in the final symbol table. So, if for example, the
 * following resources are defined {@code drawable/a.png}, {@code drawable-hdpi/a.png} and
 * {@code layout/b.xml}, two symbols will exist, {@code drawable/a} and {@code layout/b} with
 * IDs {@code 1} and {@code 3}, respectively. This behavior, that ensures ID assignment is
 * deterministic, should not be relied upon except for testing.
 */
public class ResourceDirectoryParser {

    /**
     * A valid resource file name has the format X or X.Y where X is a non-empty sequence of
     * lowercase characters, digits or underscore and Y, if present, is any sequence of characters
     * that does not contain period.
     */
    private static final Pattern VALID_RESOURCE_FILE_NAME =
            Pattern.compile("([a-z0-9_]+)(?:\\.[^.]*)?");

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
            @NonNull IdProvider idProvider) {
        Preconditions.checkArgument(directory.isDirectory(), "!directory.isDirectory()");

        SymbolTable.Builder builder = SymbolTable.builder();

        File[] resourceDirectories = directory.listFiles();
        assert resourceDirectories != null;

        Arrays.sort(resourceDirectories, Comparator.comparing(File::getName));

        for (File resourceDirectory : resourceDirectories) {
            if (!resourceDirectory.isDirectory()) {
                throw new ResourceDirectoryParseException(
                        "'"
                                + resourceDirectory.getAbsolutePath()
                                + "' is not a directory");
            }

            parseResourceDirectory(resourceDirectory, builder, idProvider);
        }

        return builder.build();
    }

    /**
     * Parses a resource directory.
     *
     * @param resourceDirectory the resource directory to parse
     * @param builder the builer to add symbols to
     * @param idProvider the ID provider to get IDs from
     * @throws ResourceDirectoryParseException failed to parse the resource directory
     */
    private static void parseResourceDirectory(
            @NonNull File resourceDirectory,
            @NonNull SymbolTable.Builder builder,
            @NonNull IdProvider idProvider) {
        assert resourceDirectory.isDirectory();

        /*
         * Compute the pseudo resource type from the resource directory name, discarding any
         * qualifiers. If the directory name is "foo", then the pseudo resource type is "foo". If
         * the directory name is "foo-bar-blah", then the pseudo resource type is "foo".
         */
        String directoryName = resourceDirectory.getName();
        int firstHyphen = directoryName.indexOf(SdkConstants.RES_QUALIFIER_SEP);
        String pseudoResourceType;
        if (firstHyphen == -1) {
            pseudoResourceType = directoryName;
        } else {
            pseudoResourceType = directoryName.substring(0, firstHyphen);
        }

        /*
         * Check if this is a resource values directory or not. If it is, then individual files
         * are not treated as resources but rather resource value XML files.
         */
        boolean isValues = pseudoResourceType.equals(SdkConstants.FD_RES_VALUES);
        DocumentBuilder documentBuilder = null;
        if (isValues) {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            try {
                documentBuilder = documentBuilderFactory.newDocumentBuilder();
            } catch (ParserConfigurationException e) {
                throw new ResourceDirectoryParseException("Failed to instantiate DOM parser", e);
            }
        }


        /*
         * Iterate all files int the resource directory and handle each one. Directories are ignored
         * and each file generates a symbol, except if isValues is true in which case the resource
         * XML file is parsed.
         */
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

            Matcher resourceFileNameMatcher =
                    VALID_RESOURCE_FILE_NAME.matcher(maybeResourceFile.getName());
            if (!resourceFileNameMatcher.matches()) {
                throw new ResourceDirectoryParseException(
                        "'"
                                + maybeResourceFile.getAbsolutePath()
                                + "' is not a valid resource file name.");
            }

            if (isValues) {
                Document domTree;
                try {
                    domTree = documentBuilder.parse(maybeResourceFile);
                } catch (SAXException|IOException e) {
                    throw new ResourceDirectoryParseException(
                            "Failed to parse XML resource file '"
                                    + maybeResourceFile.getAbsolutePath()
                                    + "'", e);
                }

                SymbolTable parsedXml = ResourceValuesXmlParser.parse(domTree, idProvider);
                parsedXml.allSymbols().forEach(s -> addIfNotExisting(builder, s));
            } else {
                String symbolName = resourceFileNameMatcher.group(1);
                assert symbolName != null && !symbolName.isEmpty();
                addIfNotExisting(
                        builder,
                        new Symbol(
                                pseudoResourceType,
                                symbolName,
                                "int",
                                Integer.toString(idProvider.next())));
            }
        }
    }

    /**
     * Adds a symbol to a {@link SymbolTable} builder, if a symbol with the same resource type and
     * name is not already there.
     *
     * @param builder the builder
     * @param symbol the symbol
     */
    private static void addIfNotExisting(
            @NonNull SymbolTable.Builder builder,
            @NonNull Symbol symbol) {
        if (!builder.contains(symbol)) {
            builder.add(symbol);
        }
    }
}
