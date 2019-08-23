/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License: InternalArtifactType<RegularFile>(FILE) Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing: InternalArtifactType<RegularFile>(FILE) software
 * distributed under the License is distributed on an "AS IS" BASIS: InternalArtifactType<RegularFile>(FILE)
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND: InternalArtifactType<RegularFile>(FILE) either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.internal.scope

import com.android.build.api.artifact.ArtifactType
import com.android.build.api.artifact.ArtifactKind
import org.gradle.api.file.Directory
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import java.io.File
import java.util.Locale

/** A type of output generated by a task.  */
sealed class InternalArtifactType<T : FileSystemLocation>(kind: ArtifactKind<T>,
    val category: Category = Category.INTERMEDIATES,
    private val folderName: String? = null
) : ArtifactType<T>(kind) {
    override val isPublic: Boolean
        get() = false

    // --- classes ---
    // These are direct task outputs. If you are looking for all the classes of a
    // module: InternalArtifactType<RegularFile>(FILE) use AnchorOutputType.ALL_CLASSES
    // Javac task output.
    object JAVAC: InternalArtifactType<Directory>(DIRECTORY)
    // Rewritten classes from non-namespaced dependencies put together into one JAR.
    object NAMESPACED_CLASSES_JAR: InternalArtifactType<RegularFile>(FILE)
    // Tested code classes
    object TESTED_CODE_CLASSES: InternalArtifactType<Directory>(DIRECTORY)
    // Classes with recalculated stack frames information (RecalculateStackFrames task)
    object FIXED_STACK_FRAMES: InternalArtifactType<Directory>(DIRECTORY)

    // --- Published classes ---
    // Class-type task output for tasks that generate published classes.

    // Packaged classes for library intermediate publishing
    // This is for external usage. For usage inside a module use ALL_CLASSES
    object RUNTIME_LIBRARY_CLASSES: InternalArtifactType<RegularFile>(FILE)

    // Packaged library classes published only to compile configuration. This is to allow other
    // projects to compile again classes that were not additionally processed e.g. classes with
    // Jacoco instrumentation (b/109771903).
    object COMPILE_LIBRARY_CLASSES: InternalArtifactType<RegularFile>(FILE)

    // Jar generated by the code shrinker
    object SHRUNK_CLASSES: InternalArtifactType<RegularFile>(FILE)

    // Dex archive artifacts for project.
    object PROJECT_DEX_ARCHIVE: InternalArtifactType<Directory>(DIRECTORY)
    // Dex archive artifacts for sub projects.
    object SUB_PROJECT_DEX_ARCHIVE: InternalArtifactType<Directory>(DIRECTORY)
    // Dex archive artifacts for external (Maven) libraries.
    object EXTERNAL_LIBS_DEX_ARCHIVE: InternalArtifactType<Directory>(DIRECTORY)
    // Dex archive artifacts for dex'ed classes coming from mixed scopes (to support legacy
    // Transform API that can output classes belonging to multiple scopes).
    object MIXED_SCOPE_DEX_ARCHIVE: InternalArtifactType<Directory>(DIRECTORY)
    // Artifact for supporting faster incremental dex archive building. This artifact contains
    // information about inputs and it should not be consumed by other tasks.
    object DEX_ARCHIVE_INPUT_JAR_HASHES: InternalArtifactType<RegularFile>(FILE)

    // External libraries' dex files only.
    object EXTERNAL_LIBS_DEX: InternalArtifactType<Directory>(DIRECTORY)
    // External file library dex archives (Desugared separately from module & project dependencies)
    object EXTERNAL_FILE_LIB_DEX_ARCHIVES: InternalArtifactType<Directory>(DIRECTORY)
    // The final dex files (if the dex splitter does not run)
    // that will get packaged in the APK or bundle.
    object DEX: InternalArtifactType<Directory>(DIRECTORY)
    // the packaged classes published by APK modules.
    // This is for external usage. For usage inside a module use ALL_CLASSES
    object APP_CLASSES: InternalArtifactType<RegularFile>(FILE)

    // Outputs of Desugar tool for project classes.
    object DESUGAR_PROJECT_CLASSES: InternalArtifactType<Directory>(DIRECTORY)
    // Outputs of Desugar tool for sub-project classes.
    object DESUGAR_SUB_PROJECT_CLASSES: InternalArtifactType<Directory>(DIRECTORY)
    // Outputs of Desugar tool for external (Maven) library classes.
    object DESUGAR_EXTERNAL_LIBS_CLASSES: InternalArtifactType<Directory>(DIRECTORY)
    // Local state output for desugar.
    object DESUGAR_LOCAL_STATE_OUTPUT: InternalArtifactType<Directory>(DIRECTORY)
    // The output of L8 invocation, which is the dex output of desugar_jdk_libs.jar
    object DESUGAR_LIB_DEX: InternalArtifactType<RegularFile>(FILE)

    // --- java res ---
    // java processing output
    object JAVA_RES: InternalArtifactType<Directory>(DIRECTORY)
    // merged java resources
    object MERGED_JAVA_RES: InternalArtifactType<RegularFile>(FILE)
    // packaged java res for aar intermediate publishing
    object LIBRARY_JAVA_RES: InternalArtifactType<RegularFile>(FILE)
    // Resources generated by the code shrinker
    object SHRUNK_JAVA_RES: InternalArtifactType<RegularFile>(FILE)

    // Full jar with both classes and java res.
    object FULL_JAR: InternalArtifactType<RegularFile>(FILE)

    // Jar generated by the code shrinker containing both Java classes and resources
    object SHRUNK_JAR: InternalArtifactType<RegularFile>(FILE)

    // A folder with classes instrumented with jacoco. Internal folder file structure reflects
    // the hierarchy of namespaces
    object JACOCO_INSTRUMENTED_CLASSES: InternalArtifactType<Directory>(DIRECTORY)
    // A folder containing jars with classes instrumented with jacoco
    object JACOCO_INSTRUMENTED_JARS: InternalArtifactType<Directory>(DIRECTORY)
    // The jacoco code coverage from the connected task
    object CODE_COVERAGE: InternalArtifactType<Directory>(DIRECTORY, Category.OUTPUTS)
    // The jacoco code coverage from the device provider tasks.
    object DEVICE_PROVIDER_CODE_COVERAGE: InternalArtifactType<Directory>(DIRECTORY, Category.OUTPUTS)
    // Additional test output data from the connected task
    object CONNECTED_ANDROID_TEST_ADDITIONAL_OUTPUT: InternalArtifactType<Directory>(DIRECTORY, Category.OUTPUTS)
    // Additional test output data from the device provider tasks.
    object DEVICE_PROVIDER_ANDROID_TEST_ADDITIONAL_OUTPUT: InternalArtifactType<Directory>(DIRECTORY, InternalArtifactType.Category.OUTPUTS)

    // --- android res ---
    // output of the resource merger ready for aapt.
    object MERGED_RES: InternalArtifactType<Directory>(DIRECTORY)
    // The R.java file/files as generated by AAPT or by the new resource processing in libraries.
    object NOT_NAMESPACED_R_CLASS_SOURCES: InternalArtifactType<Directory>(DIRECTORY, Category.GENERATED)
    // The R class jar as compiled from the R.java generated by AAPT or directly generated by
    // the new resource processing in libraries.
    object COMPILE_ONLY_NOT_NAMESPACED_R_CLASS_JAR: InternalArtifactType<RegularFile>(FILE)
    // output of the resource merger for unit tests and the resource shrinker.
    object MERGED_NOT_COMPILED_RES: InternalArtifactType<Directory>(DIRECTORY)
    // Directory containing config file for unit testing with resources
    object UNIT_TEST_CONFIG_DIRECTORY: InternalArtifactType<Directory>(DIRECTORY)
    // compiled resources (output of aapt)
    object PROCESSED_RES: InternalArtifactType<Directory>(DIRECTORY)
    // package resources for aar publishing.
    object PACKAGED_RES: InternalArtifactType<Directory>(DIRECTORY)
    // R.txt output for libraries - contains mock resource IDs used only at compile time.
    object COMPILE_SYMBOL_LIST: InternalArtifactType<RegularFile>(FILE)
    // R.txt output for applications and android tests - contains real resource IDs used at runtime.
    object RUNTIME_SYMBOL_LIST: InternalArtifactType<RegularFile>(FILE)
    // Synthetic artifacts
    object SYMBOL_LIST_WITH_PACKAGE_NAME: InternalArtifactType<RegularFile>(FILE)
    // Resources defined within the AAR.
    object DEFINED_ONLY_SYMBOL_LIST: InternalArtifactType<RegularFile>(FILE)
    //Resources defined within the current module.
    object LOCAL_ONLY_SYMBOL_LIST: InternalArtifactType<RegularFile>(FILE)
    // public.txt output
    object PUBLIC_RES: InternalArtifactType<RegularFile>(FILE)
    object SHRUNK_PROCESSED_RES: InternalArtifactType<Directory>(DIRECTORY)
    object DENSITY_OR_LANGUAGE_SPLIT_PROCESSED_RES: InternalArtifactType<Directory>(DIRECTORY)
    object ABI_PROCESSED_SPLIT_RES: InternalArtifactType<Directory>(DIRECTORY)
    object DENSITY_OR_LANGUAGE_PACKAGED_SPLIT: InternalArtifactType<Directory>(DIRECTORY)
    // linked res for the unified bundle
    object LINKED_RES_FOR_BUNDLE: InternalArtifactType<RegularFile>(FILE)
    object SHRUNK_LINKED_RES_FOR_BUNDLE: InternalArtifactType<RegularFile>(FILE)
    object COMPILED_LOCAL_RESOURCES: InternalArtifactType<Directory>(DIRECTORY)

    // Artifacts for legacy multidex
    object LEGACY_MULTIDEX_AAPT_DERIVED_PROGUARD_RULES: InternalArtifactType<RegularFile>(FILE)
    object LEGACY_MULTIDEX_MAIN_DEX_LIST: InternalArtifactType<RegularFile>(FILE)

    // The R class jar generated from R.txt for application and tests
    object COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR: InternalArtifactType<RegularFile>(FILE)

    // Information neeeded to resolve included navigation graphs into intent filters
    object NAVIGATION_JSON: InternalArtifactType<RegularFile>(FILE)

    // --- Namespaced android res ---
    // Compiled resources (directory of .flat files) for the local library
    object RES_COMPILED_FLAT_FILES: InternalArtifactType<Directory>(DIRECTORY)
    // An AAPT2 static library: InternalArtifactType<RegularFile>(FILE) containing only the current sub-project's resources.
    object RES_STATIC_LIBRARY: InternalArtifactType<RegularFile>(FILE)
    // A directory of AAPT2 static libraries generated from all non-namepaced remote dependencies.
    object RES_CONVERTED_NON_NAMESPACED_REMOTE_DEPENDENCIES: InternalArtifactType<Directory>(DIRECTORY)
    // Compiled R class jar (for compilation only: InternalArtifactType<RegularFile>(FILE) packaged in AAR)
    object COMPILE_ONLY_NAMESPACED_R_CLASS_JAR: InternalArtifactType<RegularFile>(FILE)
    // JAR file containing all of the auto-namespaced classes from dependencies.
    object COMPILE_ONLY_NAMESPACED_DEPENDENCIES_R_JAR: InternalArtifactType<RegularFile>(FILE)
    // Classes JAR files from dependencies that need to be auto-namespaced.
    object NON_NAMESPACED_CLASSES: InternalArtifactType<RegularFile>(FILE)
    // Final R class sources (to package)
    object RUNTIME_R_CLASS_SOURCES: InternalArtifactType<Directory>(DIRECTORY, Category.GENERATED)
    // Final R class classes (for packaging)
    object RUNTIME_R_CLASS_CLASSES: InternalArtifactType<Directory>(DIRECTORY)
    // Partial R.txt files generated by AAPT2 at compile time.
    object PARTIAL_R_FILES: InternalArtifactType<Directory>(DIRECTORY)

    // --- JNI libs ---
    // packaged JNI for inter-project intermediate publishing
    object LIBRARY_JNI: InternalArtifactType<Directory>(DIRECTORY)
    // packaged JNI for AAR publishing
    object LIBRARY_AND_LOCAL_JARS_JNI: InternalArtifactType<Directory>(DIRECTORY)
    // source folder jni libs merged into a single folder
    object MERGED_JNI_LIBS: InternalArtifactType<Directory>(DIRECTORY)
    // source folder shaders merged into a single folder
    object MERGED_SHADERS: InternalArtifactType<Directory>(DIRECTORY)
    // folder for NDK *.so libraries
    object NDK_LIBS: InternalArtifactType<RegularFile>(FILE)
    // native libs merged from module(s)
    object MERGED_NATIVE_LIBS: InternalArtifactType<Directory>(DIRECTORY)
    // native libs stripped of debug symbols
    object STRIPPED_NATIVE_LIBS: InternalArtifactType<Directory>(DIRECTORY)

    // Assets created by compiling shader
    object SHADER_ASSETS: InternalArtifactType<Directory>(DIRECTORY)

    object LIBRARY_ASSETS: InternalArtifactType<Directory>(DIRECTORY)
    object MERGED_ASSETS: InternalArtifactType<Directory>(DIRECTORY)

    // AIDL headers "packaged" by libraries for consumers.
    object AIDL_PARCELABLE: InternalArtifactType<Directory>(DIRECTORY)
    object AIDL_SOURCE_OUTPUT_DIR: InternalArtifactType<Directory>(DIRECTORY, Category.GENERATED)
    // renderscript headers "packaged" by libraries for consumers.
    object RENDERSCRIPT_HEADERS: InternalArtifactType<Directory>(DIRECTORY)
    // source output for rs
    object RENDERSCRIPT_SOURCE_OUTPUT_DIR: InternalArtifactType<Directory>(DIRECTORY, Category.GENERATED)
    // renderscript library
    object RENDERSCRIPT_LIB: InternalArtifactType<Directory>(DIRECTORY)

    // An output of AndroidManifest.xml check
    object CHECK_MANIFEST_RESULT: InternalArtifactType<Directory>(DIRECTORY)

    object COMPATIBLE_SCREEN_MANIFEST: InternalArtifactType<Directory>(DIRECTORY)
    object MERGED_MANIFESTS: InternalArtifactType<Directory>(DIRECTORY), Single
    object LIBRARY_MANIFEST: InternalArtifactType<RegularFile>(FILE), Single
    // Same as above: InternalArtifactType<RegularFile>(FILE) but the resource references have stripped namespaces.
    object NON_NAMESPACED_LIBRARY_MANIFEST: InternalArtifactType<RegularFile>(FILE)
    // A directory of AAR manifests that have been auto-namespaced and are fully resource namespace aware.
    object NAMESPACED_MANIFESTS: InternalArtifactType<Directory>(DIRECTORY)
    object AAPT_FRIENDLY_MERGED_MANIFESTS: InternalArtifactType<Directory>(DIRECTORY)
    object INSTANT_APP_MANIFEST: InternalArtifactType<Directory>(DIRECTORY)
    object MANIFEST_METADATA: InternalArtifactType<Directory>(DIRECTORY)
    object MANIFEST_MERGE_REPORT: InternalArtifactType<RegularFile>(FILE)
    object MANIFEST_MERGE_BLAME_FILE: InternalArtifactType<RegularFile>(FILE)
    // Simplified android manifest with original package name.
    // It's used to create namespaced res.apk static library.
    object STATIC_LIBRARY_MANIFEST: InternalArtifactType<RegularFile>(FILE)

    // List of annotation processors for metrics.
    object ANNOTATION_PROCESSOR_LIST: InternalArtifactType<RegularFile>(FILE)
    // The sources/resources generated by annotation processors in the source output directory.
    object AP_GENERATED_SOURCES: InternalArtifactType<Directory>(DIRECTORY, Category.GENERATED)
    // The classes/resources generated by annotation processors in the class output directory
    // (see bug 139327207).
    object AP_GENERATED_CLASSES: InternalArtifactType<Directory>(DIRECTORY, Category.GENERATED)

    // merged generated + consumer shrinker configs to be used when packaging rules into an AAR
    object MERGED_CONSUMER_PROGUARD_FILE: InternalArtifactType<RegularFile>(FILE)
    // the directory that consumers of an AAR or feature module can use for additional proguard rules.
    object CONSUMER_PROGUARD_DIR: InternalArtifactType<Directory>(DIRECTORY)
    // the proguard rules produced by aapt.
    object AAPT_PROGUARD_FILE: InternalArtifactType<RegularFile>(FILE)
    // the merger of a module's AAPT_PROGUARD_FILE and those of its feature(s)
    object MERGED_AAPT_PROGUARD_FILE: InternalArtifactType<RegularFile>(FILE)

    // the data binding artifact for a library that gets published with the aar
    object DATA_BINDING_ARTIFACT: InternalArtifactType<Directory>(DIRECTORY)
    // the merged data binding artifacts from all the dependencies
    object DATA_BINDING_DEPENDENCY_ARTIFACTS: InternalArtifactType<Directory>(DIRECTORY)
    // directory containing layout info files for data binding when merge-resources type == MERGE
    object DATA_BINDING_LAYOUT_INFO_TYPE_MERGE: InternalArtifactType<Directory>(DIRECTORY)
    // directory containing layout info files for data binding when merge-resources type == PACKAGE
    // see https://issuetracker.google.com/110412851
    object DATA_BINDING_LAYOUT_INFO_TYPE_PACKAGE: InternalArtifactType<Directory>(DIRECTORY)
    // the generated base classes artifacts from all dependencies
    object DATA_BINDING_BASE_CLASS_LOGS_DEPENDENCY_ARTIFACTS: InternalArtifactType<Directory>(DIRECTORY)
    // the data binding class log generated after compilation: InternalArtifactType<RegularFile>(FILE) includes merged
    // class info file
    object DATA_BINDING_BASE_CLASS_LOG_ARTIFACT: InternalArtifactType<Directory>(DIRECTORY)
    // source code generated by data binding tasks.
    object DATA_BINDING_BASE_CLASS_SOURCE_OUT: InternalArtifactType<Directory>(DIRECTORY, Category.GENERATED)

    // The lint JAR to be used for the current module.
    object LINT_JAR: InternalArtifactType<RegularFile>(FILE)
    // The lint JAR to be published in the AAR.
    object LINT_PUBLISH_JAR: InternalArtifactType<RegularFile>(FILE)

    // the zip file output of the extract annotation class.
    object ANNOTATIONS_ZIP: InternalArtifactType<RegularFile>(FILE)
    // Optional recipe file (only used for libraries) which describes typedefs defined in the
    // library: InternalArtifactType<RegularFile>(FILE) and how to process them (typically which typedefs to omit during packaging).
    object ANNOTATIONS_TYPEDEF_FILE: InternalArtifactType<RegularFile>(FILE)
    // the associated proguard file
    object ANNOTATIONS_PROGUARD: InternalArtifactType<RegularFile>(FILE)
    // The classes.jar for the AAR
    object AAR_MAIN_JAR: InternalArtifactType<RegularFile>(FILE)
    // The libs/ directory for the AAR: InternalArtifactType<RegularFile>(FILE) containing secondary jars
object AAR_LIBS_DIRECTORY: InternalArtifactType<Directory>(DIRECTORY)

    object ABI_PACKAGED_SPLIT: InternalArtifactType<Directory>(DIRECTORY)
    object FULL_APK: InternalArtifactType<Directory>(DIRECTORY)
    object APK: InternalArtifactType<Directory>(DIRECTORY)
    object APK_FOR_LOCAL_TEST: InternalArtifactType<RegularFile>(FILE)
    object APK_MAPPING: InternalArtifactType<RegularFile>(FILE, Category.OUTPUTS, "mapping")
    // zip of APK + mapping files used when publishing the APKs to a repo
    object APK_ZIP: InternalArtifactType<RegularFile>(FILE, Category.OUTPUTS, "apk-zips")
    object AAR: InternalArtifactType<RegularFile>(FILE)
    object INSTANTAPP_BUNDLE: InternalArtifactType<Directory>(DIRECTORY)
    object SPLIT_LIST: InternalArtifactType<RegularFile>(FILE)
    object APK_LIST: InternalArtifactType<RegularFile>(FILE)

    // an intermediate bundle that contains only the current module
    object MODULE_BUNDLE: InternalArtifactType<Directory>(DIRECTORY)
    // The main dex list for the bundle: InternalArtifactType<RegularFile>(FILE) unlike the main dex list for a monolithic application: InternalArtifactType<RegularFile>(FILE) this
    // analyzes all of the dynamic feature classes too.
    object MAIN_DEX_LIST_FOR_BUNDLE: InternalArtifactType<RegularFile>(FILE)
    // The final Bundle: InternalArtifactType<RegularFile>(FILE) including feature module: InternalArtifactType<RegularFile>(FILE) ready for consumption at Play Store.
    // This is only valid for the base module.
    object BUNDLE: InternalArtifactType<RegularFile>(FILE, Category.OUTPUTS)
    // The bundle artifact: InternalArtifactType<RegularFile>(FILE) including feature module: InternalArtifactType<RegularFile>(FILE) used as the base for further processing: InternalArtifactType<RegularFile>(FILE)
    // like extracting APKs. It's cheaper to produce but not suitable as a final artifact to send
    // to the Play Store.
    // This is only valid for the base module.
    object INTERMEDIARY_BUNDLE: InternalArtifactType<RegularFile>(FILE)
    // APK Set archive with APKs generated from a bundle.
    object APKS_FROM_BUNDLE: InternalArtifactType<RegularFile>(FILE)
    // output of ExtractApks applied to APKS_FROM_BUNDLE and a device config.
    object EXTRACTED_APKS: InternalArtifactType<Directory>(DIRECTORY)
    // Universal APK from the bundle
    object UNIVERSAL_APK: InternalArtifactType<RegularFile>(FILE, Category.OUTPUTS)
    // The manifest meant to be consumed by the bundle.
    object BUNDLE_MANIFEST: InternalArtifactType<Directory>(DIRECTORY)

    // file containing the metadata for the full feature set. This contains the feature names: InternalArtifactType<RegularFile>(FILE)
    // the res ID offset: InternalArtifactType<RegularFile>(FILE) both tied to the feature module path. Published by the base for the
    // other features to consume and find their own metadata.
    object FEATURE_SET_METADATA: InternalArtifactType<RegularFile>(FILE)
    // file containing the module information (like its application ID) to synchronize all base
    // and dynamic feature. This is published by the base feature and installed application module.
    object METADATA_BASE_MODULE_DECLARATION: InternalArtifactType<RegularFile>(FILE)
    // file containing only the application ID. It is used to synchronize all feature plugins
    // with the application module's application ID.
    object METADATA_APPLICATION_ID: InternalArtifactType<RegularFile>(FILE)
    object FEATURE_RESOURCE_PKG: InternalArtifactType<Directory>(DIRECTORY)
    // File containing the list of packaged dependencies of a given APK. This is consumed
    // by other APKs to avoid repackaging the same thing.
    object PACKAGED_DEPENDENCIES: InternalArtifactType<RegularFile>(FILE)
    // The information about the features in the app that is necessary for the data binding
    // annotation processor (for base feature compilation). Created by the
    // DataBindingExportFeatureApplicationIdsTask and passed down to the annotation processor via
    // processor args.
    object FEATURE_DATA_BINDING_BASE_FEATURE_INFO: InternalArtifactType<Directory>(DIRECTORY)
    // The information about the feature that is necessary for the data binding annotation
    // processor (for feature compilation). Created by DataBindingExportFeatureInfoTask and passed
    // into the annotation processor via processor args.
    object FEATURE_DATA_BINDING_FEATURE_INFO: InternalArtifactType<Directory>(DIRECTORY)
    // The base dex files output by the DexSplitter.
    object BASE_DEX: InternalArtifactType<Directory>(DIRECTORY)
    // The feature dex files output by the DexSplitter from the base. The base produces and
    // publishes these files when there's multi-apk code shrinking.
    object FEATURE_DEX: InternalArtifactType<Directory>(DIRECTORY)
    // The class files for a module and all of its runtime dependencies.
    object MODULE_AND_RUNTIME_DEPS_CLASSES: InternalArtifactType<RegularFile>(FILE)
    // The name of a dynamic or legacy instant feature`
    object FEATURE_NAME: InternalArtifactType<RegularFile>(FILE)

    // The signing configuration the feature module should be using: InternalArtifactType<RegularFile>(FILE) which is taken from the
    // application module. Also used for androidTest variants (bug 118611693). This has already
    // been validated
    object SIGNING_CONFIG: InternalArtifactType<Directory>(DIRECTORY)
    // The validated signing config output: InternalArtifactType<RegularFile>(FILE) to allow the task to be up to date: InternalArtifactType<RegularFile>(FILE) and for allowing
    // other tasks to depend on the output.
    object VALIDATE_SIGNING_CONFIG: InternalArtifactType<Directory>(DIRECTORY)

    // Project metadata
    object METADATA_FEATURE_DECLARATION: InternalArtifactType<Directory>(DIRECTORY)
    object METADATA_FEATURE_MANIFEST: InternalArtifactType<Directory>(DIRECTORY)
    object METADATA_INSTALLED_BASE_DECLARATION: InternalArtifactType<RegularFile>(FILE)
    // The metadata for the library dependencies: InternalArtifactType<RegularFile>(FILE) direct and indirect: InternalArtifactType<RegularFile>(FILE) published for each module.
    object METADATA_LIBRARY_DEPENDENCIES_REPORT: InternalArtifactType<RegularFile>(FILE)

    // The library dependencies report: InternalArtifactType<RegularFile>(FILE) direct and indirect: InternalArtifactType<RegularFile>(FILE) published for the entire app to
    // package in the bundle.
    object BUNDLE_DEPENDENCY_REPORT: InternalArtifactType<RegularFile>(FILE)

    // Config file specifying how to protect app's integrity
    object APP_INTEGRITY_CONFIG: InternalArtifactType<RegularFile>(FILE)

    // A dummy output (folder) result of CheckDuplicateClassesTask execution
    object DUPLICATE_CLASSES_CHECK: InternalArtifactType<Directory>(DIRECTORY)

    // File containing all generated proguard rules from Javac (by e.g. dagger) merged together
    object GENERATED_PROGUARD_FILE: InternalArtifactType<RegularFile>(FILE)

    // File containing unused dependencies and dependencies that can be configured as
    // implementation in the current variant
    object ANALYZE_DEPENDENCIES_REPORT: InternalArtifactType<Directory>(DIRECTORY)

    /**
     * Defines the kind of artifact type. this will be used to determine the output file location
     * for instance.
     */
    enum class Category {
        /* Generated files that are meant to be visible to users from the IDE */
        GENERATED,
        /* Intermediates files produced by tasks. */
        INTERMEDIATES,
        /* output files going into the outputs folder. This is the result of the build. */
        OUTPUTS;

        val outputPath: String
            get() = name.toLowerCase(Locale.US)

        /**
         * Return the file location for this kind of artifact type.
         *
         * @param parentDir the parent build directory
         * @return a file location which is task and variant independent.
         */
        fun getOutputDir(parentDir: File): File {
            return File(parentDir, name.toLowerCase(Locale.US))
        }
    }

    override fun getFolderName(): String {
        return folderName ?: super.getFolderName()
    }

    companion object {
        @kotlin.jvm.JvmField
        var NOT_NAMESPACED_R_CLASS_SOURCESX= InternalArtifactType.NOT_NAMESPACED_R_CLASS_SOURCES
    }
}
