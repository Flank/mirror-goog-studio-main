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

package com.android.build.gradle.internal.publishing;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.API_ELEMENTS;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.FEATURE_ELEMENTS;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.RUNTIME_ELEMENTS;

import com.android.annotations.NonNull;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.plugins.JavaPlugin;

/**
 * Helper for publishing android artifacts, both for internal (inter-project) and external
 * (to repositories).
 */
public class AndroidArtifacts {
    public static final Attribute<String> ARTIFACT_TYPE = Attribute.of("artifactType", String.class);

    // types for main artifacts
    public static final String TYPE_AAR = "aar";
    private static final String TYPE_APK = "apk";

    // types for AAR content
    private static final String TYPE_MANIFEST = "android-manifest";
    private static final String TYPE_MANIFEST_METADATA = "android-manifest-metadata";
    private static final String TYPE_ANDROID_RES = "android-res";
    private static final String TYPE_ASSETS = "android-assets";
    private static final String TYPE_JNI = "android-jni";
    private static final String TYPE_AIDL = "android-aidl";
    private static final String TYPE_RENDERSCRIPT = "android-renderscript";
    private static final String TYPE_LINT_JAR = "android-lint";
    private static final String TYPE_EXT_ANNOTATIONS = "android-ext-annot";
    private static final String TYPE_PUBLIC_RES = "android-public-res";
    private static final String TYPE_SYMBOL = "android-symbol";
    private static final String TYPE_PROGUARD_RULES = "android-proguad";
    private static final String TYPE_DATA_BINDING = "android-databinding";
    private static final String TYPE_EXPLODED_AAR = "android-exploded-aar";
    private static final String TYPE_JAR = "jar";

    // types for additional artifacts to go with APK
    private static final String TYPE_MAPPING = "android-mapping";
    private static final String TYPE_METADATA = "android-metadata";

    // types for feature-split content.
    private static final String TYPE_FEATURE_SPLIT_DECLARATION = "android-feature-split-decl";
    private static final String TYPE_FEATURE_SPLIT_MANIFEST = "android-feature-split-manifest";
    private static final String TYPE_FEATURE_IDS_DECLARATION = "android-feature-split-ids";
    private static final String TYPE_FEATURE_APPLICATION_ID = "android-feature-application-id";
    private static final String TYPE_FEATURE_RESOURCE_PKG = "android-feature-res-ap_";

    public enum ConsumedConfigType {
        COMPILE_CLASSPATH(API_ELEMENTS, true),
        RUNTIME_CLASSPATH(RUNTIME_ELEMENTS, true),
        ANNOTATION_PROCESSOR(RUNTIME_ELEMENTS, false),
        FEATURE_CLASSPATH(FEATURE_ELEMENTS, false);

        @NonNull
        private final PublishedConfigType publishedTo;
        private final boolean needsTestedComponents;

        ConsumedConfigType(
                @NonNull PublishedConfigType publishedTo, boolean needsTestedComponents) {
            this.publishedTo = publishedTo;
            this.needsTestedComponents = needsTestedComponents;
        }

        @NonNull
        public PublishedConfigType getPublishedTo() {
            return publishedTo;
        }

        public boolean needsTestedComponents() {
            return needsTestedComponents;
        }
    }

    public enum PublishedConfigType {
        API_ELEMENTS,
        RUNTIME_ELEMENTS,
        FEATURE_ELEMENTS,
    }

    public enum ArtifactScope {
        ALL, EXTERNAL, MODULE
    }

    public enum ArtifactType {
        CLASSES(JavaPlugin.CLASS_DIRECTORY),
        // Jar file for annotation processor as both classes and resources are needed, and for building model
        JAR(TYPE_JAR),

        // manifest is published to both to compare and detect provided-only library dependencies.
        MANIFEST(TYPE_MANIFEST),
        MANIFEST_METADATA(TYPE_MANIFEST_METADATA),

        // API only elements.
        AIDL(TYPE_AIDL),
        RENDERSCRIPT(TYPE_RENDERSCRIPT),
        DATA_BINDING(TYPE_DATA_BINDING),

        // runtime only elements
        JAVA_RES(JavaPlugin.RESOURCES_DIRECTORY),
        ANDROID_RES(TYPE_ANDROID_RES),
        ASSETS(TYPE_ASSETS),
        SYMBOL_LIST(TYPE_SYMBOL),
        JNI(TYPE_JNI),
        ANNOTATIONS(TYPE_EXT_ANNOTATIONS),
        PUBLIC_RES(TYPE_PUBLIC_RES),
        PROGUARD_RULES(TYPE_PROGUARD_RULES),

        // FIXME: we need a different publishing config with a CHECK Usage for this.
        LINT(TYPE_LINT_JAR),

        APK_MAPPING(TYPE_MAPPING),
        APK_METADATA(TYPE_METADATA),
        APK(TYPE_APK),

        // types for querying only. Not publishable.
        // FIXME once we only support level 2 sync, then this can be not publishable
        EXPLODED_AAR(TYPE_EXPLODED_AAR),

        // Feature split related artifacts.
        FEATURE_SPLIT_DECLARATION(TYPE_FEATURE_SPLIT_DECLARATION),
        FEATURE_SPLIT_MANIFEST(TYPE_FEATURE_SPLIT_MANIFEST),
        FEATURE_IDS_DECLARATION(TYPE_FEATURE_IDS_DECLARATION),
        FEATURE_APPLICATION_ID_DECLARATION(TYPE_FEATURE_APPLICATION_ID),
        FEATURE_RESOURCE_PKG(TYPE_FEATURE_RESOURCE_PKG);

        @NonNull
        private final String type;

        ArtifactType(@NonNull String type) {
            this.type = type;
        }

        @NonNull
        public String getType() {
            return type;
        }
    }
}
