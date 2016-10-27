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

package com.android.builder.profile;

import static com.android.builder.profile.MemoryStats.getCurrentProperties;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.analytics.CommonMetricsData;
import com.android.tools.analytics.UsageTracker;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Maps;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.GradleBuildMemorySample;
import com.google.wireless.android.sdk.stats.GradleBuildProfile;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan;
import com.google.wireless.android.sdk.stats.GradleBuildProject;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Records all the {@link GradleBuildProfileSpan}s for a process, in order it was received. */
public class ProcessRecorder {

    private final GradleBuildMemorySample mStartMemoryStats;

    private final NameAnonymizer mNameAnonymizer;

    private final GradleBuildProfile.Builder mBuild;

    private final LoadingCache<String, Project> mProjects;

    @NonNull private final Path mBenchmarkProfileOutputFile;

    private static final AtomicLong lastRecordId = new AtomicLong(1);

    static long allocateRecordId() {
        return lastRecordId.incrementAndGet();
    }

    @VisibleForTesting
    static void resetForTests() {
        lastRecordId.set(1);
    }

    @NonNull
    static ProcessRecorder get() {
        return ProcessRecorderFactory.sINSTANCE.get();
    }


    ProcessRecorder(@NonNull Path benchmarkProfileOutputFile) {
        mBenchmarkProfileOutputFile = benchmarkProfileOutputFile;
        mNameAnonymizer = new NameAnonymizer();
        mBuild = GradleBuildProfile.newBuilder();
        mStartMemoryStats = createAndRecordMemorySample();
        mProjects = CacheBuilder.newBuilder().build(new ProjectCacheLoader(mNameAnonymizer));
    }

    void writeRecord(
            @NonNull String project,
            @Nullable String variant,
            @NonNull final GradleBuildProfileSpan.Builder executionRecord) {

        executionRecord.setProject(mNameAnonymizer.anonymizeProjectPath(project));
        executionRecord.setVariant(mNameAnonymizer.anonymizeVariant(project, variant));

        mBuild.addSpan(executionRecord.build());
    }

    /**
     * Done with the recording processing, finish processing the outstanding {@link
     * GradleBuildProfileSpan} publication and shutdowns the processing queue.
     */
    void finish() throws InterruptedException {
        GradleBuildMemorySample memoryStats = createAndRecordMemorySample();
        mBuild.setBuildTime(
                memoryStats.getTimestamp() - mStartMemoryStats.getTimestamp());
        mBuild.setGcCount(
                memoryStats.getGcCount() - mStartMemoryStats.getGcCount());
        mBuild.setGcTime(
                memoryStats.getGcTimeMs() - mStartMemoryStats.getGcTimeMs());

        for (Project project : mProjects.asMap().values()) {
            for (GradleBuildVariant.Builder variant : project.variants.values()) {
                project.properties.addVariant(variant);
            }
            if (project.properties != null) {
                mBuild.addProject(project.properties);
            }
        }

        // Write benchmark file into build directory.
        try {
            Files.createDirectories(mBenchmarkProfileOutputFile.getParent());
            try (BufferedOutputStream outputStream =
                    new BufferedOutputStream(
                            Files.newOutputStream(
                                    mBenchmarkProfileOutputFile, StandardOpenOption.CREATE_NEW))) {
                mBuild.build().writeTo(outputStream);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        // Public build profile.
        UsageTracker.getInstance()
                .log(
                        AndroidStudioEvent.newBuilder()
                                .setCategory(AndroidStudioEvent.EventCategory.GRADLE)
                                .setKind(AndroidStudioEvent.EventKind.GRADLE_BUILD_PROFILE)
                                .setGradleBuildProfile(mBuild.build())
                                .setJavaProcessStats(CommonMetricsData.getJavaProcessStats())
                                .setJvmDetails(CommonMetricsData.getJvmDetails()));

    }

    /** Properties and statistics global to this build invocation. */
    @NonNull
    public static GradleBuildProfile.Builder getGlobalProperties() {
        return get().getProperties();
    }

    @NonNull
    GradleBuildProfile.Builder getProperties() {
        return mBuild;
    }

    @NonNull
    public static GradleBuildProject.Builder getProject(@NonNull String projectPath) {
        return get().mProjects.getUnchecked(projectPath).properties;
    }

    public static GradleBuildVariant.Builder addVariant(
            @NonNull String projectPath, @NonNull String variantName) {
        GradleBuildVariant.Builder properties = GradleBuildVariant.newBuilder();
        get().addVariant(projectPath, variantName, properties);
        return properties;
    }

    private void addVariant(
            @NonNull String projectPath,
            @NonNull String variantName,
            @NonNull GradleBuildVariant.Builder properties) {
        Project project = mProjects.getUnchecked(projectPath);
        properties.setId(mNameAnonymizer.anonymizeVariant(projectPath, variantName));
        project.variants.put(variantName, properties);
    }

    private GradleBuildMemorySample createAndRecordMemorySample() {
        GradleBuildMemorySample stats = getCurrentProperties();
        if (stats != null) {
            mBuild.addMemorySample(stats);
        }
        return stats;
    }

    public static void recordMemorySample() {
        get().createAndRecordMemorySample();
    }

    private static class ProjectCacheLoader extends CacheLoader<String, Project> {

        @NonNull
        private final NameAnonymizer mNameAnonymizer;

        ProjectCacheLoader(@NonNull NameAnonymizer nameAnonymizer) {
            mNameAnonymizer = nameAnonymizer;
        }

        @Override
        public Project load(@NonNull String name) throws Exception {
            return new Project(mNameAnonymizer.anonymizeProjectPath(name));
        }
    }

    private static class Project {

        Project(long id) {
            properties = GradleBuildProject.newBuilder();
            properties.setId(id);
        }

        final Map<String, GradleBuildVariant.Builder> variants = Maps.newConcurrentMap();
        final GradleBuildProject.Builder properties;
    }

}
