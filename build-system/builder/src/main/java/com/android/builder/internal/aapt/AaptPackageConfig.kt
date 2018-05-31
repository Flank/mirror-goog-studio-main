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

package com.android.builder.internal.aapt

import com.android.builder.core.VariantType
import com.android.sdklib.IAndroidTarget
import com.google.common.collect.ImmutableCollection
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import java.io.File
import java.io.Serializable

/**
 * Configuration for an `aapt package` or an `aapt2 link` operation.
 */
data class AaptPackageConfig(
        val manifestFile: File,
        val options: AaptOptions,
        val androidJarPath: String,
        val variantType: VariantType,
        val sourceOutputDir: File? = null,
        val resourceOutputApk: File? = null,
        val librarySymbolTableFiles: ImmutableCollection<File> = ImmutableList.of(),
        val symbolOutputDir: File? = null,
        val verbose: Boolean = false,
        val resourceDirs: ImmutableList<File> = ImmutableList.of(),
        val proguardOutputFile: File? = null,
        val mainDexListProguardOutputFile: File? = null,
        val splits: ImmutableCollection<String>? = null,
        val debuggable: Boolean = false,
        val customPackageForR: String? = null,
        val pseudoLocalize: Boolean = false,
        val preferredDensity: String? = null,
        val resourceConfigs: ImmutableSet<String> = ImmutableSet.of(),
        val generateProtos: Boolean = false,
        val imports: ImmutableList<File> = ImmutableList.of(),
        val packageId: Int? = null,
        val allowReservedPackageId: Boolean = false,
        val dependentFeatures: ImmutableCollection<File> = ImmutableList.of(),
        val listResourceFiles: Boolean = false,
        val staticLibrary: Boolean = false,
        val staticLibraryDependencies: ImmutableList<File> = ImmutableList.of(),
        val intermediateDir: File? = null
) : Serializable {

    fun isStaticLibrary() = staticLibrary
    fun isListResourceFiles() = listResourceFiles

    /** Builder used to create a [AaptPackageConfig] from java.  */
    class Builder {

        private var manifestFile: File? = null
        private var options: AaptOptions? = null
        private var sourceOutputDir: File? = null
        private var resourceOutputApk: File? = null
        private var librarySymbolTableFiles: ImmutableCollection<File> = ImmutableSet.of()
        private var symbolOutputDir: File? = null
        private var isVerbose: Boolean = false
        private var resourceDirs: ImmutableList<File> = ImmutableList.of()
        private var proguardOutputFile: File? = null
        private var mainDexListProguardOutputFile: File? = null
        private var splits: ImmutableCollection<String>? = null
        private var debuggable: Boolean = false
        private var customPackageForR: String? = null
        private var pseudoLocalize: Boolean = false
        private var preferredDensity: String? = null
        private var androidJarPath: String? = null
        private var resourceConfigs: ImmutableSet<String> = ImmutableSet.of()
        private var isGenerateProtos: Boolean = false
        private var variantType: VariantType? = null
        private var imports: ImmutableList<File> = ImmutableList.of()
        private var packageId: Int? = null
        private var allowReservedPackageId: Boolean = false
        private var dependentFeatures: ImmutableCollection<File> = ImmutableList.of()
        private var listResourceFiles: Boolean = false
        private var staticLibrary: Boolean = false
        private var staticLibraryDependencies: ImmutableList<File> = ImmutableList.of()
        private var intermediateDir: File? = null

        /**
         * Creates a new [AaptPackageConfig] from the data already placed in the builder.
         *
         * @return the new config
         */
        fun build(): AaptPackageConfig {
            return AaptPackageConfig(
                    manifestFile = manifestFile!!,
                    options = options!!,
                    androidJarPath = androidJarPath!!,
                    sourceOutputDir = sourceOutputDir,
                    resourceOutputApk = resourceOutputApk,
                    librarySymbolTableFiles = librarySymbolTableFiles,
                    symbolOutputDir = symbolOutputDir,
                    verbose = isVerbose,
                    resourceDirs = resourceDirs,
                    proguardOutputFile = proguardOutputFile,
                    mainDexListProguardOutputFile = mainDexListProguardOutputFile,
                    splits = splits,
                    debuggable = debuggable,
                    customPackageForR = customPackageForR,
                    pseudoLocalize = pseudoLocalize,
                    preferredDensity = preferredDensity,
                    resourceConfigs = resourceConfigs,
                    generateProtos = isGenerateProtos,
                    variantType = variantType!!,
                    imports = imports,
                    packageId = packageId,
                    allowReservedPackageId = allowReservedPackageId,
                    dependentFeatures = dependentFeatures,
                    listResourceFiles = listResourceFiles,
                    staticLibrary = staticLibrary,
                    staticLibraryDependencies = staticLibraryDependencies,
                    intermediateDir = intermediateDir
            )
        }

        fun setManifestFile(manifestFile: File): Builder {
            if (!manifestFile.isFile) {
                throw IllegalArgumentException(
                        "Manifest file '"
                                + manifestFile.absolutePath
                                + "' is not a readable file. ")
            }
            this.manifestFile = manifestFile
            return this
        }

        fun setOptions(options: AaptOptions): Builder {
            this.options = options
            return this
        }

        fun setSourceOutputDir(sourceOutputDir: File?): Builder {
            this.sourceOutputDir = sourceOutputDir
            return this
        }

        fun setSymbolOutputDir(symbolOutputDir: File?): Builder {
            this.symbolOutputDir = symbolOutputDir
            return this
        }

        fun setLibrarySymbolTableFiles(libraries: Set<File>?): Builder {
            if (libraries == null) {
                this.librarySymbolTableFiles = ImmutableSet.of()
            } else {
                this.librarySymbolTableFiles = ImmutableSet.copyOf(libraries)
            }

            return this
        }

        fun setResourceOutputApk(resourceOutputApk: File?): Builder {
            this.resourceOutputApk = resourceOutputApk
            return this
        }

        fun setResourceDir(resourceDir: File?): Builder {
            if (resourceDir != null && !resourceDir.isDirectory) {
                throw IllegalArgumentException("Path '" + resourceDir.absolutePath
                        + "' is not a readable directory.")
            }

            this.resourceDirs = ImmutableList.of(resourceDir!!)
            return this
        }

        fun setProguardOutputFile(proguardOutputFile: File?): Builder {
            this.proguardOutputFile = proguardOutputFile
            return this
        }

        fun setMainDexListProguardOutputFile(mainDexListProguardOutputFile: File?): Builder {
            this.mainDexListProguardOutputFile = mainDexListProguardOutputFile
            return this
        }

        fun setSplits(splits: Collection<String>?): Builder {
            if (splits == null) {
                this.splits = null
            } else {
                this.splits = ImmutableList.copyOf(splits)
            }

            return this
        }

        fun setDebuggable(debuggable: Boolean): Builder {
            this.debuggable = debuggable
            return this
        }

        fun setPseudoLocalize(pseudoLocalize: Boolean): Builder {
            this.pseudoLocalize = pseudoLocalize
            return this
        }

        fun setPreferredDensity(preferredDensity: String?): Builder {
            this.preferredDensity = preferredDensity
            return this
        }

        fun setAndroidTarget(androidTarget: IAndroidTarget): Builder {
            this.androidJarPath = androidTarget.getPath(IAndroidTarget.ANDROID_JAR)
            return this
        }

        fun setAndroidJarPath(androidJarPath: String): Builder {
            this.androidJarPath = androidJarPath
            return this
        }

        fun setResourceConfigs(resourceConfigs: Collection<String>): Builder {
            this.resourceConfigs = ImmutableSet.copyOf(resourceConfigs)
            return this
        }

        fun setVariantType(variantType: VariantType): Builder {
            this.variantType = variantType
            return this
        }

        fun setCustomPackageForR(packageForR: String?): Builder {
            this.customPackageForR = packageForR
            return this
        }

        fun setImports(imports: Collection<File>): Builder {
            this.imports = ImmutableList.copyOf(imports)
            return this
        }

        fun setPackageId(packageId: Int?): Builder {
            this.packageId = packageId
            return this
        }

        fun setDependentFeatures(dependentFeatures: Collection<File>): Builder {
            this.dependentFeatures = ImmutableSet.copyOf(dependentFeatures)
            return this
        }

        fun setStaticLibraryDependencies(libraries: ImmutableList<File>): Builder {
            this.staticLibraryDependencies = libraries
            return this
        }

        fun setIntermediateDir(intermediateDir: File): Builder {
            this.intermediateDir = intermediateDir
            return this
        }

        /**
         * Allows the use of a reserved package ID. This should on be used for packages with a
         * pre-O min-sdk
         */
        fun setAllowReservedPackageId(value: Boolean): Builder {
            this.allowReservedPackageId = value
            return this
        }
    }
}
