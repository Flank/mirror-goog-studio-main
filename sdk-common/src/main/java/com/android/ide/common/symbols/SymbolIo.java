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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.xml.AndroidManifestParser;
import com.android.resources.ResourceAccessibility;
import com.android.resources.ResourceType;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

/** Reads and writes symbol tables to files. */
public final class SymbolIo {

    public static final String ANDROID_ATTR_PREFIX = "android_";

    private SymbolIo() {}

    /**
     * Loads a symbol table from a symbol file.
     *
     * @param file the symbol file
     * @param tablePackage the package name associated with the table
     * @return the table read
     * @throws IOException failed to read the table
     */
    @NonNull
    public static SymbolTable read(@NonNull File file, @Nullable String tablePackage)
            throws IOException {
        return read(file, tablePackage, ReadConfiguration.AAR);
    }

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
    private static SymbolTable read(
            @NonNull File file,
            @Nullable String tablePackage,
            @NonNull ReadConfiguration readConfiguration)
            throws IOException {
        SymbolTable.Builder table;
        try (Stream<String> lines = Files.lines(file.toPath(), Charsets.UTF_8)) {
            table =
                    new SymbolLineReader(readConfiguration, lines.iterator(), file.toPath(), 1)
                            .readLines();
        }
        if (tablePackage != null) {
            table.tablePackage(tablePackage);
        }

        return table.build();
    }

    /**
     * Loads a symbol table from a synthetic namespaced symbol file.
     *
     * <p>This is just a symbol table, but with the addition of the table package as the first line.
     *
     * @param file the symbol file
     * @return the table read
     * @throws IOException failed to read the table
     */
    @NonNull
    public static SymbolTable readTableWithPackage(@NonNull File file) throws IOException {
        return readTableWithPackage(file.toPath());
    }

    @NonNull
    public static SymbolTable readTableWithPackage(@NonNull Path file) throws IOException {
        String tablePackage;
        SymbolTable.Builder table;
        try (Stream<String> lines = Files.lines(file, Charsets.UTF_8)) {
            Iterator<String> linesIterator = lines.iterator();
            if (!linesIterator.hasNext()) {
                throw new IOException(
                        "Internal error: Symbol file with package cannot be empty. File located at: "
                                + file);
            }
            tablePackage = linesIterator.next().trim();
            table = new SymbolLineReader(ReadConfiguration.AAR, linesIterator, file, 2).readLines();
        }

        table.tablePackage(tablePackage);
        return table.build();
    }

    private static class SymbolLineReader {
        @NonNull private final SymbolTable.Builder table = SymbolTable.builder();

        @NonNull private final Iterator<String> lines;
        @NonNull private final Path file;
        @NonNull private final ReadConfiguration readConfiguration;

        // Current line number and content
        private int currentLineNumber;
        @Nullable private String currentLineContent;

        // Reuse lists to avoid allocations.
        private final List<String> styleableChildrenCache = new ArrayList<>(10);
        private final List<SymbolData> aaptStyleableChildrenCache = new ArrayList<>(10);

        SymbolLineReader(
                @NonNull ReadConfiguration readConfiguration,
                @NonNull Iterator<String> lines,
                @NonNull Path file,
                int startLine) {
            this.readConfiguration = readConfiguration;
            this.lines = lines;
            this.file = file;
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
                    SymbolData data = readConfiguration.parseLine(currentLineContent, null);
                    if (data.resourceType == ResourceType.STYLEABLE) {
                        switch (data.javaType) {
                            case INT:
                                // because there are some misordered file out there we want to make sure
                                // both the resType is Styleable and the javaType is array.
                                // We skip the non arrays that are out of sort
                                if (!readConfiguration.bestEffortMisorderedFile) {
                                    throw new IOException(
                                            "Unexpected stylable child " + currentLineContent);
                                }
                                readNextLine();
                                break;
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
                        table.add(
                                new Symbol.NormalSymbol(
                                        data.resourceType, data.name, value, data.accessibility));
                        readNextLine();
                    }
                }
            } catch (IndexOutOfBoundsException | IOException e) {
                throw new IOException(
                        String.format(
                                "File format error reading %1$s line %2$d: '%3$s'",
                                file.toString(), currentLineNumber, currentLineContent),
                        e);
            }

            return table;
        }

        private void handleStyleable(@NonNull SymbolTable.Builder table, @NonNull SymbolData data)
                throws IOException {
            // Keep the current location to report if there is an error
            String styleableLineContent = currentLineContent;
            int styleableLineIndex = currentLineNumber;
            final String data_name = data.name + "_";
            styleableChildrenCache.clear();
            aaptStyleableChildrenCache.clear();
            SymbolData subData;
            while (currentLineContent != null
                    && (subData =
                                    readConfiguration.parseLine(
                                            currentLineContent, ONLY_STYLEABLE_CHILDREN))
                            != null) {
                if (readConfiguration.numericallySortedStyleableChildren) {
                    if (subData.name.startsWith(data_name)) {
                        styleableChildrenCache.add(computeItemName(data_name, subData.name));
                    } else if (!readConfiguration.bestEffortMisorderedFile) {
                        throw new IOException("Unexpected styleable child " + subData.name);
                    }
                } else {
                    aaptStyleableChildrenCache.add(subData);
                }
                readNextLine();
            }

            ImmutableList<String> childNames;
            if (readConfiguration.numericallySortedStyleableChildren) {
                childNames = ImmutableList.copyOf(styleableChildrenCache);
            } else {
                try {
                    aaptStyleableChildrenCache.sort(SYMBOL_DATA_VALUE_COMPARATOR);
                } catch (NumberFormatException e) {
                    // Report error from styleable parent.
                    currentLineContent = styleableLineContent;
                    currentLineNumber = styleableLineIndex;
                    throw new IOException(e);
                }
                ImmutableList.Builder<String> builder = ImmutableList.builder();
                for (SymbolData aaptStyleableChild : aaptStyleableChildrenCache) {
                    builder.add(computeItemName(data_name, aaptStyleableChild.name));
                }
                childNames = builder.build();
            }

            ImmutableList<Integer> values;
            if (readConfiguration.readValues) {
                try {
                    if (readConfiguration.bestEffortMisorderedFile) {
                        values = SymbolUtils.parseArrayLiteralLenient(data.value);
                    } else {
                        values = SymbolUtils.parseArrayLiteral(childNames.size(), data.value);
                    }
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

            table.add(
                    new Symbol.StyleableSymbol(data.name, values, childNames, data.accessibility));
        }

        private static final SymbolFilter ONLY_STYLEABLE_CHILDREN =
                (resType, javaType) ->
                        resType.equals(ResourceType.STYLEABLE.getName())
                                && javaType.equals(SymbolJavaType.INT.getTypeName());

        private static final Comparator<SymbolData> SYMBOL_DATA_VALUE_COMPARATOR =
                Comparator.comparingInt(o -> Integer.parseInt(o.value));
    }

    private static class SymbolData {
        @NonNull final ResourceAccessibility accessibility;
        @NonNull final ResourceType resourceType;
        @NonNull final String name;
        @NonNull final SymbolJavaType javaType;
        @NonNull final String value;

        public SymbolData(
                @NonNull ResourceType resourceType,
                @NonNull String name,
                @NonNull SymbolJavaType javaType,
                @NonNull String value) {
            this.accessibility = ResourceAccessibility.DEFAULT;
            this.resourceType = resourceType;
            this.name = name;
            this.javaType = javaType;
            this.value = value;
        }

        public SymbolData(
                @NonNull ResourceAccessibility accessibility,
                @NonNull ResourceType resourceType,
                @NonNull String name,
                @NonNull SymbolJavaType javaType,
                @NonNull String value) {
            this.accessibility = accessibility;
            this.resourceType = resourceType;
            this.name = name;
            this.javaType = javaType;
            this.value = value;
        }
    }

    @FunctionalInterface
    private interface SymbolFilter {
        boolean validate(@NonNull String resourceType, @NonNull String javaType);
    }

    @Nullable
    private static SymbolData readLine(@NonNull String line, @Nullable SymbolFilter filter)
            throws IOException {
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
        if (filter != null && !filter.validate(className, typeName)) {
            return null;
        }
        ResourceType resourceType = ResourceType.getEnum(className);
        if (resourceType == null) {
            throw new IOException("Invalid resource type " + className);
        }

        int pos3 = line.indexOf(' ', pos2 + 1);
        String name = line.substring(pos2 + 1, pos3);
        String value = line.substring(pos3 + 1).trim();

        return new SymbolData(resourceType, name, type, value);
    }

    @Nullable
    private static SymbolData readPartialRLine(@NonNull String line, @Nullable SymbolFilter filter)
            throws IOException {
        // format is "<access qualifier> <type> <class> <name>"
        // value is 0 or empty be default
        int pos = line.indexOf(' ');
        String accessName = line.substring(0, pos);
        ResourceAccessibility accessibility = ResourceAccessibility.getEnum(accessName);
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
        if (filter != null && !filter.validate(className, typeName)) {
            return null;
        }
        ResourceType resourceType = ResourceType.getEnum(className);
        if (resourceType == null) {
            throw new IOException("Invalid resource type " + className);
        }

        String name = line.substring(pos3 + 1);

        String value = type == SymbolJavaType.INT ? "0x000000" : "{ }";

        return new SymbolData(accessibility, resourceType, name, type, value);
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
        AAR(true, true, true) {
            @Override
            public SymbolData parseLine(@NonNull String line, @Nullable SymbolFilter filter)
                    throws IOException {
                return SymbolIo.readLine(line, filter);
            }
        },
        AAPT(true, false, false) {
            @Override
            public SymbolData parseLine(@NonNull String line, @Nullable SymbolFilter filter)
                    throws IOException {
                return SymbolIo.readLine(line, filter);
            }
        },
        LOCAL(true, false, false) {
            @Override
            public SymbolData parseLine(@NonNull String line, @Nullable SymbolFilter filter)
                    throws IOException {
                return SymbolIo.readLine(line, filter);
            }
        },
        PARTIAL_FILE(false, true, false) {
            @Override
            public SymbolData parseLine(@NonNull String line, @Nullable SymbolFilter filter)
                    throws IOException {
                return readPartialRLine(line, filter);
            }
        },
        ;

        ReadConfiguration(
                boolean readValues,
                boolean numericallySortedStyleableChildren,
                boolean bestEffortMisorderedFile) {
            this.readValues = readValues;
            this.numericallySortedStyleableChildren = numericallySortedStyleableChildren;
            this.bestEffortMisorderedFile = bestEffortMisorderedFile;
        }

        final boolean readValues;
        final boolean numericallySortedStyleableChildren;
        final boolean bestEffortMisorderedFile;

        abstract SymbolData parseLine(@NonNull String line, @Nullable SymbolFilter filter)
                throws IOException;
    }
    /**
     * Writes a symbol table to a symbol file.
     *
     * @param table the table
     * @param file the file where the table should be written
     * @throws UncheckedIOException I/O error
     */
    public static void write(@NonNull SymbolTable table, @NonNull File file) {
        write(table, file.toPath(), null);
    }

    public static void write(@NonNull SymbolTable table, @NonNull Path file) {
        write(table, file, null);
    }

    public static void write(
            @NonNull SymbolTable table, @NonNull Path file, @Nullable String packageName) {
        try (BufferedOutputStream os = new BufferedOutputStream(Files.newOutputStream(file));
                PrintWriter pw = new PrintWriter(os)) {
            if (packageName != null) {
                pw.println(packageName);
            }

            // loop on the resource types so that the order is always the same
            for (ResourceType resType : ResourceType.values()) {
                List<Symbol> symbols = table.getSymbolByResourceType(resType);
                if (symbols.isEmpty()) {
                    continue;
                }

                for (Symbol s : symbols) {
                    pw.print(s.getJavaType().getTypeName());
                    pw.print(' ');
                    pw.print(s.getResourceType().getName());
                    pw.print(' ');
                    pw.print(s.getName());
                    pw.print(' ');
                    if (s.getResourceType() != ResourceType.STYLEABLE) {
                        Symbol.NormalSymbol symbol = (Symbol.NormalSymbol) s;
                        pw.print("0x");
                        pw.print(Integer.toHexString(symbol.getIntValue()));
                        pw.print('\n');
                    } else {

                        Symbol.StyleableSymbol styleable = (Symbol.StyleableSymbol) s;
                        writeStyleableValue(styleable, pw);
                        pw.print('\n');
                        // Declare styleables have the attributes that were defined under their node
                        // listed in
                        // the children list.
                        List<String> children = styleable.getChildren();
                        for (int i = 0; i < children.size(); ++i) {
                            pw.print(SymbolJavaType.INT.getTypeName());
                            pw.print(' ');
                            pw.print(ResourceType.STYLEABLE.getName());
                            pw.print(' ');
                            pw.print(s.getName());
                            pw.print('_');
                            pw.print(SymbolUtils.canonicalizeValueResourceName(children.get(i)));
                            pw.print(' ');
                            pw.print(Integer.toString(i));
                            pw.print('\n');
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeStyleableValue(Symbol.StyleableSymbol s, PrintWriter pw) {
        pw.print("{ ");
        ImmutableList<Integer> values = s.getValues();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                pw.print(", ");
            }
            pw.print("0x");
            pw.print(Integer.toHexString(values.get(i)));
        }
        pw.print(" }");
    }

    public static void writePartialR(@NonNull SymbolTable table, @NonNull Path file) {

        try (BufferedOutputStream os = new BufferedOutputStream(Files.newOutputStream(file));
                PrintWriter pw = new PrintWriter(os)) {

            // loop on the resource types so that the order is always the same
            for (ResourceType resType : ResourceType.values()) {
                List<Symbol> symbols = table.getSymbolByResourceType(resType);
                if (symbols.isEmpty()) {
                    continue;
                }

                for (Symbol s : symbols) {
                    pw.print(s.getResourceAccessibility().getName());
                    pw.print(' ');
                    pw.print(s.getJavaType().getTypeName());
                    pw.print(' ');
                    pw.print(s.getResourceType().getName());
                    pw.print(' ');
                    pw.print(s.getName());
                    pw.print('\n');

                    // Declare styleables have the attributes that were defined under their node
                    // listed in
                    // the children list.
                    if (s.getResourceType() == ResourceType.STYLEABLE) {
                        List<String> children = ((Symbol.StyleableSymbol) s).getChildren();
                        for (String child : children) {
                            pw.print(s.getResourceAccessibility().getName());
                            pw.print(' ');
                            pw.print(SymbolJavaType.INT.getTypeName());
                            pw.print(' ');
                            pw.print(ResourceType.STYLEABLE.getName());
                            pw.print(' ');
                            pw.print(s.getName());
                            pw.print('_');
                            pw.print(SymbolUtils.canonicalizeValueResourceName(child));
                            pw.print('\n');
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Writes the symbol table with the package name as the first line.
     *
     * @param symbolTable The R.txt file. If it does not exist, the result will be a file containing
     *     only the package name
     * @param manifest The AndroidManifest.xml file for this library. The package name is extracted
     *     and written as the first line of the output.
     * @param outputFile The file to write the result to.
     */
    public static void writeSymbolTableWithPackage(
            @NonNull Path symbolTable, @NonNull Path manifest, @NonNull Path outputFile)
            throws IOException {
        @Nullable String packageName;
        try (InputStream is = new BufferedInputStream(Files.newInputStream(manifest))) {
            packageName = AndroidManifestParser.parse(is).getPackage();
        } catch (SAXException | ParserConfigurationException e) {
            throw new IOException(e);
        }
        writeSymbolTableWithPackage(symbolTable, packageName, outputFile);
    }

    /** Writes tye symbol table with the package name as the first line. */
    public static void writeSymbolTableWithPackage(
            @NonNull SymbolTable symbolTable, @NonNull String pkg, @NonNull File outputFile) {
        write(symbolTable, outputFile.toPath(), pkg);
    }

    /**
     * Writes the symbol table with the package name as the first line.
     *
     * @param symbolTable The R.txt file. If it does not exist, the result will be a file containing
     *     only the package name
     * @param packageName The package name for the module. If not null, it will be written as the
     *     first line of output.
     * @param outputFile The file to write the result to.
     */
    public static void writeSymbolTableWithPackage(
            @NonNull Path symbolTable, @Nullable String packageName, @NonNull Path outputFile)
            throws IOException {
        try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(outputFile))) {
            if (packageName != null) {
                os.write(packageName.getBytes(Charsets.UTF_8));
            }
            os.write('\n');
            if (!Files.exists(symbolTable)) {
                return;
            }
            try (InputStream is = new BufferedInputStream(Files.newInputStream(symbolTable))) {
                ByteStreams.copy(is, os);
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

        try (PrintWriter pw =
                new PrintWriter(new BufferedOutputStream(Files.newOutputStream(file.toPath())))) {

            pw.println("/* AUTO-GENERATED FILE.  DO NOT MODIFY.");
            pw.println(" *");
            pw.println(" * This class was automatically generated by the");
            pw.println(" * gradle plugin from the resource data it found. It");
            pw.println(" * should not be modified by hand.");
            pw.println(" */");

            if (!table.getTablePackage().isEmpty()) {
                pw.print("package ");
                pw.print(table.getTablePackage());
                pw.print(';');
                pw.println();
            }

            pw.println();
            pw.println("public final class R {");
            pw.println("    private R() {}");
            pw.println();

            final String typeName = SymbolJavaType.INT.getTypeName();

            // loop on the resource types so that the order is always the same
            for (ResourceType resType : ResourceType.values()) {
                List<Symbol> symbols = table.getSymbolByResourceType(resType);
                if (symbols.isEmpty()) {
                    continue;
                }
                pw.print("    public static final class ");
                pw.print(resType.getName());
                pw.print(" {");
                pw.println();

                pw.print("        private ");
                pw.print(resType.getName());
                pw.println("() {}");
                pw.println();

                for (Symbol s : symbols) {
                    final String name = s.getName();
                    pw.print("        ");
                    pw.print(idModifiers);
                    pw.print(' ');
                    pw.print(s.getJavaType().getTypeName());
                    pw.print(' ');
                    pw.print(name);
                    pw.print(" = ");

                    if (s.getResourceType() != ResourceType.STYLEABLE) {
                        Symbol.NormalSymbol symbol = (Symbol.NormalSymbol) s;
                        pw.print("0x");
                        pw.print(Integer.toHexString(symbol.getIntValue()));
                        pw.print(';');
                        pw.println();
                    } else {
                        Symbol.StyleableSymbol styleable = (Symbol.StyleableSymbol) s;
                        writeStyleableValue(styleable, pw);
                        pw.print(';');
                        pw.println();
                        // Declare styleables have the attributes that were defined under their
                        // node
                        // listed in the children list.
                        List<String> children = styleable.getChildren();
                        for (int i = 0; i < children.size(); ++i) {
                            pw.print("        ");
                            pw.print(idModifiers);
                            pw.print(' ');
                            pw.print(typeName);
                            pw.print(' ');
                            pw.print(name);
                            pw.print('_');
                            pw.print(SymbolUtils.canonicalizeValueResourceName(children.get(i)));
                            pw.print(" = ");
                            pw.print(i);
                            pw.print(';');
                            pw.println();
                        }
                    }
                }
                pw.print("    }");
                pw.println();
            }

            pw.print('}');
            pw.println();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return file;
    }
}
