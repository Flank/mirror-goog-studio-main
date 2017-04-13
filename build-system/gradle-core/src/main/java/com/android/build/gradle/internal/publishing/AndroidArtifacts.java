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
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.tasks.FileSupplier;
import com.google.common.base.MoreObjects;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gradle.api.Task;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

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
    private static final String TYPE_ANNOTATION_JAR = "android-annotation-jar";
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
        COMPILE_CLASSPATH(API_ELEMENTS),
        RUNTIME_CLASSPATH(RUNTIME_ELEMENTS),
        ANNOTATION_PROCESSOR(RUNTIME_ELEMENTS),
        FEATURE_CLASSPATH(FEATURE_ELEMENTS);

        @NonNull
        private final PublishedConfigType publishedTo;
        ConsumedConfigType(@NonNull PublishedConfigType publishedTo) {
            this.publishedTo = publishedTo;
        }

        @NonNull
        public PublishedConfigType getPublishedTo() {
            return publishedTo;
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

    private static final List<PublishedConfigType> API_ELEMENTS_ONLY
            = ImmutableList.of(API_ELEMENTS);
    private static final List<PublishedConfigType> RUNTIME_ELEMENTS_ONLY
            = ImmutableList.of(RUNTIME_ELEMENTS);
    private static final List<PublishedConfigType> API_AND_RUNTIME_ELEMENTS
            = ImmutableList.of(API_ELEMENTS, RUNTIME_ELEMENTS);
    private static final List<PublishedConfigType> FEATURE_ELEMENTS_ONLY =
            ImmutableList.of(FEATURE_ELEMENTS);

    public enum ArtifactType {
        CLASSES(JavaPlugin.CLASS_DIRECTORY, API_AND_RUNTIME_ELEMENTS),

        // manifest is published to both to compare and detect provided-only library dependencies.
        MANIFEST(TYPE_MANIFEST, API_AND_RUNTIME_ELEMENTS),
        MANIFEST_METADATA(TYPE_MANIFEST_METADATA, API_ELEMENTS_ONLY),

        // API only elements.
        AIDL(TYPE_AIDL, API_ELEMENTS_ONLY),
        RENDERSCRIPT(TYPE_RENDERSCRIPT, API_ELEMENTS_ONLY),
        DATA_BINDING(TYPE_DATA_BINDING, API_ELEMENTS_ONLY),

        // runtime only elements
        JAVA_RES(JavaPlugin.RESOURCES_DIRECTORY, RUNTIME_ELEMENTS_ONLY),
        ANDROID_RES(TYPE_ANDROID_RES, RUNTIME_ELEMENTS_ONLY),
        ASSETS(TYPE_ASSETS, RUNTIME_ELEMENTS_ONLY),
        SYMBOL_LIST(TYPE_SYMBOL, RUNTIME_ELEMENTS_ONLY),
        JNI(TYPE_JNI, RUNTIME_ELEMENTS_ONLY),
        ANNOTATIONS(TYPE_EXT_ANNOTATIONS, RUNTIME_ELEMENTS_ONLY),
        PUBLIC_RES(TYPE_PUBLIC_RES, RUNTIME_ELEMENTS_ONLY),
        PROGUARD_RULES(TYPE_PROGUARD_RULES, RUNTIME_ELEMENTS_ONLY),

        // FIXME: we need a different publishing config with a CHECK Usage for this.
        LINT(TYPE_LINT_JAR, API_AND_RUNTIME_ELEMENTS),

        // create a duplication of CLASSES because we don't want to publish
        // the classes of an FULL_APK to the runtime configuration as it's meant to be
        // used only for compilation, not runtime. Actually use TYPE_JAR to give access
        // to this via the model for now, the JarTransform will convert it back to CLASSES
        // FIXME: stop using TYPE_JAR for APK_CLASSES
        APK_CLASSES(TYPE_JAR, API_ELEMENTS_ONLY),
        APK_MAPPING(TYPE_MAPPING, API_ELEMENTS_ONLY),
        APK_METADATA(TYPE_METADATA, API_ELEMENTS_ONLY),
        APK(TYPE_APK, RUNTIME_ELEMENTS_ONLY),

        // types for querying only. Not publishable.
        // FIXME once we only support level 2 sync, then this can be not publishable
        EXPLODED_AAR(TYPE_EXPLODED_AAR, API_AND_RUNTIME_ELEMENTS),
        // Jar file for annotation processor as both classes and resources are needed, and for building model
        JAR(TYPE_JAR, API_AND_RUNTIME_ELEMENTS),

        // Feature split related artifacts.
        FEATURE_SPLIT_DECLARATION(TYPE_FEATURE_SPLIT_DECLARATION, FEATURE_ELEMENTS_ONLY),
        FEATURE_SPLIT_MANIFEST(TYPE_FEATURE_SPLIT_MANIFEST, FEATURE_ELEMENTS_ONLY),
        FEATURE_IDS_DECLARATION(TYPE_FEATURE_IDS_DECLARATION, API_ELEMENTS_ONLY),
        FEATURE_APPLICATION_ID_DECLARATION(TYPE_FEATURE_APPLICATION_ID, API_ELEMENTS_ONLY),
        FEATURE_RESOURCE_PKG(TYPE_FEATURE_RESOURCE_PKG, API_ELEMENTS_ONLY),
        FEATURE_CLASSES(JavaPlugin.CLASS_DIRECTORY, API_ELEMENTS_ONLY);

        @NonNull
        private final String type;
        @NonNull
        private final List<PublishedConfigType> publishedConfigTypes;

        ArtifactType(
                @NonNull String type,
                @NonNull List<PublishedConfigType> publishedConfigTypes) {
            this.type = type;
            this.publishedConfigTypes = ImmutableList.copyOf(publishedConfigTypes);
        }

        @NonNull
        public String getType() {
            return type;
        }

        @NonNull
        public Collection<PublishedConfigType> getPublishingConfigurations() {
            return publishedConfigTypes;
        }

        private static final Map<String, ArtifactType> reverseMap = new HashMap<>();

        static {
            for (ArtifactType type : values()) {
                reverseMap.put(type.getType(), type);
            }
        }

        public static ArtifactType byType(@NonNull String typeValue) {
            return reverseMap.get(typeValue);
        }
    }

    public static PublishArtifact getAarArtifact(
            @NonNull AbstractArchiveTask task,
            @NonNull String classifier) {
        return new AndroidArtifact(task.getBaseName(),
                TYPE_AAR, TYPE_AAR,
                classifier, new FileSupplier() {
            @NonNull
            @Override
            public Task getTask() {
                return task;
            }

            @Override
            public File get() {
                return task.getArchivePath();
            }
        });
    }

    private static class AndroidArtifact implements PublishArtifact {

        @NonNull
        private final String name;
        @NonNull
        private final String extension;
        @NonNull
        private final String type;
        @Nullable
        private final String classifier;
        @NonNull
        private final Supplier<File> outputFileSupplier;
        @NonNull
        private final TaskDependency taskDependency;

        private static final class DefaultTaskDependency implements TaskDependency {

            @NonNull
            private final Set<Task> tasks;

            DefaultTaskDependency(@NonNull Task task) {
                this.tasks = Collections.singleton(task);
            }

            @Override
            public Set<? extends Task> getDependencies(Task task) {
                return tasks;
            }
        }

        private AndroidArtifact(
                @NonNull String name,
                @NonNull String extension,
                @NonNull String type,
                @Nullable String classifier,
                @NonNull FileSupplier outputFileSupplier) {
            this.name = name;
            this.extension = extension;
            this.type = type;
            this.classifier = classifier;
            this.outputFileSupplier = outputFileSupplier;
            this.taskDependency
                    = new DefaultTaskDependency(outputFileSupplier.getTask());
        }

        @Override
        @NonNull
        public String getName() {
            return name;
        }

        @Nullable
        @Override
        public String getClassifier() {
            return classifier;
        }

        @Override
        public File getFile() {
            return outputFileSupplier.get();
        }

        @Override
        @NonNull
        public String getExtension() {
            return extension;
        }

        @Override
        @NonNull
        public String getType() {
            return type;
        }

        @Override
        public Date getDate() {
            // return null to let gradle use the current date during publication.
            return null;
        }

        @Override
        public TaskDependency getBuildDependencies() {
            return taskDependency;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("name", name)
                    .add("classifier", classifier)
                    .add("outputFile", outputFileSupplier.get())
                    .add("taskDependency", taskDependency)
                    .toString();
        }
    }
}
