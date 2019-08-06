/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.pipeline;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.QualifiedContent.ScopeType;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformInput;
import com.google.common.base.Charsets;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.hash.Hashing;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.FileCollection;

/**
 * Version of TransformStream handling input that is not generated by transforms.
 */
@Immutable
public class OriginalStream extends TransformStream {

    /** group id for local jars, including the ':' separating the groupId from artifactId */
    public static final String LOCAL_JAR_GROUPID = "android.local.jars:";

    @Nullable private final ArtifactCollection artifactCollection;

    public static Builder builder(@NonNull Project project, @NonNull String name) {
        return new Builder(project, name);
    }

    public static final class Builder {
        @NonNull private final Project project;
        @NonNull private final String name;
        private Set<ContentType> contentTypes = Sets.newHashSet();
        private Set<ScopeType> scopes = Sets.newHashSet();
        private FileCollection fileCollection;
        private ArtifactCollection artifactCollection;

        public Builder(@NonNull Project project, @NonNull String name) {
            this.project = project;
            this.name = name;
        }

        public OriginalStream build() {
            checkState(!scopes.isEmpty());
            checkState(!contentTypes.isEmpty());
            checkNotNull(fileCollection);

            return new OriginalStream(
                    name,
                    ImmutableSet.copyOf(contentTypes),
                    scopes,
                    artifactCollection,
                    fileCollection);
        }

        public Builder addContentTypes(@NonNull Set<ContentType> types) {
            this.contentTypes.addAll(types);
            return this;
        }

        public Builder addContentTypes(@NonNull ContentType... types) {
            this.contentTypes.addAll(Arrays.asList(types));
            return this;
        }

        public Builder addContentType(@NonNull ContentType type) {
            this.contentTypes.add(type);
            return this;
        }

        public Builder addScopes(@NonNull Collection<ScopeType> scopes) {
            this.scopes.addAll(scopes);
            return this;
        }

        public Builder addScope(@NonNull ScopeType scope) {
            this.scopes.add(scope);
            return this;
        }

        public Builder setFileCollection(@NonNull FileCollection fileCollection) {
            Preconditions.checkState(
                    this.fileCollection == null, "FileCollection already set on OriginalStream");
            this.fileCollection = fileCollection;
            return this;
        }

        public Builder setArtifactCollection(@NonNull ArtifactCollection artifactCollection) {
            this.artifactCollection = artifactCollection;
            return setFileCollection(artifactCollection.getArtifactFiles());
        }
    }

    private OriginalStream(
            @NonNull String name,
            @NonNull Set<ContentType> contentTypes,
            @NonNull Set<? super Scope> scopes,
            @Nullable ArtifactCollection artifactCollection,
            @NonNull FileCollection files) {
        super(name, contentTypes, scopes, files);
        this.artifactCollection = artifactCollection;
    }

    private static class OriginalTransformInput extends IncrementalTransformInput {

        @Override
        protected boolean checkRemovedFolder(
                @NonNull Set<? super Scope> transformScopes,
                @NonNull Set<ContentType> transformInputTypes,
                @NonNull File file,
                @NonNull List<String> fileSegments) {
            // we can never detect if a random file was removed from this input.
            return false;
        }

        @Override
        boolean checkRemovedJarFile(
                @NonNull Set<? super Scope> transformScopes,
                @NonNull Set<ContentType> transformInputTypes,
                @NonNull File file,
                @NonNull List<String> fileSegments) {
            // we can never detect if a jar was removed from this input.
            return false;
        }
    }

    @NonNull
    @Override
    TransformInput asNonIncrementalInput() {
        Set<ContentType> contentTypes = getContentTypes();
        Set<? super Scope> scopes = getScopes();

        List<JarInput> jarInputs;
        List<DirectoryInput> directoryInputs;

        if (artifactCollection != null) {
            jarInputs = Lists.newArrayList();
            directoryInputs = Lists.newArrayList();

            Map<ComponentIdentifier, Integer> duplicates = Maps.newHashMap();
            for (ResolvedArtifactResult result : artifactCollection.getArtifacts()) {
                File artifactFile = result.getFile();

                if (artifactFile.isFile()) {
                    jarInputs.add(
                            new ImmutableJarInput(
                                    getArtifactName(result, duplicates),
                                    artifactFile,
                                    Status.NOTCHANGED,
                                    contentTypes,
                                    scopes));
                } else if (artifactFile.isDirectory()) {
                    directoryInputs.add(
                            new ImmutableDirectoryInput(
                                    getArtifactName(result, duplicates),
                                    artifactFile,
                                    contentTypes,
                                    scopes));
                }
            }
        } else {
            Set<File> files = getFileCollection().getFiles();

            jarInputs =
                    files.stream()
                            .filter(File::isFile)
                            .map(
                                    file ->
                                            new ImmutableJarInput(
                                                    getUniqueInputName(file),
                                                    file,
                                                    Status.NOTCHANGED,
                                                    contentTypes,
                                                    scopes))
                            .collect(Collectors.toList());

            directoryInputs =
                    files.stream()
                            .filter(File::isDirectory)
                            .map(
                                    file ->
                                            new ImmutableDirectoryInput(
                                                    getUniqueInputName(file),
                                                    file,
                                                    contentTypes,
                                                    scopes))
                            .collect(Collectors.toList());
        }

        return new ImmutableTransformInput(jarInputs, directoryInputs, null);
    }

    @NonNull
    @Override
    IncrementalTransformInput asIncrementalInput() {
        IncrementalTransformInput input = new OriginalTransformInput();

        Set<ContentType> contentTypes = getContentTypes();
        Set<? super Scope> scopes = getScopes();

        if (artifactCollection != null) {
            Map<ComponentIdentifier, Integer> duplicates = Maps.newHashMap();
            for (ResolvedArtifactResult result : artifactCollection.getArtifacts()) {
                File artifactFile = result.getFile();

                if (artifactFile.isDirectory()) {
                    input.addFolderInput(
                            new MutableDirectoryInput(
                                    getArtifactName(result, duplicates),
                                    artifactFile,
                                    contentTypes,
                                    scopes));
                } else if (artifactFile.isFile()) {
                    input.addJarInput(
                            new QualifiedContentImpl(
                                    getArtifactName(result, duplicates),
                                    artifactFile,
                                    contentTypes,
                                    scopes));
                }
            }

        } else {
            getFileCollection()
                    .getFiles()
                    .forEach(
                            file -> {
                                if (file.isDirectory()) {
                                    input.addFolderInput(
                                            new MutableDirectoryInput(
                                                    getUniqueInputName(file),
                                                    file,
                                                    contentTypes,
                                                    scopes));
                                } else if (file.isFile()) {
                                    input.addJarInput(
                                            new QualifiedContentImpl(
                                                    getUniqueInputName(file),
                                                    file,
                                                    contentTypes,
                                                    scopes));
                                }
                            });
        }

        return input;
    }

    @NonNull
    private static String getArtifactName(
            @NonNull ResolvedArtifactResult artifactResult,
            @NonNull Map<ComponentIdentifier, Integer> deduplicationMap) {
        ComponentIdentifier id = artifactResult.getId().getComponentIdentifier();

        String baseName;

        if (id instanceof ProjectComponentIdentifier) {
            baseName = ((ProjectComponentIdentifier) id).getProjectPath();
        } else if (id instanceof ModuleComponentIdentifier) {
            baseName = id.getDisplayName();
        } else {
            // this is a local jar
            File artifactFile = artifactResult.getFile();

            baseName =
                    LOCAL_JAR_GROUPID
                            + artifactFile.getName()
                            + ":"
                            + Hashing.sha1()
                                    .hashString(artifactFile.getPath(), Charsets.UTF_16LE)
                                    .toString();
        }

        // check if a previous artifact use the same name. This can happen for instance in case
        // of an AAR with local Jars.
        // In that case happen an index to the name.
        final Integer zero = 0;
        Integer i =
                deduplicationMap.compute(
                        id,
                        (componentIdentifier, value) -> {
                            if (value == null) {
                                return zero;
                            }

                            return value + 1;
                        });
        if (!zero.equals(i)) {
            return baseName + "::" + i;
        }

        return baseName;
    }

    @NonNull
    private static String getUniqueInputName(@NonNull File file) {
        return Hashing.sha1().hashString(file.getPath(), Charsets.UTF_16LE).toString();
    }

    @NonNull
    @Override
    TransformStream makeRestrictedCopy(
            @NonNull Set<ContentType> types,
            @NonNull Set<? super Scope> scopes) {
        if (!scopes.equals(getScopes())) {
            // since the content itself (jars and folders) don't have their own notion of scopes
            // we cannot do a restricted stream.
            throw new UnsupportedOperationException("Cannot do a scope-restricted OriginalStream");
        }
        return new OriginalStream(
                getName() + "-restricted-copy",
                types,
                scopes,
                artifactCollection,
                getFileCollection());
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", getName())
                .add("scopes", getScopes())
                .add("contentTypes", getContentTypes())
                .add("fileCollection", getFileCollection())
                .toString();
    }
    
    @NonNull
    @Override
    FileCollection getOutputFileCollection(
            @NonNull Project project, @NonNull StreamFilter streamFilter) {
        if (streamFilter.accept(getContentTypes(), getScopes())) {
            return getFileCollection();
        } else {
            return project.files();
        }
    }
}
