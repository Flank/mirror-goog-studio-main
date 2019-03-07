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
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.deployer.DeployerException;
import com.android.tools.deployer.tasks.TaskRunner.Task;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.Assert;
import org.junit.Test;

public class TaskRunnerTest {

    enum Tasks {
        TASK1,
        TASK2,
        TASK3
    };

    @Test
    public void testRunningSimpleTask() throws Exception {
        String input = "text";

        ExecutorService service = Executors.newFixedThreadPool(2);

        TaskRunner runner = new TaskRunner(service);
        Task<String> start = runner.create(input);
        Task<String> add = runner.create(Tasks.TASK1, a -> a + " added", start);
        runner.run();
        String output = add.get();

        Assert.assertEquals("text added", output);
        service.shutdown();

        Assert.assertNotNull(start.getMetric());
        Assert.assertEquals(Tasks.TASK1.name(), add.getMetric().getName());
        Assert.assertTrue(add.getMetric().getEndTimeMs() >= add.getMetric().getStartTimeMs());
    }

    @Test
    public void testJoiningTasksOneThread() throws Exception {
        String input = "text";

        // We test with one thread, they should run sequentially
        ExecutorService service = Executors.newFixedThreadPool(1);

        TaskRunner runner = new TaskRunner(service);
        Task<String> start = runner.create(input);
        Task<String> task1 = runner.create(Tasks.TASK1, a -> a + " task1", start);
        Task<String> task2 = runner.create(Tasks.TASK2, a -> a + " task2", start);
        Task<String> add = runner.create(Tasks.TASK3, (a, b) -> a + "." + b, task1, task2);
        runner.run();
        String output = add.get();

        Assert.assertEquals("text task1.text task2", output);
        service.shutdown();
    }

    @Test
    public void testPreviousTasksFailed() {
        String input = "text";

        // We test with one thread, they should run sequentially
        ExecutorService service = Executors.newFixedThreadPool(1);

        TaskRunner runner = new TaskRunner(service);
        Task<String> start = runner.create(input);
        Task<String> task1 =
                runner.create(
                        Tasks.TASK1,
                        a -> {
                            throw new RuntimeException("abc");
                        },
                        start);
        Task<String> task2 = runner.create(Tasks.TASK2, a -> a + " task2", start);
        Task<String> add = runner.create(Tasks.TASK3, (a, b) -> a + "." + b, task1, task2);
        try {
            runner.run();
            fail();
        } catch (Exception de) {
            assertEquals("java.lang.RuntimeException: abc", de.getCause().getMessage());
        }
        service.shutdown();

        Assert.assertEquals("Failed", task1.getMetric().getStatus());
        // The status of task2 is not deterministic even if it is singled threaded. It depends how quickly task 1 fails.
        // It can be not started yet (no metrics) or started but dropped.
        Assert.assertEquals("Dropped", add.getMetric().getStatus());
    }

    @Test
    public void testParallelTasks() throws Exception {
        String input = "text";

        // Two threads for the two parallel tasks
        ExecutorService service = Executors.newFixedThreadPool(2);

        CountDownLatch task1Latch = new CountDownLatch(1);
        CountDownLatch task2Latch = new CountDownLatch(1);

        TaskRunner runner = new TaskRunner(service);
        Task<String> start = runner.create(input);

        // If these two tasks are run sequentially they will
        // deadlock. They need to run in parallel to unlock.
        Task<String> task1 =
                runner.create(
                        Tasks.TASK1,
                        a -> {
                            // Allow task 2 to run
                            task1Latch.countDown();
                            // Wait for task 2 signal to continue
                            waitLatch(task2Latch);
                            return a + " task1";
                        },
                        start);

        Task<String> task2 =
                runner.create(
                        Tasks.TASK2,
                        a -> {
                            // Wait for task 1 to give the go
                            waitLatch(task1Latch);
                            // Tell task 1 to continue
                            task2Latch.countDown();
                            return a + " task2";
                        },
                        start);

        Task<String> add = runner.create(Tasks.TASK3, (a, b) -> a + "." + b, task1, task2);
        runner.run();

        String output = add.get();
        Assert.assertEquals("text task1.text task2", output);
        service.shutdown();
    }

    @Test
    public void testAsyncExecutesOthers() throws Exception {
        String input = "text";

        CountDownLatch task2Latch = new CountDownLatch(1);
        ExecutorService service = Executors.newFixedThreadPool(2);

        TaskRunner runner = new TaskRunner(service);
        Task<String> start = runner.create(input);
        Task<String> task1 = runner.create(Tasks.TASK1, a -> a + " task1", start);
        Task<String> task2 =
                runner.create(
                        Tasks.TASK2,
                        a -> {
                            waitLatch(task2Latch);
                            return a + " task2";
                        },
                        start);

        // The order in which task1 and task2 is run cannot be guaranteed, but blocking task2 should not prevent task1 to finish.
        // We have two threads in the executor, so we give a different one to runAsync to not block any thread in there.
        ExecutorService async = Executors.newSingleThreadExecutor();
        runner.runAsync(async);
        String output = task1.get();
        task2Latch.countDown();

        runner.run();
        Assert.assertEquals("text task1", output);

        service.shutdown();
        async.shutdown();
    }

    @Test
    public void testNotReadyDoesNotBlock() throws Exception {
        String input = "text";

        ExecutorService service = Executors.newFixedThreadPool(2);

        CountDownLatch task1Latch = new CountDownLatch(1);

        TaskRunner runner = new TaskRunner(service);
        Task<String> start = runner.create(input);
        Task<String> task1 =
                runner.create(
                        Tasks.TASK1,
                        a -> {
                            waitLatch(task1Latch);
                            return a + "1";
                        },
                        start);
        Task<String> task2 = runner.create(Tasks.TASK2, a -> a + "2", task1);
        Task<String> task3 = runner.create(Tasks.TASK3, a -> a + "3", start);

        // We have room to run two tasks in parallel. Task 1 or 3 can start first, and until it's done
        // task2 wouldn't be available to run. But task3 can run to completion.

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Void> running =
                executor.submit(
                        () -> {
                            runner.run();
                            return null;
                        });

        String task3output = task3.get();
        Assert.assertEquals("text3", task3output);

        task1Latch.countDown(); // we let the first task complete
        String task2output = task2.get();
        Assert.assertEquals("text12", task2output);

        running.get();
    }

    @Test
    public void testJoinWaitsForSubmitted() throws Exception {
        String input = "text";

        ExecutorService service = Executors.newFixedThreadPool(2);

        TaskRunner runner = new TaskRunner(service);
        Task<String> start = runner.create(input);
        Task<String> task1 = runner.create(Tasks.TASK1, a -> a + " task2", start);
        Task<String> task2 = runner.create(Tasks.TASK2, (a, b) -> a + b + " task2", start, task1);
        Task<String> task3 =
                runner.create(Tasks.TASK3, (a, b, c) -> a + b + c + " task3", start, task1, task2);

        runner.run();
        List<Runnable> runnables = service.shutdownNow();
        assertTrue(runnables.isEmpty());
        service.shutdown();
    }

    @Test
    public void testExceptionIsThrown() {
        String input = "text";

        ExecutorService service = Executors.newFixedThreadPool(2);
        TaskRunner runner = new TaskRunner(service);
        Task<String> start = runner.create(input);
        Task<String> task1 =
                runner.create(
                        Tasks.TASK1,
                        a -> {
                            throw DeployerException.swapFailed("failed");
                        },
                        start);

        try {
            runner.run();
            fail("Exception should have been thrown");
        } catch (DeployerException e) {
            assertEquals(DeployerException.Error.SWAP_FAILED, e.getError());
            assertEquals("failed", e.getDetails());
        }
        service.shutdown();
    }

    @Test
    public void testExceptionIsPropagated() {
        String input = "text";

        ExecutorService service = Executors.newFixedThreadPool(2);
        TaskRunner runner = new TaskRunner(service);
        Task<String> start = runner.create(input);
        Task<String> task1 =
                runner.create(
                        Tasks.TASK1,
                        a -> {
                            throw DeployerException.swapFailed("failed");
                        },
                        start);
        Task<String> task2 = runner.create(Tasks.TASK2, a -> a + "2", task1);

        try {
            runner.run();
            fail("Exception should have been thrown");
        } catch (DeployerException e) {
            assertEquals(DeployerException.Error.SWAP_FAILED, e.getError());
            assertEquals("failed", e.getDetails());
        }
        service.shutdown();
    }

    @Test
    public void testReleasedTaskCanThrow() {
        String input = "text";

        ConcurrentLinkedQueue<Throwable> exceptions = new ConcurrentLinkedQueue<>();
        CountDownLatch exceptionLatch = new CountDownLatch(1);

        ExecutorService service =
                Executors.newFixedThreadPool(
                        2,
                        r ->
                                new Thread(r) {
                                    @Override
                                    public void run() {
                                        try {
                                            super.run();
                                        } catch (Throwable t) {
                                            exceptions.add(t);
                                            exceptionLatch.countDown();
                                        }
                                    }
                                });
        TaskRunner runner = new TaskRunner(service);
        Task<String> start = runner.create(input);
        Task<String> task1 =
                runner.create(
                        Tasks.TASK1,
                        a -> {
                            throw DeployerException.swapFailed("async failed");
                        },
                        start);

        runner.runAsync();
        try {
            task1.get();
            fail("Task 1 should have thrown an exception");
        } catch (DeployerException e) {
            assertEquals("async failed", e.getDetails());
        }
        waitLatch(exceptionLatch);

        assertEquals(1, exceptions.size());
        Throwable t = exceptions.poll();
        assertTrue(t instanceof RuntimeException);
        assertTrue(t.getCause() instanceof DeployerException);
        DeployerException e = (DeployerException) t.getCause();
        assertEquals(DeployerException.Error.SWAP_FAILED, e.getError());
        assertEquals("async failed", e.getDetails());
        service.shutdown();
    }

    private static void waitLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
