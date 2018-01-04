/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.builder.desugaring.DesugaringClassAnalyzer.analyze;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.builder.desugaring.DesugaringClassAnalyzer;
import com.android.builder.desugaring.DesugaringData;
import com.android.builder.desugaring.DesugaringGraph;
import com.android.builder.desugaring.DesugaringGraphs;
import com.android.ide.common.internal.WaitableExecutor;
import com.google.common.base.Stopwatch;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * This helper analyzes the transform inputs, updates the {@link DesugaringGraph} it owns, and its
 * main goal is to provide paths that should also be also considered out of date, in addition to the
 * changed files. See {@link #getAdditionalPaths()} for details.
 */
class DesugarIncrementalTransformHelper {

    @NonNull
    private static final LoggerWrapper logger =
            LoggerWrapper.getLogger(DesugarIncrementalTransformHelper.class);

    @NonNull private final String projectVariant;
    @NonNull private final TransformInvocation invocation;
    @NonNull private final WaitableExecutor executor;

    @NonNull
    private final Supplier<Set<Path>> changedPaths = Suppliers.memoize(this::findChangedPaths);

    @NonNull private final Supplier<DesugaringGraph> desugaringGraph;

    DesugarIncrementalTransformHelper(
            @NonNull String projectVariant,
            @NonNull TransformInvocation invocation,
            @NonNull WaitableExecutor executor) {
        this.projectVariant = projectVariant;
        this.invocation = invocation;
        this.executor = executor;
        DesugaringGraph graph;
        if (!invocation.isIncremental()) {
            DesugaringGraphs.invalidate(projectVariant);
            graph = null;
        } else {
            graph =
                    DesugaringGraphs.updateVariant(
                            projectVariant, () -> getIncrementalData(changedPaths, executor));
        }
        desugaringGraph =
                graph != null ? () -> graph : Suppliers.memoize(this::makeDesugaringGraph);
    }

    /**
     * Get the list of paths that should be re-desugared, and update the dependency graph.
     *
     * <p>For full builds, graph will be invalidated. No additional paths to process are returned,
     * as all inputs are considered out-of-date, and will be re-processed.
     *
     * <p>In incremental builds, graph will be initialized (if not already), or updated
     * incrementally. Once it has been populated, set of changed files is analyzed, and all
     * impacted, non-changed, paths will be returned as a result.
     */
    @NonNull
    Set<Path> getAdditionalPaths() throws InterruptedException {
        if (!invocation.isIncremental()) {
            return ImmutableSet.of();
        }

        Stopwatch stopwatch = Stopwatch.createStarted();
        logger.verbose("Desugaring dependencies incrementally.");

        Set<Path> additionalPaths = new HashSet<>();
        for (Path changed : changedPaths.get()) {
            for (Path path : desugaringGraph.get().getDependentPaths(changed)) {
                if (!changedPaths.get().contains(path)) {
                    additionalPaths.add(path);
                }
            }
        }

        logger.verbose(
                "Time to calculate desugaring dependencies: %d",
                stopwatch.elapsed(TimeUnit.MILLISECONDS));
        logger.verbose("Additional paths to desugar: %s", additionalPaths.toString());
        return additionalPaths;
    }

    @NonNull
    private DesugaringGraph makeDesugaringGraph() {
        if (!invocation.isIncremental()) {
            // Rebuild totally the graph whatever the cache status
            return DesugaringGraphs.forVariant(
                    projectVariant, getInitalGraphData(invocation, executor));
        }
        return DesugaringGraphs.forVariant(
                projectVariant,
                () -> getInitalGraphData(invocation, executor),
                () -> getIncrementalData(changedPaths, executor));
    }

    @NonNull
    private static Collection<DesugaringData> getInitalGraphData(
            @NonNull TransformInvocation invocation, @NonNull WaitableExecutor executor) {
        Set<DesugaringData> data = Sets.newConcurrentHashSet();
        for (TransformInput input : getAllInputs(invocation)) {
            for (QualifiedContent qualifiedContent :
                    Iterables.concat(input.getDirectoryInputs(), input.getJarInputs())) {
                executor.execute(
                        () -> {
                            Path toProcess = qualifiedContent.getFile().toPath();
                            try {
                                if (Files.exists(toProcess)) {
                                    data.addAll(analyze(toProcess));
                                }
                                return null;
                            } catch (Throwable t) {
                                logger.error(t, "error processing %s", toProcess);
                                throw t;
                            }
                        });
            }
        }

        try {
            executor.waitForTasksWithQuickFail(true);
        } catch (InterruptedException e) {
            throw new RuntimeException("Unable to get desugaring graph", e);
        }

        return data;
    }

    @NonNull
    private static Set<DesugaringData> getIncrementalData(
            @NonNull Supplier<Set<Path>> changedPaths, @NonNull WaitableExecutor executor) {
        Set<DesugaringData> data = Sets.newConcurrentHashSet();
        for (Path input : changedPaths.get()) {
            if (Files.notExists(input)) {
                data.add(DesugaringClassAnalyzer.forRemoved(input));
            } else {
                executor.execute(
                        () -> {
                            try {
                                data.addAll(analyze(input));
                                return null;
                            } catch (Throwable t) {
                                logger.error(t, "error processing %s", input);
                                throw t;
                            }
                        });
            }
        }

        try {
            executor.waitForTasksWithQuickFail(true);
        } catch (InterruptedException e) {
            throw new RuntimeException("Unable to get desugaring graph", e);
        }
        return data;
    }

    @NonNull
    private Set<Path> findChangedPaths() {
        Set<Path> changedPaths = Sets.newHashSet();
        for (TransformInput input : getAllInputs(invocation)) {
            for (DirectoryInput dirInput : input.getDirectoryInputs()) {
                Map<Status, Set<File>> byStatus = TransformInputUtil.getByStatus(dirInput);
                for (File modifiedFile :
                        Iterables.concat(
                                byStatus.get(Status.CHANGED),
                                byStatus.get(Status.REMOVED),
                                byStatus.get(Status.ADDED))) {
                    if (modifiedFile.toString().endsWith(SdkConstants.DOT_CLASS)) {
                        changedPaths.add(modifiedFile.toPath());
                    }
                }
            }

            for (JarInput jarInput : input.getJarInputs()) {
                if (jarInput.getStatus() != Status.NOTCHANGED) {
                    changedPaths.add(jarInput.getFile().toPath());
                }
            }
        }
        return changedPaths;
    }

    @NonNull
    private static Iterable<TransformInput> getAllInputs(@NonNull TransformInvocation invocation) {
        return Iterables.concat(invocation.getInputs(), invocation.getReferencedInputs());
    }

    public Set<Path> getDependenciesPaths(Path path) {
        return desugaringGraph.get().getDependenciesPaths(path);
    }
}
