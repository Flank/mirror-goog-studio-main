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
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import com.google.common.io.Closer;
import com.google.common.io.Files;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A class to write R.java classes based on data read from text symbol files generated by
 * aapt with the --output-text-symbols option.
 */
public class SymbolWriter {

    @NonNull
    private final String mOutFolder;
    @NonNull
    private final String mPackageName;
    @NonNull
    private final List<SymbolLoader> mSymbols = Lists.newArrayList();
    @NonNull
    private final SymbolLoader mValues;
    private final boolean generateFinalIds;

    public SymbolWriter(
            @NonNull String outFolder,
            @NonNull String packageName,
            @NonNull SymbolLoader values,
            boolean generateFinalIds) {
        mOutFolder = outFolder;
        mPackageName = packageName;
        mValues = values;
        this.generateFinalIds = generateFinalIds;
    }

    public void addSymbolsToWrite(@NonNull SymbolLoader symbols) {
        mSymbols.add(symbols);
    }

    @NonNull
    private Table<String, String, Symbol> getAllSymbols() {
        Table<String, String, Symbol> symbols = HashBasedTable.create();

        for (SymbolLoader symbolLoader : mSymbols) {
            symbols.putAll(symbolLoader.getSymbols());
        }

        return symbols;
    }

    @NonNull
    private Table<String, String, Symbol> getMatchingSymbols() {
        ImmutableTable.Builder<String, String, Symbol> symbolBuilder = ImmutableTable.builder();

        Table<String, String, Symbol> symbols = getAllSymbols();
        Table<String, String, Symbol> values = mValues.getSymbols();

        Set<String> rowSet = symbols.rowKeySet();

        for (String row : rowSet) {
            Set<String> symbolSet = symbols.row(row).keySet();

            for (String symbolName : symbolSet) {
                // get the matching SymbolEntry from the values Table.
                Symbol value = values.get(row, symbolName);
                if (value != null) {
                    symbolBuilder.put(row, symbolName, value);
                }
            }
        }

        return symbolBuilder.build();
    }

    public void write() throws IOException {
        Table<String, String, Symbol> matchingSymbols = getMatchingSymbols();
        if (matchingSymbols.isEmpty()) {
            return;
        }

        Splitter splitter = Splitter.on('.');
        Iterable<String> folders = splitter.split(mPackageName);
        File file = new File(mOutFolder);
        for (String folder : folders) {
            file = new File(file, folder);
        }
        FileUtils.mkdirs(file);
        file = new File(file, SdkConstants.FN_RESOURCE_CLASS);

        Closer closer = Closer.create();

        String idModifiers = generateFinalIds ? "public static final " : "public static ";

        try {
            BufferedWriter writer = closer.register(Files.newWriter(file, Charsets.UTF_8));

            writer.write("/* AUTO-GENERATED FILE.  DO NOT MODIFY.\n");
            writer.write(" *\n");
            writer.write(" * This class was automatically generated by the\n");
            writer.write(" * aapt tool from the resource data it found.  It\n");
            writer.write(" * should not be modified by hand.\n");
            writer.write(" */\n");

            writer.write("package ");
            writer.write(mPackageName);
            writer.write(";\n\npublic final class R {\n");

            Set<String> rowSet = matchingSymbols.rowKeySet();
            List<String> rowList = Lists.newArrayList(rowSet);
            Collections.sort(rowList);

            for (String row : rowList) {
                writer.write("\tpublic static final class ");
                writer.write(row);
                writer.write(" {\n");

                Map<String, Symbol> rowMap = matchingSymbols.row(row);
                Set<String> symbolSet = rowMap.keySet();
                ArrayList<String> symbolList = Lists.newArrayList(symbolSet);
                Collections.sort(symbolList);

                for (String symbolName : symbolList) {
                    // get the matching SymbolEntry from the values Table.
                    Symbol value = matchingSymbols.get(row, symbolName);
                    writer.write("\t\t");
                    writer.write(idModifiers);
                    writer.write(value.getJavaType());
                    writer.write(" ");
                    writer.write(value.getName());
                    writer.write(" = ");
                    writer.write(value.getValue());
                    writer.write(";\n");
                }

                writer.write("\t}\n");
            }

            writer.write("}\n");
        } catch (Throwable e) {
            throw closer.rethrow(e);
        } finally {
            closer.close();
        }
    }
}
