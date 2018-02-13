/*
 * Copyright (C) 2018 The Android Open Source Project
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

/**
 * Utilities related to AAPT2 Daemon management.
 */
@file:JvmName("Aapt2MavenUtils")

package com.android.build.gradle.internal.res.namespaced

import com.android.SdkConstants
import com.android.annotations.VisibleForTesting
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.options.BooleanOption
import com.android.builder.model.Version
import com.google.common.collect.ImmutableList
import com.google.common.collect.Sets
import com.google.common.io.ByteStreams
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.transform.ArtifactTransform
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.ArtifactAttributes
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.zip.ZipInputStream
import javax.inject.Inject

private const val TYPE_EXTRACTED_AAPT2_BINARY = "_internal-android-aapt2-binary"
private const val AAPT2_CONFIG_NAME = "_internal_aapt2_binary"

/**
 * Returns a file collection, which will contain the directory with AAPT2 to be used.
 *
 * Returns null if aapt2 should be used from the SDK.
 *
 * On first invocation, creates a new configuration ([AAPT2_CONFIG_NAME]) that
 * references the AAPT2 binary for the current platform and registers the
 * artifact transform to unzip the archive. The file collection returned is
 * backed by the artifact view of the transformed dependency.
 *
 * Idempotent.
 */
fun getAapt2FromMavenIfEnabled(globalScope: GlobalScope): FileCollection? {
    if (!globalScope.projectOptions[BooleanOption.USE_AAPT2_FROM_MAVEN]) {
        return null
    }
    return getAapt2FromMaven(globalScope.project)
}

/**
 * Returns a file collection, which will contain the directory with AAPT2 to be used.
 *
 * Ignores the feature flag [BooleanOption.USE_AAPT2_FROM_MAVEN]. Used for the namespaced pipeline.
 *
 * See [getAapt2FromMaven].
 *
 * Idempotent.
 */
fun getAapt2FromMaven(globalScope: GlobalScope): FileCollection {
    return getAapt2FromMaven(globalScope.project)
}

@VisibleForTesting
fun getAapt2FromMaven(project: Project): FileCollection {
    val existingConfig = project.configurations.findByName(AAPT2_CONFIG_NAME)
    if (existingConfig != null) {
        return getArtifactCollection(existingConfig)
    }

    val config = project.configurations.create(AAPT2_CONFIG_NAME) {
        it.isVisible = false
        it.isTransitive = false
        it.isCanBeConsumed = false
        it.description = "The AAPT2 binary to use for processing resources."
    }
    // See tools/base/aapt2 for the classifiers to use.
    val classifier = when (SdkConstants.currentPlatform()) {
        SdkConstants.PLATFORM_WINDOWS -> "windows"
        SdkConstants.PLATFORM_DARWIN -> "osx"
        SdkConstants.PLATFORM_LINUX -> "linux"
        else -> throw IllegalStateException("Unknown platform '${System.getProperty("os.name")}'")
    }
    val version = Version.ANDROID_GRADLE_PLUGIN_VERSION
    project.dependencies.add(
        config.name,
        mapOf<String, String>(
            "group" to "com.android.tools.build",
            "name" to "aapt2",
            "version" to version,
            "classifier" to classifier
        )
    )

    project.dependencies.registerTransform {
        it.from.attribute(ArtifactAttributes.ARTIFACT_FORMAT, ArtifactTypeDefinition.JAR_TYPE)
        it.to.attribute(
            ArtifactAttributes.ARTIFACT_FORMAT,
            TYPE_EXTRACTED_AAPT2_BINARY
        )
        it.artifactTransform(Aapt2Extractor::class.java)
    }

    return getArtifactCollection(config)

}

private fun getArtifactCollection(configuration: Configuration): FileCollection =
    configuration.incoming.artifactView { config ->
        config.attributes {
            it.attribute(
                ArtifactAttributes.ARTIFACT_FORMAT,
                TYPE_EXTRACTED_AAPT2_BINARY
            )
        }
    }.artifacts.artifactFiles

class Aapt2Extractor @Inject constructor() : ArtifactTransform() {
    override fun transform(input: File): MutableList<File> {
        val outDir = outputDirectory.toPath()
        Files.createDirectories(outDir)
        ZipInputStream(input.inputStream().buffered()).use { zipInputStream ->
            while (true) {
                val entry = zipInputStream.nextEntry ?: break
                if (entry.isDirectory) {
                    continue
                }
                val destinationFile = outDir.resolve(entry.name)
                Files.createDirectories(destinationFile.parent)
                Files.newOutputStream(destinationFile).buffered().use { output ->
                    ByteStreams.copy(zipInputStream, output)
                }
                // Mark executable on linux.
                if (entry.name == SdkConstants.FN_AAPT2 &&
                    (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_LINUX || SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_DARWIN)) {
                    val permissions = Files.getPosixFilePermissions(destinationFile)
                    Files.setPosixFilePermissions(
                        destinationFile,
                        Sets.union(permissions, setOf(PosixFilePermission.OWNER_EXECUTE))
                    )
                }
            }
        }
        return ImmutableList.of(outDir.toFile())
    }
}