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

package com.android.build.gradle.internal.scope

import com.android.build.api.artifact.ArtifactType
import com.android.build.api.artifact.ArtifactKind
import org.gradle.api.file.Directory
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import java.io.File
import java.util.Locale

/**
 * A lot of internal code is expecting a [ArtifactType.Single] [ArtifactType] and it is not possible
 * to store variables with the 2 types. Such variables are used to pass to Task implementation to
 * specify their output type for instance. To help, all internal single artifact types implement
 * this type.
 */
abstract class SingleArtifactType<T : FileSystemLocation>(kind: ArtifactKind<T>)
    : ArtifactType<T>(kind), ArtifactType.Single

/** A type of output generated by a task.  */
@Suppress("ClassName")
sealed class InternalArtifactType<T : FileSystemLocation>(kind: ArtifactKind<T>,
    val category: Category = Category.INTERMEDIATES,
    private val folderName: String? = null
) : SingleArtifactType<T>(kind) {

    override val isPublic: Boolean
        get() = false

    // anchor for a collection grouping all the generated bytecode
    object ALL_CLASSES: InternalArtifactType<Directory>(DIRECTORY), Replaceable

    // --- classes ---
    // These are direct task outputs. If you are looking for all the classes of a
    // module: InternalArtifactType<RegularFile>(FILE), Replaceable use AnchorOutputType.ALL_CLASSES
    // Javac task output.
    object JAVAC: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // Rewritten classes from non-namespaced dependencies put together into one JAR.
    object NAMESPACED_CLASSES_JAR: InternalArtifactType<RegularFile>(FILE), Replaceable
    // Tested code classes
    object TESTED_CODE_CLASSES: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // Classes with recalculated stack frames information (RecalculateStackFrames task)
    object FIXED_STACK_FRAMES: InternalArtifactType<Directory>(DIRECTORY), Replaceable

    // --- Published classes ---
    // Class-type task output for tasks that generate published classes.

    // Packaged classes for library intermediate publishing
    // This is for external usage. For usage inside a module use ALL_CLASSES
    object RUNTIME_LIBRARY_CLASSES_JAR: InternalArtifactType<RegularFile>(FILE), Replaceable
    /**
     * A directory containing runtime classes. NOTE: It may contain either class files only
     * (preferred) or a single jar only, see {@link AndroidArtifacts.ClassesDirFormat}.
     */
    object RUNTIME_LIBRARY_CLASSES_DIR: InternalArtifactType<Directory>(DIRECTORY), Replaceable

    // Packaged library classes published only to compile configuration. This is to allow other
    // projects to compile again classes that were not additionally processed e.g. classes with
    // Jacoco instrumentation (b/109771903).
    object COMPILE_LIBRARY_CLASSES_JAR: InternalArtifactType<RegularFile>(FILE), Replaceable

    // Jar generated by the code shrinker
    object SHRUNK_CLASSES: InternalArtifactType<RegularFile>(FILE), Replaceable

    // Dex archive artifacts for project.
    object PROJECT_DEX_ARCHIVE: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // Dex archive artifacts for sub projects.
    object SUB_PROJECT_DEX_ARCHIVE: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // Dex archive artifacts for external (Maven) libraries.
    object EXTERNAL_LIBS_DEX_ARCHIVE: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // Dex archive artifacts for external (Maven) libraries processed using aritfact transforms.
    object EXTERNAL_LIBS_DEX_ARCHIVE_WITH_ARTIFACT_TRANSFORMS: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // Dex archive artifacts for dex'ed classes coming from mixed scopes (to support legacy
    // Transform API that can output classes belonging to multiple scopes).
    object MIXED_SCOPE_DEX_ARCHIVE: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // Artifact for supporting faster incremental dex archive building. This artifact contains
    // information about inputs and it should not be consumed by other tasks.
    object DEX_ARCHIVE_INPUT_JAR_HASHES: InternalArtifactType<RegularFile>(FILE), Replaceable
    // Directory containing dependency graph(s) for desugaring
    object DESUGAR_GRAPH: InternalArtifactType<Directory>(DIRECTORY), Replaceable

    // External file library dex archives (Desugared separately from module & project dependencies)
    object EXTERNAL_FILE_LIB_DEX_ARCHIVES: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // the packaged classes published by APK modules.
    // This is for external usage. For usage inside a module use ALL_CLASSES
    object APP_CLASSES: InternalArtifactType<RegularFile>(FILE), Replaceable

    // Outputs of Desugar tool for project classes.
    object DESUGAR_PROJECT_CLASSES: InternalArtifactType<Directory>(DIRECTORY)
    // Outputs of Desugar tool for sub-project classes.
    object DESUGAR_SUB_PROJECT_CLASSES: InternalArtifactType<Directory>(DIRECTORY)
    // Outputs of Desugar tool for external (Maven) library classes.
    object DESUGAR_EXTERNAL_LIBS_CLASSES: InternalArtifactType<Directory>(DIRECTORY)
    // Local state output for desugar.
    object DESUGAR_LOCAL_STATE_OUTPUT: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // The output of L8 invocation, which is the dex output of desugar lib jar
    object DESUGAR_LIB_DEX: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // Keep rules generated by D8/R8, which are consumed by L8 to shrink the desugar lib jar
    object DESUGAR_LIB_PROJECT_KEEP_RULES: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    object DESUGAR_LIB_SUBPROJECT_KEEP_RULES: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    object DESUGAR_LIB_EXTERNAL_LIBS_KEEP_RULES: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // Keep rules for core library desugaring that are generated by the dexing task. These rules
    // are for the external libraries that are dex'ed using Gradle artifact transforms.
    object DESUGAR_LIB_EXTERNAL_LIBS_ARTIFACT_TRANSFORM_KEEP_RULES: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    object DESUGAR_LIB_MIXED_SCOPE_KEEP_RULES: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    object DESUGAR_LIB_EXTERNAL_FILE_LIB_KEEP_RULES: InternalArtifactType<RegularFile>(FILE), Replaceable

    // --- java res ---
    // java processing output
    object JAVA_RES: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // merged java resources
    object MERGED_JAVA_RES: InternalArtifactType<RegularFile>(FILE), Replaceable
    // packaged java res for aar intermediate publishing
    object LIBRARY_JAVA_RES: InternalArtifactType<RegularFile>(FILE), Replaceable
    // Resources generated by the code shrinker
    object SHRUNK_JAVA_RES: InternalArtifactType<RegularFile>(FILE), Replaceable

    // Full jar with both classes and java res.
    object FULL_JAR: InternalArtifactType<RegularFile>(FILE), Replaceable

    // Jar generated by the code shrinker containing both Java classes and resources
    object SHRUNK_JAR: InternalArtifactType<RegularFile>(FILE), Replaceable

    // A folder with classes instrumented with jacoco. Internal folder file structure reflects
    // the hierarchy of namespaces
    object JACOCO_INSTRUMENTED_CLASSES: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // A folder containing jars with classes instrumented with jacoco
    object JACOCO_INSTRUMENTED_JARS: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // The jacoco code coverage from the connected task
    object CODE_COVERAGE: InternalArtifactType<Directory>(DIRECTORY, Category.OUTPUTS), Replaceable
    // The jacoco code coverage from the device provider tasks.
    object DEVICE_PROVIDER_CODE_COVERAGE: InternalArtifactType<Directory>(DIRECTORY, Category.OUTPUTS)
    // Additional test output data from the connected task
    object CONNECTED_ANDROID_TEST_ADDITIONAL_OUTPUT: InternalArtifactType<Directory>(DIRECTORY, Category.OUTPUTS)
    // Additional test output data from the device provider tasks.
    object DEVICE_PROVIDER_ANDROID_TEST_ADDITIONAL_OUTPUT: InternalArtifactType<Directory>(DIRECTORY, Category.OUTPUTS)

    // --- android res ---
    // output of the resource merger ready for aapt.
    object MERGED_RES: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // The R.java file/files as generated by AAPT or by the new resource processing in libraries.
    object NOT_NAMESPACED_R_CLASS_SOURCES: InternalArtifactType<Directory>(DIRECTORY, Category.GENERATED), Replaceable
    // The R class jar as compiled from the R.java generated by AAPT or directly generated by
    // the new resource processing in libraries.
    object COMPILE_ONLY_NOT_NAMESPACED_R_CLASS_JAR: InternalArtifactType<RegularFile>(FILE), Replaceable
    // output of the resource merger for unit tests and the resource shrinker.
    object MERGED_NOT_COMPILED_RES: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // Directory containing config file for unit testing with resources
    object UNIT_TEST_CONFIG_DIRECTORY: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // compiled resources (output of aapt)
    object PROCESSED_RES: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // package resources for aar publishing.
    object PACKAGED_RES: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // R.txt output for libraries - contains mock resource IDs used only at compile time.
    object COMPILE_SYMBOL_LIST: InternalArtifactType<RegularFile>(FILE), Replaceable
    // R.txt output for applications and android tests - contains real resource IDs used at runtime.
    object RUNTIME_SYMBOL_LIST: InternalArtifactType<RegularFile>(FILE), Replaceable
    // Synthetic artifacts
    object SYMBOL_LIST_WITH_PACKAGE_NAME: InternalArtifactType<RegularFile>(FILE), Replaceable
    // Resources defined within the AAR.
    object DEFINED_ONLY_SYMBOL_LIST: InternalArtifactType<RegularFile>(FILE), Replaceable
    //Resources defined within the current module.
    object LOCAL_ONLY_SYMBOL_LIST: InternalArtifactType<RegularFile>(FILE), Replaceable
    // Partial R.txt directory
    object LOCAL_ONLY_PARTIAL_SYMBOL_DIRECTORY: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // public.txt output
    object PUBLIC_RES: InternalArtifactType<RegularFile>(FILE), Replaceable
    object SHRUNK_PROCESSED_RES: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    object DENSITY_OR_LANGUAGE_SPLIT_PROCESSED_RES: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    object ABI_PROCESSED_SPLIT_RES: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    object DENSITY_OR_LANGUAGE_PACKAGED_SPLIT: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // linked res for the unified bundle
    object LINKED_RES_FOR_BUNDLE: InternalArtifactType<RegularFile>(FILE), Replaceable
    object SHRUNK_LINKED_RES_FOR_BUNDLE: InternalArtifactType<RegularFile>(FILE), Replaceable
    object COMPILED_LOCAL_RESOURCES: InternalArtifactType<Directory>(DIRECTORY), Replaceable

    // Artifacts for legacy multidex
    object LEGACY_MULTIDEX_AAPT_DERIVED_PROGUARD_RULES: InternalArtifactType<RegularFile>(FILE), Replaceable
    object LEGACY_MULTIDEX_MAIN_DEX_LIST: InternalArtifactType<RegularFile>(FILE), Replaceable

    // The R class jar generated from R.txt for application and tests
    object COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR: InternalArtifactType<RegularFile>(FILE), Replaceable

    // Information neeeded to resolve included navigation graphs into intent filters
    object NAVIGATION_JSON: InternalArtifactType<RegularFile>(FILE), Replaceable

    // --- Namespaced android res ---
    // An AAPT2 static library: InternalArtifactType<RegularFile>(FILE), Replaceable containing only the current sub-project's resources.
    object RES_STATIC_LIBRARY: InternalArtifactType<RegularFile>(FILE), Replaceable
    // A directory of AAPT2 static libraries generated from all non-namepaced remote dependencies.
    object RES_CONVERTED_NON_NAMESPACED_REMOTE_DEPENDENCIES: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // Compiled R class jar (for compilation only: InternalArtifactType<RegularFile>(FILE), Replaceable packaged in AAR)
    object COMPILE_ONLY_NAMESPACED_R_CLASS_JAR: InternalArtifactType<RegularFile>(FILE), Replaceable
    // JAR file containing all of the auto-namespaced classes from dependencies.
    object COMPILE_ONLY_NAMESPACED_DEPENDENCIES_R_JAR: InternalArtifactType<RegularFile>(FILE), Replaceable
    // Classes JAR files from dependencies that need to be auto-namespaced.
    object NON_NAMESPACED_CLASSES: InternalArtifactType<RegularFile>(FILE), Replaceable
    // Final R class sources (to package)
    object RUNTIME_R_CLASS_SOURCES: InternalArtifactType<Directory>(DIRECTORY, Category.GENERATED)
    // Final R class classes (for packaging)
    object RUNTIME_R_CLASS_CLASSES: InternalArtifactType<Directory>(DIRECTORY), Replaceable

    // --- JNI libs ---
    // packaged JNI for inter-project intermediate publishing
    object LIBRARY_JNI: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // packaged JNI for AAR publishing
    object LIBRARY_AND_LOCAL_JARS_JNI: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // source folder jni libs merged into a single folder
    object MERGED_JNI_LIBS: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // source folder shaders merged into a single folder
    object MERGED_SHADERS: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // folder for NDK *.so libraries
    object NDK_LIBS: InternalArtifactType<RegularFile>(FILE), Replaceable
    // native libs merged from module(s)
    object MERGED_NATIVE_LIBS: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // native libs stripped of debug symbols
    object STRIPPED_NATIVE_LIBS: InternalArtifactType<Directory>(DIRECTORY), Replaceable

    // Assets created by compiling shader
    object SHADER_ASSETS: InternalArtifactType<Directory>(DIRECTORY), Replaceable

    object LIBRARY_ASSETS: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    object MERGED_ASSETS: InternalArtifactType<Directory>(DIRECTORY), Replaceable

    // AIDL headers "packaged" by libraries for consumers.
    object AIDL_PARCELABLE: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    object AIDL_SOURCE_OUTPUT_DIR: InternalArtifactType<Directory>(DIRECTORY, Category.GENERATED), Replaceable
    // renderscript headers "packaged" by libraries for consumers.
    object RENDERSCRIPT_HEADERS: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // source output for rs
    object RENDERSCRIPT_SOURCE_OUTPUT_DIR: InternalArtifactType<Directory>(DIRECTORY, Category.GENERATED), Replaceable
    // renderscript library
    object RENDERSCRIPT_LIB: InternalArtifactType<Directory>(DIRECTORY), Replaceable

    // An output of AndroidManifest.xml check.
    // REMOVE ME (bug 139855995): This artifact can be removed in the new variant API, we haven't
    // removed it yet for compatibility reasons.
    object CHECK_MANIFEST_RESULT: InternalArtifactType<Directory>(DIRECTORY), Replaceable

    object COMPATIBLE_SCREEN_MANIFEST: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    object MERGED_MANIFESTS: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    object LIBRARY_MANIFEST: InternalArtifactType<RegularFile>(FILE), Replaceable
    // Same as above: InternalArtifactType<RegularFile>(FILE), Replaceable but the resource references have stripped namespaces.
    object NON_NAMESPACED_LIBRARY_MANIFEST: InternalArtifactType<RegularFile>(FILE), Replaceable
    // A directory of AAR manifests that have been auto-namespaced and are fully resource namespace aware.
    object NAMESPACED_MANIFESTS: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    object AAPT_FRIENDLY_MERGED_MANIFESTS: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    object INSTANT_APP_MANIFEST: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    object MANIFEST_METADATA: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    object MANIFEST_MERGE_REPORT: InternalArtifactType<RegularFile>(FILE), Replaceable
    object MANIFEST_MERGE_BLAME_FILE: InternalArtifactType<RegularFile>(FILE), Replaceable
    // Simplified android manifest with original package name.
    // It's used to create namespaced res.apk static library.
    object STATIC_LIBRARY_MANIFEST: InternalArtifactType<RegularFile>(FILE), Replaceable

    // List of annotation processors for metrics.
    object ANNOTATION_PROCESSOR_LIST: InternalArtifactType<RegularFile>(FILE), Replaceable
    // The sources/resources generated by annotation processors in the source output directory.
    object AP_GENERATED_SOURCES: InternalArtifactType<Directory>(DIRECTORY, Category.GENERATED), Replaceable
    // The classes/resources generated by annotation processors in the class output directory
    // (see bug 139327207).
    object AP_GENERATED_CLASSES: InternalArtifactType<Directory>(DIRECTORY, Category.GENERATED), Replaceable

    // merged generated + consumer shrinker configs to be used when packaging rules into an AAR
    object MERGED_CONSUMER_PROGUARD_FILE: InternalArtifactType<RegularFile>(FILE), Replaceable
    // the directory that consumers of an AAR or feature module can use for additional proguard rules.
    object CONSUMER_PROGUARD_DIR: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // the proguard rules produced by aapt.
    object AAPT_PROGUARD_FILE: InternalArtifactType<RegularFile>(FILE), Replaceable
    // the merger of a module's AAPT_PROGUARD_FILE and those of its feature(s)
    object MERGED_AAPT_PROGUARD_FILE: InternalArtifactType<RegularFile>(FILE), Replaceable

    // the data binding artifact for a library that gets published with the aar
    object DATA_BINDING_ARTIFACT: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // the merged data binding artifacts from all the dependencies
    object DATA_BINDING_DEPENDENCY_ARTIFACTS: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // directory containing layout info files for data binding when merge-resources type == MERGE
    object DATA_BINDING_LAYOUT_INFO_TYPE_MERGE: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // directory containing layout info files for data binding when merge-resources type == PACKAGE
    // see https://issuetracker.google.com/110412851
    object DATA_BINDING_LAYOUT_INFO_TYPE_PACKAGE: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // the generated base classes artifacts from all dependencies
    object DATA_BINDING_BASE_CLASS_LOGS_DEPENDENCY_ARTIFACTS: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // the data binding class log generated after compilation: InternalArtifactType<RegularFile>(FILE), Replaceable includes merged
    // class info file
    object DATA_BINDING_BASE_CLASS_LOG_ARTIFACT: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // source code generated by data binding tasks.
    object DATA_BINDING_BASE_CLASS_SOURCE_OUT: InternalArtifactType<Directory>(DIRECTORY, Category.GENERATED), Replaceable

    // The lint JAR to be used for the current module.
    object LINT_JAR: InternalArtifactType<RegularFile>(FILE), Replaceable
    // The lint JAR to be published in the AAR.
    object LINT_PUBLISH_JAR: InternalArtifactType<RegularFile>(FILE), Replaceable

    // the zip file output of the extract annotation class.
    object ANNOTATIONS_ZIP: InternalArtifactType<RegularFile>(FILE), Replaceable
    // Optional recipe file (only used for libraries) which describes typedefs defined in the
    // library: InternalArtifactType<RegularFile>(FILE), Replaceable and how to process them (typically which typedefs to omit during packaging).
    object ANNOTATIONS_TYPEDEF_FILE: InternalArtifactType<RegularFile>(FILE), Replaceable
    // the associated proguard file
    object ANNOTATIONS_PROGUARD: InternalArtifactType<RegularFile>(FILE), Replaceable
    // The classes.jar for the AAR
    object AAR_MAIN_JAR: InternalArtifactType<RegularFile>(FILE), Replaceable
    // The libs/ directory for the AAR: InternalArtifactType<RegularFile>(FILE), Replaceable containing secondary jars
    object AAR_LIBS_DIRECTORY: InternalArtifactType<Directory>(DIRECTORY), Replaceable

    object ABI_PACKAGED_SPLIT: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    object FULL_APK: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    object APK: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    object APK_FOR_LOCAL_TEST: InternalArtifactType<RegularFile>(FILE), Replaceable
    object APK_MAPPING: InternalArtifactType<RegularFile>(FILE, Category.OUTPUTS, "mapping"), Replaceable
    // zip of APK + mapping files used when publishing the APKs to a repo
    object APK_ZIP: InternalArtifactType<RegularFile>(FILE, Category.OUTPUTS, "apk-zips")
    object AAR: InternalArtifactType<RegularFile>(FILE), Replaceable
    object INSTANTAPP_BUNDLE: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    object APK_LIST: InternalArtifactType<RegularFile>(FILE), Replaceable

    // an intermediate bundle that contains only the current module
    object MODULE_BUNDLE: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // directory with intermediate bundles of the asset packs for the app.
    object ASSET_PACK_BUNDLE: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // The main dex list for the bundle: InternalArtifactType<RegularFile>(FILE), Replaceable unlike the main dex list for a monolithic application: InternalArtifactType<RegularFile>(FILE), Replaceable this
    // analyzes all of the dynamic feature classes too.
    object MAIN_DEX_LIST_FOR_BUNDLE: InternalArtifactType<RegularFile>(FILE), Replaceable
    // The final Bundle: InternalArtifactType<RegularFile>(FILE), Replaceable including feature module: InternalArtifactType<RegularFile>(FILE), Replaceable ready for consumption at Play Store.
    // This is only valid for the base module.
    object BUNDLE: InternalArtifactType<RegularFile>(FILE, Category.OUTPUTS), Replaceable
    // The bundle artifact: InternalArtifactType<RegularFile>(FILE), Replaceable including feature module: InternalArtifactType<RegularFile>(FILE), Replaceable used as the base for further processing: InternalArtifactType<RegularFile>(FILE), Replaceable
    // like extracting APKs. It's cheaper to produce but not suitable as a final artifact to send
    // to the Play Store.
    // This is only valid for the base module.
    object INTERMEDIARY_BUNDLE: InternalArtifactType<RegularFile>(FILE), Replaceable
    // APK Set archive with APKs generated from a bundle.
    object APKS_FROM_BUNDLE: InternalArtifactType<RegularFile>(FILE), Replaceable
    // output of ExtractApks applied to APKS_FROM_BUNDLE and a device config.
    object EXTRACTED_APKS: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // Universal APK from the bundle
    object UNIVERSAL_APK: InternalArtifactType<RegularFile>(FILE, Category.OUTPUTS)
    // The manifest meant to be consumed by the bundle.
    object BUNDLE_MANIFEST: InternalArtifactType<Directory>(DIRECTORY), Replaceable

    // file containing the metadata for the full feature set. This contains the feature names: InternalArtifactType<RegularFile>(FILE), Replaceable
    // the res ID offset: InternalArtifactType<RegularFile>(FILE), Replaceable both tied to the feature module path. Published by the base for the
    // other features to consume and find their own metadata.
    object FEATURE_SET_METADATA: InternalArtifactType<RegularFile>(FILE), Replaceable
    // file containing the module information (like its application ID) to synchronize all base
    // and dynamic feature. This is published by the base feature and installed application module.
    object BASE_MODULE_METADATA: InternalArtifactType<RegularFile>(FILE), Replaceable
    // file containing only the application ID. It is used to synchronize all feature plugins
    // with the application module's application ID.
    object METADATA_APPLICATION_ID: InternalArtifactType<RegularFile>(FILE), Replaceable
    object FEATURE_RESOURCE_PKG: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // File containing the list of packaged dependencies of a given APK. This is consumed
    // by other APKs to avoid repackaging the same thing.
    object PACKAGED_DEPENDENCIES: InternalArtifactType<RegularFile>(FILE), Replaceable
    // The information about the features in the app that is necessary for the data binding
    // annotation processor (for base feature compilation). Created by the
    // DataBindingExportFeatureApplicationIdsTask and passed down to the annotation processor via
    // processor args.
    object FEATURE_DATA_BINDING_BASE_FEATURE_INFO: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // The information about the feature that is necessary for the data binding annotation
    // processor (for feature compilation). Created by DataBindingExportFeatureInfoTask and passed
    // into the annotation processor via processor args.
    object FEATURE_DATA_BINDING_FEATURE_INFO: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // The base dex files output by the DexSplitter.
    object BASE_DEX: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // The feature dex files output by the DexSplitter from the base. The base produces and
    // publishes these files when there's multi-apk code shrinking.
    object FEATURE_DEX: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // The class files for a module and all of its runtime dependencies.
    object MODULE_AND_RUNTIME_DEPS_CLASSES: InternalArtifactType<RegularFile>(FILE), Replaceable
    // The name of a dynamic or legacy instant feature`
    object FEATURE_NAME: InternalArtifactType<RegularFile>(FILE), Replaceable

    // The signing configuration the feature module should be using: InternalArtifactType<RegularFile>(FILE), Replaceable which is taken from the
    // application module. Also used for androidTest variants (bug 118611693). This has already
    // been validated
    object SIGNING_CONFIG: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // The validated signing config output: InternalArtifactType<RegularFile>(FILE), Replaceable to allow the task to be up to date: InternalArtifactType<RegularFile>(FILE), Replaceable and for allowing
    // other tasks to depend on the output.
    object VALIDATE_SIGNING_CONFIG: InternalArtifactType<Directory>(DIRECTORY), Replaceable

    // Project metadata
    object METADATA_FEATURE_DECLARATION: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    object METADATA_FEATURE_MANIFEST: InternalArtifactType<Directory>(DIRECTORY), Replaceable
    // The metadata for the library dependencies: InternalArtifactType<RegularFile>(FILE), Replaceable direct and indirect: InternalArtifactType<RegularFile>(FILE), Replaceable published for each module.
    object METADATA_LIBRARY_DEPENDENCIES_REPORT: InternalArtifactType<RegularFile>(FILE), Replaceable

    // The library dependencies report: InternalArtifactType<RegularFile>(FILE), Replaceable direct and indirect: InternalArtifactType<RegularFile>(FILE), Replaceable published for the entire app to
    // package in the bundle.
    object BUNDLE_DEPENDENCY_REPORT: InternalArtifactType<RegularFile>(FILE), Replaceable

    // Config file specifying how to protect app's integrity
    object APP_INTEGRITY_CONFIG: InternalArtifactType<RegularFile>(FILE), Replaceable

    // A dummy output (folder) result of CheckDuplicateClassesTask execution
    object DUPLICATE_CLASSES_CHECK: InternalArtifactType<Directory>(DIRECTORY), Replaceable

    // File containing all generated proguard rules from Javac (by e.g. dagger) merged together
    object GENERATED_PROGUARD_FILE: InternalArtifactType<RegularFile>(FILE), Replaceable

    // File containing unused dependencies and dependencies that can be configured as
    // implementation in the current variant
    object ANALYZE_DEPENDENCIES_REPORT: InternalArtifactType<Directory>(DIRECTORY), Replaceable

    // File containing SDK dependency block value.
    object SDK_DEPENDENCY_DATA: InternalArtifactType<RegularFile>(FILE), Replaceable

    // Artifacts privately shared with Android Studio.
    object APK_IDE_MODEL: InternalArtifactType<RegularFile>(FILE), Replaceable
    object BUNDLE_IDE_MODEL : InternalArtifactType<RegularFile>(FILE), Replaceable
    object APK_FROM_BUNDLE_IDE_MODEL : InternalArtifactType<RegularFile>(FILE), Replaceable

    object ASSET_PACK_MANIFESTS: InternalArtifactType<Directory>(DIRECTORY)

    object LINKED_RES_FOR_ASSET_PACK: InternalArtifactType<Directory>(DIRECTORY), Replaceable

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
