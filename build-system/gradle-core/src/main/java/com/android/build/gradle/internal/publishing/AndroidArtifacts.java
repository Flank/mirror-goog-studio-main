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
import groovy.lang.Closure;
import java.io.File;
import java.util.Collections;
import java.util.Date;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ConfigurablePublishArtifact;
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

    public static PublishArtifact buildManifestArtifact(
            @NonNull String name,
            @NonNull FileSupplier outputFileSupplier) {
        return new AndroidArtifact(
                name, "xml", "xml", null /*classifier*/, outputFileSupplier);
    }

    public static PublishArtifact buildMappingArtifact(
            @NonNull String name,
            @NonNull FileSupplier outputFileSupplier) {
        return new AndroidArtifact(
                name, "map", "map", null /*classifier*/, outputFileSupplier);
    }

    public static PublishArtifact buildMetadataArtifact(
            @NonNull String name,
            @NonNull FileSupplier outputFileSupplier) {
        return new AndroidArtifact(
                name, "mtd", "mtd", null /*classifier*/, outputFileSupplier);
    }

    public static void publish(
            Project project,
            String publishConfigName,
            File file,
            String builtBy,
            String type) {
        project.getArtifacts().add(
                publishConfigName,
                file, new Closure(project /*doesnt matter*/) {
                    public Object doCall(ConfigurablePublishArtifact artifact) {
                        artifact.setType(type);
                        artifact.builtBy(project.getTasks().getByName(builtBy));
                        return null;
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
