/*
 * Copyright (C) 2020 The Android Open Source Project
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

@file:Suppress("SameParameterValue")

package com.android.tools.lint.model

import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.VALUE_TRUE
import com.android.ide.common.repository.GradleVersion
import com.android.sdklib.AndroidVersion
import com.android.utils.XmlUtils
import com.google.common.base.Charsets
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParser.END_DOCUMENT
import org.xmlpull.v1.XmlPullParser.END_TAG
import org.xmlpull.v1.XmlPullParser.START_TAG
import org.xmlpull.v1.XmlPullParserException
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.Reader
import java.io.Writer

// TODO: Switch to kotlinx.serialization
//  (https://github.com/Kotlin/kotlinx.serialization)
// but this requires a Kotlin compiler plugin, requiring some changes to the build system,
// so avoiding it temporarily to unblock integrating by writing boring manual encoding
// and decoding code here

object LmSerialization {
    interface LmSerializationAdapter {
        /**
         * The file we're reading/writing from if known (only used for error message display)
         *
         * If [variantName] is not null, returns the path to the variant file, otherwise
         * it's the module file.
         */
        fun file(variantName: String?): File? = null

        /** The root folder for the project; used only for making paths relative in persistence */
        fun root(): File? = null

        /** Returns the reader to use for the given variant, or if null, the module itself */
        fun getReader(variantName: String?): Reader

        /** Returns the writer to use for the given variant, or if null, the module itself */
        fun getWriter(variantName: String?): Writer
    }

    /** Default implementation of [LmSerializationAdapter] which uses files */
    class LmSerializationFileAdapter(private val moduleFile: File) : LmSerializationAdapter {
        override fun root(): File {
            return moduleFile.parentFile
        }

        override fun file(variantName: String?): File {
            return if (variantName != null) {
                File(root(), getVariantFileName(moduleFile, variantName))
            } else {
                moduleFile
            }
        }

        override fun getReader(variantName: String?): Reader {
            val file = file(variantName)
            return BufferedReader(InputStreamReader(FileInputStream(file), Charsets.UTF_8))
        }

        override fun getWriter(variantName: String?): Writer {
            val file = file(variantName)
            file.delete()
            file.parentFile?.mkdirs()
            return BufferedWriter(OutputStreamWriter(FileOutputStream(file), Charsets.UTF_8))
        }
    }

    /**
     * Reads an XML descriptor from the given [xmlFile] and returns a lint model.
     */
    fun read(xmlFile: File): LmModule {
        return read(LmSerializationFileAdapter(xmlFile))
    }

    /**
     * Reads an XML description from a [reader] of a model and returns a lint model.
     * If [variantNames] is not null, limit the variants read into the model to just
     * the specified ones.
     */
    fun read(reader: LmSerializationAdapter, variantNames: List<String>? = null): LmModule {
        return LmModuleReader(reader).readModule(variantNames)
    }

    /**
     * Writes a lint [module] to the given [destination]. If [writeVariants] is not null,
     * it will also write the given variants into files next to the module file. By
     * default this includes all module variants.
     *
     * If applicable, you can also record which tool wrote this file (in the case of lint
     * for example, use LintClient.getClientDisplayRevision()) via the [createdBy] string.
     * Writes a lint [module] to the given [destination]
     */
    fun write(
        module: LmModule,
        destination: LmSerializationAdapter,
        writeVariants: List<LmVariant>? = module.variants,
        createdBy: String? = null
    ) {
        LmModuleWriter(destination).writeModule(module, writeVariants, createdBy)
    }

    /**
     * Writes a lint [variant] to the given [writer]
     */
    fun write(
        variant: LmVariant,
        writer: LmSerializationAdapter,
        createdBy: String? = null
    ) {
        LmVariantWriter(writer, variant.name).writeVariant(variant, createdBy)
    }

    /**
     * Writes a lint [module] to the given [destination]. If [writeVariants] is not null,
     * it will also write the given variants into files next to the module file. By
     * default this includes all module variants.
     *
     * If applicable, you can also record which tool wrote this file (in the case of lint
     * for example, use LintClient.getClientDisplayRevision()) via the [createdBy] string.
     */
    fun write(
        module: LmModule,
        destination: File,
        writeVariants: List<LmVariant>? = module.variants,
        createdBy: String? = null
    ) {
        write(module, LmSerializationFileAdapter(destination), writeVariants, createdBy)
    }

    /**
     * Writes a lint [variant] to the given [destination]. If applicable, you can also
     * record which tool wrote this file (in the case of lint for example, use
     * LintClient.getClientDisplayRevision()).
     */
    fun write(
        variant: LmVariant,
        destination: File,
        createdBy: String? = null
    ) {
        write(variant, LmSerializationFileAdapter(destination), createdBy)
    }

    private fun getVariantFileName(moduleFile: File, variantName: String) =
        "${moduleFile.name}-${variantName}$DOT_XML"
}

private open class LmWriter(
    protected val adapter: LmSerialization.LmSerializationAdapter,
    protected val printer: PrintWriter
) {
    protected var root: File? = adapter.root()

    protected fun indent(level: Int) {
        for (i in 0 until level) {
            printer.print("  ")
        }
    }

    protected fun PrintWriter.printAttribute(name: String, value: String, indent: Int) {
        println()
        // +1: implicit, +1: hanging indent
        indent(indent + 2)
        print(name)
        print("=\"")
        print(XmlUtils.toXmlAttributeValue(value))
        print("\"")
    }

    protected fun PrintWriter.printName(name: String?, indent: Int) {
        name ?: return
        printAttribute("name", name, indent)
    }

    protected fun PrintWriter.printFile(
        name: String,
        file: File?,
        indent: Int,
        relativeTo: File? = root
    ) {
        file ?: return

        val fullPath = file.path
        val path = if (relativeTo != null && fullPath.startsWith(relativeTo.path)) {
            if (fullPath.length > relativeTo.path.length && fullPath[relativeTo.path
                    .length] == File.separatorChar
            )
                fullPath.substring(relativeTo.path.length + 1)
            else
                fullPath.substring(relativeTo.path.length)
        } else {
            fullPath
        }
        printAttribute(name, path, indent)
    }

    protected fun PrintWriter.printFiles(
        name: String,
        path: Collection<File>?,
        indent: Int,
        relativeTo: File? = root
    ) {
        path ?: return
        if (path.isEmpty()) {
            return
        }
        printAttribute(name, path.joinToString(File.pathSeparator) {
            val fullPath = it.path
            if (relativeTo != null && fullPath.startsWith(relativeTo.path)) {
                if (fullPath.length > relativeTo.path.length && fullPath[relativeTo.path
                        .length] == File.separatorChar
                )
                    fullPath.substring(relativeTo.path.length + 1)
                else
                    fullPath.substring(relativeTo.path.length)
            } else {
                fullPath
            }
        }, indent)
    }

    protected fun PrintWriter.printStrings(
        name: String,
        strings: Collection<String>,
        indent: Int,
        separator: String = ","
    ) {
        if (strings.isEmpty()) {
            return
        }
        printAttribute(name, strings.joinToString(separator), indent)
    }

    protected fun writeSourceProviders(
        sourceProviders: List<LmSourceProvider>,
        tag: String,
        indent: Int
    ) {
        indent(indent)
        printer.print("<")
        printer.print(tag)
        printer.println(">")
        for (sourceProvider in sourceProviders) {
            writeSourceProvider(sourceProvider, indent + 1)
        }
        indent(indent)
        printer.print("</")
        printer.print(tag)
        printer.println(">")
    }

    protected fun writeSourceProvider(
        sourceProvider: LmSourceProvider,
        indent: Int,
        tag: String = "sourceProvider"
    ) {
        indent(indent)
        printer.print("<")
        printer.print(tag)
        printer.printFile("manifest", sourceProvider.manifestFile, indent)
        printer.printFiles("javaDirectories", sourceProvider.javaDirectories, indent)
        printer.printFiles("resDirectories", sourceProvider.resDirectories, indent)
        printer.printFiles("assetsDirectories", sourceProvider.assetsDirectories, indent)
        if (sourceProvider.isInstrumentationTest()) {
            printer.printAttribute("androidTest", VALUE_TRUE, indent)
        }
        if (sourceProvider.isUnitTest()) {
            printer.printAttribute("unitTest", VALUE_TRUE, indent)
        }
        if (sourceProvider.isDebugOnly()) {
            printer.printAttribute("debugOnly", VALUE_TRUE, indent)
        }
        printer.println("/>")
    }
}

private class LmModuleWriter(
    adapter: LmSerialization.LmSerializationAdapter
) : LmWriter(adapter, PrintWriter(adapter.getWriter(variantName = null))) {
    fun writeModule(
        module: LmModule,
        writeVariants: List<LmVariant>? = module.variants,
        createdBy: String? = null
    ) {
        this.root = module.dir // write paths relative to the module dir

        val indent = 0
        printer.print("<lint-module")
        printer.printAttribute("format", "1", indent)
        printer.printFile("dir", module.dir, indent)
        printer.printName(module.moduleName, indent)
        printer.printAttribute("type", module.type.name, indent)
        createdBy?.let { printer.printAttribute("createdBy", it, indent) }

        module.mavenName?.let { printer.printAttribute("maven", it.toString(), indent) }
        module.gradleVersion?.let { printer.printAttribute("gradle", it.toString(), indent) }
        printer.printFile("buildFolder", module.buildFolder, indent)
        module.resourcePrefix?.let { printer.printAttribute("resourcePrefix", it, indent) }
        printer.printStrings("dynamicFeatures", module.dynamicFeatures, indent)
        printer.printFiles("bootClassPath", module.bootClassPath, indent)
        printer.printAttribute("javaSourceLevel", module.javaSourceLevel, indent)
        printer.printAttribute("compileTarget", module.compileTarget, indent)
        if (module.neverShrinking()) {
            printer.printAttribute("neverShrinking", VALUE_TRUE, indent)
        }
        printer.println(">")

        writeBuildFeatures(module.buildFeatures, indent + 1)

        writeLintOptions(module.lintOptions, indent + 1)

        if (writeVariants != null) {
            for (variant in writeVariants) {
                writeVariantReference(variant, indent + 1)

                LmSerialization.write(variant, adapter, createdBy)
            }
        }

        printer.println("</lint-module>")
    }

    private fun writeVariantReference(
        variant: LmVariant,
        indent: Int
    ) {
        indent(indent)
        printer.print("<variant name=\"")
        printer.print(variant.name)
        printer.println("\"/>")
    }

    private fun writeBuildFeatures(
        buildFeatures: LmBuildFeatures,
        indent: Int
    ) {
        indent(indent)
        printer.print("<buildFeatures")
        if (buildFeatures.coreLibraryDesugaringEnabled) {
            printer.printAttribute("coreLibraryDesugaring", VALUE_TRUE, indent)
        }
        if (buildFeatures.viewBinding) {
            printer.printAttribute("viewBinding", VALUE_TRUE, indent)
        }
        if (buildFeatures.namespacingMode != LmNamespacingMode.DISABLED) {
            printer.printAttribute("namespacing", buildFeatures.namespacingMode.name, indent)
        }
        printer.println("/>")
    }

    private fun writeLintOptions(
        lintOptions: LmLintOptions,
        indent: Int
    ) {
        indent(indent)
        printer.print("<lintOptions")

        printer.printFile("lintConfig", lintOptions.lintConfig, indent)
        printer.printFile("baselineFile", lintOptions.baselineFile, indent)

        if (lintOptions.checkDependencies) {
            printer.printAttribute("checkDependencies", VALUE_TRUE, indent)
        }
        if (lintOptions.checkTestSources) {
            printer.printAttribute("checkTestSources", VALUE_TRUE, indent)
        }

        if (lintOptions.disable.isNotEmpty()) {
            printer.printStrings("disable", lintOptions.disable, indent)
        }
        if (lintOptions.enable.isNotEmpty()) {
            printer.printStrings("enable", lintOptions.enable, indent)
        }
        if (lintOptions.check?.isNotEmpty() == true) {
            printer.printStrings("check", lintOptions.disable, indent)
        }
        if (lintOptions.abortOnError) {
            printer.printAttribute("abortOnError", VALUE_TRUE, indent)
        }
        if (lintOptions.absolutePaths) {
            printer.printAttribute("absolutePaths", VALUE_TRUE, indent)
        }
        if (lintOptions.noLines) {
            printer.printAttribute("noLines", VALUE_TRUE, indent)
        }
        if (lintOptions.quiet) {
            printer.printAttribute("isQuiet", VALUE_TRUE, indent)
        }
        if (lintOptions.checkAllWarnings) {
            printer.printAttribute("checkAllWarnings", VALUE_TRUE, indent)
        }
        if (lintOptions.ignoreWarnings) {
            printer.printAttribute("ignoreWarnings", VALUE_TRUE, indent)
        }
        if (lintOptions.warningsAsErrors) {
            printer.printAttribute("warningsAsErrors", VALUE_TRUE, indent)
        }
        if (lintOptions.ignoreTestSources) {
            printer.printAttribute("ignoreTestSources", VALUE_TRUE, indent)
        }
        if (lintOptions.checkGeneratedSources) {
            printer.printAttribute("checkGeneratedSources", VALUE_TRUE, indent)
        }
        if (lintOptions.checkReleaseBuilds) {
            printer.printAttribute("checkReleaseBuilds", VALUE_TRUE, indent)
        }
        if (lintOptions.explainIssues) {
            printer.printAttribute("explainIssues", VALUE_TRUE, indent)
        }
        if (lintOptions.showAll) {
            printer.printAttribute("showAll", VALUE_TRUE, indent)
        }
        if (lintOptions.textReport) {
            printer.printAttribute("textReport", VALUE_TRUE, indent)
        }
        printer.printFile("textOutput", lintOptions.textOutput, indent)

        if (lintOptions.htmlReport) {
            printer.printAttribute("htmlReport", VALUE_TRUE, indent)
        }
        printer.printFile("htmlOutput", lintOptions.htmlOutput, indent)
        if (lintOptions.xmlReport) {
            printer.printAttribute("xmlReport", VALUE_TRUE, indent)
        }
        printer.printFile("xmlOutput", lintOptions.xmlOutput, indent)

        if (!writeSeverityOverrides(lintOptions.severityOverrides, indent + 1)) {
            printer.println("/>")
        } else {
            indent(indent)
            printer.println("</lintOptions>")
        }
    }

    private fun writeSeverityOverrides(
        severityOverrides: Map<String, LmSeverity>?,
        indent: Int
    ): Boolean {
        severityOverrides ?: return false
        if (severityOverrides.isEmpty()) return false

        printer.println(">")
        indent(indent)
        printer.println("<severities>")
        severityOverrides.asSequence().sortedBy { it.key }.forEach { (id, severity) ->
            indent(indent + 1)
            printer.print("<severity")
            printer.printAttribute("id", id, indent)
            printer.printAttribute("severity", severity.name, indent)
            printer.println(" />")
        }
        indent(indent)
        printer.println("</severities>")
        return true
    }
}

private class LmVariantWriter(
    adapter: LmSerialization.LmSerializationAdapter,
    variantName: String
) : LmWriter(adapter, PrintWriter(adapter.getWriter(variantName))) {
    fun writeVariant(
        variant: LmVariant,
        createdBy: String? = null
    ) {
        this.root = variant.module.dir // write paths relative to the module dir
        val indent = 0
        indent(indent)
        printer.print("<variant")
        printer.printName(variant.name, indent)
        createdBy?.let { printer.printAttribute("createdBy", it, indent) }
        if (variant.useSupportLibraryVectorDrawables) {
            printer.printAttribute("useSupportLibraryVectorDrawables", VALUE_TRUE, indent)
        }
        // These used to be on the mergedFlavor
        variant.`package`?.let { printer.printAttribute("package", it, indent) }
        variant.versionCode?.let { printer.printAttribute("versionCode", it.toString(), indent) }
        variant.versionName?.let { printer.printAttribute("versionName", it, indent) }
        variant.minSdkVersion?.let { printer.printAttribute("minSdkVersion", it.apiString, indent) }
        variant.targetSdkVersion
            ?.let { printer.printAttribute("targetSdkVersion", it.apiString, indent) }
        if (variant.debuggable) {
            printer.printAttribute("debuggable", VALUE_TRUE, indent)
        }
        if (variant.shrinkable) {
            printer.printAttribute("shrinking", VALUE_TRUE, indent)
        }
        printer.printFiles("proguardFiles", variant.proguardFiles, indent)
        printer.printFiles("consumerProguardFiles", variant.consumerProguardFiles, indent)
        printer.printStrings("resourceConfigurations", variant.resourceConfigurations, indent)
        printer.println(">")

        writeSourceProviders(variant.sourceProviders, "sourceProviders", indent + 1)
        writeSourceProviders(variant.testSourceProviders, "testSourceProviders", indent + 1)

        writeResValues(variant.resValues, indent + 1)
        writeManifestPlaceholders(variant.manifestPlaceholders, indent + 1)

        writeArtifact(variant.mainArtifact, "mainArtifact", indent + 1)
        variant.androidTestArtifact?.let { artifact ->
            writeArtifact(artifact, "androidTestArtifact", indent + 1)
        }
        variant.testArtifact?.let { artifact ->
            writeArtifact(artifact, "testArtifact", indent + 1)
        }
        indent(indent)
        printer.println("</variant>")
    }

    private fun writeManifestPlaceholders(manifestPlaceholders: Map<String, String>, indent: Int) {
        if (manifestPlaceholders.isEmpty()) {
            return
        }
        indent(indent)
        printer.println("<manifestPlaceholders>")
        manifestPlaceholders.asSequence().sortedBy { it.key }.forEach {
            val key = it.key
            val value = it.value
            indent(indent + 1)
            printer.print("<placeholder")
            printer.printName(key, indent + 1)
            printer.printAttribute("value", value, indent + 1)
            printer.println(" />")
        }
        indent(indent)
        printer.println("</manifestPlaceholders>")
    }

    private fun writeResValues(resValues: Map<String, LmResourceField>, indent: Int) {
        if (resValues.isEmpty()) {
            return
        }
        indent(indent)
        printer.println("<resValues>")
        resValues.asSequence().sortedBy { it.key }.forEach {
            indent(indent + 1)
            printer.print("<resValue")
            val resourceField = it.value
            printer.printAttribute("type", resourceField.type, indent + 1)
            printer.printName(resourceField.name, indent + 1)
            printer.printAttribute("value", resourceField.value, indent + 1)
            printer.println(" />")
        }
        indent(indent)
        printer.println("</resValues>")
    }

    private fun writeArtifact(artifact: LmArtifact, tag: String, indent: Int) {
        indent(indent)
        printer.print("<")
        printer.print(tag)

        printer.printName(artifact.name, indent)
        printer.printFiles("classFolders", artifact.classFolders, indent)
        if (artifact is LmAndroidArtifact) {
            printer.printAttribute("applicationId", artifact.applicationId, indent)
            printer.printFiles("generatedSourceFolders", artifact.generatedSourceFolders, indent)
            printer.printFiles(
                "generatedResourceFolders",
                artifact.generatedResourceFolders,
                indent
            )
        }
        printer.println(">")

        writeDependencies(artifact.dependencies, indent + 1)

        indent(indent)
        printer.print("</")
        printer.print(tag)
        printer.println(">")
    }

    private fun writeDependencies(dependencies: LmDependencies, indent: Int) {
        indent(indent)
        printer.println("<dependencies>")

        for (library in dependencies.direct) {
            writeLibrary(library, indent + 1)
        }

        indent(indent)
        printer.println("</dependencies>")
    }

    private fun writeLibrary(library: LmLibrary, indent: Int) {
        indent(indent)
        printer.print("<library")
        printer.printFiles("jars", library.jarFiles, indent)
        library.project?.let { printer.printAttribute("project", it, indent) }
        library.requestedCoordinates
            ?.let { printer.printAttribute("requested", it.toString(), indent) }
        printer.printAttribute("resolved", library.resolvedCoordinates.toString(), indent)
        if (library.provided) {
            printer.printAttribute("provided", VALUE_TRUE, indent)
        }
        if (library.skipped) {
            printer.printAttribute("skipped", VALUE_TRUE, indent)
        }
        if (library is LmAndroidLibrary) {
            printer.printFile("folder", library.folder, indent)
            printer.printFile("manifest", library.manifest, indent, library.folder)
            printer.printFile("resFolder", library.resFolder, indent, library.folder)
            printer.printFile("assetsFolder", library.assetsFolder, indent, library.folder)
            printer.printFile("lintJar", library.lintJar, indent, library.folder)
            printer.printFile("publicResources", library.publicResources, indent, library.folder)
            printer.printFile("symbolFile", library.symbolFile, indent, library.folder)
            printer.printFile(
                "externalAnnotations",
                library.externalAnnotations,
                indent,
                library.folder
            )
            printer.printFile("proguardRules", library.proguardRules, indent, library.folder)
            library.projectId?.let { printer.printAttribute("projectId", it, indent) }
        }

        if (library.dependencies.isEmpty()) {
            printer.println("/>")
        } else {
            printer.println(">")
            indent(indent + 1)
            printer.println("<dependencies>")
            for (dep in library.dependencies) {
                writeLibrary(dep, indent + 2)
            }
            indent(indent + 1)
            printer.println("</dependencies>")
            indent(indent)
            printer.println("</library>")
        }
    }
}

private abstract class LmReader(
    protected val adapter: LmSerialization.LmSerializationAdapter,
    reader: Reader
) {
    protected abstract val path: String
    protected var root: File? = adapter.root()
    protected val parser = KXmlParser()

    init {
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(reader)
    }

    protected fun String.toApiVersion(): AndroidVersion {
        return AndroidVersion(this)
    }

    protected fun String.toMavenCoordinate(): LmMavenName? {
        return LmMavenName.parse(this)
    }

    protected fun getLocation(): String {
        return "$path:${parser.lineNumber}"
    }

    protected fun expectTag(name: String) {
        if (name != parser.name) {
            error("Unexpected tag `<${parser.name}>` at ${getLocation()}; expected `<$name>`")
        }
    }

    protected fun unexpectedTag() {
        error("Unexpected tag `<${parser.name}>` at ${getLocation()}")
    }

    protected fun missingData() {
        error("Missing data at ${getLocation()}")
    }

    protected fun finishTag(name: String) {
        while (parser.next() != END_DOCUMENT) {
            val eventType = parser.eventType
            if (eventType == START_TAG) {
                unexpectedTag()
            } else if (eventType == END_TAG) {
                expectTag(name)
                break
            }
        }
    }

    protected fun getOptionalAttribute(name: String): String? {
        return parser.getAttributeValue(null, name)
    }

    protected fun getRequiredAttribute(name: String): String {
        return parser.getAttributeValue(null, name)
            ?: error("Expected `$name` attribute in <${parser.name}> tag at ${getLocation()}")
    }

    protected fun getName(): String = getRequiredAttribute("name")

    protected fun getOptionalFile(attribute: String): File? {
        val path = getOptionalAttribute(attribute) ?: return null
        val file = File(path)
        return if (root != null && !file.isAbsolute) {
            File(root, path)
        } else {
            file
        }
    }

    protected fun getRequiredFile(attribute: String, relativeTo: File? = root): File {
        val path = getRequiredAttribute(attribute)
        val file = File(path)
        return if (relativeTo != null && !file.isAbsolute) {
            File(relativeTo, path)
        } else {
            file
        }
    }

    protected fun getOptionalBoolean(
        attribute: String,
        default: Boolean
    ): Boolean {
        val value = getOptionalAttribute(attribute) ?: return default
        return value == VALUE_TRUE
    }

    // Reverses [printStrings]
    protected fun getStrings(name: String, separator: String = ","): List<String> {
        return getOptionalAttribute(name)?.split(separator) ?: emptyList()
    }

    // Reverses [printFiles]
    protected fun getFiles(name: String): List<File> {
        return getOptionalAttribute(name)?.split(File.pathSeparator)?.map { path ->
            val file = File(path)
            if (root != null && !file.isAbsolute) {
                File(root, path)
            } else {
                file
            }
        } ?: emptyList()
    }

    protected fun readSourceProvider(tag: String = "sourceProvider"): LmSourceProvider {
        expectTag(tag)
        val manifestFile = getRequiredFile("manifest")
        val javaDirectories = getFiles("javaDirectories")
        val resDirectories = getFiles("resDirectories")
        val assetsDirectories = getFiles("assetsDirectories")
        val androidTestOnly = getOptionalBoolean("androidTest", false)
        val unitTestOnly = getOptionalBoolean("unitTest", false)
        val debugOnly = getOptionalBoolean("debugOnly", false)
        finishTag(tag)

        return DefaultLmSourceProvider(
            manifestFile = manifestFile,
            javaDirectories = javaDirectories,
            resDirectories = resDirectories,
            assetsDirectories = assetsDirectories,
            debugOnly = debugOnly,
            instrumentationTestOnly = androidTestOnly,
            unitTestOnly = unitTestOnly
        )
    }

    protected fun readSourceProviders(tag: String): List<LmSourceProvider> {
        expectTag(tag)

        val sourceProviders = mutableListOf<LmSourceProvider>()

        while (parser.next() != END_DOCUMENT) {
            val eventType = parser.eventType
            if (eventType == START_TAG) {
                when (parser.name) {
                    "sourceProvider" -> sourceProviders.add(readSourceProvider())
                    else -> unexpectedTag()
                }
            } else if (eventType == END_TAG) {
                expectTag(tag)
                break
            }
        }

        return sourceProviders
    }
}

private class LmModuleReader(
    adapter: LmSerialization.LmSerializationAdapter
) : LmReader(adapter, adapter.getReader(variantName = null)) {
    override val path: String = adapter.file(null)?.path ?: "<unknown>"

    private fun readBuildFeatures(): LmBuildFeatures {
        expectTag("buildFeatures")
        val coreLibraryDesugaringEnabled = getOptionalBoolean("coreLibraryDesugaring", false)
        val viewBinding = getOptionalBoolean("viewBinding", false)
        val namespacingMode =
            getOptionalAttribute("nameSpacingMode")?.let { LmNamespacingMode.valueOf(it) }
                ?: LmNamespacingMode.DISABLED

        finishTag("buildFeatures")
        return DefaultLmBuildFeatures(
            viewBinding = viewBinding,
            coreLibraryDesugaringEnabled = coreLibraryDesugaringEnabled,
            namespacingMode = namespacingMode
        )
    }

    private fun readLintOptions(): LmLintOptions {
        expectTag("lintOptions")
        val isCheckTestSources = getOptionalBoolean("checkTestSources", false)
        val lintConfig = getOptionalFile("lintConfig")
        val isCheckDependencies = getOptionalBoolean("checkDependencies", false)
        val baselineFile = getOptionalFile("baselineFile")
        var severityOverrides: Map<String, LmSeverity>? = null
        val enable = getStrings("enable").let {
            if (it.isNotEmpty()) it.toSet() else emptySet()
        }
        val disable = getStrings("disable").let {
            if (it.isNotEmpty()) it.toSet() else emptySet()
        }
        val check = getStrings("check").let {
            if (it.isNotEmpty()) it.toSet() else null
        }
        val abortOnError = getOptionalBoolean("abortOnError", false)
        val absolutePaths = getOptionalBoolean("absolutePaths", false)
        val noLines = getOptionalBoolean("noLines", false)
        val quiet = getOptionalBoolean("quiet", false)
        val checkAllWarnings = getOptionalBoolean("checkAllWarnings", false)
        val ignoreWarnings = getOptionalBoolean("ignoreWarnings", false)
        val warningsAsErrors = getOptionalBoolean("warningsAsErrors", false)
        val ignoreTestSources = getOptionalBoolean("ignoreTestSources", false)
        val checkGeneratedSources = getOptionalBoolean("checkGeneratedSources", false)
        val checkReleaseBuilds = getOptionalBoolean("checkReleaseBuilds", false)
        val explainIssues = getOptionalBoolean("explainIssues", false)
        val showAll = getOptionalBoolean("showAll", false)
        val textReport = getOptionalBoolean("textReport", false)
        val textOutput = getOptionalFile("textOutput")
        val htmlReport = getOptionalBoolean("htmlReport", false)
        val htmlOutput = getOptionalFile("htmlOutput")
        val xmlReport = getOptionalBoolean("xmlReport", false)
        val xmlOutput = getOptionalFile("xmlOutput")

        while (parser.next() != END_DOCUMENT) {
            val eventType = parser.eventType
            if (eventType == START_TAG) {
                when (parser.name) {
                    "severities" -> severityOverrides = readSeverities()
                    else -> unexpectedTag()
                }
            } else if (eventType == END_TAG) {
                expectTag("lintOptions")
                break
            }
        }

        return DefaultLmLintOptions(
            checkTestSources = isCheckTestSources,
            lintConfig = lintConfig,
            checkDependencies = isCheckDependencies,
            baselineFile = baselineFile,
            severityOverrides = severityOverrides,
            enable = enable,
            disable = disable,
            check = check,
            abortOnError = abortOnError,
            absolutePaths = absolutePaths,
            noLines = noLines,
            quiet = quiet,
            checkAllWarnings = checkAllWarnings,
            ignoreWarnings = ignoreWarnings,
            warningsAsErrors = warningsAsErrors,
            ignoreTestSources = ignoreTestSources,
            checkGeneratedSources = checkGeneratedSources,
            checkReleaseBuilds = checkReleaseBuilds,
            explainIssues = explainIssues,
            showAll = showAll,
            textReport = textReport,
            textOutput = textOutput,
            htmlReport = htmlReport,
            htmlOutput = htmlOutput,
            xmlReport = xmlReport,
            xmlOutput = xmlOutput
        )
    }

    private fun readSeverities(): Map<String, LmSeverity> {
        expectTag("severities")

        val map = mutableMapOf<String, LmSeverity>()
        while (parser.next() != END_DOCUMENT) {
            val eventType = parser.eventType
            if (eventType == START_TAG) {
                when (parser.name) {
                    "severity" -> {
                        val id = getRequiredAttribute("id")
                        val severityString = getRequiredAttribute("severity")
                        val severity = LmSeverity.fromName(severityString)
                            ?: error("Unexpected severity $severityString for id $id at ${getLocation()}")
                        map[id] = severity
                        finishTag("severity")
                    }
                    else -> unexpectedTag()
                }
            } else if (eventType == END_TAG) {
                expectTag("severities")
                break
            }
        }

        return map
    }

    fun readModule(variantNames: List<String>? = null): LmModule {
        try {
            parser.nextTag()
            expectTag("lint-module")
            val dir = getRequiredFile("dir")
            root = dir
            val name = getName()
            val type = LmModuleType.valueOf(getRequiredAttribute("type"))
            val mavenString = getOptionalAttribute("maven")?.let {
                LmMavenName.parse(it)
            }
            val gradleVersion = getOptionalAttribute("gradle")?.let { (GradleVersion.tryParse(it)) }

            val buildFolder = getRequiredFile("buildFolder")
            val resourcePrefix = getOptionalAttribute("resourcePrefix")
            val dynamicFeatures = getStrings("dynamicFeatures")
            val bootClassPath = getFiles("bootClassPath")
            val javaSourceLevel = getRequiredAttribute("javaSourceLevel")
            val compileTarget = getRequiredAttribute("compileTarget")
            val neverShrinking = getOptionalBoolean("neverShrinking", false)
            val variants = mutableListOf<LmVariant>()
            var lintOptions: LmLintOptions? = null
            var buildFeatures: LmBuildFeatures? = null

            loop@ while (parser.next() != END_DOCUMENT) {
                val eventType = parser.eventType
                if (eventType != START_TAG) {
                    continue
                }
                when (parser.name) {
                    "buildFeatures" -> buildFeatures = readBuildFeatures()
                    "lintOptions" -> lintOptions = readLintOptions()
                    "variant" -> break@loop
                    else -> unexpectedTag()
                }
            }

            if (lintOptions == null || buildFeatures == null) {
                missingData()
            }

            val module = DefaultLmModule(
                dir = dir,
                moduleName = name,
                type = type,
                mavenName = mavenString,
                gradleVersion = gradleVersion,
                buildFolder = buildFolder,
                lintOptions = lintOptions!!,
                buildFeatures = buildFeatures!!,
                resourcePrefix = resourcePrefix,
                dynamicFeatures = dynamicFeatures,
                bootClassPath = bootClassPath,
                javaSourceLevel = javaSourceLevel,
                compileTarget = compileTarget,
                neverShrinking = neverShrinking,

                // still empty list; will construct it below
                variants = variants,
                oldProject = null
            )

            // Always last; requires separate handling since we need to pass in the
            // constructed module to each variant
            if (parser.name == "variant") {
                readVariantReference(module, variantNames)?.let { variants.add(it) }
                while (parser.next() != END_DOCUMENT) {
                    val eventType = parser.eventType
                    if (eventType != START_TAG) {
                        continue
                    }
                    val tag = parser.name
                    if (tag == "variant") {
                        readVariantReference(module, variantNames)?.let { variants.add(it) }
                    } else {
                        unexpectedTag()
                    }
                }
            }

            return module
        } catch (e: XmlPullParserException) {
            error(e)
        }
    }

    private fun readVariantReference(
        module: LmModule,
        variantNames: List<String>? = null
    ): LmVariant? {
        expectTag("variant")
        val variantName = getName()
        finishTag("variant")
        return if (variantNames == null || variantNames.contains(variantName)) {
            val reader = LmVariantReader(adapter, variantName)
            reader.readVariant(module)
        } else {
            null
        }
    }
}

private class LmVariantReader(
    adapter: LmSerialization.LmSerializationAdapter,
    variantName: String
) : LmReader(adapter, adapter.getReader(variantName)) {
    override val path: String = adapter.file(variantName)?.path ?: "<unknown>"

    private fun readLibrary(): LmLibrary {
        expectTag("library")
        var android = false

        val jars = getFiles("jars")
        val project = getOptionalAttribute("project")
        val requested = getOptionalAttribute("requested")?.toMavenCoordinate()
        val resolved = getOptionalAttribute("resolved")?.toMavenCoordinate()
        val provided = getOptionalBoolean("provided", false)
        val skipped = getOptionalBoolean("skipped", false)

        // Android library?

        var manifestFile: File? = null
        var resFolder: File? = null
        var assetsFolder: File? = null
        var lintJar: File? = null
        var publicResources: File? = null
        var symbolFile: File? = null
        var externalAnnotations: File? = null
        var proguardRules: File? = null
        var projectId: String? = null

        val folder = getOptionalFile("folder")
        if (folder != null) {
            android = true

            resFolder = getRequiredFile("resFolder", folder)
            assetsFolder = getRequiredFile("assetsFolder", folder)
            lintJar = getRequiredFile("lintJar", folder)
            publicResources = getRequiredFile("publicResources", folder)
            symbolFile = getRequiredFile("symbolFile", folder)
            manifestFile = getRequiredFile("manifest", folder)
            externalAnnotations = getRequiredFile("externalAnnotations", folder)
            proguardRules = getRequiredFile("proguardRules", folder)
            projectId = getOptionalAttribute("projectId")
        }

        var dependencies: LmDependencies? = null
        while (parser.next() != END_DOCUMENT) {
            val eventType = parser.eventType
            if (eventType == START_TAG) {
                when (parser.name) {
                    "dependencies" -> dependencies = readDependencies(computeAll = false)
                    else -> unexpectedTag()
                }
            } else if (eventType == END_TAG) {
                expectTag("library")
                break
            }
        }

        if (android) {
            return DefaultLmAndroidLibrary(
                jarFiles = jars,
                manifest = manifestFile!!,
                folder = folder!!,
                resFolder = resFolder!!,
                assetsFolder = assetsFolder!!,
                lintJar = lintJar!!,
                publicResources = publicResources!!,
                symbolFile = symbolFile!!,
                externalAnnotations = externalAnnotations!!,
                projectId = projectId,
                proguardRules = proguardRules!!,
                project = project,
                provided = provided,
                skipped = skipped,
                requestedCoordinates = requested,
                resolvedCoordinates = resolved!!,
                dependencies = dependencies?.direct ?: emptyList()
            )
        } else {
            return DefaultLmJavaLibrary(
                jarFiles = jars,
                project = project,
                provided = provided,
                skipped = skipped,
                requestedCoordinates = requested,
                resolvedCoordinates = resolved!!,
                dependencies = dependencies?.direct ?: emptyList()
            )
        }
    }

    private fun readDependencies(computeAll: Boolean = true): LmDependencies {
        expectTag("dependencies")
        val direct = mutableListOf<LmLibrary>()
        while (parser.next() != END_DOCUMENT) {
            val eventType = parser.eventType
            if (eventType == START_TAG) {
                when (parser.name) {
                    "library" -> direct.add(readLibrary())
                    else -> unexpectedTag()
                }
            } else if (eventType == END_TAG) {
                expectTag("dependencies")
                break
            }
        }

        val all = mutableListOf<LmLibrary>()
        if (computeAll) {
            val seen = mutableSetOf<LmLibrary>()
            for (lib in direct) {
                addAllLibraries(lib, all, seen)
            }
        }

        return DefaultLmDependencies(
            direct = direct,
            all = all
        )
    }

    private fun addAllLibraries(
        library: LmLibrary,
        list: MutableList<LmLibrary>,
        seen: MutableSet<LmLibrary>
    ) {
        list.add(library)
        seen.add(library)
        for (dep in library.dependencies) {
            if (!seen.contains(dep)) {
                addAllLibraries(dep, list, seen)
            }
        }
    }

    private fun readResValues(): Map<String, LmResourceField> {
        expectTag("resValues")

        val resValues: MutableMap<String, LmResourceField> = mutableMapOf()

        while (parser.next() != END_DOCUMENT) {
            val eventType = parser.eventType
            if (eventType == START_TAG) {
                when (parser.name) {
                    "resValue" -> {
                        val value = readResValue()
                        resValues[value.name] = value
                    }
                    else -> unexpectedTag()
                }
            } else if (eventType == END_TAG) {
                expectTag("resValues")
                break
            }
        }

        return resValues
    }

    private fun readResValue(): LmResourceField {
        expectTag("resValue")
        val type = getRequiredAttribute("type")
        val name = getName()
        val value = getRequiredAttribute("value")

        finishTag("resValue")
        return DefaultLmResourceField(type = type, name = name, value = value)
    }

    private fun readManifestPlaceholders(): Map<String, String> {
        expectTag("manifestPlaceholders")

        val placeholders: MutableMap<String, String> = mutableMapOf()

        while (parser.next() != END_DOCUMENT) {
            val eventType = parser.eventType
            if (eventType == START_TAG) {
                when (parser.name) {
                    "placeholder" -> {
                        val name = getName()
                        val value = getRequiredAttribute("value")
                        finishTag("placeholder")
                        placeholders[name] = value
                    }
                    else -> unexpectedTag()
                }
            } else if (eventType == END_TAG) {
                expectTag("manifestPlaceholders")
                break
            }
        }

        return placeholders
    }

    private fun readAndroidArtifact(tag: String): LmAndroidArtifact {
        return readArtifact(tag, android = true) as LmAndroidArtifact
    }

    private fun readJavaArtifact(tag: String): LmJavaArtifact {
        return readArtifact(tag, android = false) as LmJavaArtifact
    }

    private fun readArtifact(tag: String, android: Boolean): LmArtifact {
        expectTag(tag)

        val name = getName()
        val classFolders = getFiles("classFolders")

        val applicationId: String
        val generatedSourceFolders: Collection<File>
        val generatedResourceFolders: Collection<File>
        if (android) {
            applicationId = getRequiredAttribute("applicationId")
            generatedSourceFolders = getFiles("generatedSourceFolders")
            generatedResourceFolders = getFiles("generatedResourceFolders")
        } else {
            applicationId = ""
            generatedSourceFolders = emptyList()
            generatedResourceFolders = emptyList()
        }

        var dependencies: LmDependencies? = null

        while (parser.next() != END_DOCUMENT) {
            val eventType = parser.eventType
            if (eventType == START_TAG) {
                when (parser.name) {
                    "dependencies" -> dependencies = readDependencies()
                    else -> unexpectedTag()
                }
            } else if (eventType == END_TAG) {
                expectTag(tag)
                break
            }
        }

        if (dependencies == null) {
            missingData()
        }

        if (android) {
            return DefaultLmAndroidArtifact(
                name = name,
                applicationId = applicationId,
                generatedResourceFolders = generatedResourceFolders,
                generatedSourceFolders = generatedSourceFolders,
                classFolders = classFolders,
                dependencies = dependencies!!
            )
        } else {
            return DefaultLmJavaArtifact(
                name = name,
                classFolders = classFolders,
                dependencies = dependencies!!
            )
        }
    }

    fun readVariant(module: LmModule): LmVariant {
        try {
            parser.nextTag()
            expectTag("variant")
            getOptionalFile("dir")?.let { root = it }
            val name = getName()
            val useSupportLibraryVectorDrawables =
                getOptionalBoolean("useSupportLibraryVectorDrawables", false)
            var mainArtifact: LmAndroidArtifact? = null
            var testArtifact: LmJavaArtifact? = null
            var androidTestArtifact: LmAndroidArtifact? = null
            val oldVariant: com.android.builder.model.Variant? = null

            val packageName = getOptionalAttribute("package")
            val versionCode = getOptionalAttribute("versionCode")?.toInt()
            val versionName = getOptionalAttribute("versionName")
            val minSdkVersion = getOptionalAttribute("minSdkVersion")?.toApiVersion()
            val targetSdkVersion = getOptionalAttribute("targetSdkVersion")?.toApiVersion()
            val debuggable = getOptionalBoolean("debuggable", false)
            val shrinkable = getOptionalBoolean("shrinking", false)
            val proguardFiles = getFiles("proguardFiles")
            val consumerProguardFiles = getFiles("consumerProguardFiles")
            val resourceConfigurations = getStrings("resourceConfigurations")
            var resValues: Map<String, LmResourceField> = emptyMap()
            var manifestPlaceholders: Map<String, String> = emptyMap()
            var sourceProviders: List<LmSourceProvider> = emptyList()
            var testSourceProviders: List<LmSourceProvider> = emptyList()

            expectTag("variant")

            while (parser.next() != END_DOCUMENT) {
                val eventType = parser.eventType
                if (eventType == START_TAG) {
                    when (parser.name) {
                        "resValues" -> resValues = readResValues()
                        "manifestPlaceholders" -> manifestPlaceholders = readManifestPlaceholders()
                        "mainArtifact" -> mainArtifact = readAndroidArtifact(parser.name)
                        "androidTestArtifact" -> androidTestArtifact =
                            readAndroidArtifact(parser.name)
                        "testArtifact" -> testArtifact = readJavaArtifact(parser.name)
                        "sourceProviders" -> sourceProviders = readSourceProviders(parser.name)
                        "testSourceProviders" -> testSourceProviders = readSourceProviders(parser.name)
                        else -> unexpectedTag()
                    }
                } else if (eventType == END_TAG) {
                    expectTag("variant")
                    break
                }
            }

            if (mainArtifact == null) {
                missingData()
            }

            return DefaultLmVariant(
                module = module,
                name = name,
                useSupportLibraryVectorDrawables = useSupportLibraryVectorDrawables,
                mainArtifact = mainArtifact!!,
                androidTestArtifact = androidTestArtifact,
                testArtifact = testArtifact,
                oldVariant = oldVariant,
                `package` = packageName,
                versionCode = versionCode,
                versionName = versionName,
                minSdkVersion = minSdkVersion,
                targetSdkVersion = targetSdkVersion,
                proguardFiles = proguardFiles,
                consumerProguardFiles = consumerProguardFiles,
                resourceConfigurations = resourceConfigurations,
                resValues = resValues,
                manifestPlaceholders = manifestPlaceholders,
                sourceProviders = sourceProviders,
                testSourceProviders = testSourceProviders,
                debuggable = debuggable,
                shrinkable = shrinkable
            )
        } catch (e: XmlPullParserException) {
            error(e)
        }
    }
}
