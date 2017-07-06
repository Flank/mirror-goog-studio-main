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
import com.android.annotations.Nullable;
import com.android.ide.common.xml.AndroidManifestParser;
import com.android.resources.ResourceType;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

/**
 * Reads and writes symbol tables to files.
 */
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
        List<String> lines = Files.readAllLines(file.toPath(), Charsets.UTF_8);

        SymbolTable.Builder table = readLines(lines, 1, file.toPath());

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

    public static SymbolTable readTableWithPackage(@NonNull Path file) throws IOException {

        List<String> lines = Files.readAllLines(file, Charsets.UTF_8);

        if (lines.isEmpty()) {
            throw new IOException("Internal error: Symbol file with package cannot be empty.");
        }

        SymbolTable.Builder table = readLines(lines, 2, file);
        table.tablePackage(lines.get(0).trim());

        return table.build();
    }

    private static SymbolTable.Builder readLines(
            @NonNull List<String> lines, int startLine, @NonNull Path file) throws IOException {
        SymbolTable.Builder table = SymbolTable.builder();

        int lineIndex = startLine;
        String line = null;
        try {
            final int count = lines.size();
            for (; lineIndex <= count ; lineIndex++) {
                line = lines.get(lineIndex - 1);

                SymbolData data = readLine(line, null);
                // because there are some misordered file out there we want to make sure
                // both the resType is Styleable and the javaType is array.
                // We skip the non arrays that are out of sort
                if (data.resourceType == ResourceType.STYLEABLE) {
                    if (data.javaType == SymbolJavaType.INT_LIST) {
                        List<String> childrenNames = Lists.newArrayList();
                        final String data_name = data.name + "_";
                        SymbolData subData;
                        // read the next line
                        while (lineIndex < count
                                && (subData =
                                                readLine(
                                                        lines.get(lineIndex),
                                                        (resourceType, javaType) ->
                                                                resourceType.equals(
                                                                                ResourceType
                                                                                        .STYLEABLE
                                                                                        .getName())
                                                                        && javaType.equals(
                                                                                SymbolJavaType.INT
                                                                                        .getTypeName())))
                                        != null) {
                            // line is value, inc the index
                            lineIndex++;

                            // check if the sub item actually belongs to this declare-styleable,
                            // because of broken R.txt files.
                            // We could have a int/styleable that follows a int[]/styleable but
                            // is an index for a different declare-stylealbe.
                            if (subData.name.startsWith(data_name)) {
                                // tweak the name to remove the styleable.
                                String indexName = subData.name.substring(data_name.length());
                                // check if it's a namespace, in which case replace android_name
                                // with android:name
                                if (indexName.startsWith(ANDROID_ATTR_PREFIX)) {
                                    indexName =
                                            SdkConstants.ANDROID_NS_NAME_PREFIX
                                                    + indexName.substring(
                                                            ANDROID_ATTR_PREFIX.length());
                                }

                                childrenNames.add(indexName);
                            }
                        }

                        table.add(
                                Symbol.createSymbol(
                                        data.resourceType,
                                        data.name,
                                        data.javaType,
                                        data.value,
                                        childrenNames));
                    }

                } else {
                    table.add(
                            Symbol.createSymbol(
                                    data.resourceType, data.name, data.javaType, data.value));
                }
            }
        } catch (IndexOutOfBoundsException | IOException e) {
            throw new IOException(
                    String.format(
                            "File format error reading %s line %d: '%s'",
                            file.toString(), lineIndex, line),
                    e);
        }
        return table;
    }

    private static class SymbolData {
        @NonNull final ResourceType resourceType;
        @NonNull final String name;
        @NonNull final SymbolJavaType javaType;
        @NonNull final String value;

        public SymbolData(
                @NonNull ResourceType resourceType,
                @NonNull String name,
                @NonNull SymbolJavaType javaType,
                @NonNull String value) {
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
        String value = line.substring(pos3 + 1);

        return new SymbolData(resourceType, name, type, value);
    }



    /**
     * Writes a symbol table to a symbol file.
     *
     * @param table the table
     * @param file the file where the table should be written
     * @throws UncheckedIOException I/O error
     */
    public static void write(@NonNull SymbolTable table, @NonNull File file) {
        write(table, file.toPath());
    }

    public static void write(@NonNull SymbolTable table, @NonNull Path file) {
        List<String> lines = new ArrayList<>();

        for (Symbol s : table.allSymbols()) {
            lines.add(
                    s.getJavaType().getTypeName()
                            + " "
                            + s.getResourceType().getName()
                            + " "
                            + s.getName()
                            + " "
                            + s.getValue());

            // Declare styleables have the attributes that were defined under their node listed in
            // the children list.
            if (s.getJavaType() == SymbolJavaType.INT_LIST) {
                Preconditions.checkArgument(
                        s.getResourceType() == ResourceType.STYLEABLE,
                        "Only resource type 'styleable' is allowed to have java type 'int[]'");

                List<String> children = s.getChildren();
                for (int i = 0; i < children.size(); ++i) {
                    lines.add(
                            SymbolJavaType.INT.getTypeName()
                                    + " "
                                    + ResourceType.STYLEABLE.getName()
                                    + " "
                                    + s.getName()
                                    + "_"
                                    + SymbolUtils.canonicalizeValueResourceName(children.get(i))
                                    + " "
                                    + i);
                }
            }
        }

        try (BufferedOutputStream os = new BufferedOutputStream(Files.newOutputStream(file))) {
            for (String line : lines) {
                os.write(line.getBytes(Charsets.UTF_8));
                os.write('\n');
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
     * file and any necessary directories. For example, if the package is {@code a.b} and the
     * class name is {@code RR}, this method will generate a file called {@code RR.java} in
     * directory {@code directory/a/b} creating directories {@code a} and {@code b} if necessary.
     *
     * @param table the table to export
     * @param directory the directory where the R source should be generated
     * @param finalIds should the generated IDs be final?
     * @return the generated file
     * @throws UncheckedIOException failed to generate the source
     */
    @NonNull
    public static File exportToJava(
            @NonNull SymbolTable table,
            @NonNull File directory,
            boolean finalIds) {
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
        file = new File(file, SymbolTable.R_CLASS_NAME + SdkConstants.DOT_JAVA);

        /*
         * Identify all resource types.
         */

        EnumSet<ResourceType> resourceTypes = EnumSet.noneOf(ResourceType.class);
        table.allSymbols().forEach(s -> resourceTypes.add(s.getResourceType()));

        /*
         * Write the class file.
         */
        String idModifiers = finalIds? "public static final" : "public static";

        try (
                FileOutputStream fos = new FileOutputStream(file);
                PrintWriter pw = new PrintWriter(fos)) {

            pw.println("/* AUTO-GENERATED FILE.  DO NOT MODIFY.");
            pw.println(" *");
            pw.println(" * This class was automatically generated by the");
            pw.println(" * gradle plugin from the resource data it found. It");
            pw.println(" * should not be modified by hand.");
            pw.println(" */");

            if (!table.getTablePackage().isEmpty()) {
                pw.println("package " + table.getTablePackage() + ";");
            }

            pw.println();
            pw.println("public final class " + SymbolTable.R_CLASS_NAME + " {");

            final String typeName = SymbolJavaType.INT.getTypeName();

            for (ResourceType rt : resourceTypes) {
                pw.println("    public static final class " + rt + " {");

                // Sort the symbols by name to make output preditable and, therefore, testing
                // easier.
                SortedSet<Symbol> syms = new TreeSet<>(Comparator.comparing(Symbol::getName));
                table.allSymbols().forEach(sym -> {
                    if (sym.getResourceType().equals(rt)) {
                        syms.add(sym);
                    }
                });

                for (Symbol s : syms) {
                    final String name = s.getName();
                    pw.println(
                            "        "
                                    + idModifiers
                                    + " "
                                    + s.getJavaType().getTypeName()
                                    + " "
                                    + name
                                    + " = "
                                    + s.getValue()
                                    + ";");

                    // Declare styleables have the attributes that were defined under their node
                    // listed in the children list.
                    if (s.getJavaType() == SymbolJavaType.INT_LIST) {
                        Preconditions.checkArgument(
                                s.getResourceType() == ResourceType.STYLEABLE,
                                "Only resource type 'styleable'"
                                        + " is allowed to have java type 'int[]'");

                        List<String> children = s.getChildren();
                        for (int i = 0; i < children.size(); ++i) {
                            pw.println(
                                    "        "
                                            + idModifiers
                                            + " "
                                            + typeName
                                            + " "
                                            + name
                                            + "_"
                                            + SymbolUtils.canonicalizeValueResourceName(
                                                    children.get(i))
                                            + " = "
                                            + i
                                            + ";");
                        }
                    }
                }

                pw.println("    }");
            }

            pw.println("}");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return file;
    }
}
