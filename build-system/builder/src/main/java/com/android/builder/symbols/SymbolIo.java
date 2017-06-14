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
import com.android.resources.ResourceType;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Reads and writes symbol tables to files.
 */
public final class SymbolIo {

    private SymbolIo() {}

    /**
     * Loads a symbol table from a symbol file.
     *
     * @param file the symbol file
     * @return the table read
     * @throws IOException failed to read the table
     */
    @NonNull
    public static SymbolTable read(@NonNull File file) throws IOException {
        List<String> lines;
        try {
            lines = Files.readAllLines(file.toPath(), Charsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        SymbolTable.Builder table = SymbolTable.builder();

        int lineIndex = 1;
        String line = null;
        try {
            final int count = lines.size();
            for (; lineIndex <= count ; lineIndex++) {
                line = lines.get(lineIndex - 1);

                // format is "<type> <class> <name> <value>"
                // don't want to split on space as value could contain spaces.
                int pos = line.indexOf(' ');
                String typeName = line.substring(0, pos);
                SymbolJavaType type = SymbolJavaType.getEnum(typeName);
                int pos2 = line.indexOf(' ', pos + 1);
                String className = line.substring(pos + 1, pos2);
                ResourceType resourceType = ResourceType.getEnum(className);
                if (resourceType == null) {
                    throw new IOException("Invalid resource type " + className);
                }
                int pos3 = line.indexOf(' ', pos2 + 1);
                String name = line.substring(pos2 + 1, pos3);
                String value = line.substring(pos3 + 1);
                table.add(Symbol.createSymbol(resourceType, name, type, value));
            }
        } catch (IndexOutOfBoundsException | IOException e) {
            throw new IOException(
                    String.format(
                            "File format error reading %s line %d: '%s'",
                            file.getAbsolutePath(), lineIndex, line),
                    e);
        }

        return table.build();
    }

    /**
     * Writes a symbol table to a symbol file.
     *
     * @param table the table
     * @param file the file where the table should be written
     * @throws UncheckedIOException I/O error
     */
    public static void write(@NonNull SymbolTable table, @NonNull File file) {
        List<String> lines = new ArrayList<>();

        /*
         * Identify all resource types.
         */
        EnumSet<ResourceType> resourceTypes = EnumSet.noneOf(ResourceType.class);
        table.allSymbols().forEach(s -> resourceTypes.add(s.getResourceType()));

        for (ResourceType rt : resourceTypes) {
            /*
             * The order used to generate the symbols is important. Styleable array declarations
             * need to happen before the styleable element. Using the name always guarantees this
             * since the array name is always shorter (e.g. "ActionBar" for the styleable vs
             * "ActionBar_tileName" for the attribute)
             */
            SortedSet<Symbol> syms = new TreeSet<>(Comparator.comparing(Symbol::getName));
            table.allSymbols()
                    .forEach(
                            sym -> {
                                if (sym.getResourceType().equals(rt)) {
                                    syms.add(sym);
                                }
                            });

            for (Symbol s : syms) {
                lines.add(
                        s.getJavaType().getTypeName()
                                + " "
                                + s.getResourceType().getName()
                                + " "
                                + s.getName()
                                + " "
                                + s.getValue());
            }
        }

        try (
                FileOutputStream fos = new FileOutputStream(file);
                PrintWriter pw = new PrintWriter(fos)) {
            lines.forEach(pw::println);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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
                    pw.println(
                            "        "
                                    + idModifiers
                                    + " "
                                    + s.getJavaType().getTypeName()
                                    + " "
                                    + s.getName()
                                    + " = "
                                    + s.getValue()
                                    + ";");
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
