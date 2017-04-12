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
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;

/**
 * Representation of a stream for internal usage of the {@link TransformManager} to wire up
 * the different Transforms.
 *
 * Transforms read from and write into TransformStreams, via a custom view of them:
 * {@link TransformInput}, and {@link TransformOutputProvider}.
 *
 * This contains information about the content via {@link QualifiedContent}, dependencies, and the
 * actual file information.
 *
 * The dependencies is what triggers the creation of the files and any Transform (task) consuming
 * the files must be made to depend on these objects.
 */
@Immutable
public abstract class TransformStream {

    @NonNull private final String name;
    @NonNull private final Set<ContentType> contentTypes;
    @NonNull private final Set<? super Scope> scopes;
    @NonNull private final FileCollection fileCollection;

    /**
     * Creates the stream
     *
     * @param name the name of the string. This is used only for debugging purpose in order to more
     *     easily identify streams when debugging transforms. There is no restrictions on what the
     *     name can be.
     * @param contentTypes the content type(s) of the stream
     * @param scopes the scope(s) of the stream
     * @param fileCollection the file collection that makes up the content of the stream
     */
    protected TransformStream(
            @NonNull String name,
            @NonNull Set<ContentType> contentTypes,
            @NonNull Set<? super Scope> scopes,
            @NonNull FileCollection fileCollection) {
        this.name = name;
        this.contentTypes = contentTypes;
        this.scopes = scopes;
        this.fileCollection = fileCollection;
    }

    @NonNull
    public String getName() {
        return name;
    }

    /**
     * Returns the type of content that the stream represents.
     *
     * <p>
     * It's never null nor empty, but can contain several types.
     */
    @NonNull
    public Set<ContentType> getContentTypes() {
        return contentTypes;
    }

    /**
     * Returns the scope of the stream.
     *
     * <p>
     * It's never null nor empty, but can contain several scopes.
     */
    @NonNull
    public Set<? super Scope> getScopes() {
        return scopes;
    }

    /** Returns the stream content as a FileCollection. */
    @NonNull
    public FileCollection getFileCollection() {
        return fileCollection;
    }

    /**
     * Returns the transform input for this stream.
     *
     * All the {@link JarInput} and {@link DirectoryInput} will be in non-incremental mode.
     *
     * @return the transform input.
     */
    @NonNull
    abstract TransformInput asNonIncrementalInput();

    /**
     * Returns a list of QualifiedContent for the jars and one for the folders.
     *
     */
    @NonNull
    abstract IncrementalTransformInput asIncrementalInput();

    @NonNull
    abstract TransformStream makeRestrictedCopy(
            @NonNull Set<ContentType> types,
            @NonNull Set<? super Scope> scopes);

    /**
     * Returns a FileCollection that contains the outputs.
     *
     * The type/scope of the output is filtered by a StreamFilter.
     *
     * @param project a Projet object to create new FileCollection
     * @param streamFilter the stream filter.
     *
     * @return the FileCollection
     */
    @NonNull
    FileCollection getOutputFileCollection(
            @NonNull Project project,
            @NonNull StreamFilter streamFilter) {
        return fileCollection;
    }
}
