/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.integration.common.fixture;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ModelBuilderParameter;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeVariantAbi;
import com.android.builder.model.NativeVariantInfo;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.GlobalLibraryMap;
import com.android.utils.Pair;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;

/**
 * a Build Action that returns all the models of the parameterized type for all the Gradle projects
 */
public class GetAndroidModelAction<T> implements BuildAction<ModelContainer<T>> {

    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();

    private final Class<T> type;

    private final boolean shouldGenerateSources;

    // Determines whether models are fetched with multiple threads.
    private final boolean isMultiThreaded;

    public GetAndroidModelAction(Class<T> type) {
        this(type, true);
    }

    public GetAndroidModelAction(Class<T> type, boolean isMultiThreaded) {
        this(type, isMultiThreaded, false);
    }

    public GetAndroidModelAction(
            Class<T> type, boolean isMultiThreaded, boolean shouldGenerateSource) {
        this.type = type;
        // parallelization hit a change in Gradle 3.2 which makes it not work.
        this.isMultiThreaded = false; // isMultiThreaded;
        this.shouldGenerateSources = shouldGenerateSource;
    }

    @Override
    public ModelContainer<T> execute(BuildController buildController) {

        long t1 = System.currentTimeMillis();

        // accumulate pairs of (build Id, project) to query.
        List<Pair<BuildIdentifier, BasicGradleProject>> projects = Lists.newArrayList();

        GradleBuild rootBuild = buildController.getBuildModel();
        BuildIdentifier rootBuildId = rootBuild.getBuildIdentifier();

        // add the root project.
        for (BasicGradleProject gradleProject : rootBuild.getProjects()) {
            projects.add(Pair.of(rootBuildId, gradleProject));
        }

        // and the included builds
        for (GradleBuild gradleBuild : rootBuild.getIncludedBuilds()) {
            BuildIdentifier buildId = gradleBuild.getBuildIdentifier();
            for (BasicGradleProject basicGradleProject : gradleBuild.getProjects()) {
                projects.add(Pair.of(buildId, basicGradleProject));
            }
        }

        final int projectCount = projects.size();
        Map<BuildIdentifier, Map<String, T>> modelMap =
                Maps.newHashMapWithExpectedSize(projectCount);

        List<Thread> threads = Lists.newArrayListWithCapacity(CPU_COUNT);
        List<ModelQuery> queries = Lists.newArrayListWithCapacity(CPU_COUNT);

        if (isMultiThreaded) {
            for (int i = 0; i < CPU_COUNT; i++) {
                ModelQuery modelQuery = new ModelQuery(projects, buildController);
                queries.add(modelQuery);
                Thread t = new Thread(modelQuery);
                threads.add(t);
                t.start();
            }

            for (int i = 0; i < CPU_COUNT; i++) {
                try {
                    threads.get(i).join();
                    ModelQuery modelQuery = queries.get(i);
                    mergeMap(modelMap, modelQuery.getModelMaps());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } else {
            ModelQuery modelQuery = new ModelQuery(projects, buildController);
            modelQuery.run();
            modelMap.putAll(modelQuery.getModelMaps());
        }

        GlobalLibraryMap globalLibraryMap = null;
        if (type == AndroidProject.class) {
            globalLibraryMap = getGlobalLibraryMap(buildController, projects);
        }

        long t2 = System.currentTimeMillis();
        System.out.println("GetAndroidModelAction: " + (t2 - t1) + "ms");

        return new ModelContainer<>(rootBuildId, modelMap, globalLibraryMap);
    }

    private void mergeMap(
            @NonNull Map<BuildIdentifier, Map<String, T>> to,
            @NonNull Map<BuildIdentifier, Map<String, T>> from) {
        for (Map.Entry<BuildIdentifier, Map<String, T>> entry : from.entrySet()) {
            if (to.containsKey(entry.getKey())) {
                Map<String, T> map = to.get(entry.getKey());
                map.putAll(entry.getValue());
            } else {
                to.put(entry.getKey(), entry.getValue());
            }
        }
    }

    @NonNull
    private static GlobalLibraryMap getGlobalLibraryMap(
            BuildController buildController,
            List<Pair<BuildIdentifier, BasicGradleProject>> projects) {
        return projects.stream()
                .map(Pair::getSecond)
                .peek(System.out::println)
                .map(project -> buildController.findModel(project, GlobalLibraryMap.class))
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No GlobalLibraryMap model found."));
    }

    // index used by threads to get the new project to query.
    private volatile int currentIndex = 0;

    protected synchronized int getNextIndex() {
        return currentIndex++;
    }

    class ModelQuery implements Runnable {

        @NonNull private final Map<BuildIdentifier, Map<String, T>> models;
        @NonNull private final List<Pair<BuildIdentifier, BasicGradleProject>> projects;
        @NonNull private final BuildController buildController;

        public ModelQuery(
                @NonNull List<Pair<BuildIdentifier, BasicGradleProject>> projects,
                @NonNull BuildController buildController) {
            this.projects = projects;
            this.buildController = buildController;

            models = Maps.newHashMapWithExpectedSize(projects.size() / CPU_COUNT);
        }

        @NonNull
        public Map<BuildIdentifier, Map<String, T>> getModelMaps() {
            return models;
        }

        @Override
        public void run() {
            final int count = projects.size();

            int index;
            while ((index = getNextIndex()) < count) {
                Pair<BuildIdentifier, BasicGradleProject> pair = projects.get(index);
                BasicGradleProject project = pair.getSecond();
                T model;
                if (type != ParameterizedAndroidProject.class) {
                    model = buildController.findModel(project, type);
                } else {
                    //noinspection unchecked
                    model = (T) getParameterizedAndroidProject(project);
                }
                if (model != null) {
                    Map<String, T> perBuildMap =
                            models.computeIfAbsent(pair.getFirst(), id -> new HashMap<>());
                    perBuildMap.put(project.getPath(), model);
                }
            }
        }

        @Nullable
        private ParameterizedAndroidProject getParameterizedAndroidProject(
                @NonNull BasicGradleProject project) {
            AndroidProject androidProject =
                    buildController.findModel(
                            project,
                            AndroidProject.class,
                            ModelBuilderParameter.class,
                            p -> p.setShouldBuildVariant(false));
            if (androidProject != null) {
                NativeAndroidProject nativeAndroidProject =
                        buildController.findModel(
                                project,
                                NativeAndroidProject.class,
                                ModelBuilderParameter.class,
                                p -> p.setShouldBuildVariant(false));
                List<Variant> variants = new ArrayList<>();
                List<NativeVariantAbi> nativeVariantAbis = new ArrayList<>();
                for (String variantName : androidProject.getVariantNames()) {
                    Variant variant =
                            buildController.findModel(
                                    project,
                                    Variant.class,
                                    ModelBuilderParameter.class,
                                    p -> {
                                        p.setVariantName(variantName);
                                        p.setShouldGenerateSources(shouldGenerateSources);
                                    });
                    if (variant != null) {
                        variants.add(variant);
                        if (nativeAndroidProject != null) {
                            NativeVariantInfo variantinfo =
                                    nativeAndroidProject.getVariantInfos().get(variantName);
                            assert variantinfo
                                    != null; // This should exist if the variant exists in AndroidProject
                            for (String abi : variantinfo.getAbiNames()) {
                                NativeVariantAbi nativeVariantAbi =
                                        buildController.findModel(
                                                project,
                                                NativeVariantAbi.class,
                                                ModelBuilderParameter.class,
                                                p -> {
                                                    p.setVariantName(variantName);
                                                    p.setAbiName(abi);
                                                });
                                if (nativeVariantAbi != null) {
                                    nativeVariantAbis.add(nativeVariantAbi);
                                }
                            }
                        }
                    }
                }
                return new ParameterizedAndroidProject(
                        androidProject, variants, nativeAndroidProject, nativeVariantAbis);
            }
            return null;
        }
    }
}
