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

package com.android.build.gradle.internal.scope;

import com.android.build.api.artifact.ArtifactType;

/** A type of output generated by a task. */
public enum InternalArtifactType implements ArtifactType {
    // --- classes ---
    // These are direct task outputs. If you are looking for all the classes of a
    // module, use AnchorOutputType.ALL_CLASSES
    // Javac task output.
    JAVAC,

    // --- Published classes ---
    // Class-type task output for tasks that generate published classes.

    // Packaged classes for AAR intermediate publishing
    // This is for external usage. For usage inside a module use ALL_CLASSES
    LIBRARY_CLASSES,
    // the packaged classes published by APK modules.
    // This is for external usage. For usage inside a module use ALL_CLASSES
    APP_CLASSES,
    // the packaged classes published by feature modules.
    // This is for external usage. For usage inside a module use ALL_CLASSES
    FEATURE_CLASSES,

    // --- java res ---
    // java processing output
    JAVA_RES,
    // packaged java res for aar intermediate publishing
    LIBRARY_JAVA_RES,

    // Full jar with both classes and java res.
    FULL_JAR,

    // --- android res ---
    // R.TXT with platform attr values
    PLATFORM_R_TXT,
    // output of the resource merger ready for aapt.
    MERGED_RES,
    // The R.java file/files as generated by AAPT or by the new resource processing in libraries.
    NOT_NAMESPACED_R_CLASS_SOURCES,
    // The R class jar as compiled from the R.java generated by AAPT or directly generated by
    // the new resource processing in libraries.
    COMPILE_ONLY_NOT_NAMESPACED_R_CLASS_JAR,
    // output of the resource merger for unit tests and the resource shrinker.
    MERGED_NOT_COMPILED_RES,
    // Directory containing config file for unit testing with resources
    UNIT_TEST_CONFIG_DIRECTORY,
    // compiled resources (output of aapt)
    PROCESSED_RES,
    // package resources for aar publishing.
    PACKAGED_RES,
    // R.txt output
    SYMBOL_LIST,
    // Synthetic artifacts
    SYMBOL_LIST_WITH_PACKAGE_NAME,
    // public.txt output
    PUBLIC_RES,
    SHRUNK_PROCESSED_RES,
    DENSITY_OR_LANGUAGE_SPLIT_PROCESSED_RES,
    ABI_PROCESSED_SPLIT_RES,
    DENSITY_OR_LANGUAGE_PACKAGED_SPLIT,
    INSTANT_RUN_MAIN_APK_RESOURCES,
    INSTANT_RUN_PACKAGED_RESOURCES,
    // linked res for the unified bundle
    LINKED_RES_FOR_BUNDLE,

    // Published folders for bundling. This has extra cost as we need to copy
    // the content of the pipeline. Use TransformManager.getPipelineOutputAsFileCollection
    // whenever possible. See PipelineToPublicationTask
    PUBLISHED_DEX,
    PUBLISHED_JAVA_RES,
    PUBLISHED_NATIVE_LIBS,

    // --- Namespaced android res ---
    // Compiled resources (directory of .flat files) for the local library
    RES_COMPILED_FLAT_FILES,
    // An AAPT2 static library, containing only the current sub-project's resources.
    RES_STATIC_LIBRARY,
    // Compiled R class jar (for compilation only, packaged in AAR)
    COMPILE_ONLY_NAMESPACED_R_CLASS_JAR,
    // res-ids.txt
    NAMESPACED_SYMBOL_LIST_WITH_PACKAGE_NAME,
    // Final R class sources (to package)
    RUNTIME_R_CLASS_SOURCES,
    // Final R class classes (for packaging)
    RUNTIME_R_CLASS_CLASSES,

    // --- JNI libs ---
    // packaged JNI for inter-project intermediate publishing
    LIBRARY_JNI,
    // packaged JNI for AAR publishing
    LIBRARY_AND_LOCAL_JARS_JNI,

    // Assets created by compiling shader
    SHADER_ASSETS,

    LIBRARY_ASSETS,
    MERGED_ASSETS,
    MOCKABLE_JAR,

    // AIDL headers "packaged" by libraries for consumers.
    AIDL_PARCELABLE,
    // renderscript headers "packaged" by libraries for consumers.
    RENDERSCRIPT_HEADERS,

    COMPATIBLE_SCREEN_MANIFEST,
    MERGED_MANIFESTS,
    LIBRARY_MANIFEST,
    AAPT_FRIENDLY_MERGED_MANIFESTS,
    INSTANT_RUN_MERGED_MANIFESTS,
    MANIFEST_METADATA,
    MANIFEST_MERGE_REPORT,

    // List of annotation processors for metrics.
    ANNOTATION_PROCESSOR_LIST,

    // the file that consumers of an AAR can use for additional proguard rules.
    CONSUMER_PROGUARD_FILE,

    // the data binding artifact for a library that gets published with the aar
    DATA_BINDING_ARTIFACT,
    // the merged data binding artifacts from all the dependencies
    DATA_BINDING_DEPENDENCY_ARTIFACTS,
    // the generated base classes artifacts from all dependencies
    DATA_BINDING_BASE_CLASS_LOGS_DEPENDENCY_ARTIFACTS,
    // the data binding class log generated after compilation, includes merged
    // class info file
    DATA_BINDING_BASE_CLASS_LOG_ARTIFACT,
    // source code generated by data binding tasks.
    DATA_BINDING_BASE_CLASS_SOURCE_OUT,

    LINT_JAR,

    // the zip file output of the extract annotation class.
    ANNOTATIONS_ZIP,
    // the associated TypeDef file
    ANNOTATIONS_TYPEDEF_FILE,
    // the associated proguard file
    ANNOTATIONS_PROGUARD,
    // The classes.jar for the AAR
    AAR_MAIN_JAR,
    // The libs/ directory for the AAR, containing secondary jars
    AAR_LIBS_DIRECTORY,

    ABI_PACKAGED_SPLIT,
    FULL_APK,
    APK,
    APK_MAPPING,
    AAR,
    INSTANTAPP_BUNDLE,
    SPLIT_LIST,
    APK_LIST,

    FEATURE_IDS_DECLARATION,
    FEATURE_APPLICATION_ID_DECLARATION,
    FEATURE_RESOURCE_PKG,
    FEATURE_TRANSITIVE_DEPS,

    // Project metadata
    METADATA_FEATURE_DECLARATION,
    METADADA_FEATURE_MANIFEST,
    METADATA_APP_ID_DECLARATION,
}
