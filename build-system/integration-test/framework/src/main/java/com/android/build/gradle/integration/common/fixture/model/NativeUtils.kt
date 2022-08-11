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

package com.android.build.gradle.integration.common.fixture.model

import com.android.SdkConstants
import com.android.build.gradle.integration.BazelIntegrationTestsSuite
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.ModelBuilderV2
import com.android.build.gradle.integration.common.fixture.ModelBuilderV2.NativeModuleParams
import com.android.build.gradle.integration.common.fixture.ModelContainerV2
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.io.hardLinkOrCopyFile
import com.android.build.gradle.internal.cxx.logging.getCxxStructuredLogFolder
import com.android.build.gradle.internal.cxx.logging.readStructuredLogs
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.createCxxAbiModelFromJson
import com.android.build.gradle.internal.cxx.model.metadataGenerationCommandFile
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.builder.model.v2.models.ndk.NativeVariant
import com.android.utils.cxx.streamCompileCommands
import com.google.gson.JsonArray
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.*
import com.android.build.gradle.internal.cxx.string.StringDecoder
import com.android.testutils.TestUtils
import com.android.testutils.diff.UnifiedDiff
import com.google.common.base.Splitter
import org.gradle.tooling.GradleConnector
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipInputStream
import com.android.SdkConstants.GRADLE_LATEST_VERSION
import com.android.build.gradle.internal.cxx.configure.ConfigureInvalidationState
import com.android.build.gradle.internal.cxx.configure.decodeConfigureInvalidationState
import com.android.build.gradle.internal.cxx.configure.shouldConfigure
import com.android.build.gradle.internal.cxx.process.decodeExecuteProcess
import com.android.builder.model.v2.ide.SyncIssue
import com.android.utils.SdkUtils.escapePropertyValue
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.util.JsonFormat

data class CompileCommandsJsonBinEntry(
        val sourceFile: String,
        val compiler: String,
        val workingDir: String,
        val flags: List<String>
)

fun File.readAsFileIndex(): List<File> = readLines(StandardCharsets.UTF_8).map { File(it) }
fun File.readAsFileIndex(normalizer: FileNormalizer): List<String> =
        readLines(StandardCharsets.UTF_8).map { normalizer.normalize(File(it)) }

fun File.readCompileCommandsJsonBin(normalizer: FileNormalizer): List<CompileCommandsJsonBinEntry> {
    val entries = mutableListOf<CompileCommandsJsonBinEntry>()
    streamCompileCommands(this) {
        var jsonArray = JsonArray()
        flags.forEach(jsonArray::add)
        jsonArray = normalizer.normalize(jsonArray).asJsonArray
        val normalizedFlags = (0 until jsonArray.size()).map { jsonArray.get(it).asString }
        entries.add(
                CompileCommandsJsonBinEntry(
                        normalizer.normalize(sourceFile),
                        normalizer.normalize(compiler),
                        normalizer.normalize(workingDirectory),
                        normalizedFlags
                )
        )
    }
    return entries
}
fun File.dumpCompileCommandsJsonBin(normalizer: FileNormalizer): String =
        readCompileCommandsJsonBin(normalizer)
                .sortedBy { it.toString() }
                .joinToString("\n\n") { (sourceFile: String, compiler: String, workingDir: String, rawFlags: List<String>) ->
                    val flags = rawFlags
                        .filter { flag -> flag.contains("-target") || flag.contains("none-linux-android") }
                    """
                        sourceFile: $sourceFile
                        compiler:   $compiler
                        workingDir: $workingDir
                        flags:      $flags
                    """.trimIndent()
                }

/**
 * Return a report of build outputs (*.so, *.o, *.a).
 */
fun GradleTestProject.goldenBuildProducts() : String {
    val fetchResult = modelV2().fetchNativeModules(NativeModuleParams(listOf(), listOf()))
    val hashToKey = fetchResult.cxxFileVariantSegmentTranslator()
    val projectFolder = recoverExistingCxxAbiModels().first().variant.module.project.rootBuildGradleFolder
    return projectFolder.walk()
            .filter { file -> file.extension in listOf("so", "o", "a") }
            // We're walking the intermediate output directory rather than the installation
            // directory (AGP doesn't use CMake's install task), so the directory may also contain
            // some files that aren't part of the library output. For some versions of CMake this
            // directory includes files used for compiler identification that we will wrongly
            // identify as our own outputs. Filter those out of the files to compare to the expected
            // outputs.
            .filterNot { file -> file.path.contains("CompilerId") }
            .map { fetchResult.normalizer.normalize(it) }
            .map { hashToKey(it) }
            .sorted()
            .toList()
            .joinToString("\n")
}

/**
 * Output configuration flags for the given ABI.
 */
fun GradleTestProject.goldenConfigurationFlags(abi: Abi) : String {
    val fetchResult =
            modelV2()
                .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING) // CMake cannot detect compiler attributes
                .fetchNativeModules(NativeModuleParams(listOf("debug"), listOf(abi.tag)))
    val recoveredAbiModel = recoverExistingCxxAbiModels().single { it.abi == abi }
    val hashToKey = fetchResult.cxxFileVariantSegmentTranslator()
    return hashToKey(recoveredAbiModel.goldenConfigurationFlags())
}

private fun CxxAbiModel.goldenConfigurationFlags() : String {
    fun String.slash() : String = replace("\\", "/")
    fun File.slash() : String = toString().slash()
    return metadataGenerationCommandFile.readText()
            .slash()
            .replace(variant.module.project.rootBuildGradleFolder.slash(), "{PROJECT}")
            .replace(variant.module.ndkFolder.slash(), "{NDK}")
            .trim()
            .lines()
            .map { line ->
                when(variant.module.buildSystem) {
                    NativeBuildSystem.CMAKE -> {
                        val cmake = variant.module.cmake!!
                        line
                            .replace(cmake.cmakeExe!!.slash(), "{CMAKE}")
                            .replace(variant.module.ninjaExe!!.slash(), "{NINJA}")
                            .replace("-GNinja", "-G{Generator}")
                            .replace("-GAndroid Gradle - Ninja", "-G{Generator}")
                    }
                    NativeBuildSystem.NINJA -> {
                        line
                            .replace(variant.module.ninjaExe!!.slash(), "{NINJA}")
                            .replace("-GNinja", "-G{Generator}")
                            .replace("-GAndroid Gradle - Ninja", "-G{Generator}")
                    }
                    else -> line
                }
            }
            .filter { !it.startsWith(" ") }
            .filter { it.isNotBlank() }
            .filter { !it.startsWith("jvmArgs") }
            .filter { !it.startsWith("arguments") }
            .filter { !it.startsWith("Executable :") }
            .sorted()
            .joinToString("\n")
}

private val abiTags = Abi.values().map { abi -> abi.tag }.toSet()

fun findAbiSegment(file: File) : String? {
    val name = file.name
    if (name in abiTags) return file.name
    return findAbiSegment(file.parentFile?:return null)
}

fun findCxxSegment(file: File) : String? {
    val name = file.name
    if (name.endsWith("cxx")) return file.name
    return findCxxSegment(file.parentFile?:return null)
}

fun findIntermediatesSegment(file: File) : String? {
    val name = file.name
    if (name.endsWith("intermediates")) return file.name
    return findIntermediatesSegment(file.parentFile?:return null)
}

fun findConfigurationSegment(file: File) : String? {
    val abi = findAbiSegment(file) ?: return null
    val cxx = findCxxSegment(file) ?: findIntermediatesSegment(file) ?: return null
    val string = file.toString().replace("\\", "/")
    return string.substring(
            string.lastIndexOf(cxx) + cxx.length + 1,
            string.lastIndexOf(abi) - 1)
        .replace("/meta", "")
}

/**
 * Discover and build a map (list of pair) from output file subsegment to a human readable name
 * that can be used in tests. For example,
 *
 *      cmake/debug ==> {DEBUG}
 *      cmake/release ==> {RELEASE}
 */
private fun ModelBuilderV2.FetchResult<ModelContainerV2>.hashEquivalents() : List<Pair<String, String>> {
    val abis = container.infoMaps.values
            .flatMap { it.values }
            .mapNotNull { it.nativeModule }
            .flatMap { it.variants }
            .flatMap { it.abis.map { abi -> it to abi } }
    val segments = (abis.map { (variant, abi) ->
        findConfigurationSegment(abi.sourceFlagsFile)!! to "{${variant.name.toUpperCase(Locale.ROOT)}}"
    } + abis.map { (variant, abi) ->
        findConfigurationSegment(abi.symbolFolderIndexFile)!! to "{${variant.name.toUpperCase(Locale.ROOT)}}"
    }).distinct()
    val configurationAliases = segments
            .map { (segment, alias) ->
                "/.cxx/$segment" to "/.cxx/$alias"
            }
    val intermediatesCxxAliases = segments
            .map { (segment, alias) ->
                "/cxx/$segment" to "/$alias"
            }
    val intermediatesAliases = segments
            .map { (segment, alias) ->
                "/intermediates/$segment" to "/intermediates/$alias"
            }
    return configurationAliases + intermediatesCxxAliases + intermediatesAliases
}

/**
 * Recover existing CxxAbiModels that were written to disk during the configure phase.
 * No new configures should be done.
 */
fun GradleTestProject.recoverExistingCxxAbiModels(): List<CxxAbiModel> {
    return recoverExistingCxxAbiModels(buildDir.parentFile)
}

/**
 * Search from [rootSearchFolder] level for build_model.json to reconstitute into CxxAbiModels.
 */
fun recoverExistingCxxAbiModels(rootSearchFolder : File): List<CxxAbiModel> {
    val modelFiles = rootSearchFolder.walk().filter { file -> file.name == "build_model.json" }.toList()
    val models = modelFiles.filter { it.isFile }
        .map { createCxxAbiModelFromJson(it.readText()) }
        .distinct()
    if (models.isEmpty()) {
        error("Could not recover any CxxAbiModels from ${rootSearchFolder}, did configure run?")
    }
    return models
}

/**
 * Mainly a Java helper to avoid writing tons of filter code.
 */
fun GradleTestProject.getSoFolderFor(abi : Abi) =
    recoverExistingCxxAbiModels().singleOrNull { it.abi == abi }?.soFolder


private fun List<Pair<String, String>>.toTranslateFunction() = run {
    { value : String ->
        var result = value
        for((old, new) in this) {
            result = result.replace(old, new)
        }
        result
    }
}

/**
 * Build a translator that will convert from ".cxx/[some stuff in related to "debug" variant]/x86"
 * to .cxx/{DEBUG}/x86 so that baselines can be compared even with "stuff" changes (as it is for
 * configuration folding).
 */
fun ModelBuilderV2.FetchResult<ModelContainerV2>.cxxFileVariantSegmentTranslator() =
        hashEquivalents().toTranslateFunction()

/**
 * Sort [NativeModule] with variant names in alphabetic order and ABI names in ordinal order
 * from [Abi].
 */
fun NativeModule.sorted() : NativeModule {
    val module = this
    fun sorted(variant : NativeVariant) = object : NativeVariant by variant {
        override val abis = variant.abis
                .sortedBy { abi -> Abi.getByName(abi.name)!!.ordinal }
    }
    return object : NativeModule by module {
        override val variants = module.variants
                    .map(::sorted)
                    .sortedBy { variant -> variant.name }

    }
}

/**
 * Dumps the content of [NativeModule] as a string. The dump format is like the following
 *
 * ```
 *   [HelloWorld.app]:
 *   > NativeModule
 *     ...
 *   < NativeModule
 * ```
 *
 * where `...` is the common model dumper [NativeModule.writeToBuilder]. The dumper normalizes
 * any file paths relative to well-known directories, for example, project root, SDK root. This
 * makes test easier to write since these directories generally change across platforms and test
 * runs. The normalization is done by [FileNormalizer].
 */
fun ModelBuilderV2.FetchResult<ModelContainerV2>.dump(map:(NativeModule)->NativeModule): String {
    val sb = StringBuilder()
    withCxxFileNormalizer().apply {
        container.infoMaps.forEach { (_, modelMap) ->
            modelMap.forEach { (moduleName, modelInfo) ->
                sb.appendln("[$moduleName]")
                modelInfo.nativeModule?.let {
                    sb.appendln(
                        snapshotModel(
                            modelName = "NativeModule",
                            modelAction = { it },
                            project = this,
                            referenceProject = null
                        ) {
                            snapshotNativeModule()
                        }
                    )
                }
            }
        }
    }
    return sb.toString().trim().replace(System.lineSeparator(), "\n")
}

/**
 * Add a C/C++ path normalizer to this [ModelBuilderV2.FetchResult]
 */
fun ModelBuilderV2.FetchResult<ModelContainerV2>.withCxxFileNormalizer()
        : ModelBuilderV2.FetchResult<ModelContainerV2> {
    val cxxSegmentTranslator = cxxFileVariantSegmentTranslator()
    val normalizer = object : FileNormalizer by normalizer {
        override fun normalize(file: File) =
                cxxSegmentTranslator(normalizer.normalize(file))
    }
    return copy(normalizer = normalizer)
}

fun ModelBuilderV2.FetchResult<ModelContainerV2>.dump() = dump { it }

fun filterByVariantName(vararg names: String) : (NativeModule) -> NativeModule = { nativeModule:NativeModule ->
    object : NativeModule by nativeModule {
        override val variants = nativeModule.variants.filter { names.contains(it.name) }
    }
}

/**
 * Produce all combinations of elements from c1 and c2.
 */
fun cartesianOf(c1:Array<*>, c2:Array<*>) : Array<Array<*>> =
        c1.flatMap { e1 -> c2.map { e2 -> arrayOf(e1, e2) } }.toTypedArray()

/**
 * Produce all combinations of elements individual arrays in [members].
 */
fun cartesianOf(vararg members : Array<*>) =
    cartesianOfIndices(members.map { it.size }).map {
        it.mapIndexed { index, ordinal ->
            members[index][ordinal]
        }
    }
    .map { it.toTypedArray() }
    .toTypedArray()

private fun cartesianOfIndices(sizes: List<Int>) : List<List<Int>> =
    if (sizes.size == 1) (0 until sizes[0]).map { listOf(it) }
    else (0 until sizes[0]).flatMap { ordinal ->
        cartesianOfIndices(sizes.drop(1)).map { inner ->
            listOf(ordinal) + inner
        }
    }

/**
 * Produces all pairs, triples, etc up to size [maxTupleSize] from [row].
 * Tuples elements are in ascending order according to their place in [row].
 * The result key is the index of the field in [row].
 * The result value is the value of the field at index in [row].
 */
private fun tuplesOfRow(row : Array<Any?>, maxTupleSize : Int, startIndex : Int = 0,
    outer : Map<Int, Any?> = mapOf(), result : MutableSet<Map<Int, Any?>> = mutableSetOf())
    : Set<Map<Int, Any?>> {
    if (maxTupleSize == 0) return result
    for (index in startIndex until row.size) {
        val tuple = outer.toMutableMap()
        tuple[index] = row[index]
        result.add(tuple)
        tuplesOfRow(row, maxTupleSize - 1, index + 1, tuple, result)
    }
    return result
}

/**
 * Find a small set of rows from [this] that covers all pairs, triples, etc of field
 * values. The purposes is to select a smaller but still representative set of tests
 * so that we don't have to run all combinations. If [maxTupleSize] is set to the
 * size of rows in [this] then all rows will be returned.
 */
fun Array<Array<Any?>>.minimizeUsingTupleCoverage(maxTupleSize : Int): Array<Array<Any?>> {
    val remainingTuples = flatMap { tuplesOfRow(it, maxTupleSize) }.toMutableSet()
    val result = mutableSetOf<Int>()
    while (remainingTuples.isNotEmpty()) {
        // Choose the row that covers the most remaining uncovered tuples
        val bestRow = indices
            .filter { !result.contains(it) }
            .minByOrNull { (remainingTuples - tuplesOfRow(this[it], maxTupleSize)).size }!!
        remainingTuples.removeAll(tuplesOfRow(this[bestRow], maxTupleSize))
        result.add(bestRow)
    }
    return result.map { this[it] }.toTypedArray()
}

/**
 * Enable C/C++ structured logging for this project.
 */
fun enableCxxStructuredLogging(project : GradleTestProject) {
    val logFolder = getCxxStructuredLogFolder(project.rootProject.projectDir)
    logFolder.mkdirs()
}

/**
 * Delete structured logs but leave structured logging enabled.
 */
fun deleteExistingStructuredLogs(project : GradleTestProject) {
    val logFolder = getCxxStructuredLogFolder(project.rootProject.projectDir)
    logFolder.deleteRecursively()
    logFolder.mkdirs()
}

/**
 * Given a [GradleTestProject], read structured log records of a
 * particular type (the type returned by [decode] function).
 */
inline fun <reified Encoded, Decoded> GradleTestProject.readStructuredLogs(
    crossinline decode: (Encoded, StringDecoder) -> Decoded) : List<Decoded> {
    val logFolder = getCxxStructuredLogFolder(rootProject.projectDir)
    return readStructuredLogs(logFolder, decode)
}

/**
 * Return the last [ConfigureInvalidationState] that was recorded.
 */
val GradleTestProject.lastConfigureInvalidationState : ConfigureInvalidationState get() {
    val configures = readStructuredLogs(::decodeConfigureInvalidationState)
    if (configures.isEmpty()) error("Configure was not run")
    return configures.last()
}

/**
 * Assert that the last C/C++ configure was a rebuild. If not, emit error with diagnostic.
 */
fun GradleTestProject.assertLastConfigureWasRebuild() {
    val configure = lastConfigureInvalidationState
    if (configure.shouldConfigure) return
    val sb = StringBuilder("Expected last configure to be a rebuild but it wasn't ")
    JsonFormat.printer().appendTo(makePathsShorter(configure), sb)
    error(sb.toString())
}

/**
 * Assert that the last C/C++ configure was not a rebuild. If it is, emit error with diagnostic.
 */
fun GradleTestProject.assertLastConfigureWasNotRebuild()  {
    val configure = lastConfigureInvalidationState
    if (!configure.shouldConfigure) return
    val sb = StringBuilder("Expected last configure to not be a rebuild but it was ")
    JsonFormat.printer().appendTo(makePathsShorter(configure), sb)
    error(sb.toString())
}

private fun makePathsShorter(state : ConfigureInvalidationState) : ConfigureInvalidationState {
    fun shorten(file : String) = File(file).name
    return state.toBuilder()
        .clearInputFiles().addAllInputFiles(state.inputFilesList.map(::shorten))
        .clearRequiredOutputFiles().addAllRequiredOutputFiles(state.requiredOutputFilesList.map(::shorten))
        .clearOptionalOutputFiles().addAllOptionalOutputFiles(state.optionalOutputFilesList.map(::shorten))
        .clearHardConfigureFiles().addAllHardConfigureFiles(state.hardConfigureFilesList.map(::shorten))
        .clearSoftConfigureReasons().addAllSoftConfigureReasons(state.softConfigureReasonsList.map { it.toBuilder().setFileName(shorten(it.fileName)).build()})
        .clearHardConfigureReasons().addAllHardConfigureReasons(state.hardConfigureReasonsList.map { it.toBuilder().setFileName(shorten(it.fileName)).build()})
        .clearAddedSinceFingerPrintsFiles().addAllAddedSinceFingerPrintsFiles(state.addedSinceFingerPrintsFilesList.map(::shorten))
        .clearRemovedSinceFingerPrintsFiles().addAllRemovedSinceFingerPrintsFiles(state.removedSinceFingerPrintsFilesList.map(::shorten))
        .build()
}

/**
 * Return a total count of processes executed.
 */
val GradleTestProject.totalProcessExecuted : Int get() =
    readStructuredLogs(::decodeExecuteProcess).size

/**
 * Unzip [zip] to the folder [out] including subdirectories.
 */
fun unzip(zip: File, out: File) {
    val buffer = ByteArray(1024)
    FileInputStream(zip).use { fis ->
        BufferedInputStream(fis).use { bis ->
            ZipInputStream(bis).use { zis ->
                var zipEntry = zis.nextEntry
                while (zipEntry != null) {
                    val fileName = zipEntry.name
                    val newFile = File(out, fileName)
                    if (!fileName.endsWith("/")) {
                        if (newFile.exists()) {
                            newFile.delete()
                        }
                        newFile.parentFile.mkdirs()
                        FileOutputStream(newFile).use { fos ->
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                fos.write(buffer, 0, len)
                            }
                        }
                    }
                    zipEntry = zis.nextEntry
                }
            }
        }
    }
}

/**
 * Hardlink all files in one folder to another including subdirectories.
 */
fun hardLinkOrCopyDirectory(source : File, out : File) {
    source.walkTopDown().forEach { file ->
        val relative = file.relativeTo(source)
        val destination = out.resolve(relative)
        if (file.isDirectory) {
            file.mkdirs()
        } else {
            hardLinkOrCopyFile(file, destination)
        }
    }
}


/**
 * Wraps a perfgate build benchmark project with convenience functions for
 * setup and execution. It has setup specific to native projects and isn't
 * necessarily suitable for non-native projects.
 */
class NativeBuildBenchmarkProject(
    relativeBuildRoot: String,
    workingFolder: File,
    buildbenchmark: String,
    setupDiff: String = "setup.diff") {

    private val arguments = mutableListOf<String>()
    private val testRootFolder = File(System.getenv("TEST_TMPDIR")).absoluteFile
    private val outDir = workingFolder.resolve("out")
    private val initScript = outDir.resolve("init.script").absoluteFile
    private val defaultGradleUserHome = outDir.resolve("_home").absoluteFile
    private val buildDir = outDir.resolve("_build").absoluteFile
    private val localMavenRepo= outDir.resolve("_tmp_local_maven").absoluteFile
    private val repoDir = outDir.resolve("_repo").absoluteFile
    private val androidDir = outDir.resolve("_android").absoluteFile
    private val distribution = locate("tools/external/gradle/gradle-$GRADLE_LATEST_VERSION-bin.zip")
    private val prebuilts = locate("prebuilts/studio/buildbenchmarks/$buildbenchmark")
    private val src = workingFolder.resolve("src")
    val buildRoot = src.resolve(relativeBuildRoot)
    private val localPropertiesFile : File = buildRoot.resolve(SdkConstants.FN_LOCAL_PROPERTIES)

    /**
     * Add a repo called [repo] to this project.
     * If [repo] is a zip, then it is unzipped into the project's
     * 'repo' directory.
     * If [repo] is a directory, then it is symlinked into the
     * project's 'repo' directory.
     */
    private fun addRepo(repo: File) {
        if (repo.name.endsWith(".zip")) {
            unzip(repo, repoDir)
        } else if (repo.isDirectory) {
            hardLinkOrCopyDirectory(repo, repoDir)
        } else {
            throw IllegalArgumentException("Unknown repo type ${repo.name}")
        }
    }

    /**
     * Add an argument to the gradle invocation.
     */
    fun addArgument(argument: String) {
        arguments.add(argument)
    }

    /**
     * Apply a diff from the prebuilts folder to the local project.
     */
    fun applyDiff(diff: String) {
        UnifiedDiff(prebuilts.resolve(diff))
            .apply(src, 3)
    }

    /**
     * Enabled C/C++ structured logging. If there was a previous structured
     * log folder then it is deleted.
     */
    fun enableCxxStructuredLogging() {
        val structuredLogFolder = getCxxStructuredLogFolder(buildRoot)
        if (structuredLogFolder.isDirectory) {
            structuredLogFolder.deleteRecursively()
        }
        structuredLogFolder.mkdirs()
    }

    /**
     * Read structured log records that match the types in [decode].
     * Other records are ignored.
     */
    inline fun <reified Encoded, Decoded> readStructuredLogs(
        crossinline decode: (Encoded, StringDecoder) -> Decoded) : List<Decoded> {
        val structuredLogFolder = getCxxStructuredLogFolder(buildRoot)
        return readStructuredLogs(structuredLogFolder, decode)
    }

    /**
     * Run one ore more gradle tasks.
     */
    fun run(vararg tasks: String) {
        val env = mutableMapOf<String, String>()
        env["ANDROID_SDK_ROOT"] = TestUtils.getSdk().toFile().absolutePath
        env["BUILD_DIR"] = buildDir.absolutePath
        env["ANDROID_PREFS_ROOT"] = androidDir.absolutePath
        env["ANDROID_HOME"] = File(TestUtils.getRelativeSdk()).absolutePath
        env["ANDROID_SDK_HOME"] = androidDir.absolutePath
        System.getenv("SystemRoot")?.let { env["SystemRoot"] = it }
        System.getenv("TEMP")?.let { env["TEMP"] = it }
        System.getenv("TMP")?.let { env["TMP"] = it }

        val arguments = mutableListOf<String>(
            "--offline",
            "--init-script",
            initScript.absolutePath,
            "-PinjectedMavenRepo=" + repoDir.absolutePath,
            "-Dmaven.repo.local=" + localMavenRepo.absolutePath,
            "-Dcom.android.gradle.version=" + getLocalGradleVersion()
        )
        arguments.addAll(this.arguments)

        // Workaround for issue https://github.com/gradle/gradle/issues/5188
        System.setProperty("gradle.user.home", "")

        GradleConnector.newConnector()
            .useDistribution(distribution.toURI())
            .useGradleUserHomeDir(defaultGradleUserHome)
            .forProjectDirectory(buildRoot)
            .connect().use { connection ->
                connection
                    .newBuild()
                    .setEnvironmentVariables(env)
                    .withArguments(arguments)
                    .forTasks(*tasks)
                    .setStandardOutput(System.out)
                    .setStandardError(System.err)
                    .run()
            }
    }

    private fun createInitScript(initScript: File, repoDir: File) {
        initScript.writeText("""allprojects {
                              buildscript {
                                repositories {
                                   maven { url '${repoDir.toURI()}'}
                                }
                              }
                              repositories {
                                maven {
                                  url '${repoDir.toURI()}'
                                  metadataSources {
                                    mavenPom()
                                    artifact()
                                  }
                                }
                              }
                            }
                            """.trimIndent())
    }

    private fun locate(path : String) : File {
        var current = File(".").absoluteFile
        lateinit var folder : File
        do {
            folder = current.resolve(path)
            if (folder.exists()) return folder
            current = current.parentFile
                ?: error("Could not locate $path")
        } while(true)
    }

    private fun getLocalGradleVersion(): String {
        val file = locate("tools/buildSrc/base/version.properties")
        FileInputStream(file).use { fis ->
            val properties = Properties()
            properties.load(fis)
            return properties.getProperty("buildVersion")!!
        }
    }

    private fun getLocalRepositories() : List<Path> {
        return when {
            TestUtils.runningFromBazel() -> BazelIntegrationTestsSuite.MAVEN_REPOS
            System.getenv("CUSTOM_REPO") != null -> {
                val customRepo = System.getenv("CUSTOM_REPO")
                Splitter.on(File.pathSeparatorChar)
                    .split(customRepo).map { Paths.get(it) }
            }
            else -> throw IllegalStateException("Tests must be run from the build system")
        }
    }

    init {
        unzip(prebuilts.resolve("src.zip"), src)
        applyDiff(setupDiff)
        addRepo(prebuilts.resolve("repo.zip"))
        getLocalRepositories().forEach {
            addRepo(it.toFile())
        }
        buildDir.mkdirs()
        androidDir.mkdirs()
        createInitScript(initScript, repoDir)
        localPropertiesFile.writeText("""
            ndk.symlinkdir=${escapePropertyValue(testRootFolder.absolutePath)}
        """.trimIndent())
    }
}


fun assertEqualsMultiline(actual : String, expected : String) {
    if (actual == expected) return
    val actualLines = actual.split("\n").toTypedArray()
    val expectedLines = expected.split("\n").toTypedArray()

    var line = 0
    for((actualLine, expectedLine) in actualLines zip expectedLines) {
        assertThat(actualLine)
            .named("Difference on line $line")
            .isEqualTo(expectedLine)
        ++line
    }

    assertThat(actual).isEqualTo(expected)
}

