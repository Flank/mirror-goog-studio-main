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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.common.jimfs.Jimfs;
import com.google.wireless.android.sdk.stats.AndroidStudioStats;
import com.google.wireless.android.sdk.stats.AndroidStudioStats.GradleBuildProfileSpan.ExecutionType;

import org.junit.Before;
import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for the {@link ProcessRecorder} class
 */
public class ProcessRecorderTest {


    private Path outputFile;

    @Before
    public void setUp() throws IOException {
        // reset for each test.
        outputFile = Jimfs.newFileSystem().getPath("profile_proto");
        ProcessRecorderFactory.initializeForTests(outputFile);
    }

    @Test
    public void testBasicRecord() throws Exception {
        ThreadRecorder.get().record(ExecutionType.SOME_RANDOM_PROCESSING,
                ":projectName", null, () -> 10);
        ProcessRecorderFactory.shutdown();
        AndroidStudioStats.GradleBuildProfile profile = loadProfile();
        assertThat(profile.getSpan(0).getType()).isEqualTo(ExecutionType.SOME_RANDOM_PROCESSING);
        assertThat(profile.getSpan(0).getId()).isNotEqualTo(0);
        assertThat(profile.getSpan(0).getVariant()).isEqualTo(0);
        assertThat(profile.getSpan(0).getStartTimeInMs()).isNotEqualTo(0);
    }

    @Test
    public void testRecordWithAttributes() throws Exception {
        ThreadRecorder.get().record(
                ExecutionType.SOME_RANDOM_PROCESSING, ":projectName", "foo", () -> 10);
        ProcessRecorderFactory.shutdown();
        AndroidStudioStats.GradleBuildProfile profile = loadProfile();
        assertThat(profile.getSpan(0).getType()).isEqualTo(ExecutionType.SOME_RANDOM_PROCESSING);
        assertThat(profile.getSpan(0).getVariant()).isNotEqualTo(0);
        assertThat(profile.getSpan(0).getStartTimeInMs()).isNotEqualTo(0);
    }

    @Test
    public void testRecordsOrder() throws Exception {
        ThreadRecorder.get().record(
                ExecutionType.SOME_RANDOM_PROCESSING, "projectName", null, () ->
                        ThreadRecorder.get().record(ExecutionType.SOME_RANDOM_PROCESSING,
                                "projectName", null, () -> 10));
        ProcessRecorderFactory.shutdown();
        AndroidStudioStats.GradleBuildProfile profile = loadProfile();
        assertThat(profile.getSpanList()).hasSize(2);
        AndroidStudioStats.GradleBuildProfileSpan parent = profile.getSpan(1);
        AndroidStudioStats.GradleBuildProfileSpan child = profile.getSpan(0);
        assertThat(child.getId()).isGreaterThan(parent.getId());
        assertThat(child.getParentId()).isEqualTo(parent.getId());
    }

    @Test
    public void testMultipleSpans() throws Exception {

        Integer value = ThreadRecorder.get().record(
                ExecutionType.SOME_RANDOM_PROCESSING,
                ":projectName",
                null,
                () -> ThreadRecorder.get().record(
                        ExecutionType.SOME_RANDOM_PROCESSING,
                        ":projectName",
                        null,
                        () -> {
                            Integer first = ThreadRecorder.get().record(
                                    ExecutionType.SOME_RANDOM_PROCESSING,
                                    ":projectName", null, () -> 1);
                            Integer second = ThreadRecorder.get().record(
                                    ExecutionType.SOME_RANDOM_PROCESSING,
                                    ":projectName", null, () -> 3);
                            Integer third = ThreadRecorder.get().record(
                                    ExecutionType.SOME_RANDOM_PROCESSING,
                                    ":projectName", null, () -> {
                                        Integer value1 = ThreadRecorder.get().record(
                                                ExecutionType.SOME_RANDOM_PROCESSING,
                                                ":projectName", null,
                                                () -> 7);
                                        assertNotNull(value1);
                                        return 5 + value1;
                                    });
                            assertNotNull(first);
                            assertNotNull(second);
                            assertNotNull(third);
                            return first + second + third;
                        }));

        assertNotNull(value);
        assertEquals(16, value.intValue());
        ProcessRecorderFactory.shutdown();
        AndroidStudioStats.GradleBuildProfile profile = loadProfile();
        assertThat(profile.getSpanList()).hasSize(6);

        List<AndroidStudioStats.GradleBuildProfileSpan> records =
                profile.getSpanList()
                        .stream()
                        .sorted((a, b) -> Long.signum(a.getId() - b.getId()))
                        .collect(Collectors.toList());
        assertEquals(records.get(0).getId(), records.get(1).getParentId());
        assertEquals(records.get(1).getId(), records.get(2).getParentId());
        assertEquals(records.get(1).getId(), records.get(3).getParentId());
        assertEquals(records.get(1).getId(), records.get(4).getParentId());
        assertEquals(records.get(4).getId(), records.get(5).getParentId());

        assertThat(records.get(1).getDurationInMs())
                .isAtLeast(records.get(2).getDurationInMs()
                        + records.get(3).getDurationInMs()
                        + records.get(4).getDurationInMs());

        assertThat(records.get(4).getDurationInMs()).isAtLeast(records.get(5).getDurationInMs());
    }

    private AndroidStudioStats.GradleBuildProfile loadProfile() throws IOException {
        return AndroidStudioStats.GradleBuildProfile.parseFrom(Files.readAllBytes(outputFile));
    }
}
