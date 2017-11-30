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
@file:JvmName("SymbolExportUtils")

package com.android.builder.symbols

import com.android.ide.common.symbols.RGeneration
import com.android.ide.common.symbols.SymbolIo
import com.android.ide.common.symbols.SymbolTable
import com.android.ide.common.symbols.SymbolUtils
import com.android.utils.FileUtils
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * Processes the symbol table and generates necessary files: R.txt, R.java and proguard rules
 * (`aapt_rules.txt`). Afterwards generates `R.java` for all libraries the main
 * library depends on.
 *
 * @param librarySymbols table with symbols of resources for the library.
 * @param libraries libraries which this library depends on
 * @param mainPackageName package name of this library
 * @param manifestFile manifest file
 * @param sourceOut directory to contain R.java
 * @param rClassOutputJar file to output R.jar.
 * @param symbolFileOut R.txt file location
 * @param proguardOut directory to contain proguard rules
 * @param mergedResources directory containing merged resources
 */
@Throws(IOException::class)
fun processLibraryMainSymbolTable(
        librarySymbols: SymbolTable,
        libraries: Set<File>,
        mainPackageName: String?,
        manifestFile: File,
        sourceOut: File?,
        rClassOutputJar: File?,
        symbolFileOut: File,
        proguardOut: File?,
        mergedResources: File?,
        platformSymbols: SymbolTable,
        disableMergeInLib: Boolean) {

    // Parse the manifest only when necessary.
    val finalPackageName = if (mainPackageName == null || proguardOut != null) {
        val manifestData = SymbolUtils.parseManifest(manifestFile)
        // Generate aapt_rules.txt containing keep rules if minify is enabled.
        if (proguardOut != null) {
            Files.write(
                    proguardOut.toPath(),
                    SymbolUtils.generateMinifyKeepRules(manifestData, mergedResources))
        }
        mainPackageName ?: SymbolUtils.getPackageNameFromManifest(manifestData)
    } else {
        mainPackageName
    }

    // Get symbol tables of the libraries we depend on.
    val depSymbolTables = SymbolUtils.loadDependenciesSymbolTables(libraries)

    val mainSymbolTable: SymbolTable
    mainSymbolTable = if (disableMergeInLib) {
        // Merge all the symbols together.
        // We have to rewrite the IDs because some published R.txt inside AARs are using the
        // wrong value for some types, and we need to ensure there is no collision in the
        // file we are creating.
        SymbolUtils.mergeAndRenumberSymbols(
                finalPackageName, librarySymbols, depSymbolTables, platformSymbols)
    } else {
        librarySymbols.rename(finalPackageName)
    }

    // Generate R.txt file.
    Files.createDirectories(symbolFileOut.toPath().parent)
    SymbolIo.write(mainSymbolTable, symbolFileOut)

    val tablesToWrite = RGeneration.generateAllSymbolTablesToWrite(mainSymbolTable, depSymbolTables)

    if (sourceOut != null) {
        FileUtils.cleanOutputDir(sourceOut)
        // Generate R.java files for main and dependencies
        tablesToWrite.forEach { SymbolIo.exportToJava(it, sourceOut, false) }
    }

    if (rClassOutputJar != null) {
        FileUtils.deleteIfExists(rClassOutputJar)
        exportToCompiledJava(tablesToWrite, rClassOutputJar.toPath())
    }
}

