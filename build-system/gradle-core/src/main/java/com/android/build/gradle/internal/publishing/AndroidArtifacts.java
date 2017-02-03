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
    private static final String TYPE_ATOM_BUNDLE = "atombundle";

    // types for AAR/ATOM content
    private static final String TYPE_MANIFEST = "android-manifest";
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

    // types for ATOM content.
    private static final String TYPE_RESOURCES_PKG = "android-res-ap_";
    private static final String TYPE_ATOM_MANIFEST = "android-atom-manifest";
    private static final String TYPE_ATOM_ANDROID_RES = "android-atom-res";
    private static final String TYPE_ATOM_DEX = "android-atom-dex";
    private static final String TYPE_ATOM_JAVA_RES = "android-atom-java-res";
    private static final String TYPE_ATOM_JNI = "android-atom-jni";
    private static final String TYPE_ATOM_ASSETS = "android-atom-assets";

    // types for additional artifacts to go with APK
    private static final String TYPE_MAPPING = "android-mapping";
    private static final String TYPE_METADATA = "android-metadata";

    public enum ConfigType {
        COMPILE, RUNTIME, ANNOTATION_PROCESSOR
    }

    public enum ArtifactScope {
        ALL, EXTERNAL, MODULE
    }

    public enum ArtifactType {
        CLASSES(JavaPlugin.CLASS_DIRECTORY, ConfigType.COMPILE, ConfigType.RUNTIME),

        AIDL(TYPE_AIDL, ConfigType.COMPILE),
        RENDERSCRIPT(TYPE_RENDERSCRIPT, ConfigType.COMPILE),
        DATA_BINDING(TYPE_DATA_BINDING, ConfigType.COMPILE),

        JAVA_RES(JavaPlugin.RESOURCES_DIRECTORY, ConfigType.RUNTIME),
        MANIFEST(TYPE_MANIFEST, ConfigType.RUNTIME),
        ANDROID_RES(TYPE_ANDROID_RES, ConfigType.RUNTIME),
        ASSETS(TYPE_ASSETS, ConfigType.RUNTIME),
        SYMBOL_LIST(TYPE_SYMBOL, ConfigType.RUNTIME),
        JNI(TYPE_JNI, ConfigType.RUNTIME),
        ANNOTATIONS(TYPE_EXT_ANNOTATIONS, ConfigType.RUNTIME),
        PUBLIC_RES(TYPE_PUBLIC_RES, ConfigType.RUNTIME),
        PROGUARD_RULES(TYPE_PROGUARD_RULES, ConfigType.RUNTIME),

        LINT(TYPE_LINT_JAR),

        // create a duplication of CLASSES because we don't want to publish
        // the classes of an APK to the runtime configuration as it's meant to be
        // used only for compilation, not runtime.
        APK_CLASSES(JavaPlugin.CLASS_DIRECTORY, ConfigType.COMPILE),
        APK_MAPPING(TYPE_MAPPING, ConfigType.COMPILE),
        APK_METADATA(TYPE_METADATA, ConfigType.COMPILE),
        APK(TYPE_APK, ConfigType.RUNTIME),

        RESOURCES_PKG(TYPE_RESOURCES_PKG),
        ATOM_MANIFEST(TYPE_ATOM_MANIFEST),
        ATOM_ANDROID_RES(TYPE_ATOM_ANDROID_RES),
        ATOM_DEX(TYPE_ATOM_DEX),
        ATOM_JAVA_RES(TYPE_ATOM_JAVA_RES),
        ATOM_JNI(TYPE_ATOM_JNI),
        ATOM_ASSETS(TYPE_ATOM_ASSETS),
        METADATA(TYPE_METADATA);

        private final String type;
        private final Collection<ConfigType> configTypes;
        ArtifactType(@NonNull String type, @NonNull ConfigType... configTypes) {
            this.type = type;
            this.configTypes = ImmutableList.copyOf(configTypes);
        }

        @NonNull
        public String getType() {
            return type;
        }

        @NonNull
        public Collection<ConfigType> getPublishingConfigurations() {
            return configTypes;
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

    public static PublishArtifact buildAtomArtifact(
            @NonNull String name,
            @Nullable String classifier,
            @NonNull FileSupplier outputFileSupplier) {
        return new AndroidArtifact(
                name, TYPE_ATOM_BUNDLE, TYPE_ATOM_BUNDLE, classifier, outputFileSupplier);
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
        public String getExtension() {
            return extension;
        }

        @Override
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
