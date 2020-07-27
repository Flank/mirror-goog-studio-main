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
import com.android.SdkConstants.APPCOMPAT_LIB_ARTIFACT
import com.android.SdkConstants.LEANBACK_V17_ARTIFACT
import com.android.sdklib.AndroidTargetHash
import com.android.sdklib.AndroidVersion
import com.android.support.AndroidxNameUtils
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.model.LintModelExternalLibrary
import com.android.tools.lint.model.LintModelMavenName
import com.android.tools.lint.model.LintModelModule
import com.android.tools.lint.model.LintModelModuleType
import com.android.tools.lint.model.LintModelSourceProvider
import com.android.tools.lint.model.LintModelVariant
import com.android.utils.XmlUtils
import com.google.common.collect.Lists
import java.io.File
import java.io.IOException
import java.util.ArrayList

/**
 * Lint project for a project backed by a [LintModelModule] (which could be an app, a library,
 * dynamic feature, etc.
 */
open class LintModelModuleProject(
    client: LintClient,
    dir: File,
    referenceDir: File,
    private val variant: LintModelVariant,
    mergedManifest: File?
) : Project(client, dir, referenceDir) {
    private val model: LintModelModule get() = variant.module

    init {
        gradleProject = true
        mergeManifests = true
        directLibraries = mutableListOf()
        mergedManifest?.let { readManifest(it) }
        manifestMinSdk = variant.minSdkVersion
        manifestTargetSdk = variant.targetSdkVersion
    }

    fun setExternalLibrary(external: Boolean) {
        externalLibrary = external
    }

    fun setMavenCoordinates(mc: LintModelMavenName) {
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

    private val sourceProviders: List<LintModelSourceProvider>
        get() = variant.sourceProviders

    private val testSourceProviders: List<LintModelSourceProvider>
        get() = variant.testSourceProviders

    override fun getBuildModule(): LintModelModule = variant.module
    override fun getBuildVariant(): LintModelVariant? = variant
    override fun isLibrary(): Boolean = model.type === LintModelModuleType.LIBRARY ||
        model.type === LintModelModuleType.JAVA_LIBRARY

    override fun isAndroidProject(): Boolean = type != LintModelModuleType.JAVA_LIBRARY
    override fun hasDynamicFeatures(): Boolean =
        model.type === LintModelModuleType.APP && model.dynamicFeatures.isNotEmpty()

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

    override fun getJavaSourceFolders(): List<File> {
        if (javaSourceFolders == null) {
            javaSourceFolders = Lists.newArrayList()
            sourceProviders.forEach { provider ->
                // model returns path whether or not it exists
                provider.javaDirectories.asSequence().filter { it.exists() }.forEach {
                    javaSourceFolders.add(it)
                }
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
            testSourceProviders.forEach { provider ->
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
            for (outputClassFolder in mainArtifact.classOutputs) {
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
                        for (outputClassFolder in mainArtifact.classOutputs) {
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
                // TODO: Why direct here and all in test libraries? And shouldn't
                // this be tied to checkDependencies somehow? If we're creating
                // project from the android libraries then I'll get the libraries there
                // right?
                val dependencies = variant.mainArtifact.dependencies
                val direct = dependencies.compileDependencies.roots
                javaLibraries = Lists.newArrayListWithExpectedSize(direct.size)
                for (graphItem in direct) {
                    val library = graphItem.findLibrary() ?: continue
                    if (library !is LintModelExternalLibrary) continue
                    library.addJars(javaLibraries, false)
                }
            }
            javaLibraries
        } else {
            // Skip provided libraries?
            if (nonProvidedJavaLibraries == null) {
                val dependencies = variant.mainArtifact.dependencies
                val direct = dependencies.packageDependencies.roots
                nonProvidedJavaLibraries = Lists.newArrayListWithExpectedSize(direct.size)
                for (graphItem in direct) {
                    val library = graphItem.findLibrary() ?: continue
                    if (library !is LintModelExternalLibrary) continue
                    library.addJars(nonProvidedJavaLibraries, true)
                }
            }
            nonProvidedJavaLibraries
        }
    }

    override fun getTestLibraries(): List<File> {
        if (testLibraries == null) {
            testLibraries = Lists.newArrayListWithExpectedSize(6)
            variant.androidTestArtifact?.let { artifact ->
                for (library in artifact.dependencies.getAll()) {
                    // Note that we don't filter out AndroidLibraries here like
                    // // for getJavaLibraries, but we need to include them
                    // for tests since we don't keep them otherwise
                    // (TODO: Figure out why)
                    if (library !is LintModelExternalLibrary) continue
                    library.addJars(testLibraries, false)
                }
            }
            variant.testArtifact?.let { artifact ->
                for (library in artifact.dependencies.getAll()) {
                    if (library !is LintModelExternalLibrary) continue
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
            val targetSdk = variant.targetSdkVersion ?: minSdkVersion
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
                    val a = variant.mainArtifact
                    supportLib = a.findCompileDependency(ANDROIDX_SUPPORT_LIB_ARTIFACT) != null ||
                        a.findCompileDependency("com.android.support:support-v4") != null
                }
                supportLib
            }
            ANDROIDX_APPCOMPAT_LIB_ARTIFACT -> {
                if (appCompat == null) {
                    val a = variant.mainArtifact
                    appCompat = a.findCompileDependency(ANDROIDX_APPCOMPAT_LIB_ARTIFACT) != null ||
                        a.findCompileDependency(APPCOMPAT_LIB_ARTIFACT) != null
                }
                appCompat
            }
            ANDROIDX_LEANBACK_ARTIFACT -> {
                if (leanback == null) {
                    val a = variant.mainArtifact
                    leanback = a.findCompileDependency(ANDROIDX_LEANBACK_ARTIFACT) != null ||
                        a.findCompileDependency(LEANBACK_V17_ARTIFACT) != null
                }
                leanback
            }
            else -> super.dependsOn(id)
        }
    }
}

/**
 * Adds all the jar files from this library into the given list, skipping provided
 * libraries if requested
 */
fun LintModelExternalLibrary.addJars(list: MutableList<File>, skipProvided: Boolean) {
    if (skipProvided && provided) {
        return
    }

    for (jar in jarFiles) {
        if (!list.contains(jar)) {
            if (jar.exists()) {
                list.add(jar)
            }
        }
    }
}
