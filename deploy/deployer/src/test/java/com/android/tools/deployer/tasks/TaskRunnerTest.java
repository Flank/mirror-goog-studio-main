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

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.fail;

import com.android.tools.deployer.DeployerException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.Assert;
import org.junit.Test;

public class TaskRunnerTest {

    @Test
    public void testRunningSimpleTask() throws Exception {
        String input = "text";

        ExecutorService service = Executors.newFixedThreadPool(2);

        TaskRunner runner = new TaskRunner(service);
        TaskRunner.Task<String> start = runner.submit(input);
        TaskRunner.Task<String> add = runner.submit("add", a -> a + " added", start);
        String output = add.get();

        Assert.assertEquals("text added", output);
    }

    @Test
    public void testJoiningTasksOneThread() throws Exception {
        String input = "text";

        // We test with one thread, they should run sequentially
        ExecutorService service = Executors.newFixedThreadPool(1);

        TaskRunner runner = new TaskRunner(service);
        TaskRunner.Task<String> start = runner.submit(input);
        TaskRunner.Task<String> task1 = runner.submit("task1", a -> a + " task1", start);
        TaskRunner.Task<String> task2 = runner.submit("task2", a -> a + " task2", start);
        TaskRunner.Task<String> add = runner.submit("join", (a, b) -> a + "." + b, task1, task2);
        String output = add.get();

        Assert.assertEquals("text task1.text task2", output);
    }

    @Test
    public void testPreviousTasksFailed() throws Exception {
        String input = "text";

        // We test with one thread, they should run sequentially
        ExecutorService service = Executors.newFixedThreadPool(1);

        TaskRunner runner = new TaskRunner(service);
        TaskRunner.Task<String> start = runner.submit(input);
        TaskRunner.Task<String> task1 =
                runner.submit(
                        "task1",
                        a -> {
                            throw new RuntimeException("abc");
                        },
                        start);
        TaskRunner.Task<String> task2 = runner.submit("task2", a -> a + " task2", start);
        TaskRunner.Task<String> add = runner.submit("join", (a, b) -> a + "." + b, task1, task2);
        try {
            String output = add.get();
            fail();
        } catch (Exception de) {
            assertEquals("java.lang.RuntimeException: abc", de.getCause().getMessage());
        }
        runner.join();
    }

    @Test
    public void testParallelTasks() throws Exception {
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
    }

    @Test
    public void testJoinExecutesOthers() throws Exception {
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

        // Test that the first task is not blbocked by the second one.
        String output = task1.get();
        task2Latch.countDown();
        Assert.assertEquals("text task1", output);
    }

    @Test
    public void testNotReadyDoesNotBlock() throws Exception {
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
    }

    @Test
    public void testJoinWaitsForSubmitted() throws Exception {
        String input = "text";

        CountDownLatch task1Latch = new CountDownLatch(1);
        CountDownLatch task2Latch = new CountDownLatch(1);
        CountDownLatch task3Latch = new CountDownLatch(1);
        ExecutorService service = Executors.newFixedThreadPool(2);

        TaskRunner runner = new TaskRunner(service);
        assertEquals(0, runner.getPendingTasks());
        TaskRunner.Task<String> start = runner.submit(input);
        assertEquals(0, runner.getPendingTasks());
        TaskRunner.Task<String> task1 =
                runner.submit(
                        "task1",
                        a -> {
                            waitLatch(task1Latch);
                            return a + " task2";
                        },
                        start);
        assertEquals(1, runner.getPendingTasks());
        TaskRunner.Task<String> task2 =
                runner.submit(
                        "task2",
                        (a, b) -> {
                            waitLatch(task2Latch);
                            return a + b + " task2";
                        },
                        start,
                        task1);
        assertEquals(2, runner.getPendingTasks());
        TaskRunner.Task<String> task3 =
                runner.submit(
                        "task2",
                        (a, b, c) -> {
                            waitLatch(task3Latch);
                            return a + b + c + " task3";
                        },
                        start,
                        task1,
                        task2);
        assertEquals(3, runner.getPendingTasks());
        task1Latch.countDown();
        task1.get();
        assertEquals(2, runner.getPendingTasks());
        task2Latch.countDown();
        task2.get();
        assertEquals(1, runner.getPendingTasks());
        task3Latch.countDown();
        runner.join();
        assertEquals(0, runner.getPendingTasks());
    }

    @Test
    public void testExceptionIsThrown() {
        String input = "text";

        ExecutorService service = Executors.newFixedThreadPool(2);
        TaskRunner runner = new TaskRunner(service);
        TaskRunner.Task<String> start = runner.submit(input);
        TaskRunner.Task<String> task1 =
                runner.submit(
                        "task1",
                        a -> {
                            throw new DeployerException(
                                    DeployerException.Error.INSTALL_FAILED, "failed");
                        },
                        start);

        try {
            task1.get();
            fail("Exception should have been thrown");
        } catch (DeployerException e) {
            assertEquals(DeployerException.Error.INSTALL_FAILED, e.getError());
            assertEquals("failed", e.getMessage());
        }
    }

    @Test
    public void testExceptionIsPropagated() {
        String input = "text";

        ExecutorService service = Executors.newFixedThreadPool(2);
        TaskRunner runner = new TaskRunner(service);
        TaskRunner.Task<String> start = runner.submit(input);
        TaskRunner.Task<String> task1 =
                runner.submit(
                        "task1",
                        a -> {
                            throw new DeployerException(
                                    DeployerException.Error.INSTALL_FAILED, "failed");
                        },
                        start);
        TaskRunner.Task<String> task2 = runner.submit("task2", a -> a + "2", task1);

        try {
            task2.get();
            fail("Exception should have been thrown");
        } catch (DeployerException e) {
            assertEquals(DeployerException.Error.INSTALL_FAILED, e.getError());
            assertEquals("failed", e.getMessage());
        }
    }

    private static void waitLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
