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
package com.android.tools.lint.detector.api

import com.android.SdkConstants.ANDROIDX_APPCOMPAT_LIB_ARTIFACT
import com.android.SdkConstants.ANDROIDX_LEANBACK_ARTIFACT
import com.android.SdkConstants.ANDROIDX_SUPPORT_LIB_ARTIFACT
import com.android.sdklib.AndroidTargetHash
import com.android.sdklib.AndroidVersion
import com.android.support.AndroidxNameUtils
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.model.LmDependencies
import com.android.tools.lint.model.LmJavaLibrary
import com.android.tools.lint.model.LmLibrary
import com.android.tools.lint.model.LmMavenName
import com.android.tools.lint.model.LmModule
import com.android.tools.lint.model.LmModuleType
import com.android.tools.lint.model.LmSourceProvider
import com.android.tools.lint.model.LmVariant
import com.android.utils.XmlUtils
import com.google.common.collect.Lists
import java.io.File
import java.io.IOException
import java.util.ArrayList
import java.util.LinkedHashSet

/**
 * Lint project for a project backed by a [LmModule] (which could be an app, a library,
 * dynamic feature, etc.
 */
open class LmModuleProject(
    client: LintClient,
    dir: File,
    referenceDir: File,
    private val variant: LmVariant,
    mergedManifest: File?
) : Project(client, dir, referenceDir) {
    private val model: LmModule get() = variant.module

    init {
        gradleProject = true
        mergeManifests = true
        directLibraries = mutableListOf()
        mergedManifest?.let { readManifest(it) }
    }

    @JvmField
    var kotlinSourceFolders: List<File>? = null

    fun setExternalLibrary(external: Boolean) {
        externalLibrary = external
    }

    fun setMavenCoordinates(mc: LmMavenName) {
        mavenCoordinates = mc
    }

    fun addDirectLibrary(project: Project) {
        directLibraries.add(project)
    }

    private fun readManifest(manifest: File) {
        if (manifest.exists()) {
            try {
                val xml = manifest.readText()
                val document = XmlUtils.parseDocumentSilently(xml, true)
                document?.let { readManifest(it) }
            } catch (e: IOException) {
                client.log(e, "Could not read manifest %1\$s", manifest)
            }
        }
    }

    override fun initialize() {
        // Deliberately not calling super; that code is for ADT compatibility
    }

    private val sourceProviders: List<LmSourceProvider>
        get() = variant.sourceProviders

    private val testSourceProviders: List<LmSourceProvider>
        get() = variant.testSourceProviders

    override fun getBuildModule(): LmModule = variant.module
    override fun getBuildVariant(): LmVariant? = variant
    override fun isLibrary(): Boolean = model.type === LmModuleType.LIBRARY
    override fun hasDynamicFeatures(): Boolean =
        model.type === LmModuleType.APP && model.dynamicFeatures.isNotEmpty()

    override fun getManifestFiles(): List<File> {
        if (manifestFiles == null) {
            manifestFiles = Lists.newArrayList()
            sourceProviders.forEach { provider ->
                val manifestFile = provider.manifestFile
                if (manifestFile.exists()) { // model returns path whether or not it exists
                    manifestFiles.add(manifestFile)
                }
            }
        }
        return manifestFiles
    }

    override fun getProguardFiles(): List<File> {
        if (proguardFiles == null) {
            proguardFiles = variant.proguardFiles + variant.consumerProguardFiles
            // proguardFiles.addAll(container.config.getTestProguardFiles())
        }
        return proguardFiles
    }

    override fun getResourceFolders(): List<File> {
        if (resourceFolders == null) {
            resourceFolders = Lists.newArrayList()
            sourceProviders.forEach { provider ->
                // model returns path whether or not it exists
                provider.resDirectories.asSequence().filter { it.exists() }.forEach {
                    resourceFolders.add(it)
                }
            }
        }
        return resourceFolders
    }

    override fun getGeneratedResourceFolders(): List<File> {
        if (generatedResourceFolders == null) {
            generatedResourceFolders = variant.mainArtifact.generatedResourceFolders.asSequence()
                .filter { it.exists() }.toList()
        }
        return generatedResourceFolders
    }

    override fun getAssetFolders(): List<File> {
        if (assetFolders == null) {
            assetFolders = Lists.newArrayList()
            sourceProviders.forEach { provider ->
                // model returns path whether or not it exists
                provider.assetsDirectories.asSequence().filter { it.exists() }.forEach {
                    assetFolders.add(it)
                }
            }
        }
        return assetFolders
    }

    private fun addUnique(file: File, result: MutableList<File>, uniqueFiles: MutableSet<File>) {
        val canonical = try {
            file.canonicalFile
        } catch (e: IOException) {
            file
        }
        if (uniqueFiles.add(canonical)) {
            result.add(file)
        }
    }

    override fun getJavaSourceFolders(): List<File> {
        if (javaSourceFolders == null) {
            javaSourceFolders = Lists.newArrayList()
            // The Kotlin source folders might overlap with the Java source folders.
            val uniqueFiles: MutableSet<File> = LinkedHashSet()
            sourceProviders.forEach { provider ->
                // Model returns path whether or not it exists.
                provider.javaDirectories.asSequence().filter { it.exists() }.forEach {
                    addUnique(it, javaSourceFolders, uniqueFiles)
                }
            }
            kotlinSourceFolders?.asSequence()?.filter { it.exists() }?.forEach {
                addUnique(it, javaSourceFolders, uniqueFiles)
            }
        }
        return javaSourceFolders
    }

    override fun getGeneratedSourceFolders(): List<File> {
        if (generatedSourceFolders == null) {
            val artifact = variant.mainArtifact
            generatedSourceFolders = artifact.generatedSourceFolders.asSequence()
                .filter { it.exists() }.toList()
        }
        return generatedSourceFolders
    }

    override fun getTestSourceFolders(): List<File> {
        if (testSourceFolders == null) {
            testSourceFolders = Lists.newArrayList()
            testSourceProviders?.forEach { provider ->
                // model returns path whether or not it exists
                provider.javaDirectories.asSequence().filter { it.exists() }.forEach {
                    testSourceFolders.add(it)
                }
            }
        }
        return testSourceFolders
    }

    override fun getJavaClassFolders(): List<File> {
        if (javaClassFolders == null) {
            javaClassFolders = ArrayList(3) // common: javac, kotlinc, R.jar
            var mainArtifact = variant.mainArtifact
            for (outputClassFolder in mainArtifact.classFolders) {
                if (outputClassFolder.exists()) {
                    javaClassFolders.add(outputClassFolder)
                }
            }
            if (javaClassFolders.isEmpty() && isLibrary) {
                // For libraries we build the release variant instead
                for (variant in model.variants) {
                    if (variant != this.variant) {
                        mainArtifact = variant.mainArtifact
                        var found = false
                        for (outputClassFolder in mainArtifact.classFolders) {
                            if (outputClassFolder.exists()) {
                                javaClassFolders.add(outputClassFolder)
                                found = true
                            }
                        }
                        if (found) {
                            break
                        }
                    }
                }
            }
        }
        return javaClassFolders
    }

    override fun getJavaLibraries(includeProvided: Boolean): List<File> {
        return if (includeProvided) {
            if (javaLibraries == null) {
                val dependencies = variant.mainArtifact.dependencies
                // TODO: Why direct here and all in test libraries? And shouldn't
                // this be tied to checkDependencies somehow? If we're creating
                // project from the android libraries then I'll get the libraries there
                // right?
                val direct = dependencies.direct
                javaLibraries = Lists.newArrayListWithExpectedSize(direct.size)
                for (library in direct) {
                    (library as? LmJavaLibrary)?.addJars(javaLibraries, false)
                }
            }
            javaLibraries
        } else {
            // Skip provided libraries?
            if (nonProvidedJavaLibraries == null) {
                val dependencies = variant.mainArtifact.dependencies
                val direct = dependencies.direct
                nonProvidedJavaLibraries = Lists.newArrayListWithExpectedSize(direct.size)
                for (library in direct) {
                    (library as? LmJavaLibrary)?.addJars(nonProvidedJavaLibraries, true)
                }
            }
            nonProvidedJavaLibraries
        }
    }

    override fun getTestLibraries(): List<File> {
        if (testLibraries == null) {
            testLibraries = Lists.newArrayListWithExpectedSize(6)
            variant.androidTestArtifact?.let { artifact ->
                for (library in artifact.dependencies.all) {
                    // Note that we don't filter out AndroidLibraries here like
                    // // for getJavaLibraries, but we need to include them
                    // for tests since we don't keep them otherwise
                    // (TODO: Figure out why)
                    library.addJars(testLibraries, false)
                }
            }
            variant.testArtifact?.let { artifact ->
                for (library in artifact.dependencies.all) {
                    library.addJars(testLibraries, false)
                }
            }
        }
        return testLibraries
    }

    override fun getPackage(): String? {
        if (pkg == null) { // only used as a fallback in case manifest somehow is null
            val packageName = variant.`package`
            if (packageName != null) {
                return packageName
            }
        }
        return pkg // from manifest
    }

    override fun getMinSdkVersion(): AndroidVersion {
        return manifestMinSdk ?: run {
            val minSdk = variant.minSdkVersion
                ?: super.getMinSdkVersion() // from manifest
            manifestMinSdk = minSdk
            minSdk
        }
    }

    override fun getTargetSdkVersion(): AndroidVersion {
        return manifestTargetSdk ?: run {
            val targetSdk = variant.targetSdkVersion
                ?: super.getTargetSdkVersion() // from manifest
            manifestTargetSdk = targetSdk
            targetSdk
        }
    }

    override fun getBuildSdk(): Int {
        if (buildSdk == -1) {
            val compileTarget = model.compileTarget
            val version = AndroidTargetHash.getPlatformVersion(compileTarget)
            buildSdk = version?.featureLevel ?: super.getBuildSdk()
        }
        return buildSdk
    }

    override fun getBuildTargetHash(): String? {
        return model.compileTarget
    }

    override fun dependsOn(artifact: String): Boolean? {
        @Suppress("MoveVariableDeclarationIntoWhen") // also used in else
        val id = AndroidxNameUtils.getCoordinateMapping(artifact)
        return when (id) {
            ANDROIDX_SUPPORT_LIB_ARTIFACT -> {
                if (supportLib == null) {
                    // OR,
                    // androidx.legacy:legacy-support-v4
                    val dependencies = variant.mainArtifact.dependencies
                    supportLib = dependsOn(dependencies, ANDROIDX_SUPPORT_LIB_ARTIFACT)
                }
                supportLib
            }
            ANDROIDX_APPCOMPAT_LIB_ARTIFACT -> {
                if (appCompat == null) {
                    val dependencies = variant.mainArtifact.dependencies
                    appCompat = dependsOn(dependencies, ANDROIDX_APPCOMPAT_LIB_ARTIFACT)
                }
                appCompat
            }
            ANDROIDX_LEANBACK_ARTIFACT -> {
                if (leanback == null) {
                    val dependencies = variant.mainArtifact.dependencies
                    leanback = dependsOn(dependencies, ANDROIDX_LEANBACK_ARTIFACT)
                }
                leanback
            }
            else -> super.dependsOn(id)
        }
    }

    companion object {
        fun dependsOn(
            dependencies: LmDependencies,
            artifact: String
        ): Boolean {
            for (library in dependencies.all) {
                if (libraryMatches(artifact, library)) {
                    return true
                }
            }
            return false
        }

        fun dependsOn(
            library: LmLibrary,
            artifact: String?
        ): Boolean {
            if (libraryMatches(artifact!!, library)) {
                return true
            }
            for (dependency in library.dependencies) {
                if (dependsOn(dependency, artifact)) {
                    return true
                }
            }
            return false
        }

        private fun libraryMatches(
            artifact: String,
            lib: LmLibrary
        ): Boolean {
            val (groupId, artifactId) = lib.resolvedCoordinates
            var c = "$groupId:$artifactId"
            c = AndroidxNameUtils.getCoordinateMapping(c)
            return artifact == c
        }
    }
}
