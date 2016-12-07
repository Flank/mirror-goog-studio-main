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

import com.android.annotations.NonNull;
import com.android.annotations.concurrency.Immutable;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import groovy.lang.Closure;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;

/**
 * Version of TransformStream handling outputs of transforms.
 */
@Immutable
class IntermediateStream extends TransformStream {

    static Builder builder(Project project) {
        return new Builder(project);
    }

    static final class Builder {

        private final Project project;
        private Set<ContentType> contentTypes = Sets.newHashSet();
        private Set<QualifiedContent.ScopeType> scopes = Sets.newHashSet();
        private File rootLocation;
        private String taskName;

        public Builder(Project project) {
            this.project = project;
        }

        public IntermediateStream build() {
            Preconditions.checkNotNull(rootLocation);
            Preconditions.checkNotNull(taskName);
            Preconditions.checkState(!contentTypes.isEmpty());
            Preconditions.checkState(!scopes.isEmpty());

            // create a file collection with the files and the dependencies.
            FileCollection fileCollection = project.files(rootLocation,
                    new Closure(project) {
                        public Object doCall(ConfigurableFileCollection fileCollection) {
                            fileCollection.builtBy(taskName);
                            return null;
                        }
                    });


            return new IntermediateStream(
                    ImmutableSet.copyOf(contentTypes),
                    ImmutableSet.copyOf(scopes),
                    fileCollection);
        }

        Builder addContentTypes(@NonNull Set<ContentType> types) {
            this.contentTypes.addAll(types);
            return this;
        }

        Builder addContentTypes(@NonNull ContentType... types) {
            this.contentTypes.addAll(Arrays.asList(types));
            return this;
        }

        Builder addScopes(@NonNull Set<? super Scope> scopes) {
            for (Object scope : scopes) {
                this.scopes.add((QualifiedContent.ScopeType) scope);
            }
            return this;
        }

        Builder addScopes(@NonNull Scope... scopes) {
            this.scopes.addAll(Arrays.asList(scopes));
            return this;
        }

        Builder setRootLocation(@NonNull final File rootLocation) {
            this.rootLocation = rootLocation;
            return this;
        }

        Builder setTaskName(@NonNull String taskName) {
            this.taskName = taskName;
            return this;
        }
    }

    private IntermediateStream(
            @NonNull Set<ContentType> contentTypes,
            @NonNull Set<? super Scope> scopes,
            @NonNull FileCollection fileCollection) {
        super(contentTypes, scopes, fileCollection);
    }

    /**
     * Returns the files that make up the streams. The callable allows for resolving this lazily.
     */
    @NonNull
    File getRootLocation() {
        return getFiles().getSingleFile();
    }

    /**
     * Returns a new view of this content as a {@link TransformOutputProvider}.
     */
    @NonNull
    TransformOutputProvider asOutput() {
        return new TransformOutputProviderImpl(getRootLocation());
    }

    @NonNull
    @Override
    TransformInput asNonIncrementalInput() {
        return IntermediateFolderUtils.computeNonIncrementalInputFromFolder(
                getRootLocation(),
                getContentTypes(),
                getScopes());
    }

    @NonNull
    @Override
    IncrementalTransformInput asIncrementalInput() {
        return IntermediateFolderUtils.computeIncrementalInputFromFolder(
                getRootLocation(),
                getContentTypes(),
                getScopes());
    }

    @NonNull
    @Override
    TransformStream makeRestrictedCopy(
            @NonNull Set<ContentType> types,
            @NonNull Set<? super Scope> scopes) {
        return new IntermediateStream(
                types,
                scopes,
                getFiles());
    }

    @Override
    @NonNull
    FileCollection getOutputFileCollection(@NonNull Project project, @NonNull StreamFilter streamFilter) {
        // create a collection that only returns the requested content type/scope,
        // and contain the dependency information.

        // the collection inside this type of stream cannot be used as is. This is because it
        // contains the root location rather that the actual inputs of the stream. Therefore
        // we need to go through them and create a single collection that contains the actual
        // inputs.
        // However the content of the intermediate root folder isn't known at configuration
        // time so we need to pass a callable that will compute the files dynamically.
        Callable<List<File>> callable = () -> {
            List<File> files = Lists.newArrayList();

            // get the inputs
            TransformInput input = asNonIncrementalInput();

            // collect the files and dependency info for the collection
            for (QualifiedContent content : Iterables.concat(
                    input.getJarInputs(), input.getDirectoryInputs())) {
                if (streamFilter.accept(content.getContentTypes(), content.getScopes())) {
                    files.add(content.getFile());
                }
            }

            return files;
        };

        return project.files(callable).builtBy(getFiles().getBuildDependencies());
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("scopes", getScopes())
                .add("contentTypes", getContentTypes())
                .add("fileCollection", getFiles())
                .toString();
    }
}
