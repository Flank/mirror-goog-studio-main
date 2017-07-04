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

package com.android.build.gradle.internal.transforms;

import com.android.annotations.NonNull;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.gradle.internal.pipeline.TransformManager;
import java.io.File;
import java.util.Set;

/** A simple jar input for the transforms pipeline. */
public class SimpleJarInput implements JarInput {

    @NonNull private final File file;
    @NonNull private final Status status;
    @NonNull private final String name;
    @NonNull private final Set<ContentType> contentTypes;
    @NonNull private final Set<? super Scope> scopes;

    public static Builder builder(@NonNull File jarFile) {
        return new Builder(jarFile);
    }

    SimpleJarInput(
            @NonNull File file,
            @NonNull Status status,
            @NonNull String name,
            @NonNull Set<ContentType> contentTypes,
            @NonNull Set<? super Scope> scopes) {
        this.file = file;
        this.status = status;
        this.name = name;
        this.contentTypes = contentTypes;
        this.scopes = scopes;
    }

    @Override
    @NonNull
    public File getFile() {
        return file;
    }

    @Override
    @NonNull
    public Status getStatus() {
        return status;
    }

    @Override
    @NonNull
    public String getName() {
        return name;
    }

    @Override
    @NonNull
    public Set<ContentType> getContentTypes() {
        return contentTypes;
    }

    @Override
    @NonNull
    public Set<? super Scope> getScopes() {
        return scopes;
    }

    public static class Builder {
        private File file;
        private Status status;
        private String name;
        private Set<ContentType> contentTypes;
        private Set<? super Scope> scopes;

        public Builder(File file) {
            this.file = file;
            this.name = file.getName();
            this.status = Status.ADDED;
            this.contentTypes = TransformManager.CONTENT_CLASS;
            this.scopes = TransformManager.SCOPE_FULL_LIBRARY_WITH_LOCAL_JARS;
        }

        public Builder setStatus(Status status) {
            this.status = status;
            return this;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setContentTypes(@NonNull Set<QualifiedContent.ContentType> contentTypes) {
            this.contentTypes = contentTypes;
            return this;
        }

        public Builder setScopes(@NonNull Set<QualifiedContent.Scope> scopes) {
            this.scopes = scopes;
            return this;
        }

        public SimpleJarInput create() {
            return new SimpleJarInput(file, status, name, contentTypes, scopes);
        }
    }
}
