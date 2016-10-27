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

package com.android.build.gradle.internal.profile;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.profile.AsyncRecorder;
import com.android.builder.profile.ProcessRecorderFactory;
import com.android.builder.profile.Recorder;
import com.android.builder.profile.ThreadRecorder;
import com.android.utils.ILogger;
import com.google.common.base.Joiner;
import com.google.common.jimfs.Jimfs;
import com.google.wireless.android.sdk.stats.GradleBuildProfile;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan.ExecutionType;
import com.google.wireless.android.sdk.stats.GradleTaskExecution;
import com.google.wireless.android.sdk.stats.GradleTransformExecution;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskState;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link RecordingBuildListener}
 */
public class RecordingBuildListenerTest {

    @Mock
    Task mTask;

    @Mock
    Task mSecondTask;

    @Mock
    TaskState mTaskState;

    @Mock
    Project mProject;

    @Mock
    ILogger logger;

    private Path mProfileProtoFile;

    private static final class TestRecorder implements Recorder {

        final AtomicLong recordId = new AtomicLong(0);
        final List<GradleBuildProfileSpan> records = new CopyOnWriteArrayList<>();

        @Override
        public <T> T record(
                @NonNull ExecutionType executionType,
                @NonNull String projectPath,
                String variant,
                @NonNull Block<T> block) {
            throw new UnsupportedOperationException("record method was not supposed to be called.");
        }

        @Nullable
        @Override
        public <T> T record(
                @NonNull ExecutionType executionType,
                @Nullable GradleTransformExecution transform,
                @NonNull String projectPath,
                @Nullable String variant,
                @NonNull Block<T> block) {
            throw new UnsupportedOperationException("record method was not supposed to be called");
        }

        @Override
        public long allocationRecordId() {
            return recordId.incrementAndGet();
        }

        @Override
        public void closeRecord(
                @NonNull String project,
                @Nullable String variant,
                @NonNull GradleBuildProfileSpan.Builder executionRecord) {
            if (project.equals(":projectName")) {
                executionRecord.setProject(1);
            }
            if ("variantName".equals(variant)) {
                executionRecord.setVariant(1);
            }

            records.add(executionRecord.build());
        }
    }


    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mProject.getPath()).thenReturn(":projectName");
        when(mTask.getName()).thenThrow(new AssertionError("Nothing should be using task name"));
        when(mTask.getPath()).thenReturn(":projectName:taskName");
        when(mTask.getProject()).thenReturn(mProject);
        when(mSecondTask.getPath()).thenReturn(":projectName:task2Name");
        when(mSecondTask.getName())
                .thenThrow(new AssertionError("Nothing should be using task name"));
        when(mSecondTask.getProject()).thenReturn(mProject);
        mProfileProtoFile = Jimfs.newFileSystem().getPath("/tmp/profile_proto.rawproto");
        ProcessRecorderFactory.initializeForTests(mProfileProtoFile);
    }

    @Test
    public void singleThreadInvocation() {
        TestRecorder recorder = new TestRecorder();
        RecordingBuildListener listener = new RecordingBuildListener(recorder);

        listener.beforeExecute(mTask);
        listener.afterExecute(mTask, mTaskState);
        assertEquals(1, recorder.records.size());
        GradleBuildProfileSpan record = recorder.records.get(0);
        assertEquals(1, record.getId());
        assertEquals(0, record.getParentId());
    }

    @Test
    public void singleThreadWithMultipleSpansInvocation() throws InterruptedException, IOException {

        RecordingBuildListener listener =
                new RecordingBuildListener(ThreadRecorder.get());

        listener.beforeExecute(mTask);
        ThreadRecorder.get().record(ExecutionType.SOME_RANDOM_PROCESSING,
                ":projectName", null, new Recorder.Block<Void>() {
                    @Override
                    public Void call() throws Exception {
                        Logger.getAnonymousLogger().finest("useless block");
                        return null;
                    }
                }); {
        }
        listener.afterExecute(mTask, mTaskState);
        ProcessRecorderFactory.shutdown();

        GradleBuildProfile profile = loadProfile();
        assertEquals("Span count", 2, profile.getSpanCount());

        GradleBuildProfileSpan record = getRecordForId(profile.getSpanList(), 2);
        assertEquals(0, record.getParentId());

        record = getRecordForId(profile.getSpanList(), 3);
        assertNotNull(record);
        assertEquals(2, record.getParentId());
        assertEquals(ExecutionType.SOME_RANDOM_PROCESSING, record.getType());
    }



    @Test
    public void simulateTasksUnorderedLifecycleEventsDelivery()
            throws InterruptedException, IOException {

        RecordingBuildListener listener =
                new RecordingBuildListener(AsyncRecorder.get());

        listener.beforeExecute(mTask);
        listener.beforeExecute(mSecondTask);
        ThreadRecorder.get()
                .record(
                        ExecutionType.SOME_RANDOM_PROCESSING,
                        ":projectName",
                        null,
                        () -> {
                            logger.verbose("useless block");
                            return null;
                        });
        listener.afterExecute(mTask, mTaskState);
        listener.afterExecute(mSecondTask, mTaskState);

        ProcessRecorderFactory.shutdown();
        GradleBuildProfile profile = loadProfile();

        assertEquals(3, profile.getSpanCount());
        GradleBuildProfileSpan record = getRecordForId(profile.getSpanList(), 2);
        assertEquals(1, record.getProject());

        record = getRecordForId(profile.getSpanList(), 3);
        assertEquals(0, record.getParentId());
        assertEquals(1, record.getProject());

        record = getRecordForId(profile.getSpanList(), 4);
        assertNotNull(record);
        assertEquals(ExecutionType.SOME_RANDOM_PROCESSING, record.getType());
    }

    @Test
    public void multipleThreadsInvocation() {
        TestRecorder recorder = new TestRecorder();
        RecordingBuildListener listener = new RecordingBuildListener(recorder);
        Task secondTask = mock(Task.class);
        when(secondTask.getPath()).thenReturn(":projectName:secondTaskName");
        when(secondTask.getProject()).thenReturn(mProject);

        // first thread start
        listener.beforeExecute(mTask);

        // now second threads start
        listener.beforeExecute(secondTask);

        // first thread finishes
        listener.afterExecute(mTask, mTaskState);

        // and second thread finishes
        listener.afterExecute(secondTask, mTaskState);

        assertEquals(2, recorder.records.size());
        GradleBuildProfileSpan record = getRecordForId(recorder.records, 1);
        assertEquals(1, record.getId());
        assertEquals(0, record.getParentId());

        record = getRecordForId(recorder.records, 2);
        assertEquals(2, record.getId());
        assertEquals(0, record.getParentId());
        assertEquals(1, record.getProject());
    }

    @Test
    public void multipleThreadsOrderInvocation() {
        TestRecorder recorder = new TestRecorder();
        RecordingBuildListener listener = new RecordingBuildListener(recorder);
        Task secondTask = mock(Task.class);
        when(secondTask.getPath()).thenReturn(":projectName:secondTaskName");
        when(secondTask.getProject()).thenReturn(mProject);

        // first thread start
        listener.beforeExecute(mTask);

        // now second threads start
        listener.beforeExecute(secondTask);

        // second thread finishes
        listener.afterExecute(secondTask, mTaskState);

        // and first thread finishes
        listener.afterExecute(mTask, mTaskState);

        assertEquals(2, recorder.records.size());
        GradleBuildProfileSpan record = getRecordForId(recorder.records, 1);
        assertEquals(1, record.getId());
        assertEquals(0, record.getParentId());
        assertEquals(1, record.getProject());

        record = getRecordForId(recorder.records, 2);
        assertEquals(2, record.getId());
        assertEquals(0, record.getParentId());
        assertEquals(1, record.getProject());
    }

    @Test
    public void ensureTaskStateRecorded() {
        TestRecorder recorder = new TestRecorder();
        RecordingBuildListener listener = new RecordingBuildListener(recorder);

        when(mTaskState.getDidWork()).thenReturn(true);
        when(mTaskState.getExecuted()).thenReturn(true);
        when(mTaskState.getFailure()).thenReturn(new RuntimeException("Task failure"));
        when(mTaskState.getSkipped()).thenReturn(false);
        when(mTaskState.getUpToDate()).thenReturn(false);

        listener.beforeExecute(mTask);
        listener.afterExecute(mTask, mTaskState);

        assertEquals(1, recorder.records.size());
        assertThat(recorder.records.get(0).getType()).named("execution type").isEqualTo(ExecutionType.TASK_EXECUTION);
        GradleTaskExecution task = recorder.records.get(0).getTask();
        assertThat(task.getDidWork()).named("task.did_work").isTrue();
        assertThat(task.getFailed()).named("task.failed").isTrue();
        assertThat(task.getSkipped()).named("task.skipped").isFalse();
        assertThat(task.getUpToDate()).named("task.up_to_date").isFalse();
    }

    @Test
    public void checkTasksEnum() {
        assertThat(
                        AnalyticsUtil.getTaskExecutionType(
                                org.gradle.api.tasks.compile.JavaCompile.class))
                .named("JavaCompile")
                .isEqualTo(GradleTaskExecution.Type.JAVA_COMPILE);
    }


    private GradleBuildProfile loadProfile() throws IOException {
        return GradleBuildProfile.parseFrom(Files.readAllBytes(mProfileProtoFile));
    }

    @NonNull
    private static GradleBuildProfileSpan getRecordForId(
            @NonNull List<GradleBuildProfileSpan> records, long recordId) {
        for (GradleBuildProfileSpan record : records) {
            if (record.getId() == recordId) {
                return record;
            }
        }
        throw new AssertionError(
                "No record with id " + recordId + " found in ["
                        + Joiner.on(", ").join(records) + "]");
    }
}
