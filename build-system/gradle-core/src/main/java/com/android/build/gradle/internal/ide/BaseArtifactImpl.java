/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.internal.ide;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.model.BaseArtifact;
import com.android.builder.model.Dependencies;
import com.android.builder.model.SourceProvider;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Objects;

/**
 * Implementation of BaseArtifact that is serializable
 */
@Immutable
abstract class BaseArtifactImpl implements BaseArtifact, Serializable {

    @NonNull
    protected final Collection<File> generatedSourceFolders;

    private final String name;
    @NonNull
    private final String assembleTaskName;
    @NonNull
    private final String compileTaskName;
    @NonNull
    private final File classesFolder;
    @NonNull
    private final File javaResourcesFolder;
    @NonNull
    private final Dependencies compileDependencies;
    @NonNull
    private final Dependencies packageDependencies;
    @Nullable
    private final SourceProvider variantSourceProvider;
    @Nullable
    private final SourceProvider multiFlavorSourceProviders;


    BaseArtifactImpl(@NonNull String name,
            @NonNull String assembleTaskName,
            @NonNull String compileTaskName,
            @NonNull File classesFolder,
            @NonNull File javaResourcesFolder,
            @NonNull Dependencies compileDependencies,
            @NonNull Dependencies packageDependencies,
            @Nullable SourceProvider variantSourceProvider,
            @Nullable SourceProvider multiFlavorSourceProviders,
            @NonNull Collection<File> generatedSourceFolders) {
        this.name = name;
        this.assembleTaskName = assembleTaskName;
        this.compileTaskName = compileTaskName;
        this.classesFolder = classesFolder;
        this.javaResourcesFolder = javaResourcesFolder;
        this.compileDependencies = compileDependencies;
        this.packageDependencies = packageDependencies;
        this.variantSourceProvider = variantSourceProvider;
        this.multiFlavorSourceProviders = multiFlavorSourceProviders;
        this.generatedSourceFolders = generatedSourceFolders;
    }

    @NonNull
    @Override
    public String getName() {
        return name;
    }

    @NonNull
    @Override
    public String getCompileTaskName() {
        return compileTaskName;
    }

    @NonNull
    @Override
    public String getAssembleTaskName() {
        return assembleTaskName;
    }

    @NonNull
    @Override
    public File getClassesFolder() {
        return classesFolder;
    }

    @NonNull
    @Override
    public File getJavaResourcesFolder() {
        return javaResourcesFolder;
    }

    @NonNull
    @Override
    @Deprecated
    public Dependencies getDependencies() {
        return compileDependencies;
    }

    @NonNull
    @Override
    public Dependencies getCompileDependencies() {
        return compileDependencies;
    }

    @NonNull
    @Override
    public Dependencies getPackageDependencies() {
        return packageDependencies;
    }

    @Nullable
    @Override
    public SourceProvider getVariantSourceProvider() {
        return variantSourceProvider;
    }

    @Nullable
    @Override
    public SourceProvider getMultiFlavorSourceProvider() {
        return multiFlavorSourceProviders;
    }

    @NonNull
    @Override
    public Collection<File> getGeneratedSourceFolders() {
        return generatedSourceFolders;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BaseArtifactImpl that = (BaseArtifactImpl) o;
        return Objects.equals(generatedSourceFolders, that.generatedSourceFolders) &&
                Objects.equals(name, that.name) &&
                Objects.equals(assembleTaskName, that.assembleTaskName) &&
                Objects.equals(compileTaskName, that.compileTaskName) &&
                Objects.equals(classesFolder, that.classesFolder) &&
                Objects.equals(javaResourcesFolder, that.javaResourcesFolder) &&
                Objects.equals(compileDependencies, that.compileDependencies) &&
                Objects.equals(packageDependencies, that.packageDependencies) &&
                Objects.equals(variantSourceProvider, that.variantSourceProvider) &&
                Objects.equals(multiFlavorSourceProviders, that.multiFlavorSourceProviders);
    }

    @Override
    public int hashCode() {
        return Objects
                .hash(generatedSourceFolders, name, assembleTaskName, compileTaskName,
                        classesFolder,
                        javaResourcesFolder, compileDependencies, packageDependencies,
                        variantSourceProvider, multiFlavorSourceProviders);
    }
}
