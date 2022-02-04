/*
 * Copyright (C) 2019 The Android Open Source Project
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

@file:JvmName("DesugarLibUtils")

package com.android.build.gradle.internal.utils

import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.impl.getFeatureLevel
import com.android.build.gradle.internal.dependency.GenericTransformParameters
import com.android.build.gradle.internal.dependency.VariantDependencies.Companion.CONFIG_NAME_CORE_LIBRARY_DESUGARING
import com.android.builder.dexing.D8DesugaredMethodsGenerator
import com.android.builder.packaging.JarFlinger
import com.android.sdklib.AndroidTargetHash
import com.android.tools.r8.Version
import com.google.common.io.ByteStreams
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.internal.artifacts.ArtifactAttributes
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipInputStream

// The name of desugar config json file
private const val DESUGAR_LIB_CONFIG_FILE = "desugar.json"
private const val ANDROID_SUBDIR = "android"
private const val FAKE_DEPENDENCY_JAR = "FakeDependency.jar"
// The output of L8 invocation, which is the dex output of desugar lib jar
const val DESUGAR_LIB_DEX = "_internal-desugar-lib-dex"
// The output of DesugarLibConfigExtractor which extracts the desugar config json file from
// desugar lib configuration jar
const val DESUGAR_LIB_CONFIG = "_internal-desugar-lib-config"
private const val DESUGAR_LIB_LINT = "_internal-desugar-lib-lint"
private const val D8_DESUGAR_METHODS = "_internal-d8-desugar-methods"
private val ATTR_LINT_MIN_SDK: Attribute<String> = Attribute.of("lint-min-sdk", String::class.java)
private val ATTR_LINT_COMPILE_SDK: Attribute<String> = Attribute.of("lint-compile-sdk", String::class.java)

/**
 * Returns a file collection which contains desugar lib jars
 */
fun getDesugarLibJarFromMaven(project: Project): FileCollection {
    val configuration = getDesugarLibConfiguration(project)
    return getArtifactCollection(configuration)
}

/** Implementation of provider holding JSON file value. */
abstract class DesugarConfigJson: ValueSource<String, DesugarConfigJson.Parameters> {
    interface Parameters: ValueSourceParameters {
        val desugarJson: ConfigurableFileCollection
    }

    override fun obtain(): String? {
        val jsonFiles = parameters.desugarJson.files
        return if (jsonFiles.isEmpty()) {
            null
        } else {
            val content = StringBuilder()
            val dirs = jsonFiles.map { it.toPath() }
            dirs.forEach {
                content.append(String(Files.readAllBytes(it), StandardCharsets.UTF_8))
            }
            content.toString()
        }
    }
}

/**
 * Returns a provider which represents the content of desugar.json file extracted from
 * desugar lib configuration jars
 */
fun getDesugarLibConfig(project: Project): Provider<String> {
    val configuration = project.configurations.findByName(CONFIG_NAME_CORE_LIBRARY_DESUGARING)!!

    registerDesugarLibConfigTransform(project)

    return project.providers.of(DesugarConfigJson::class.java) {
        it.parameters.desugarJson.setFrom(getDesugarLibConfigFromTransform(configuration))
    }
}

/**
 * Returns a [FileCollection] which contains files with desugared methods supported by D8 and core
 * library desugaring.
 */
fun getDesugaredMethods(
    project: Project,
    coreLibDesugar: Boolean,
    minSdkVersion: AndroidVersion,
    compileSdkVersion: String?,
    bootclasspath: Provider<List<RegularFile>>
): FileCollection {
    val desugaredMethodsFiles = project.objects.fileCollection()

    val coreLibDesugarConfig =
        project.configurations.findByName(CONFIG_NAME_CORE_LIBRARY_DESUGARING)!!
    if (coreLibDesugar && compileSdkVersion != null) {
        val minSdk = minSdkVersion.getFeatureLevel()
        val compileSdk = AndroidTargetHash.getPlatformVersion(compileSdkVersion)!!.featureLevel
        registerDesugarLibLintTransform(project, minSdk, compileSdk)
        desugaredMethodsFiles.from(
            getDesugarLibLintFromTransform(coreLibDesugarConfig, minSdk, compileSdk))
    }

    val fakeJar = project.gradle.gradleUserHomeDir.resolve("$ANDROID_SUBDIR/$FAKE_DEPENDENCY_JAR")
    if (!fakeJar.exists()) {
        fakeJar.parentFile.mkdirs()
        JarFlinger(fakeJar.toPath()).use {}
    }
    val fakeDependency = project.dependencies.create(project.files(fakeJar))
    val adhocConfiguration = project.configurations.detachedConfiguration(fakeDependency)

    registerD8BackportedMethodsTransform(
        project, coreLibDesugar, project.files(bootclasspath), Version.getVersionString())
    desugaredMethodsFiles.fromDisallowChanges(
        getD8DesugarMethodFileFromTransform(adhocConfiguration)
    )
    return desugaredMethodsFiles
}

/**
 * Returns the configuration of core library to be desugared and throws runtime exception if the
 * user didn't add any dependency to that configuration.
 *
 * Note: this method is only used when core library desugaring is enabled.
 */
private fun getDesugarLibConfiguration(project: Project): Configuration {
    val configuration = project.configurations.findByName(CONFIG_NAME_CORE_LIBRARY_DESUGARING)!!
    if (configuration.dependencies.isEmpty()) {
        throw RuntimeException("$CONFIG_NAME_CORE_LIBRARY_DESUGARING configuration contains no " +
                "dependencies. If you intend to enable core library desugaring, please add " +
                "dependencies to $CONFIG_NAME_CORE_LIBRARY_DESUGARING configuration.")
    }
    return configuration
}

private fun getDesugarLibConfigFromTransform(configuration: Configuration): FileCollection {
    return configuration.incoming.artifactView { configuration ->
        configuration.attributes {
            it.attribute(
                ArtifactAttributes.ARTIFACT_FORMAT,
                DESUGAR_LIB_CONFIG
            )
        }
    }.artifacts.artifactFiles
}

private fun getArtifactCollection(configuration: Configuration): FileCollection =
    configuration.incoming.artifactView { config ->
        config.attributes {
            it.attribute(
                ArtifactAttributes.ARTIFACT_FORMAT,
                ArtifactTypeDefinition.JAR_TYPE
            )
        }
    }.artifacts.artifactFiles

private fun registerDesugarLibConfigTransform(project: Project) {
    project.dependencies.registerTransform(DesugarLibConfigExtractor::class.java) { spec ->
        spec.from.attribute(ArtifactAttributes.ARTIFACT_FORMAT, ArtifactTypeDefinition.JAR_TYPE)
        spec.to.attribute(ArtifactAttributes.ARTIFACT_FORMAT, DESUGAR_LIB_CONFIG)
    }
}

private fun registerDesugarLibLintTransform(project: Project, minSdkVersion: Int, compileSdkVersion: Int) {
    project.dependencies.registerTransform(DesugarLibLintExtractor::class.java) { spec ->
        spec.parameters { parameters ->
            parameters.minSdkVersion.set(minSdkVersion)
            parameters.compileSdkVersion.set(compileSdkVersion)
        }
        spec.from.attribute(ArtifactAttributes.ARTIFACT_FORMAT, ArtifactTypeDefinition.JAR_TYPE)
        spec.to.attribute(ArtifactAttributes.ARTIFACT_FORMAT, DESUGAR_LIB_LINT)
        spec.from.attribute(ATTR_LINT_MIN_SDK, minSdkVersion.toString())
        spec.to.attribute(ATTR_LINT_MIN_SDK, minSdkVersion.toString())
        spec.from.attribute(ATTR_LINT_COMPILE_SDK, compileSdkVersion.toString())
        spec.to.attribute(ATTR_LINT_COMPILE_SDK, compileSdkVersion.toString())
    }
}

private fun registerD8BackportedMethodsTransform(
        project: Project,
        coreLibDesugar: Boolean,
        bootclasspath: ConfigurableFileCollection,
        d8Version: String
) {
    project.dependencies.registerTransform(D8BackportedMethodsGenerator::class.java) { spec ->
        spec.parameters { parameters ->
            parameters.d8Version.set(d8Version)

            if (coreLibDesugar) {
                parameters.coreLibDesugarConfig.set(
                    getDesugarLibConfig(project).forUseAtConfigurationTime())
                parameters.bootclasspath.from(bootclasspath)
            }
        }
        spec.from.attribute(ArtifactAttributes.ARTIFACT_FORMAT, ArtifactTypeDefinition.JAR_TYPE)
        spec.to.attribute(ArtifactAttributes.ARTIFACT_FORMAT, D8_DESUGAR_METHODS)
    }
}

private fun getDesugarLibLintFromTransform(
    configuration: Configuration,
    minSdkVersion: Int,
    compileSdkVersion: Int
): FileCollection {
    return configuration.incoming.artifactView { configuration ->
        configuration.attributes {
            it.attribute(
                ArtifactAttributes.ARTIFACT_FORMAT,
                DESUGAR_LIB_LINT
            )
            it.attribute(ATTR_LINT_MIN_SDK, minSdkVersion.toString())
            it.attribute(ATTR_LINT_COMPILE_SDK, compileSdkVersion.toString())
        }
    }.artifacts.artifactFiles
}

private fun getD8DesugarMethodFileFromTransform(configuration: Configuration): FileCollection {
    return configuration.incoming.artifactView { configuration ->
        configuration.attributes {
            it.attribute(ArtifactAttributes.ARTIFACT_FORMAT, D8_DESUGAR_METHODS)
        }
    }.artifacts.artifactFiles
}

/**
 * Extract the desugar config json file from desugar lib configuration jar.
 */
@CacheableTransform
abstract class DesugarLibConfigExtractor : TransformAction<TransformParameters.None> {
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val inputFile = inputArtifact.get().asFile
        ZipInputStream(inputFile.inputStream().buffered()).use { zipInputStream ->
            while(true) {
                val entry = zipInputStream.nextEntry ?: break
                if (entry.name.endsWith(DESUGAR_LIB_CONFIG_FILE)) {
                    val outputFile = outputs.file(inputFile.nameWithoutExtension + "-$DESUGAR_LIB_CONFIG_FILE")
                    Files.newOutputStream(outputFile.toPath()).buffered().use { output ->
                        ByteStreams.copy(zipInputStream, output)
                    }
                    break
                }
            }
        }
    }
}


/**
 * Extract the specific lint file with desugared APIs based on minSdkVersion & compileSdkVersion
 * from desugar lib configuration jar.
 */
@CacheableTransform
abstract class DesugarLibLintExtractor : TransformAction<DesugarLibLintExtractor.Parameters> {
    interface Parameters: GenericTransformParameters {
        @get:Input
        val minSdkVersion: Property<Int>

        @get:Input
        val compileSdkVersion: Property<Int>
    }

    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val inputFile = inputArtifact.get().asFile
        val minSdkVersion = parameters.minSdkVersion.get()

        // Search for the lint file based on compileSdkVersion specified by the user. If we are not able to find it,
        // fallback to the lint file with a lower compileSdkVersion. Currently, the lowest compileSdkVersion for
        // lint files is 26.
        for (compileSdkVersion in parameters.compileSdkVersion.get() downTo 26) {
            val pattern = if (minSdkVersion >= 21) {
                "${compileSdkVersion}_21.txt"
            } else {
                "${compileSdkVersion}_1.txt"
            }

            ZipInputStream(inputFile.inputStream().buffered()).use { zipInputStream ->
                while(true) {
                    val entry = zipInputStream.nextEntry ?: break
                    if (entry.name.endsWith(pattern)) {
                        val outputFile = outputs.file(inputFile.nameWithoutExtension + "-desugar-lint.txt")
                        Files.newOutputStream(outputFile.toPath()).buffered().use { output ->
                            ByteStreams.copy(zipInputStream, output)
                        }
                        return
                    }
                }
            }
        }
    }
}

/**
 * Generate a file of D8 backported desugared methods by invoking D8 API instead of processing
 * input artifact.
 */
@CacheableTransform
abstract class D8BackportedMethodsGenerator
    : TransformAction<D8BackportedMethodsGenerator.Parameters> {
    interface Parameters: GenericTransformParameters {
        @get:Input
        val d8Version: Property<String>

        @get:Input
        @get:Optional
        val coreLibDesugarConfig: Property<String>

        @get:CompileClasspath
        @get:Optional
        val bootclasspath: ConfigurableFileCollection
    }

    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val outputFile = outputs.file("D8BackportedDesugaredMethods.txt")
        outputFile.printWriter().use {
            D8DesugaredMethodsGenerator.generate(
                parameters.coreLibDesugarConfig.orNull,
                parameters.bootclasspath.files
            ).forEach { method->
                it.println(method)
            }
        }
    }
}
