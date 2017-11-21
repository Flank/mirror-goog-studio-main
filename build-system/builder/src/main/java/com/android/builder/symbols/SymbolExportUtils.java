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

package com.android.builder.symbols;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.symbols.RGeneration;
import com.android.ide.common.symbols.SymbolIo;
import com.android.ide.common.symbols.SymbolTable;
import com.android.ide.common.symbols.SymbolUtils;
import com.android.ide.common.xml.ManifestData;
import com.google.common.base.Preconditions;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;

public final class SymbolExportUtils {

    private SymbolExportUtils() {}


    /**
     * Processes the symbol table and generates necessary files: R.txt, R.java and proguard rules
     * ({@code aapt_rules.txt}). Afterwards generates {@code R.java} for all libraries the main
     * library depends on.
     *
     * @param librarySymbols table with symbols of resources for the library.
     * @param libraries libraries which this library depends on
     * @param mainPackageName package name of this library
     * @param manifestFile manifest file
     * @param sourceOut directory to contain R.java
     * @param symbolFileOut R.txt file location
     * @param proguardOut directory to contain proguard rules
     * @param mergedResources directory containing merged resources
     */
    public static void processLibraryMainSymbolTable(
            @NonNull final SymbolTable librarySymbols,
            @NonNull Set<File> libraries,
            @Nullable String mainPackageName,
            @NonNull File manifestFile,
            @NonNull File sourceOut,
            @NonNull File symbolFileOut,
            @Nullable File proguardOut,
            @Nullable File mergedResources,
            @NonNull SymbolTable platformSymbols,
            boolean disableMergeInLib)
            throws IOException {

        Preconditions.checkNotNull(sourceOut, "Source output directory should not be null");
        Preconditions.checkNotNull(symbolFileOut, "Symbols output directory should not be null");

        // Parse the manifest only when necessary.
        if (mainPackageName == null || proguardOut != null) {
            ManifestData manifestData = SymbolUtils.parseManifest(manifestFile);

            if (mainPackageName == null) {
                mainPackageName = SymbolUtils.getPackageNameFromManifest(manifestData);
            }
            // Generate aapt_rules.txt containing keep rules if minify is enabled.
            if (proguardOut != null) {
                Files.write(
                        proguardOut.toPath(),
                        SymbolUtils.generateMinifyKeepRules(manifestData, mergedResources));
            }
        }

        // Get symbol tables of the libraries we depend on.
        Set<SymbolTable> depSymbolTables = SymbolUtils.loadDependenciesSymbolTables(libraries);

        SymbolTable mainSymbolTable;
        if (disableMergeInLib) {
            // Merge all the symbols together.
            // We have to rewrite the IDs because some published R.txt inside AARs are using the
            // wrong value for some types, and we need to ensure there is no collision in the
            // file we are creating.
            mainSymbolTable =
                    SymbolUtils.mergeAndRenumberSymbols(
                            mainPackageName, librarySymbols, depSymbolTables, platformSymbols);
        } else {
            mainSymbolTable = librarySymbols.rename(mainPackageName);
        }

        // Generate R.txt file.
        Files.createDirectories(symbolFileOut.toPath().getParent());
        SymbolIo.write(mainSymbolTable, symbolFileOut);

        // Generate R.java file.
        SymbolIo.exportToJava(mainSymbolTable, sourceOut, false);

        // Generate the R.java files for each individual library.
        RGeneration.generateRForLibraries(mainSymbolTable, depSymbolTables, sourceOut, false);
    }
}
