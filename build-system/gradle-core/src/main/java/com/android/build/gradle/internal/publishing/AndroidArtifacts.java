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
import com.android.builder.model.AndroidArtifact;
import com.google.common.base.MoreObjects;
import com.google.common.base.Supplier;
import groovy.lang.Closure;
import java.io.File;
import java.util.Collections;
import java.util.Date;
import java.util.Set;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ConfigurablePublishArtifact;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

/**
 * Helper for publishing android artifacts, both for internal (inter-project) and external
 * (to repositories).
 */
public class AndroidArtifacts {
    public static final String ARTIFACT_TYPE = "artifactType";

    // types for main artifacts
    public static final String TYPE_AAR = "aar";
    public static final String TYPE_APK = "apk";
    public static final String TYPE_ATOM_BUNDLE = "atombundle";

    // types for AAR/ATOM content
    public static final String TYPE_JAR = "jar";
    public static final String TYPE_JAVA_RES = "java-res";
    public static final String TYPE_MANIFEST = "android-manifest";
    public static final String TYPE_RESOURCES = "android-res";
    public static final String TYPE_ASSETS = "android-assets";
    public static final String TYPE_LOCAL_JARS = "android-local-jars";
    public static final String TYPE_JNI = "android-jni";
    public static final String TYPE_AIDL = "android-aidl";
    public static final String TYPE_RENDERSCRIPT = "android-renderscript";
    public static final String TYPE_LINT_JAR = "android-lint";
    public static final String TYPE_EXT_ANNOTATIONS = "android-ext-annot";
    public static final String TYPE_PUBLIC_RES = "android-public-res";
    public static final String TYPE_SYMBOLS = "android-symbols";
    public static final String TYPE_PROGUARD_RULES = "android-proguad";
    public static final String TYPE_DATA_BINDING = "android-databinding";
    public static final String TYPE_RESOURCES_PKG = "android-res-ap_";

    // scoped types for jars
    // This is the published folders of local jars for AAR modules
    public static final String TYPE_JAR_SUB_PROJECTS = "jar-scope-sub";
    // This is the list of local jars transformed from TYPE_JAR_SUB_PROJECT, in order to query
    // only these.
    public static final String TYPE_JAR_SUB_PROJECTS_LOCAL_DEPS = "jar-scope-sub-local";

    // types for additional artifacts to go with APK
    public static final String TYPE_MAPPING = "android-mapping";
    public static final String TYPE_METADATA = "android-metadata";
    public static final String TYPE_TESTED_MANIFEST = "android-tested-manifest";

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

    public static PublishArtifact buildArtifact(
            @NonNull String name,
            @NonNull String type,
            @Nullable String classifier,
            @NonNull FileSupplier outputFileSupplier) {
        return new AndroidArtifact(name, type, type, classifier, outputFileSupplier);
    }

    public static PublishArtifact buildApkArtifact(
            @NonNull String name,
            @Nullable String classifier,
            @NonNull FileSupplier outputFileSupplier) {
        return new AndroidArtifact(
                name, TYPE_APK, TYPE_APK, classifier, outputFileSupplier);
    }

    public static PublishArtifact buildAtomArtifact(
            @NonNull String name,
            @Nullable String classifier,
            @NonNull FileSupplier outputFileSupplier) {
        return new AndroidArtifact(
                name, TYPE_ATOM_BUNDLE, TYPE_ATOM_BUNDLE, classifier, outputFileSupplier);
    }

    public static void publishArtifact() {

    }

    public static void publishIntermediateArtifact(
            Project project,
            String publishConfigName,
            File file,
            String builtBy,
            String type) {
        project.getConfigurations().getByName(publishConfigName).getOutgoing().variants(
                (NamedDomainObjectContainer<ConfigurationVariant> variants) -> {
                    variants.create(type, (variant) ->
                            variant.artifact(file, (artifact) -> {
                                artifact.setType(type);
                                artifact.builtBy(project.getTasks().getByName(builtBy));
                            }));

                });
    }
    //
    //public static void publishIntermediateArtifact(
    //        Configuration configuration,
    //        File file,
    //        String builtBy,
    //        String type) {
    //    configuration.getOutgoing().variants(
    //            (NamedDomainObjectContainer<ConfigurationVariant> variants) -> {
    //                variants.create(type, (variant) ->
    //                        variant.artifact(file, (artifact) -> {
    //                            artifact.setType(type);
    //                            artifact.builtBy(project.getTasks().getByName(builtBy));
    //                        }));
    //
    //            });
    //}
    //

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
