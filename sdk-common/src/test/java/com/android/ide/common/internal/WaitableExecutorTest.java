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

package com.android.ide.common.internal;

import static org.junit.Assert.fail;

import com.google.common.base.Throwables;
import com.google.common.truth.Truth;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.junit.Test;

/** Tests for the {@link WaitableExecutor}. */
public class WaitableExecutorTest {

    private final WaitableExecutor executor = WaitableExecutor.useGlobalSharedThreadPool();

    @Test
    public void checkTaskResults() throws InterruptedException {
        executor.execute(() -> 1);
        executor.execute(() -> 2);
        executor.execute(() -> 3);

        List<?> results =
                executor.waitForAllTasks()
                        .stream()
                        .map(WaitableExecutor.TaskResult::getValue)
                        .collect(Collectors.toList());
        Truth.assertThat(results).containsExactly(1, 2, 3);
    }

    @Test
    public void checkNoTaskResults() throws InterruptedException {
        List<?> results =
                executor.waitForAllTasks()
                        .stream()
                        .map(WaitableExecutor.TaskResult::getValue)
                        .collect(Collectors.toList());
        Truth.assertThat(results).isEmpty();
    }

    @Test
    public void checkOneFails() throws InterruptedException {
        executor.execute(() -> 1);
        executor.execute(() -> 2);
        executor.execute(
                () -> {
                    throw new RuntimeException("Fail this task");
                });

        List<WaitableExecutor.TaskResult<Integer>> results = executor.waitForAllTasks();
        List<Integer> values =
                results.stream()
                        .map(WaitableExecutor.TaskResult::getValue)
                        .collect(Collectors.toList());
        Truth.assertThat(values).containsExactly(1, 2, null);

        List<String> exceptions =
                results.stream()
                        .map(WaitableExecutor.TaskResult::getException)
                        .filter(Objects::nonNull)
                        .map(Throwable::getMessage)
                        .collect(Collectors.toList());
        Truth.assertThat(exceptions)
                .named("Exceptions from results " + results)
                .containsExactly("Fail this task");
    }

    @Test
    public void checkExceptionThrownIfOneFails() throws InterruptedException {
        executor.execute(() -> 1);
        executor.execute(() -> 2);
        executor.execute(
                () -> {
                    throw new RuntimeException("Fail this task");
                });

        try {
            executor.waitForTasksWithQuickFail(false);
            fail();
        } catch (Exception e) {
            Truth.assertThat(Throwables.getRootCause(e).getMessage()).contains("Fail this task");
        }
    }
}
