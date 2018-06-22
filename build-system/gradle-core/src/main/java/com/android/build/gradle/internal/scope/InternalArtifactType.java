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

import com.android.annotations.NonNull;
import com.android.build.api.artifact.ArtifactType;
import java.io.File;
import java.util.Locale;

/** A type of output generated by a task. */
public enum InternalArtifactType implements ArtifactType {
    // --- classes ---
    // These are direct task outputs. If you are looking for all the classes of a
    // module, use AnchorOutputType.ALL_CLASSES
    // Javac task output.
    JAVAC,
    // Rewritten classes from non-namespaced dependencies. Fully resource namespaced now.
    NAMESPACED_CLASSES,
    // Rewritten classes from non-namespaced dependencies put together into one JAR.
    NAMESPACED_CLASSES_JAR,

    // --- Published classes ---
    // Class-type task output for tasks that generate published classes.

    // Packaged classes for AAR intermediate publishing
    // This is for external usage. For usage inside a module use ALL_CLASSES
    LIBRARY_CLASSES_JAR,
    LIBRARY_CLASSES_DIR,
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
    // output of the resource merger ready for aapt.
    MERGED_RES,
    // The R.java file/files as generated by AAPT or by the new resource processing in libraries.
    NOT_NAMESPACED_R_CLASS_SOURCES(Category.GENERATED),
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
    // Resources defined within the AAR.
    DEFINED_ONLY_SYMBOL_LIST,
    // public.txt output
    PUBLIC_RES,
    SHRUNK_PROCESSED_RES,
    DENSITY_OR_LANGUAGE_SPLIT_PROCESSED_RES,
    ABI_PROCESSED_SPLIT_RES,
    DENSITY_OR_LANGUAGE_PACKAGED_SPLIT,
    INSTANT_RUN_MAIN_APK_RESOURCES,
    INSTANT_RUN_PACKAGED_RESOURCES,
    INSTANT_RUN_SPLIT_APK_RESOURCES,
    // linked res for the unified bundle
    LINKED_RES_FOR_BUNDLE,

    // Artifacts for legacy multidex
    LEGACY_MULTIDEX_AAPT_DERIVED_PROGUARD_RULES,
    LEGACY_MULTIDEX_MAIN_DEX_LIST,

    // --- Namespaced android res ---
    // Compiled resources (directory of .flat files) for the local library
    RES_COMPILED_FLAT_FILES,
    // An AAPT2 static library, containing only the current sub-project's resources.
    RES_STATIC_LIBRARY,
    // Compiled R class jar (for compilation only, packaged in AAR)
    COMPILE_ONLY_NAMESPACED_R_CLASS_JAR,
    // JAR file containing all of the auto-namespaced classes from dependencies.
    COMPILE_ONLY_NAMESPACED_DEPENDENCIES_R_JAR,
    // JARs containing auto-namespaced classes from dependencies.
    COMPILE_ONLY_NAMESPACED_DEPENDENCIES_R_JARS,
    // Classes JAR files from dependencies that need to be auto-namespaced.
    NON_NAMESPACED_CLASSES,
    // Final R class sources (to package)
    RUNTIME_R_CLASS_SOURCES(Category.GENERATED),
    // Final R class classes (for packaging)
    RUNTIME_R_CLASS_CLASSES,
    // Partial R.txt files generated by AAPT2 at compile time.
    PARTIAL_R_FILES,

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
    AIDL_SOURCE_OUTPUT_DIR(Category.GENERATED),
    // renderscript headers "packaged" by libraries for consumers.
    RENDERSCRIPT_HEADERS,
    // source output for rs
    RENDERSCRIPT_SOURCE_OUTPUT_DIR(Category.GENERATED),

    COMPATIBLE_SCREEN_MANIFEST,
    MERGED_MANIFESTS,
    LIBRARY_MANIFEST,
    // AAR manifests that have been auto-namespaced and are fully resource namespace aware.
    NAMESPACED_MANIFESTS,
    AAPT_FRIENDLY_MERGED_MANIFESTS,
    INSTANT_RUN_MERGED_MANIFESTS,
    MANIFEST_METADATA,
    MANIFEST_MERGE_REPORT,
    // Simplified android manifest with original package name.
    // It's used to create namespaced res.apk static library.
    STATIC_LIBRARY_MANIFEST,

    // List of annotation processors for metrics.
    ANNOTATION_PROCESSOR_LIST,

    // the file that consumers of an AAR can use for additional proguard rules.
    CONSUMER_PROGUARD_FILE,
    // the proguard rules produced by aapt.
    AAPT_PROGUARD_FILE,
    // the merger of a module's AAPT_PROGUARD_FILE and those of its feature(s)
    MERGED_AAPT_PROGUARD_FILE,

    // the data binding artifact for a library that gets published with the aar
    DATA_BINDING_ARTIFACT,
    // the merged data binding artifacts from all the dependencies
    DATA_BINDING_DEPENDENCY_ARTIFACTS,
    // directory containing layout info files for data binding when merge-resources type == MERGE
    DATA_BINDING_LAYOUT_INFO_TYPE_MERGE,
    // directory containing layout info files for data binding when merge-resources type == PACKAGE
    // see https://issuetracker.google.com/110412851
    DATA_BINDING_LAYOUT_INFO_TYPE_PACKAGE,
    // the generated base classes artifacts from all dependencies
    DATA_BINDING_BASE_CLASS_LOGS_DEPENDENCY_ARTIFACTS,
    // the data binding class log generated after compilation, includes merged
    // class info file
    DATA_BINDING_BASE_CLASS_LOG_ARTIFACT,
    // source code generated by data binding tasks.
    DATA_BINDING_BASE_CLASS_SOURCE_OUT(Category.GENERATED),

    LINT_JAR,

    // the zip file output of the extract annotation class.
    ANNOTATIONS_ZIP,
    // Optional recipe file (only used for libraries) which describes typedefs defined in the
    // library, and how to process them (typically which typedefs to omit during packaging).
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
    APK_FOR_LOCAL_TEST,
    APK_MAPPING,
    AAR,
    INSTANTAPP_BUNDLE,
    SPLIT_LIST,
    APK_LIST,

    // an intermediate bundle that contains only the current module
    MODULE_BUNDLE,
    // The main dex list for the bundle, unlike the main dex list for a monolithic application, this
    // analyzes all of the dynamic feature classes too.
    MAIN_DEX_LIST_FOR_BUNDLE,
    // the full bundle, including feature module. This is only valid for the base module.
    BUNDLE(Category.OUTPUTS),
    // APK Set archive with APKs generated from a bundle.
    APKS_FROM_BUNDLE,
    // output of ExtractApks applied to APKS_FROM_BUNDLE and a device config.
    EXTRACTED_APKS,

    // file containing the metadata for the full feature set. This contains the feature names,
    // the res ID offset, both tied to the feature module path. Published by the base for the
    // other features to consume and find their own metadata.
    FEATURE_SET_METADATA,
    // file containing the module information (like its application ID) to synchronize all base
    // and dynamic feature. This is published by the base feature and installed application module.
    METADATA_BASE_MODULE_DECLARATION,
    // file containing only the application ID. It is used to synchronize all feature plugins
    // with the application module's application ID.
    METADATA_APPLICATION_ID,
    FEATURE_RESOURCE_PKG,
    // File containing the list of transitive dependencies of a given feature. This is consumed
    // by other features to avoid repackaging the same thing.
    FEATURE_TRANSITIVE_DEPS,
    // The information about the features in the app that is necessary for the data binding
    // annotation processor (for base feature compilation). Created by the
    // DataBindingExportFeatureApplicationIdsTask and passed down to the annotation processor via
    // processor args.
    FEATURE_DATA_BINDING_BASE_FEATURE_INFO,
    // The information about the feature that is necessary for the data binding annotation
    // processor (for feature compilation). Created by DataBindingExportFeatureInfoTask and passed
    // into the annotation processor via processor args.
    FEATURE_DATA_BINDING_FEATURE_INFO,
    // The feature dex files output by the DexSplitter from the base. The base produces and
    // publishes these files when there's multi-apk code shrinking.
    FEATURE_DEX,
    // The class files for a feature and all of its runtime dependencies.
    FEATURE_AND_RUNTIME_DEPS_CLASSES,
    // The java resources for a feature and all of its runtime dependencies.
    FEATURE_AND_RUNTIME_DEPS_JAVA_RES,

    // Project metadata
    METADATA_FEATURE_DECLARATION,
    METADATA_FEATURE_MANIFEST,
    METADATA_INSTALLED_BASE_DECLARATION,

    INSTANT_RUN_APP_INFO_OUTPUT_FILE;

    /**
     * Defines the kind of artifact type. this will be used to determine the output file location
     * for instance.
     */
    enum Category {
        /* Generated files that are meant to be visible to users from the IDE */
        GENERATED,
        /* Intermediates files produced by tasks. */
        INTERMEDIATES,
        /* output files going into the outputs folder. This is the result of the build. */
        OUTPUTS;

        /**
         * Return the file location for this kind of artifact type.
         *
         * @param parentDir the parent build directory
         * @return a file location which is task and variant independent.
         */
        @NonNull
        File getOutputDir(File parentDir) {
            return new File(parentDir, name().toLowerCase(Locale.US));
        }

        @NonNull
        String getOutputPath() {
            return name().toLowerCase(Locale.US);
        }
    }

    final Category category;

    InternalArtifactType() {
        this.category = Category.INTERMEDIATES;
    }

    InternalArtifactType(Category category) {
        this.category = category;
    }
}
