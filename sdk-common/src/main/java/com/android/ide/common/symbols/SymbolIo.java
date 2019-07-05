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
package com.android.ide.common.symbols;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.xml.AndroidManifestParser;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.android.utils.FileUtils;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

/**
 * Reads and writes symbol tables to files.
 *
 * <pre>
 * AAR Format:
 *  - R.txt in AARs
 *     - Format is <type> <class> <name> <value>
 *     - Contains the resources for this library and all its transitive dependencies.
 *     - Written using writeForAar()
 *     - Only read as part of writeSymbolListWithPackageName() as there are corrupt files with
 *       styleable children not below their parents in the wild.
 *     - IDs and styleable children don't matter, as this is only used to filtering symbols when
 *       generating R classes.
 *  - public.txt in AARs
 *     - Format is <class> <name>
 *     - Contains all the resources from this AAR that are public (missing public.txt means all
 *       resources should be public)
 *     - There are no IDs or styleable children here, needs to be merged with R.txt and filtered by
 *       the public visibility to actually get a full list of public resources from the AAR
 *
 * AAPT2 Outputs the following formats:
 *  - R.txt as output by AAPT2, where ID values matter.
 *     - Format is <type> <class> <name> <value>
 *     - Read using readFromAapt().
 *     - This is what the R class is generated from.
 *  - Partial R file format.
 *     - Format is <access qualifier> <type> <class> <name>
 *     - Contains only the resources defined in a single source file.
 *     - Used to push R class generation earlier.
 *
 * Internal intermediates:
 *  - Symbol list with package name. Used to filter down the generated R class for this library in
 *    the non-namespaced case.
 *     - Format is <type> <name> [<child> [<child>, [...]]], with the first line as the package name.
 *     - Contains resources from this sub-project and all its transitive dependencies.
 *     - Read by readSymbolListWithPackageName()
 *     - Generated from AARs and AAPT2 symbol tables by writeSymbolListWithPackageName()
 *  - R def format,
 *     - Used for namespace backward compatibility.
 *     - Contains only the resources defined in a single library.
 *     - Has the package name as the first line.
 *     - May contain internal resource types (e.g. "maybe attributes" defined under declare
 *       styleable resources).
 *
 *  All files are written in UTF-8. R files use linux-type line separators, while R.java use system
 *  line separators.
 * </pre>
 */
public final class SymbolIo {

    public static final String ANDROID_ATTR_PREFIX = "android_";

    private SymbolIo() {}

    /**
     * Loads a symbol table from a symbol file created by aapt.
     *
     * @param file the symbol file
     * @param tablePackage the package name associated with the table
     * @return the table read
     * @throws IOException failed to read the table
     */
    @NonNull
    public static SymbolTable readFromAapt(@NonNull File file, @Nullable String tablePackage)
            throws IOException {
        return read(file, tablePackage, ReadConfiguration.AAPT);
    }

    /**
     * Loads a symbol table from a symbol file created by aapt, but ignores all the resource values.
     * It will also ignore any styleable children that are not under their parents.
     *
     * @param file the symbol file
     * @param tablePackage the package name associated with the table
     * @return the table read
     * @throws IOException failed to read the table
     */
    @NonNull
    public static SymbolTable readFromAaptNoValues(
            @NonNull File file, @Nullable String tablePackage) throws IOException {
        return read(file, tablePackage, ReadConfiguration.AAPT_NO_VALUES);
    }

    /**
     * Loads a symbol table from a symbol file created by aapt, but ignores all the resource values.
     * It will also ignore any styleable children that are not under their parents.
     *
     * @param reader the reader for reading the symbol file
     * @param filename the name of the symbol file for use in error messages
     * @param tablePackage the package name associated with the table
     * @return the table read
     * @throws IOException failed to read the table
     */
    @NonNull
    public static SymbolTable readFromAaptNoValues(
            @NonNull BufferedReader reader, @NonNull String filename, @Nullable String tablePackage)
            throws IOException {
        return read(reader.lines(), filename, tablePackage, ReadConfiguration.AAPT_NO_VALUES);
    }

    /**
     * Loads a symbol table from a partial symbol file created by aapt during compilation.
     *
     * @param file the partial symbol file
     * @param tablePackage the package name associated with the table
     * @return the table read
     * @throws IOException failed to read the table
     */
    @NonNull
    public static SymbolTable readFromPartialRFile(
            @NonNull File file, @Nullable String tablePackage) throws IOException {
        return read(file, tablePackage, ReadConfiguration.PARTIAL_FILE);
    }

    @NonNull
    public static SymbolTable readFromPublicTxtFile(
            @NonNull File file, @Nullable String tablePackage) throws IOException {
        return read(file, tablePackage, ReadConfiguration.PUBLIC_FILE);
    }

    @NonNull
    private static SymbolTable read(
            @NonNull File file,
            @Nullable String tablePackage,
            @NonNull ReadConfiguration readConfiguration)
            throws IOException {
        String filename = file.getAbsolutePath();
        try (Stream<String> lines = Files.lines(file.toPath())) {
            return read(lines, filename, tablePackage, readConfiguration);
        }
    }

    @NonNull
    private static SymbolTable read(
            @NonNull Stream<String> lines,
            @NonNull String filename,
            @Nullable String tablePackage,
            @NonNull ReadConfiguration readConfiguration)
            throws IOException {
        Iterator<String> linesIterator = lines.iterator();
        int startLine = checkFileTypeHeader(linesIterator, readConfiguration, filename);
        SymbolTable.Builder table =
                new SymbolLineReader(readConfiguration, linesIterator, filename, startLine)
                        .readLines();
        if (tablePackage != null) {
            table.tablePackage(tablePackage);
        }
        return table.build();
    }

    /**
     * Loads a symbol table from a synthetic namespaced symbol file.
     *
     * <p>See {@link #writeSymbolListWithPackageName(Path, Path, Path)} for format details.
     *
     * @param file the symbol file
     * @return the table read
     * @throws IOException failed to read the table
     */
    @NonNull
    public static SymbolTable readSymbolListWithPackageName(@NonNull Path file) throws IOException {
        return readWithPackage(file, ReadConfiguration.SYMBOL_LIST_WITH_PACKAGE);
    }

    /**
     * Loads a symbol table from an partial file.
     *
     * @param file the symbol file
     * @return the table read
     * @throws IOException failed to read the table
     */
    @NonNull
    public static SymbolTable readRDef(@NonNull Path file) throws IOException {
        return readWithPackage(file, ReadConfiguration.R_DEF);
    }

    @NonNull
    private static SymbolTable readWithPackage(
            @NonNull Path file, @NonNull ReadConfiguration readConfiguration) throws IOException {
        try (Stream<String> lines = Files.lines(file, UTF_8)) {
            Iterator<String> linesIterator = lines.iterator();
            String filePath = file.toString();
            int startLine = checkFileTypeHeader(linesIterator, readConfiguration, filePath);
            if (!linesIterator.hasNext()) {
                throw new IOException(
                        "Internal error: Symbol file with package cannot be empty. File located at: "
                                + file);
            }
            String tablePackage = linesIterator.next().trim();
            SymbolTable.Builder table =
                    new SymbolLineReader(readConfiguration, linesIterator, filePath, startLine + 1)
                            .readLines();

            table.tablePackage(tablePackage);
            return table.build();
        }
    }

    private static int checkFileTypeHeader(
            @NonNull Iterator<String> lines,
            @NonNull ReadConfiguration readConfiguration,
            @NonNull String filename)
            throws IOException {
        if (readConfiguration.fileTypeHeader == null) {
            return 1;
        }
        if (!lines.hasNext()) {
            throw new IOException(
                    "Internal Error: Invalid symbol file '"
                            + filename
                            + "', cannot be empty for type '"
                            + readConfiguration
                            + "'");
        }
        String firstLine = lines.next();
        if (!lines.hasNext() || !readConfiguration.fileTypeHeader.equals(firstLine)) {
            throw new IOException(
                    "Internal Error: Invalid symbol file '"
                            + filename
                            + "', first line is incorrect for type '"
                            + readConfiguration
                            + "'.\n Expected '"
                            + readConfiguration.fileTypeHeader
                            + "' but got '"
                            + firstLine
                            + "'");
        }
        return 2;
    }

    private static class SymbolLineReader {
        @NonNull private final SymbolTable.Builder table = SymbolTable.builder();

        @NonNull private final Iterator<String> lines;
        @NonNull private final String filename;
        @NonNull private final ReadConfiguration readConfiguration;

        // Current line number and content
        private int currentLineNumber;
        @Nullable private String currentLineContent;

        // Reuse list to avoid allocations.
        private final List<SymbolData> aaptStyleableChildrenCache = new ArrayList<>(10);

        SymbolLineReader(
                @NonNull ReadConfiguration readConfiguration,
                @NonNull Iterator<String> lines,
                @NonNull String filename,
                int startLine) {
            this.readConfiguration = readConfiguration;
            this.lines = lines;
            this.filename = filename;
            currentLineNumber = startLine - 1;
        }

        private void readNextLine() {
            if (!lines.hasNext()) {
                currentLineContent = null;
            } else {
                currentLineContent = lines.next();
                currentLineNumber++;
            }
        }

        @NonNull
        SymbolTable.Builder readLines() throws IOException {
            if (!lines.hasNext()) {
                return table;
            }
            readNextLine();
            try {
                while (currentLineContent != null) {
                    SymbolData data = readConfiguration.parseLine(currentLineContent);
                    if (data.resourceType == ResourceType.STYLEABLE) {
                        switch (data.javaType) {
                            case INT:
                                if (readConfiguration.ignoreRogueChildren) {
                                    // If we're ignoring rogue children (styleable children that are
                                    // not under their parent), we can just read the next line and
                                    // continue.
                                    readNextLine();
                                    break;
                                } else {
                                    // If we're not ignoring rogue children, we need to error out.
                                    throw new IOException(
                                            "Unexpected styleable child " + currentLineContent);
                                }
                            case INT_LIST:
                                readNextLine();
                                handleStyleable(table, data);
                                // Already at the next line, as handleStyleable has to read forward
                                // to find its children.
                                break;
                        }
                    } else {
                        int value = 0;
                        if (readConfiguration.readValues) {
                            value = SymbolUtils.valueStringToInt(data.value);
                        }
                        String canonicalName =
                                readConfiguration.rawSymbolNames
                                        ? SymbolUtils.canonicalizeValueResourceName(data.name)
                                        : data.name;

                        if (data.resourceType == ResourceType.ATTR) {
                            table.add(
                                    new Symbol.AttributeSymbol(
                                            data.name,
                                            value,
                                            data.maybeDefinition,
                                            data.accessibility,
                                            canonicalName));
                        } else {
                            table.add(
                                    new Symbol.NormalSymbol(
                                            data.resourceType,
                                            data.name,
                                            value,
                                            data.accessibility,
                                            canonicalName));
                        }
                        readNextLine();
                    }
                }
            } catch (IndexOutOfBoundsException | IOException e) {
                throw new IOException(
                        String.format(
                                "File format error reading %1$s line %2$d: '%3$s'",
                                filename, currentLineNumber, currentLineContent),
                        e);
            }

            return table;
        }

        private void handleStyleable(@NonNull SymbolTable.Builder table, @NonNull SymbolData data)
                throws IOException {
            if (readConfiguration.singleLineStyleable) {
                String canonicalName =
                        readConfiguration.rawSymbolNames
                                ? SymbolUtils.canonicalizeValueResourceName(data.name)
                                : data.name;
                table.add(
                        new Symbol.StyleableSymbol(
                                data.name,
                                ImmutableList.of(),
                                data.children,
                                data.accessibility,
                                canonicalName));
                return;
            }
            // Keep the current location to report if there is an error
            String styleableLineContent = currentLineContent;
            int styleableLineIndex = currentLineNumber;
            final String data_name = data.name + "_";
            aaptStyleableChildrenCache.clear();
            List<SymbolData> children = aaptStyleableChildrenCache;
            while (currentLineContent != null) {
                SymbolData subData = readConfiguration.parseLine(currentLineContent);
                if (subData.resourceType != ResourceType.STYLEABLE
                        || subData.javaType != SymbolJavaType.INT) {
                    break;
                }
                children.add(subData);
                readNextLine();
            }

            // Having the attrs in order only matters if the values matter.
            if (readConfiguration.readValues) {
                try {
                    children.sort(SYMBOL_DATA_VALUE_COMPARATOR);
                } catch (NumberFormatException e) {
                    // Report error from styleable parent.
                    currentLineContent = styleableLineContent;
                    currentLineNumber = styleableLineIndex;
                    throw new IOException(e);
                }
            }
            ImmutableList.Builder<String> builder = ImmutableList.builder();
            for (SymbolData aaptStyleableChild : children) {
                builder.add(computeItemName(data_name, aaptStyleableChild.name));
            }
            ImmutableList<String> childNames = builder.build();

            ImmutableList<Integer> values;
            if (readConfiguration.readValues) {
                try {
                    values = SymbolUtils.parseArrayLiteral(childNames.size(), data.value);
                } catch (NumberFormatException e) {
                    // Report error from styleable parent.
                    currentLineContent = styleableLineContent;
                    currentLineNumber = styleableLineIndex;
                    throw new IOException(
                            "Unable to parse array literal " + data.name + " = " + data.value, e);
                }
            } else {
                values = ImmutableList.of();
            }
            String canonicalName =
                    readConfiguration.rawSymbolNames
                            ? SymbolUtils.canonicalizeValueResourceName(data.name)
                            : data.name;
            table.add(
                    new Symbol.StyleableSymbol(
                            canonicalName, values, childNames, data.accessibility, data.name));
        }

        private static final Comparator<SymbolData> SYMBOL_DATA_VALUE_COMPARATOR =
                Comparator.comparingInt(o -> Integer.parseInt(o.value));
    }

    private static final class SymbolData {
        @NonNull final ResourceVisibility accessibility;
        @NonNull final ResourceType resourceType;
        @NonNull final String name;
        @NonNull final SymbolJavaType javaType;
        @NonNull final String value;
        @NonNull final ImmutableList<String> children;
        final boolean maybeDefinition;

        public SymbolData(
                @NonNull ResourceType resourceType,
                @NonNull String name,
                @NonNull SymbolJavaType javaType,
                @NonNull String value) {
            this.accessibility = ResourceVisibility.UNDEFINED;
            this.resourceType = resourceType;
            this.name = name;
            this.javaType = javaType;
            this.value = value;
            this.children = ImmutableList.of();
            this.maybeDefinition = false;
        }

        public SymbolData(
                @NonNull ResourceVisibility accessibility,
                @NonNull ResourceType resourceType,
                @NonNull String name,
                @NonNull SymbolJavaType javaType,
                @NonNull String value) {
            this.accessibility = accessibility;
            this.resourceType = resourceType;
            this.name = name;
            this.javaType = javaType;
            this.value = value;
            this.children = ImmutableList.of();
            this.maybeDefinition = false;
        }

        public SymbolData(@NonNull String name, @NonNull ImmutableList<String> children) {
            this.accessibility = ResourceVisibility.UNDEFINED;
            this.resourceType = ResourceType.STYLEABLE;
            this.name = name;
            this.javaType = SymbolJavaType.INT_LIST;
            this.value = "";
            this.children = children;
            this.maybeDefinition = false;
        }

        public SymbolData(@NonNull ResourceType resourceType, @NonNull String name) {
            this.accessibility = ResourceVisibility.UNDEFINED;
            this.resourceType = resourceType;
            this.name = name;
            this.javaType =
                    resourceType == ResourceType.STYLEABLE
                            ? SymbolJavaType.INT_LIST
                            : SymbolJavaType.INT;
            this.value = "";
            this.children = ImmutableList.of();
            this.maybeDefinition = false;
        }

        public SymbolData(@NonNull String name, boolean maybeDefinition) {
            this.accessibility = ResourceVisibility.UNDEFINED;
            this.name = name;
            this.javaType = SymbolJavaType.INT;
            this.resourceType = ResourceType.ATTR;
            this.value = "";
            this.children = ImmutableList.of();
            this.maybeDefinition = maybeDefinition;
        }
    }

    @NonNull
    private static SymbolData readAaptLine(@NonNull String line) throws IOException {
        // format is "<type> <class> <name> <value>"
        // don't want to split on space as value could contain spaces.
        int pos = line.indexOf(' ');
        String typeName = line.substring(0, pos);
        SymbolJavaType type = SymbolJavaType.getEnum(typeName);
        if (type == null) {
            throw new IOException("Invalid symbol type " + typeName);
        }

        int pos2 = line.indexOf(' ', pos + 1);
        String className = line.substring(pos + 1, pos2);

        ResourceType resourceType = ResourceType.fromClassName(className);
        if (resourceType == null) {
            throw new IOException("Invalid resource type " + className);
        }

        int pos3 = line.indexOf(' ', pos2 + 1);
        String name = line.substring(pos2 + 1, pos3);
        String value = line.substring(pos3 + 1).trim();

        return new SymbolData(resourceType, name, type, value);
    }

    @NonNull
    private static SymbolData readPartialRLine(@NonNull String line) throws IOException {
        // format is "<access qualifier> <type> <class> <name>"
        int pos = line.indexOf(' ');
        String accessName = line.substring(0, pos);
        ResourceVisibility accessibility = ResourceVisibility.getEnum(accessName);
        if (accessibility == null) {
            throw new IOException("Invalid resource access qualifier " + accessName);
        }

        int pos2 = line.indexOf(' ', pos + 1);
        String typeName = line.substring(pos + 1, pos2);
        SymbolJavaType type = SymbolJavaType.getEnum(typeName);
        if (type == null) {
            throw new IOException("Invalid symbol type " + typeName);
        }

        int pos3 = line.indexOf(' ', pos2 + 1);
        String className = line.substring(pos2 + 1, pos3);

        ResourceType resourceType = ResourceType.fromClassName(className);
        if (resourceType == null) {
            throw new IOException("Invalid resource type " + className);
        }

        String name = line.substring(pos3 + 1);

        return new SymbolData(accessibility, resourceType, name, type, "");
    }

    @NonNull
    private static SymbolData readPublicTxtLine(@NonNull String line) throws IOException {
        // format is "<class> <name>"
        int pos = line.indexOf(' ');
        String className = line.substring(0, pos);
        ResourceType resourceType = ResourceType.fromClassName(className);
        if (resourceType == null) {
            throw new IOException("Invalid resource type " + className);
        }
        // If it's a styleable it must be the parent. Styleable-children are only references to
        // attrs, if a child is to be public then the corresponding attr will be marked as public.
        // Styleable children (int styleable) should not be present in the public.txt.
        String typeName = resourceType == ResourceType.STYLEABLE ? "int[]" : "int";
        SymbolJavaType type = SymbolJavaType.getEnum(typeName);
        if (type == null) {
            throw new IOException("Invalid symbol type " + typeName);
        }

        String name = line.substring(pos + 1);
        return new SymbolData(ResourceVisibility.PUBLIC, resourceType, name, type, "");
    }

    @NonNull
    private static SymbolData readSymbolListWithPackageLine(@NonNull String line)
            throws IOException {
        // format is "<type> <name>[ <child>[ <child>[ ...]]]"
        int startPos = line.indexOf(' ');
        boolean maybeDefinition = false;
        String typeName = line.substring(0, startPos);
        ResourceType resourceType;
        if (typeName.equals("attr?")) {
            maybeDefinition = true;
            resourceType = ResourceType.ATTR;
        } else {
            resourceType = ResourceType.fromClassName(typeName);
        }
        if (resourceType == null) {
            throw new IOException("Invalid symbol type " + typeName);
        }
        int endPos = line.indexOf(' ', startPos + 1);
        // If styleable with children
        if (resourceType == ResourceType.STYLEABLE && endPos > 0) {
            String name = line.substring(startPos + 1, endPos);
            startPos = endPos + 1;
            ImmutableList.Builder<String> children = ImmutableList.builder();
            while (true) {
                endPos = line.indexOf(' ', startPos);
                if (endPos == -1) {
                    children.add(line.substring(startPos));
                    break;
                }
                children.add(line.substring(startPos, endPos));
                startPos = endPos + 1;
            }
            return new SymbolData(name, children.build());
        } else {
            String name = line.substring(startPos + 1);
            if (resourceType == ResourceType.ATTR) {
                return new SymbolData(name, maybeDefinition);
            } else {
                return new SymbolData(resourceType, name);
            }
        }
    }

    private static String computeItemName(@NonNull String prefix, @NonNull String name) {
        // tweak the name to remove the styleable prefix
        String indexName = name.substring(prefix.length());
        // check if it's a namespace, in which case replace android_name
        // with android:name
        if (indexName.startsWith(ANDROID_ATTR_PREFIX)) {
            indexName =
                    SdkConstants.ANDROID_NS_NAME_PREFIX
                            + indexName.substring(ANDROID_ATTR_PREFIX.length());
        }

        return indexName;
    }

    private enum ReadConfiguration {
        AAPT(true, false) {
            @NonNull
            @Override
            public SymbolData parseLine(@NonNull String line) throws IOException {
                return readAaptLine(line);
            }
        },
        AAPT_NO_VALUES(false, false, false, true, null) {
            @NonNull
            @Override
            public SymbolData parseLine(@NonNull String line) throws IOException {
                return readAaptLine(line);
            }
        },
        SYMBOL_LIST_WITH_PACKAGE(false, true) {
            @NonNull
            @Override
            public SymbolData parseLine(@NonNull String line) throws IOException {
                return readSymbolListWithPackageLine(line);
            }
        },
        R_DEF(false, true, true, false, "R_DEF: Internal format may change without notice") {
            @NonNull
            @Override
            public SymbolData parseLine(@NonNull String line) throws IOException {
                return readSymbolListWithPackageLine(line);
            }
        },
        PARTIAL_FILE(false, false) {
            @NonNull
            @Override
            public SymbolData parseLine(@NonNull String line) throws IOException {
                return readPartialRLine(line);
            }
        },
        PUBLIC_FILE(false, true) {
            @NonNull
            @Override
            public SymbolData parseLine(@NonNull String line) throws IOException {
                return readPublicTxtLine(line);
            }
        };

        ReadConfiguration(boolean readValues, boolean singleLineStyleable) {
            this(readValues, singleLineStyleable, false, false, null);
        }

        ReadConfiguration(
                boolean readValues,
                boolean singleLineStyleable,
                boolean rawSymbolNames,
                boolean ignoreRogueChildren,
                @Nullable String fileTypeHeader) {
            this.readValues = readValues;
            this.singleLineStyleable = singleLineStyleable;
            this.fileTypeHeader = fileTypeHeader;
            this.rawSymbolNames = rawSymbolNames;
            this.ignoreRogueChildren = ignoreRogueChildren;
        }

        final boolean readValues;
        final boolean singleLineStyleable;
        final boolean rawSymbolNames;
        final boolean ignoreRogueChildren;
        @Nullable final String fileTypeHeader;

        @NonNull
        abstract SymbolData parseLine(@NonNull String line) throws IOException;
    }
    /**
     * Writes a symbol table to a symbol file.
     *
     * @param table the table
     * @param file the file where the table should be written
     * @throws IOException I/O error
     */
    public static void writeForAar(@NonNull SymbolTable table, @NonNull File file)
            throws IOException {
        writeForAar(table, file.toPath());
    }

    /**
     * Writes a symbol table to a symbol file.
     *
     * @param table the table
     * @param file the file where the table should be written
     * @throws IOException I/O error
     */
    public static void writeForAar(@NonNull SymbolTable table, @NonNull Path file)
            throws IOException {
        try (Writer writer = Files.newBufferedWriter(file)) {
            // loop on the resource types so that the order is always the same
            for (ResourceType resType : ResourceType.values()) {
                List<Symbol> symbols = table.getSymbolByResourceType(resType);
                if (symbols.isEmpty()) {
                    continue;
                }

                for (Symbol s : symbols) {
                    writer.write(s.getJavaType().getTypeName());
                    writer.write(' ');
                    writer.write(s.getResourceType().getName());
                    writer.write(' ');
                    writer.write(s.getCanonicalName());
                    writer.write(' ');
                    if (s.getResourceType() != ResourceType.STYLEABLE) {
                        writer.write("0x");
                        writer.write(Integer.toHexString(s.getIntValue()));
                        writer.write('\n');
                    } else {

                        Symbol.StyleableSymbol styleable = (Symbol.StyleableSymbol) s;
                        writeStyleableValue(styleable, writer);
                        writer.write('\n');
                        // Declare styleables have the attributes that were defined under their node
                        // listed in
                        // the children list.
                        List<String> children = styleable.getChildren();
                        for (int i = 0; i < children.size(); ++i) {
                            writer.write(SymbolJavaType.INT.getTypeName());
                            writer.write(' ');
                            writer.write(ResourceType.STYLEABLE.getName());
                            writer.write(' ');
                            writer.write(s.getCanonicalName());
                            writer.write('_');
                            writer.write(
                                    SymbolUtils.canonicalizeValueResourceName(children.get(i)));
                            writer.write(' ');
                            writer.write(Integer.toString(i));
                            writer.write('\n');
                        }
                    }
                }
            }
        }
    }

    private static void writeStyleableValue(Symbol.StyleableSymbol s, Writer writer)
            throws IOException {
        writer.write("{ ");
        ImmutableList<Integer> values = s.getValues();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                writer.write(", ");
            }
            writer.write("0x");
            writer.write(Integer.toHexString(values.get(i)));
        }
        writer.write(" }");
    }

    /**
     * Writes a file listing the resources provided by the library.
     *
     * <p>This uses the symbol list with package name format of {@code "<type> <name>[ <child>[
     * <child>[ ...]]]" }.
     */
    public static void writeRDef(@NonNull SymbolTable table, @NonNull Path file)
            throws IOException {
        Preconditions.checkNotNull(
                ReadConfiguration.R_DEF.fileTypeHeader,
                "Missing package for R-def file " + file.toAbsolutePath());

        try (Writer writer = Files.newBufferedWriter(file)) {
            writer.write(ReadConfiguration.R_DEF.fileTypeHeader);
            writer.write('\n');
            writer.write(table.getTablePackage());
            writer.write('\n');
            // loop on the resource types so that the order is always the same
            for (ResourceType resType : ResourceType.values()) {
                List<Symbol> symbols = table.getSymbolByResourceType(resType);
                if (symbols.isEmpty()) {
                    continue;
                }

                for (Symbol s : symbols) {
                    writer.write(s.getResourceType().getName());
                    if (s.getResourceType() == ResourceType.ATTR
                            && ((Symbol.AttributeSymbol) s).isMaybeDefinition()) {
                        writer.write('?');
                    }
                    writer.write(' ');
                    writer.write(s.getName());
                    if (s.getResourceType() == ResourceType.STYLEABLE) {
                        List<String> children = s.getChildren();
                        for (String child : children) {
                            writer.write(' ');
                            writer.write(child);
                        }
                    }
                    writer.write('\n');
                }
            }
        }
    }

    /**
     * Writes the abridged symbol table with the package name as the first line.
     *
     * <p>This collapses the styleable children so the subsequent lines have the format {@code
     * "<type> <canonical_name>[ <child>[ <child>[ ...]]]"}
     *
     * @param symbolTable The R.txt file. If it does not exist, the result will be a file containing
     *     only the package name
     * @param manifest The AndroidManifest.xml file for this library. The package name is extracted
     *     and written as the first line of the output.
     * @param outputFile The file to write the result to.
     */
    public static void writeSymbolListWithPackageName(
            @NonNull Path symbolTable, @NonNull Path manifest, @NonNull Path outputFile)
            throws IOException {
        @Nullable String packageName;
        try (InputStream is = new BufferedInputStream(Files.newInputStream(manifest))) {
            packageName = AndroidManifestParser.parse(is).getPackage();
        } catch (SAXException | ParserConfigurationException e) {
            throw new IOException(
                    "Failed to get package name from manifest " + manifest.toAbsolutePath(), e);
        }
        writeSymbolListWithPackageName(symbolTable, packageName, outputFile);
    }

    /**
     * Writes the symbol table with the package name as the first line.
     *
     * <p>This collapses the styleable children so the subsequent lines have the format {@code
     * "<type> <canonical_name>[ <child>[ <child>[ ...]]]" }
     *
     * @param symbolTable The R.txt file. If it does not exist, the result will be a file containing
     *     only the package name
     * @param packageName The package name for the module. If not null, it will be written as the
     *     first line of output.
     * @param outputFile The file to write the result to.
     */
    public static void writeSymbolListWithPackageName(
            @NonNull Path symbolTable, @Nullable String packageName, @NonNull Path outputFile)
            throws IOException {
        try (SymbolListWithPackageNameWriter writer =
                new SymbolListWithPackageNameWriter(
                        packageName, Files.newBufferedWriter(outputFile))) {
            if (Files.exists(symbolTable)) {
                try (Stream<String> lines = Files.lines(symbolTable)) {
                    SymbolUtils.readAarRTxt(lines.iterator(), writer);
                }
            } else {
                SymbolUtils.visitEmptySymbolTable(writer);
            }
        }
    }

    /**
     * Exports a symbol table to a java {@code R} class source. This method will create the source
     * file and any necessary directories. For example, if the package is {@code a.b} and the class
     * name is {@code RR}, this method will generate a file called {@code RR.java} in directory
     * {@code directory/a/b} creating directories {@code a} and {@code b} if necessary.
     *
     * @param table the table to export
     * @param directory the directory where the R source should be generated
     * @param finalIds should the generated IDs be final?
     * @return the generated file
     * @throws UncheckedIOException failed to generate the source
     */
    @NonNull
    public static File exportToJava(
            @NonNull SymbolTable table, @NonNull File directory, boolean finalIds) {
        Preconditions.checkArgument(directory.isDirectory());

        /*
         * Build the path to the class file, creating any needed directories.
         */
        Splitter splitter = Splitter.on('.');
        Iterable<String> directories = splitter.split(table.getTablePackage());
        File file = directory;
        for (String d : directories) {
            file = new File(file, d);
        }

        FileUtils.mkdirs(file);
        file = new File(file, SdkConstants.R_CLASS + SdkConstants.DOT_JAVA);

        String idModifiers = finalIds ? "public static final" : "public static";

        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath())) {

            writer.write("/* AUTO-GENERATED FILE.  DO NOT MODIFY.");
            writer.newLine(); // use system line separator
            writer.write(" *");
            writer.newLine();
            writer.write(" * This class was automatically generated by the");
            writer.newLine();
            writer.write(" * gradle plugin from the resource data it found. It");
            writer.newLine();
            writer.write(" * should not be modified by hand.");
            writer.newLine();
            writer.write(" */");
            writer.newLine();

            if (!table.getTablePackage().isEmpty()) {
                writer.write("package ");
                writer.write(table.getTablePackage());
                writer.write(';');
                writer.newLine();
            }

            writer.newLine();
            writer.write("public final class R {");
            writer.newLine();
            writer.write("    private R() {}");
            writer.newLine();
            writer.newLine();

            final String typeName = SymbolJavaType.INT.getTypeName();

            // loop on the resource types so that the order is always the same
            for (ResourceType resType : ResourceType.values()) {
                List<Symbol> symbols = table.getSymbolByResourceType(resType);
                if (symbols.isEmpty()) {
                    continue;
                }
                writer.write("    public static final class ");
                writer.write(resType.getName());
                writer.write(" {");
                writer.newLine();

                writer.write("        private ");
                writer.write(resType.getName());
                writer.write("() {}");
                writer.newLine();
                writer.newLine();

                for (Symbol s : symbols) {
                    final String name = s.getCanonicalName();
                    writer.write("        ");
                    writer.write(idModifiers);
                    writer.write(' ');
                    writer.write(s.getJavaType().getTypeName());
                    writer.write(' ');
                    writer.write(name);
                    writer.write(" = ");

                    if (s.getResourceType() != ResourceType.STYLEABLE) {
                        writer.write("0x");
                        writer.write(Integer.toHexString(s.getIntValue()));
                        writer.write(';');
                        writer.newLine();
                    } else {
                        Symbol.StyleableSymbol styleable = (Symbol.StyleableSymbol) s;
                        writeStyleableValue(styleable, writer);
                        writer.write(';');
                        writer.newLine();
                        // Declare styleables have the attributes that were defined under their
                        // node
                        // listed in the children list.
                        List<String> children = styleable.getChildren();
                        for (int i = 0; i < children.size(); ++i) {
                            writer.write("        ");
                            writer.write(idModifiers);
                            writer.write(' ');
                            writer.write(typeName);
                            writer.write(' ');
                            writer.write(name);
                            writer.write('_');
                            writer.write(
                                    SymbolUtils.canonicalizeValueResourceName(children.get(i)));
                            writer.write(" = ");
                            writer.write(Integer.toString(i));
                            writer.write(';');
                            writer.newLine();
                        }
                    }
                }
                writer.write("    }");
                writer.newLine();
            }

            writer.write('}');
            writer.newLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return file;
    }
}
