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
import com.android.annotations.Nullable;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.SecondaryInput;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.mockito.Mockito;

class TransformTestHelper {

    static InvocationBuilder invocationBuilder() {
        return new InvocationBuilder();
    }
    static TransformInputBuilder inputBuilder() {
        return new TransformInputBuilder();
    }
    static JarInputBuilder jarBuilder(@NonNull File jarFile) {
        return new JarInputBuilder(jarFile);
    }
    static SingleJarInputBuilder singleJarBuilder(@NonNull File jarFile) {
        return new SingleJarInputBuilder(jarFile);
    }

    static class InvocationBuilder {

        private TransformOutputProvider transformOutputProvider;
        private Set<TransformInput> inputs = new HashSet<>();
        private Set<TransformInput> referencedInputs = new HashSet<>();
        private Set<SecondaryInput> secondaryInputs = new HashSet<>();
        private boolean isIncremental;

        InvocationBuilder setTransformOutputProvider(
                @NonNull TransformOutputProvider transformOutputProvider) {
            this.transformOutputProvider = transformOutputProvider;
            return this;
        }

        public InvocationBuilder setInputs(@NonNull Set<TransformInput> inputs) {
            this.inputs.clear();
            this.inputs.addAll(inputs);
            return this;
        }

        public InvocationBuilder addInput(@NonNull TransformInput input) {
            inputs.add(input);
            return this;
        }

        public InvocationBuilder setReferencedInputs(@NonNull Set<TransformInput> referencedInputs) {
            this.referencedInputs.clear();
            this.referencedInputs.addAll(referencedInputs);
            return this;
        }

        public InvocationBuilder addReferenceInput(@NonNull TransformInput input) {
            referencedInputs.add(input);
            return this;
        }

        public InvocationBuilder setSecondaryInputs(@NonNull Set<SecondaryInput> secondaryInputs) {
            this.secondaryInputs.clear();
            this.secondaryInputs.addAll(secondaryInputs);
            return this;
        }

        public InvocationBuilder addSecondaryInput(@NonNull SecondaryInput input) {
            secondaryInputs.add(input);
            return this;
        }

        public InvocationBuilder setIncremental(boolean incremental) {
            isIncremental = incremental;
            return this;
        }

        TransformInvocation build() {
            return new FakeTransformInvocation(
                    transformOutputProvider,
                    ImmutableSet.copyOf(inputs),
                    ImmutableSet.copyOf(referencedInputs),
                    ImmutableSet.copyOf(secondaryInputs),
                    isIncremental);
        }
    }

    private static class FakeTransformInvocation implements TransformInvocation {

        @NonNull
        private final TransformOutputProvider transformOutputProvider;
        @NonNull
        private final ImmutableSet<TransformInput> inputs;
        @NonNull
        private final ImmutableSet<TransformInput> referencedInputs;
        @NonNull
        private final ImmutableSet<SecondaryInput> secondaryInputs;
        private final boolean isIncremental;

        private FakeTransformInvocation(
                @NonNull TransformOutputProvider transformOutputProvider,
                @NonNull ImmutableSet<TransformInput> inputs,
                @NonNull ImmutableSet<TransformInput> referencedInputs,
                @NonNull ImmutableSet<SecondaryInput> secondaryInputs,
                boolean isIncremental) {
            this.transformOutputProvider = transformOutputProvider;
            this.inputs = inputs;
            this.referencedInputs = referencedInputs;
            this.secondaryInputs = secondaryInputs;
            this.isIncremental = isIncremental;
        }

        @NonNull
        @Override
        public Context getContext() {
            return Mockito.mock(Context.class);
        }

        @NonNull
        @Override
        public Collection<TransformInput> getInputs() {
            return inputs;
        }

        @NonNull
        @Override
        public Collection<TransformInput> getReferencedInputs() {
            return referencedInputs;
        }

        @NonNull
        @Override
        public Collection<SecondaryInput> getSecondaryInputs() {
            return secondaryInputs;
        }

        @Nullable
        @Override
        public TransformOutputProvider getOutputProvider() {
            return transformOutputProvider;
        }

        @Override
        public boolean isIncremental() {
            return isIncremental;
        }
    }

    static class TransformInputBuilder {

        private final List<JarInput> jarInputs = new ArrayList<>();
        private final List<DirectoryInput> dirInputs = new ArrayList<>();

        TransformInputBuilder addInput(QualifiedContent input) {
            if (input instanceof  JarInput) {
                jarInputs.add((JarInput) input);
            } else if (input instanceof DirectoryInput) {
                dirInputs.add((DirectoryInput) input);
            } else {
                throw new RuntimeException("Unknown input type");
            }

            return this;
        }

        FakeTransformInput build() {
            return new FakeTransformInput(
                    ImmutableList.copyOf(jarInputs),
                    ImmutableList.copyOf(dirInputs));
        }
    }

    private static class FakeTransformInput implements TransformInput {

        private final ImmutableList<JarInput> jarInputs;
        private final ImmutableList<DirectoryInput> dirInputs;

        FakeTransformInput(
                ImmutableList<JarInput> jarInputs,
                ImmutableList<DirectoryInput> dirInputs) {
            this.jarInputs = jarInputs;
            this.dirInputs = dirInputs;
        }

        @NonNull
        @Override
        public Collection<JarInput> getJarInputs() {
            return jarInputs;
        }

        @NonNull
        @Override
        public Collection<DirectoryInput> getDirectoryInputs() {
            return dirInputs;
        }
    }

    static class BaseJarInputBuilder<T extends BaseJarInputBuilder> {

        protected Status status = Status.NOTCHANGED;
        protected String name;
        protected File jarFile;
        protected final Set<ContentType> contentTypes = new HashSet<>();
        protected final Set<? super Scope> scopes = new HashSet<>();

        BaseJarInputBuilder(File jarFile) {
            this.jarFile = jarFile;
            this.name = jarFile.getName();
        }

        public T setStatus(Status status) {
            this.status = status;
            return (T) this;
        }

        public T setContentTypes(@NonNull ContentType... types) {
            this.contentTypes.addAll(Arrays.asList(types));
            return (T) this;
        }

        public T setScopes(Scope... scopes) {
            this.scopes.addAll(Arrays.asList(scopes));
            return (T) this;
        }
    }

    static class JarInputBuilder extends BaseJarInputBuilder<JarInputBuilder> {

        JarInputBuilder(File jarFile) {
            super(jarFile);
        }

        JarInput build() {
            return new FakeJarInput(
                    status,
                    name,
                    jarFile,
                    ImmutableSet.copyOf(contentTypes),
                    ImmutableSet.copyOf(scopes));
        }
    }

    static class SingleJarInputBuilder extends BaseJarInputBuilder<SingleJarInputBuilder> {

        SingleJarInputBuilder(File jarFile) {
            super(jarFile);
        }

        TransformInput build() {
            return new FakeTransformInput(
                    ImmutableList.of(new FakeJarInput(
                            status,
                            name,
                            jarFile,
                            ImmutableSet.copyOf(contentTypes),
                            ImmutableSet.copyOf(scopes))),
                    ImmutableList.of());
        }
    }


    private static class FakeJarInput implements JarInput {

        @NonNull
        private final Status status;
        @NonNull
        private final String name;
        @NonNull
        private final File jarFile;
        @NonNull
        private final ImmutableSet<ContentType> contentTypes;
        @NonNull
        private final ImmutableSet<? super Scope> scopes;

        public FakeJarInput(
                @NonNull Status status,
                @NonNull String name,
                @NonNull File jarFile,
                @NonNull ImmutableSet<ContentType> contentTypes,
                @NonNull ImmutableSet<? super Scope> scopes) {
            this.status = status;
            this.name = name;
            this.jarFile = jarFile;
            this.contentTypes = contentTypes;
            this.scopes = scopes;
        }

        @NonNull
        @Override
        public Status getStatus() {
            return status;
        }

        @NonNull
        @Override
        public String getName() {
            return name;
        }

        @NonNull
        @Override
        public File getFile() {
            return jarFile;
        }

        @NonNull
        @Override
        public Set<ContentType> getContentTypes() {
            return contentTypes;
        }

        @NonNull
        @Override
        public Set<? super Scope> getScopes() {
            return scopes;
        }
    }

    private static class FakeDirectoryInput implements DirectoryInput {

        private final String name;
        private final ScopeType scopeType;
        private final File file;
        private final Map<File, Status> changedFiles;

        protected FakeDirectoryInput(
                String name,
                ScopeType scopeType,
                File rootFolder,
                Map<File, Status> changedFiles) {
            this.name = name;
            this.scopeType = scopeType;
            this.file = rootFolder;
            this.changedFiles = changedFiles;
        }

        @NonNull
        @Override
        public String getName() {
            return name;
        }

        @NonNull
        @Override
        public Set<ContentType> getContentTypes() {
            return ImmutableSet.of(ExtendedContentType.DEX);
        }

        @NonNull
        @Override
        public Set<? super Scope> getScopes() {
            return ImmutableSet.of(scopeType);
        }

        @NonNull
        @Override
        public File getFile() {
            return file;
        }

        @NonNull
        @Override
        public Map<File, Status> getChangedFiles() {
            return ImmutableMap.of();
        }
    }
}
