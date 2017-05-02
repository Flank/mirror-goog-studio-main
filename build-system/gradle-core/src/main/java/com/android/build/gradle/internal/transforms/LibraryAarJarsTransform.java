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
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.tasks.annotations.TypedefRemover;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A Transforms that takes the project/project local streams for CLASSES and RESOURCES,
 * and processes and combines them, and put them in the bundle folder.
 *
 * This creates a main jar with the classes from the main scope, and all the java resources, and
 * a set of local jars (which are mostly the same as before except without the resources).
 * This is used to package the AAR.
 *
 * Regarding Streams, this is a no-op transform as it does not write any output to any stream. It
 * uses secondary outputs to write directly into the bundle folder.
 */
public class LibraryAarJarsTransform extends LibraryBaseTransform {

    public LibraryAarJarsTransform(
            @NonNull File mainClassLocation,
            @NonNull File localJarsLocation,
            @Nullable File typedefRecipe,
            @NonNull String packageName,
            boolean packageBuildConfig) {
        super(mainClassLocation, localJarsLocation, typedefRecipe, packageName, packageBuildConfig);
    }

    @NonNull
    @Override
    public String getName() {
        return "syncLibJars";
    }

    @NonNull
    @Override
    public Set<? super Scope> getReferencedScopes() {
        return TransformManager.SCOPE_FULL_LIBRARY_WITH_LOCAL_JARS;
    }

    @Override
    public boolean isIncremental() {
        // TODO make incremental
        return false;
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFileOutputs() {
        return ImmutableList.of(mainClassLocation);
    }

    @Override
    public void transform(@NonNull TransformInvocation invocation)
            throws IOException, TransformException, InterruptedException {
        // non incremental transform, need to clear out outputs.
        // main class jar will get rewritten, just delete local jar folder content.
        if (localJarsLocation != null) {
            FileUtils.deleteDirectoryContents(localJarsLocation);
        }
        if (typedefRecipe != null && !typedefRecipe.exists()) {
            throw new IllegalStateException("Type def recipe not found: " + typedefRecipe);
        }

        List<Pattern> patterns = computeExcludeList();

        // first look for what inputs we have. There shouldn't be that many inputs so it should
        // be quick and it'll allow us to minimize jar merging if we don't have to.
        List<QualifiedContent> mainScope = Lists.newArrayList();
        List<QualifiedContent> localJarScope = Lists.newArrayList();

        for (TransformInput input : invocation.getReferencedInputs()) {
            for (QualifiedContent qualifiedContent : Iterables.concat(
                    input.getJarInputs(), input.getDirectoryInputs())) {
                if (qualifiedContent.getScopes().contains(Scope.PROJECT)) {
                    // even if the scope contains both project + local jar, we treat this as main
                    // scope.
                    mainScope.add(qualifiedContent);
                } else {
                    localJarScope.add(qualifiedContent);
                }
            }
        }

        // process main scope.
        if (mainScope.isEmpty()) {
            throw new RuntimeException("Empty Main scope for " + getName());
        }

        mergeInputsToLocation(
                mainScope,
                mainClassLocation,
                archivePath -> checkEntry(patterns, archivePath),
                typedefRecipe != null ? new TypedefRemover().setTypedefFile(typedefRecipe) : null);

        // process local scope
        FileUtils.deleteDirectoryContents(localJarsLocation);
        processLocalJars(localJarScope);
    }
}
