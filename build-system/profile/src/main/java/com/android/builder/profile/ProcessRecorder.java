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
import com.android.tools.analytics.UsageTracker;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Maps;
import com.google.wireless.android.sdk.stats.AndroidStudioStats;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Records all the {@link AndroidStudioStats.GradleBuildProfileSpan}s for a process, in order it was
 * received.
 */
public class ProcessRecorder {

    private final AndroidStudioStats.GradleBuildMemorySample mStartMemoryStats;

    private final NameAnonymizer mNameAnonymizer;

    private final AndroidStudioStats.GradleBuildProfile.Builder mBuild;

    private final LoadingCache<String, Project> mProjects;

    @Nullable
    private final Path mBenchmarkProfileOutputFile;

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


    ProcessRecorder(@Nullable Path benchmarkProfileOutputFile) {
        mBenchmarkProfileOutputFile = benchmarkProfileOutputFile;
        mNameAnonymizer = new NameAnonymizer();
        mBuild = AndroidStudioStats.GradleBuildProfile.newBuilder();
        mStartMemoryStats = createAndRecordMemorySample();
        mProjects = CacheBuilder.newBuilder().build(new ProjectCacheLoader(mNameAnonymizer));
    }

    void writeRecord(
            @NonNull String project,
            @Nullable String variant,
            @NonNull final AndroidStudioStats.GradleBuildProfileSpan.Builder executionRecord) {

        executionRecord.setProject(mNameAnonymizer.anonymizeProjectName(project));
        executionRecord.setVariant(mNameAnonymizer.anonymizeVariant(project, variant));

        mBuild.addSpan(executionRecord.build());
    }

    /**
     * Done with the recording processing, finish processing the outstanding {@link
     * AndroidStudioStats.GradleBuildProfileSpan} publication and shutdowns the processing queue.
     */
    void finish() throws InterruptedException {
        AndroidStudioStats.GradleBuildMemorySample memoryStats =
                createAndRecordMemorySample();
        mBuild.setBuildTime(
                memoryStats.getTimestamp() - mStartMemoryStats.getTimestamp());
        mBuild.setGcCount(
                memoryStats.getGcCount() - mStartMemoryStats.getGcCount());
        mBuild.setGcTime(
                memoryStats.getGcTimeMs() - mStartMemoryStats.getGcTimeMs());

        for (Project project : mProjects.asMap().values()) {
            for (AndroidStudioStats.GradleBuildVariant.Builder variant :
                    project.variants.values()) {
                project.properties.addVariant(variant);
            }
            if (project.properties != null) {
                mBuild.addProject(project.properties);
            }
        }

        if (mBenchmarkProfileOutputFile != null) {
            // Internal benchmark. This code path is only invoked in tests.
            try (BufferedOutputStream outputStream =
                         new BufferedOutputStream(
                                 Files.newOutputStream(
                                         mBenchmarkProfileOutputFile,
                                         StandardOpenOption.CREATE_NEW))) {
                mBuild.build().writeTo(outputStream);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

        } else {
            // Public build profile.
            AndroidStudioStats.AndroidStudioEvent.Builder studioStats =
                    AndroidStudioStats.AndroidStudioEvent.newBuilder();
            studioStats.setCategory(
                    AndroidStudioStats.AndroidStudioEvent.EventCategory.GRADLE);
            studioStats.setKind(
                    AndroidStudioStats.AndroidStudioEvent.EventKind.GRADLE_BUILD_PROFILE);
            studioStats.setGradleBuildProfile(mBuild.build());

            UsageTracker.getInstance().log(studioStats);
        }
    }

    /**
     * Properties and statistics global to this build invocation.
     */
    @NonNull
    public static AndroidStudioStats.GradleBuildProfile.Builder getGlobalProperties() {
        return get().getProperties();
    }

    @NonNull
    AndroidStudioStats.GradleBuildProfile.Builder getProperties() {
        return mBuild;
    }

    @NonNull
    public static AndroidStudioStats.GradleBuildProject.Builder getProject(
            @NonNull String projectPath) {
        return get().mProjects.getUnchecked(projectPath).properties;
    }

    public static AndroidStudioStats.GradleBuildVariant.Builder addVariant(
            @NonNull String projectPath,
            @NonNull String variantName) {
        AndroidStudioStats.GradleBuildVariant.Builder properties =
                AndroidStudioStats.GradleBuildVariant.newBuilder();
        get().addVariant(projectPath, variantName, properties);
        return properties;
    }

    private void addVariant(@NonNull String projectPath, @NonNull String variantName,
            @NonNull AndroidStudioStats.GradleBuildVariant.Builder properties) {
        Project project = mProjects.getUnchecked(projectPath);
        properties.setId(mNameAnonymizer.anonymizeVariant(projectPath, variantName));
        project.variants.put(variantName, properties);
    }

    private AndroidStudioStats.GradleBuildMemorySample createAndRecordMemorySample() {
        AndroidStudioStats.GradleBuildMemorySample stats = getCurrentProperties();
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
            return new Project(mNameAnonymizer.anonymizeProjectName(name));
        }
    }

    private static class Project {

        Project(long id) {
            properties = AndroidStudioStats.GradleBuildProject.newBuilder();
            properties.setId(id);
        }

        final Map<String, AndroidStudioStats.GradleBuildVariant.Builder> variants =
                Maps.newConcurrentMap();
        final AndroidStudioStats.GradleBuildProject.Builder properties;
    }

}
