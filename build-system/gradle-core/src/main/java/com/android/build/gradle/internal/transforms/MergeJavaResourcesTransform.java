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

package com.android.build.gradle.internal.transforms;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.packaging.ParsedPackagingOptions;
import com.android.build.gradle.internal.pipeline.IncrementalFileMergerTransformUtils;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.MergeJavaResourcesDelegate;
import com.android.builder.files.FileCacheByPath;
import com.android.builder.merge.IncrementalFileMergerInput;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Transform to merge all the Java resources.
 *
 * Based on the value of {@link #getInputTypes()} this will either process native libraries
 * or java resources. While native libraries inside jars are technically java resources, they
 * must be handled separately.
 */
public class MergeJavaResourcesTransform extends Transform {

    @NonNull private final PackagingOptions packagingOptions;

    @NonNull
    private final String name;

    @NonNull private final Set<? super Scope> mergeScopes;
    @NonNull
    private final Set<ContentType> mergedType;

    @NonNull
    private final File intermediateDir;

    @NonNull private final File cacheDir;

    public MergeJavaResourcesTransform(
            @NonNull PackagingOptions packagingOptions,
            @NonNull Set<? super Scope> mergeScopes,
            @NonNull ContentType mergedType,
            @NonNull String name,
            @NonNull VariantScope variantScope) {
        this.packagingOptions = packagingOptions;
        this.name = name;
        this.mergeScopes = ImmutableSet.copyOf(mergeScopes);
        this.mergedType = ImmutableSet.of(mergedType);
        this.intermediateDir = variantScope.getIncrementalDir(
                variantScope.getFullVariantName() + "-" + name);

        cacheDir = new File(intermediateDir, "zip-cache");
    }

    @NonNull
    @Override
    public String getName() {
        return name;
    }

    @NonNull
    @Override
    public Set<ContentType> getInputTypes() {
        return mergedType;
    }

    @NonNull
    @Override
    public Set<? super Scope> getScopes() {
        return mergeScopes;
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryDirectoryOutputs() {
        return ImmutableList.of(cacheDir);
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        return ImmutableMap.of(
                "exclude", packagingOptions.getExcludes(),
                "pickFirst", packagingOptions.getPickFirsts(),
                "merge", packagingOptions.getMerges());
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    /**
     * Obtains the file where incremental state is saved.
     *
     * @return the file, may not exist
     */
    @NonNull
    private File incrementalStateFile() {
        return new File(intermediateDir, "merge-state");
    }

    @Override
    public void transform(@NonNull TransformInvocation invocation)
            throws IOException, TransformException {
        FileUtils.mkdirs(cacheDir);
        FileCacheByPath zipCache = new FileCacheByPath(cacheDir);

        TransformOutputProvider outputProvider = invocation.getOutputProvider();
        checkNotNull(outputProvider, "Missing output object for transform " + getName());

        ParsedPackagingOptions parsedPackagingOptions =
                new ParsedPackagingOptions(packagingOptions);

        boolean full = false;
        if (!incrementalStateFile().isFile() || !invocation.isIncremental()) {
            /*
             * This is a full build.
             */
            outputProvider.deleteAll();
            full = true;
        }

        ContentType singleMergedType = Iterables.getOnlyElement(mergedType);

        final Format outputFormat =
                singleMergedType == QualifiedContent.DefaultContentType.RESOURCES
                        ? Format.JAR
                        : Format.DIRECTORY;
        final File outputLocation =
                outputProvider.getContentLocation(
                        "resources", getOutputTypes(), getScopes(), outputFormat);

        List<Runnable> cacheUpdates = new ArrayList<>();

        Map<IncrementalFileMergerInput, QualifiedContent> contentMap = new HashMap<>();
        List<IncrementalFileMergerInput> inputs =
                new ArrayList<>(
                        IncrementalFileMergerTransformUtils.toInput(
                                invocation,
                                zipCache,
                                cacheUpdates,
                                full,
                                contentMap));

        MergeJavaResourcesDelegate delegate =
                new MergeJavaResourcesDelegate(
                        inputs,
                        outputLocation,
                        contentMap,
                        parsedPackagingOptions,
                        singleMergedType,
                        incrementalStateFile(),
                        invocation.isIncremental() && isIncremental());

        delegate.run();

        cacheUpdates.forEach(Runnable::run);
    }
}