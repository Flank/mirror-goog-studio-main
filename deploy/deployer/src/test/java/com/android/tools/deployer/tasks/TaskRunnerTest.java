/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.deployer.tasks;

import com.android.tools.deployer.Trace;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;

public class TaskRunnerTest {

    @Test
    public void testRunningSimpleTask() throws Exception {
        String input = "text";
        Trace.begin("testRunningSimpleTask");

        ExecutorService service = Executors.newFixedThreadPool(2);

        TaskRunner runner = new TaskRunner(service);
        TaskRunner.Task<String> start = runner.submit(input);
        TaskRunner.Task<String> add = runner.submit("add", a -> a + " added", start);
        String output = add.get();

        Assert.assertEquals("text added", output);
        Trace.end();
    }

    @Test
    public void testJoiningTasksOneThread() throws Exception {
        String input = "text";
        Trace.begin("testJoiningTasksOneThread");

        // We test with one thread, they should run sequentially
        ExecutorService service = Executors.newFixedThreadPool(1);

        TaskRunner runner = new TaskRunner(service);
        TaskRunner.Task<String> start = runner.submit(input);
        TaskRunner.Task<String> task1 = runner.submit("task1", a -> a + " task1", start);
        TaskRunner.Task<String> task2 = runner.submit("task2", a -> a + " task2", start);
        TaskRunner.Task<String> add = runner.submit("join", (a, b) -> a + "." + b, task1, task2);
        String output = add.get();

        Assert.assertEquals("text task1.text task2", output);
        Trace.end();
    }

    @Test
    public void testParallelTasks() throws Exception {
        Trace.begin("testParallelTasks");
        String input = "text";

        // Two threads for the two parallel tasks
        ExecutorService service = Executors.newFixedThreadPool(2);

        CountDownLatch task1Latch = new CountDownLatch(1);
        CountDownLatch task2Latch = new CountDownLatch(1);

        TaskRunner runner = new TaskRunner(service);
        TaskRunner.Task<String> start = runner.submit(input);

        // If these two tasks are run sequentially they will
        // deadlock. They need to run in parallel to unlock.
        TaskRunner.Task<String> task1 =
                runner.submit(
                        "task1",
                        a -> {
                            // Allow task 2 to run
                            task1Latch.countDown();
                            // Wait for task 2 signal to continue
                            waitLatch(task2Latch);
                            return a + " task1";
                        },
                        start);

        TaskRunner.Task<String> task2 =
                runner.submit(
                        "task2",
                        a -> {
                            // Wait for task 1 to give the go
                            waitLatch(task1Latch);
                            // Tell task 1 to continue
                            task2Latch.countDown();
                            return a + " task2";
                        },
                        start);

        TaskRunner.Task<String> add = runner.submit("join", (a, b) -> a + "." + b, task1, task2);
        String output = add.get();

        Assert.assertEquals("text task1.text task2", output);
        Trace.end();
    }

    @Test
    public void testJoinExecutesOthers() throws Exception {
        Trace.begin("testJoinExecutesOthers");
        String input = "text";

        CountDownLatch task2Latch = new CountDownLatch(1);
        ExecutorService service = Executors.newFixedThreadPool(2);

        TaskRunner runner = new TaskRunner(service);
        TaskRunner.Task<String> start = runner.submit(input);
        TaskRunner.Task<String> task1 = runner.submit("task1", a -> a + " task1", start);
        TaskRunner.Task<String> task2 =
                runner.submit(
                        "task2",
                        a -> {
                            waitLatch(task2Latch);
                            return a + " task2";
                        },
                        start);

        String output = task1.get();
        // Test that run can return without task2
        task2Latch.countDown();
        runner.join();
        Assert.assertEquals("text task1", output);
        Trace.end();
    }

    @Test
    public void testNotReadyDoesNotBlock() throws Exception {
        Trace.begin("testJoinExecutesOthers");
        String input = "text";

        ExecutorService service = Executors.newFixedThreadPool(2);

        CountDownLatch task1Latch = new CountDownLatch(1);

        TaskRunner runner = new TaskRunner(service);
        TaskRunner.Task<String> start = runner.submit(input);
        TaskRunner.Task<String> task1 =
                runner.submit(
                        "first",
                        a -> {
                            waitLatch(task1Latch);
                            return a + "1";
                        },
                        start);
        TaskRunner.Task<String> task2 = runner.submit("blocks", a -> a + "2", task1);
        TaskRunner.Task<String> task3 = runner.submit("can_run", a -> a + "3", start);

        // We have room to run two tasks in parallel. Task 1 should start first, and until it's done
        // task2 wouldn't be available to run. But task3 can run to completion.
        String task3output = task3.get();
        Assert.assertEquals("text3", task3output);

        task1Latch.countDown(); // we let the first task complete
        String task2output = task2.get();
        Assert.assertEquals("text12", task2output);
        Trace.end();
    }

    @AfterClass
    public static void dumpTrace() {
        Trace.reset();
    }

    private static void waitLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
