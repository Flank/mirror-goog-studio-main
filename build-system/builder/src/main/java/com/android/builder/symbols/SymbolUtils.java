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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.internal.aapt.AaptPackageConfig.LibraryInfo;
import com.android.ide.common.xml.AndroidManifestParser;
import com.android.ide.common.xml.ManifestData;
import com.android.io.FileWrapper;
import com.android.io.StreamException;
import com.android.xml.AndroidManifest;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/** Helper methods related to Symbols and resource processing. */
public final class SymbolUtils {

    /**
     * Processes the symbol table and generates necessary files: R.txt, R.java and proguard rules
     * ({@code aapt_rules.txt}). Afterwards generates {@code R.java} for all libraries the main
     * library depends on.
     *
     * @param librarySymbols table with symbols of resources for the library.
     * @param libraries libraries which this library depends on
     * @param enforceUniquePackageName should the package name be unique in the project
     * @param mainPackageName package name of this library
     * @param manifestFile manifest file
     * @param sourceOut directory to contain R.java
     * @param symbolsOut directory to contain R.txt
     * @param proguardOut directory to contain proguard rules
     * @param mergedResources directory containing merged resources
     */
    public static void processLibraryMainSymbolTable(
            @NonNull final SymbolTable librarySymbols,
            @NonNull List<LibraryInfo> libraries,
            boolean enforceUniquePackageName,
            @Nullable String mainPackageName,
            @NonNull File manifestFile,
            @NonNull File sourceOut,
            @NonNull File symbolsOut,
            @Nullable File proguardOut,
            @Nullable File mergedResources)
            throws IOException {

        Preconditions.checkNotNull(sourceOut, "Source output directory should not be null");
        Preconditions.checkNotNull(symbolsOut, "Symbols output directory should not be null");

        // Parse the manifest only when necessary.
        if (mainPackageName == null || proguardOut != null) {
            ManifestData manifestData = SymbolUtils.parseManifest(manifestFile);

            if (mainPackageName == null) {
                mainPackageName = getPackageNameFromManifest(manifestData);
            }
            // Generate aapt_rules.txt containing keep rules if minify is enabled.
            if (proguardOut != null) {
                Files.write(
                        proguardOut.toPath(),
                        generateMinifyKeepRules(manifestData, mergedResources));
            }
        }

        // Get symbol tables of the libraries we depend on.
        Set<SymbolTable> depSymbolTables =
                loadDependenciesSymbolTables(libraries, enforceUniquePackageName, mainPackageName);
        // This will produce a symbol table with conflicting IDs but that doesn't matter for
        // compilation.
        // Use a map keyed by the symbol name and type to de-duplicate symbols coming from
        // libraries.
        HashMap<String, Symbol> symbols = new HashMap<>();
        symbols.putAll(librarySymbols.getSymbols());
        for (SymbolTable depSymbolTable : depSymbolTables) {
            symbols.putAll(depSymbolTable.getSymbols());
        }
        SymbolTable mainSymbolTable =
                SymbolTable.builder()
                        .tablePackage(mainPackageName)
                        .addAll(symbols.values())
                        .build();

        // Generate R.txt file.
        generateRTxt(mainSymbolTable, symbolsOut);

        // Generate R.java file.
        SymbolIo.exportToJava(mainSymbolTable, sourceOut, false);

        // Generate the R.java files for each individual library.
        RGeneration.generateRForLibraries(mainSymbolTable, depSymbolTables, sourceOut, false);
    }

    /**
     * Load symbol tables of each library on which the main library/application depends on.
     *
     * @param libraries libraries which the main library/application depends on
     * @param enforceUniquePackageName should the package name be unique in the project
     * @param mainPackageName package name of the main library/application
     * @return a set of of symbol table for each library
     */
    @NonNull
    public static Set<SymbolTable> loadDependenciesSymbolTables(
            @NonNull List<LibraryInfo> libraries,
            boolean enforceUniquePackageName,
            @NonNull String mainPackageName)
            throws IOException {

        // For each dependency, load its symbol file.
        Set<SymbolTable> depSymbolTables = new HashSet<>();
        for (LibraryInfo dependency : libraries) {
            File depMan = dependency.getManifest();
            String depPackageName;

            try {
                depPackageName = AndroidManifest.getPackage(new FileWrapper(depMan));
            } catch (StreamException e) {
                throw new RuntimeException(
                        "Failed to read manifest " + depMan.getAbsolutePath(), e);
            }

            if (mainPackageName.equals(depPackageName) && enforceUniquePackageName) {
                throw new RuntimeException(
                        String.format(
                                "Error: A library uses the same package as this project: %s",
                                depPackageName));
            }

            File rFile = dependency.getSymbolFile();
            SymbolTable depSymbols =
                    (rFile != null && rFile.exists())
                            ? SymbolIo.read(rFile)
                            : SymbolTable.builder().build();
            depSymbols = depSymbols.rename(depPackageName);
            depSymbolTables.add(depSymbols);
        }

        return depSymbolTables;
    }

    /**
     * Pulls out the package name from the given android manifest.
     *
     * @param manifestFile manifest file of the library
     * @return package name held in the manifest
     * @throws IOException if there is a problem reading the manifest or if the manifest does not
     *     contain a package name
     */
    @NonNull
    public static String getPackageNameFromManifest(@NonNull File manifestFile) throws IOException {
        ManifestData manifestData;
        try {
            manifestData = AndroidManifestParser.parse(new FileWrapper(manifestFile));
        } catch (SAXException | IOException e) {
            throw new IOException(
                    "Failed to parse android manifest XML file at path: '"
                            + manifestFile.getAbsolutePath(),
                    e);
        }
        return getPackageNameFromManifest(manifestData);
    }

    /**
     * Pulls out the package name from the given parsed android manifest.
     *
     * @param manifest the parsed manifest of the library
     * @return package name held in the manifest
     */
    @NonNull
    public static String getPackageNameFromManifest(@NonNull ManifestData manifest) {
        return manifest.getPackage();
    }

    /**
     * Generates keep rules based on the nodes declared in the manifest file.
     *
     * <p>Used in the new resource processing, since aapt is not used in processing libraries'
     * resources and the {@code aapt_rules.txt} file and its rules are required by minify.
     *
     * <p>Goes through all {@code application}, {@code instrumentation}, {@code activity}, {@code
     * service}, {@code provider} and {@code receiver} keep class data in the manifest, generates
     * keep rules for each of them and returns them as a list.
     *
     * <p>For examples refer to {@code SymbolUtilsTest.java}.
     *
     * @param manifest containing keep class data
     */
    public static List<String> generateMinifyKeepRules(
            @NonNull ManifestData manifest,
            @Nullable File mergedResources) throws IOException {
        return generateKeepRules(manifest, false, mergedResources);
    }

    /**
     * Generates keep rules based on the nodes declared in the manifest file.
     *
     * <p>When AAPT2 is enabled, this method is called to generate {@code manifest_keep.txt} file
     * for Dex. Goes through all {@code application}, {@code instrumentation}, {@code activity},
     * {@code service}, {@code provider} and {@code receiver} nodes and generates keep rules for
     * each of them, as long as the node doesn't declare it belongs to a private process. Returns
     * the keep rules as a list.
     *
     * <p>For examples refer to {@code SymbolUtilsTest.java}.
     *
     * @param manifest containing keep class data
     */
    public static List<String> generateMainDexKeepRules(
            @NonNull ManifestData manifest,
            @Nullable File mergedResources) throws IOException {
        return generateKeepRules(manifest, true, mergedResources);
    }

    @VisibleForTesting
    static List<String> generateKeepRules(
            @NonNull ManifestData manifest,
            boolean isMainDex,
            @Nullable File mergedResources) throws IOException {
        ArrayList<String> rules = new ArrayList();
        rules.add("# Generated by the gradle plugin");

        // Find all the rules based on the AndroidManifest
        for (ManifestData.KeepClass keepClass : manifest.getKeepClasses()) {
            if (isMainDex) {
                // When creating keep rules for Dex, we should sometimes omit some activity, service
                // provider and receiver nodes. It is based on the process declared in their node or,
                // if none was specified or was empty, on the default process of the application.
                // If the process was not declared, was empty or starts with a colon symbol (last
                // case meaning private process), we do not need to keep that class.
                String type = keepClass.getType();
                String process = keepClass.getProcess();
                if ((type == AndroidManifest.NODE_ACTIVITY
                                || type == AndroidManifest.NODE_SERVICE
                                || type == AndroidManifest.NODE_PROVIDER
                                || type == AndroidManifest.NODE_RECEIVER)
                        && (process == null || process.isEmpty() || process.startsWith(":"))) {
                    continue;
                }
            }
            rules.add(String.format("-keep class %s { <init>(...); }", keepClass.getName()));
        }

        // Now go through all the layout files and find classes that need to be kept
        if (mergedResources != null) {
            try {
                DocumentBuilder documentBuilder;
                DocumentBuilderFactory documentBuilderFactory =
                        DocumentBuilderFactory.newInstance();
                documentBuilder = documentBuilderFactory.newDocumentBuilder();

                for (File typeDir : mergedResources.listFiles()) {
                    if (typeDir.isDirectory() && typeDir.getName().startsWith("layout")) {
                        for (File layoutXml : typeDir.listFiles()) {
                            if (layoutXml.isFile()) {
                                generateKeepRulesFromLayoutXmlFile(
                                        layoutXml, documentBuilder, rules);
                            }
                        }
                    }
                }
            } catch (ParserConfigurationException e) {
                throw new IOException("Failed to read merged resources", e);
            }


        }

        Collections.sort(rules);
        return rules;
    }

    static void generateKeepRulesFromLayoutXmlFile(
            @NonNull File layout,
            @NonNull DocumentBuilder documentBuilder,
            @NonNull List<String> rules) throws IOException {

        try {
            Document xmlDocument = documentBuilder.parse(layout);
            Element root = xmlDocument.getDocumentElement();
            if (root != null) {
                generateKeepRulesFromXmlNode(root, rules);
            }
        } catch (SAXException|IOException e) {
            throw new IOException(
                    "Failed to parse XML resource file " + layout.getAbsolutePath(), e);
        }
    }

    private static void generateKeepRulesFromXmlNode(
            @NonNull Element node,
            @NonNull List<String> rules) {

        String tag = node.getTagName();
        if (tag.contains(".")) {
            rules.add(String.format("-keep class %s { <init>(...); }", tag));
        }

        Node current = node.getFirstChild();
        while (current != null) {
            if (current.getNodeType() == Node.ELEMENT_NODE) {
                // handle its children
                generateKeepRulesFromXmlNode((Element) current, rules);
            }
            current = current.getNextSibling();
        }
    }

    /**
     * Creates and writes a symbol table into an R.txt file in a given directory.
     *
     * @param table the table
     * @param directory the directory where the table should be written
     */
    public static void generateRTxt(@NonNull SymbolTable table, @NonNull File directory) {
        File file = new File(directory, SymbolTable.R_CLASS_NAME + SdkConstants.DOT_TXT);
        SymbolIo.write(table, file);
    }

    public static ManifestData parseManifest(@NonNull File manifestFile) throws IOException {
        try {
            return AndroidManifestParser.parse(new FileWrapper(manifestFile));
        } catch (SAXException | IOException e) {
            throw new IOException(
                    "Failed to parse android manifest XML file at path: '"
                            + manifestFile.getAbsolutePath(),
                    e);
        }
    }

    /**
     * Updates the value resource name to mimic aapt's behaviour - replaces all dots and colons with
     * underscores.
     *
     * <p>If the name contains whitespaces or other illegal characters, they are not checked in this
     * method, but caught in the Symbol constructor call to {@link Symbol#validateSymbol}.
     *
     * @param name the resource name to be updated
     * @return a valid resource name
     */
    @NonNull
    public static String canonicalizeValueResourceName(@NonNull String name) {
        return CharMatcher.anyOf(".:").replaceFrom(name, "_");
    }

    public static enum SymbolTableGenerationMode {
        /** The main symbol table loaded from a merge of all the libraries. */
        FROM_MERGED_RESOURCES,
        /** The main symbol table was loaded from the resources in this library. */
        ONLY_PACKAGED_RESOURCES,
    }
}
