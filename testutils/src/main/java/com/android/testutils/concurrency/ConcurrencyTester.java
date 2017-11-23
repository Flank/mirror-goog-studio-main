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

package com.android.testutils.concurrency;

import com.android.annotations.NonNull;
import com.google.common.base.Preconditions;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.Assert;

/**
 * Utility class to test concurrency.
 *
 * <p>This class is used to check whether or not a set of threads violate a concurrency contract
 * (e.g., whether they run concurrently when they are not allowed to, or vice versa).
 *
 * <p>The following is a usage scenario of this class. Suppose we have a method that accepts an
 * action (of type {@link Runnable} or the like) as an argument (e.g., {@code methodUnderTest(...,
 * actionUnderTest)}) and at some point during the method's execution, the method will invoke the
 * action exactly once. When several threads are concurrently calling the method (possibly with
 * different parameter values), the method under test may make a contract that all the threads can
 * execute the corresponding actions concurrently, or it may make a contract that all the threads
 * cannot execute the actions concurrently. To check whether the method under test meets the
 * concurrency contract, we can write a test as follows.
 *
 * <pre>{@code
 * ConcurrencyTester tester = new ConcurrencyTester();
 * for (...) {
 *     Function actionUnderTest = (input) -> { ... };
 *     tester.addMethodInvocationFromNewThread(
 *             (Function instrumentedActionUnderTest) -> {
 *                 // instrumentedActionUnderTest is actionUnderTest plus the instrumented code by
 *                 // ConcurrencyTester, so that the tester can perform necessary concurrency
 *                 // checks when the action starts and finishes.
 *                 // The client will need to call methodUnderTest with this instrumented action.
 *                 methodUnderTest(..., instrumentedActionUnderTest);
 *             },
 *             actionUnderTest);
 * }
 * }</pre>
 *
 * <p>Then, if the actions are allowed to run concurrently, we can make the following assertion:
 *
 * <pre>{@code
 * tester.assertThatActionsCanRunConcurrently();
 * }</pre>
 *
 * <p>If the actions are not allowed to run concurrently, we can make the following assertion:
 *
 * <pre>{@code
 * tester.assertThatActionsCannotRunConcurrently();
 * }</pre>
 */
public final class ConcurrencyTester<F, T> {

    /**
     * The timeout for the main thread to wait for a new action to start in the case that there are
     * one or more actions currently running and the actions are expected to run concurrently. As
     * this timeout increases, the chance of a falsely failing test (or flaky test) is reduced;
     * however, a truly failing test (or non-flaky test) will take longer to run. Since it is okay
     * for a failing test to take a long time to run (it does not happen frequently), we set a large
     * timeout to prevent flaky tests.
     */
    @NonNull private static final Duration TIMEOUT_TO_START_ACTION_WHEN_CONCURRENCY_EXPECTED =
            Duration.ofSeconds(60);

    /**
     * The timeout for the main thread to wait for a new action to start in the case that there are
     * one or more actions currently running and the actions are not expected to run concurrently.
     * As this timeout increases, the chance of a falsely passing test is reduced; however, a truly
     * passing test will take longer to run. Therefore, we set a small timeout to prevent
     * long-running passing tests, but make sure it is still large enough for the tests to be
     * effective.
     */
    @NonNull private static final Duration TIMEOUT_TO_START_ACTION_WHEN_NO_CONCURRENCY_EXPECTED =
            Duration.ofSeconds(1);

    /** The running pattern of a set of actions. */
    private enum RunningPattern {

        /** All actions run concurrently. */
        CONCURRENT,

        /** All actions run sequentially. */
        SEQUENTIAL,

        /** More than one but not all actions run concurrently. */
        MIXED
    }

    @NonNull private List<Consumer<Function<F, T>>> methodInvocationList = new LinkedList<>();

    @NonNull private List<Function<F, T>> actionUnderTestList = new LinkedList<>();

    /**
     * Adds a new invocation of the method under test to this {@link ConcurrencyTester} instance.
     * The {@code ConcurrencyTester} will execute each invocation in a separate thread and check
     * whether the corresponding actions under test meet the concurrency requirement.
     *
     * @param methodUnderTestInvocation the invocation of the method under test which will be
     *     executed from a new thread
     * @param actionUnderTest the action under test
     */
    public void addMethodInvocationFromNewThread(
            @NonNull Consumer<Function<F, T>> methodUnderTestInvocation,
            @NonNull Function<F, T> actionUnderTest) {
        methodInvocationList.add(methodUnderTestInvocation);
        actionUnderTestList.add(actionUnderTest);
    }

    /**
     * Executes the invocations of the method under test in separate threads and asserts that all
     * the actions ran concurrently. Note that a failed assertion means that either the actions were
     * not allowed to run concurrently (which violates the concurrency requirement) or the actions
     * accidentally ran sequentially. However, while the latter case is possible, the implementation
     * of this method makes sure that it is unlikely to happen.
     */
    public void assertThatActionsCanRunConcurrently() {
        Preconditions.checkArgument(
                methodInvocationList.size() >= 2,
                "There must be at least 2 actions for concurrency checks.");
        Assert.assertTrue(
                "Two or more actions ran sequentially"
                        + " while all the actions were expected to run concurrently.",
                executeActionsAndGetRunningPattern(
                                TIMEOUT_TO_START_ACTION_WHEN_CONCURRENCY_EXPECTED)
                        == RunningPattern.CONCURRENT);
    }

    /**
     * Executes the invocations of the method under test in separate threads and asserts that all
     * the actions ran sequentially. Note that a successful assertion means that either the actions
     * were not allowed to run concurrently (which meets the concurrency requirement) or the actions
     * accidentally ran sequentially. However, while the latter case is possible, the implementation
     * of this method makes sure that it is unlikely to happen.
     */
    public void assertThatActionsCannotRunConcurrently() {
        Preconditions.checkArgument(
                methodInvocationList.size() >= 2,
                "There must be at least 2 actions for concurrency checks.");
        Assert.assertTrue(
                "Two or more actions ran concurrently"
                        + " while all the actions were expected to run sequentially.",
                executeActionsAndGetRunningPattern(
                                TIMEOUT_TO_START_ACTION_WHEN_NO_CONCURRENCY_EXPECTED)
                        == RunningPattern.SEQUENTIAL);
    }

    /**
     * Executes the invocations of the method under test in separate threads and asserts that one
     * and only one of the actions was executed.
     */
    public void assertThatOnlyOneActionIsExecuted() {
        Preconditions.checkArgument(
                methodInvocationList.size() >= 2,
                "There must be at least 2 actions for concurrency checks.");

        AtomicInteger executedActions = new AtomicInteger(0);
        List<Runnable> runnables = new LinkedList<>();

        for (int i = 0; i < methodInvocationList.size(); i++) {
            Consumer<Function<F, T>> methodInvocation = methodInvocationList.get(i);
            Function<F, T> actionUnderTest = actionUnderTestList.get(i);
            runnables.add(
                    () ->
                            methodInvocation.accept(
                                    (input) -> {
                                        executedActions.getAndIncrement();
                                        return actionUnderTest.apply(input);
                                    }));
        }

        Map<Thread, Optional<Throwable>> threads = executeRunnablesInThreads(runnables);
        waitForThreadsToFinish(threads);

        Assert.assertTrue(
                executedActions.get()
                        + " actions were executed while only one action was expected to run.",
                executedActions.get() == 1);
    }

    /**
     * Executes the invocations of the method under test in separate threads and returns the running
     * pattern of their actions.
     *
     * @param timeoutToStartAction the timeout for the main thread to wait for a new action to start
     *     in the case that there are one or more actions currently running
     * @return the running pattern of the actions
     */
    private RunningPattern executeActionsAndGetRunningPattern(
            @NonNull Duration timeoutToStartAction) {
        // We use blocking queues and count-down latches for the actions to communicate with the
        // main thread. When an action starts, it notifies the main thread that it has started and
        // continues immediately. When an action is going to finish, it creates a CountDownLatch,
        // sends it to the main thread, and waits for the main thread to allow it to finish.
        BlockingQueue<Thread> startedActionQueue = new LinkedBlockingQueue<>();
        BlockingQueue<CountDownLatch> finishRequestQueue = new LinkedBlockingQueue<>();

        Runnable actionStartedHandler = () -> startedActionQueue.add(Thread.currentThread());

        Runnable actionFinishedHandler = () -> {
            CountDownLatch finishRequest = new CountDownLatch(1);
            finishRequestQueue.add(finishRequest);
            try {
                finishRequest.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        // Attach the event handlers to the actions
        List<Runnable> runnables = new LinkedList<>();
        for (int i = 0; i < methodInvocationList.size(); i++) {
            Consumer<Function<F, T>> methodInvocation = methodInvocationList.get(i);
            Function<F, T> actionUnderTest = actionUnderTestList.get(i);

            Function<F, T> instrumentedActionUnderTest = (input) -> {
                actionStartedHandler.run();
                try {
                    return actionUnderTest.apply(input);
                } finally {
                    actionFinishedHandler.run();
                }
            };
            runnables.add(() -> methodInvocation.accept(instrumentedActionUnderTest));
        }

        // Execute each invocation in a separate thread
        Map<Thread, Optional<Throwable>> threads = executeRunnablesInThreads(runnables);

        // Begin to monitor how the actions are executed
        int remainingActions = runnables.size();
        Queue<CountDownLatch> finishRequests = new LinkedList<>();
        int maxConcurrentActions = 0;

        // To prevent the actions from *accidentally* running sequentially, when an action is going
        // to finish, we don't let it finish immediately but try waiting for the next action to
        // start. The following loop aims to "force" the actions to run concurrently. If it
        // succeeds, it means that the actions are able to run concurrently. If it doesn't succeed,
        // it means that either the actions are not able to run concurrently, or the actions take
        // too long to start.
        while (remainingActions > 0) {
            // Wait for a new action to start. If there are currently no running actions, let's wait
            // for the new action without a timeout. If there are currently one or more running
            // actions, the running actions could block new actions and prevent them from starting
            // (e.g., when the actions are not allowed to run concurrently). To avoid waiting
            // indefinitely, we need to set a timeout.
            Thread startedAction;
            try {
                if (finishRequests.isEmpty()) {
                    startedAction = startedActionQueue.take();
                } else {
                    startedAction =
                            startedActionQueue.poll(
                                    timeoutToStartAction.toMillis(), TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            // If a new action has started, let it run but do not let it finish. Instead, we keep
            // waiting for more actions to start (repeat the loop).
            //noinspection VariableNotUsedInsideIf
            if (startedAction != null) {
                remainingActions--;
                CountDownLatch finishRequest;
                try {
                    finishRequest = finishRequestQueue.take();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                finishRequests.add(finishRequest);
                if (finishRequests.size() > maxConcurrentActions) {
                    maxConcurrentActions = finishRequests.size();
                }
            } else {
                // If no action has started (which implies that there are currently one or more
                // running actions and the timeout expired), it could be that either the running
                // actions are blocking the new actions, or the new actions are taking too long to
                // start. Since we cannot distinguish these two cases, we let all the running
                // actions finish and repeat the loop.
                while (!finishRequests.isEmpty()) {
                    finishRequests.remove().countDown();
                }
            }
        }

        // Let all the running actions finish
        while (!finishRequests.isEmpty()) {
            finishRequests.remove().countDown();
        }

        // Wait for all the threads to finish
        waitForThreadsToFinish(threads);

        // Determine the running pattern based on maxConcurrentActions
        Preconditions.checkState(
                maxConcurrentActions >= 1 && maxConcurrentActions <= runnables.size());
        if (maxConcurrentActions == 1) {
            return RunningPattern.SEQUENTIAL;
        } else if (maxConcurrentActions == runnables.size()) {
            return RunningPattern.CONCURRENT;
        } else {
            return RunningPattern.MIXED;
        }
    }

    /**
     * Executes the runnables in separate threads and returns immediately after all the threads have
     * started execution (this method does not wait until all the threads have terminated).
     *
     * <p>This methods returns a map from the threads to any exceptions thrown during the execution
     * of the threads. Note that the map's values (if any) are not available immediately but only
     * after the threads have terminated.
     *
     * @param runnables the runnables to be executed
     * @return a map from the threads to any exceptions thrown during the execution of the threads
     */
    @NonNull
    private static Map<Thread, Optional<Throwable>> executeRunnablesInThreads(
            @NonNull List<Runnable> runnables) {
        ConcurrentMap<Thread, Optional<Throwable>> threads = new ConcurrentHashMap<>();
        CountDownLatch allThreadsStartedLatch = new CountDownLatch(runnables.size());

        for (Runnable runnable : runnables) {
            Thread thread = new Thread(() -> {
                allThreadsStartedLatch.countDown();
                runnable.run();
            });
            threads.put(thread, Optional.empty());
            thread.setUncaughtExceptionHandler(
                    (aThread, throwable) -> threads.put(aThread, Optional.of(throwable)));
        }

        for (Thread thread : threads.keySet()) {
            thread.start();
        }

        // Wait for all the threads to start execution
        try {
            allThreadsStartedLatch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        return threads;
    }

    /** Waits for all the threads to finish. */
    private static void waitForThreadsToFinish(@NonNull Map<Thread, Optional<Throwable>> threads) {
        // Wait for the threads to finish
        for (Thread thread : threads.keySet()) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        // Throw any exceptions that occurred during the execution of the threads
        //noinspection OptionalUsedAsFieldOrParameterType
        for (Optional<Throwable> throwable : threads.values()) {
            if (throwable.isPresent()) {
                throw new RuntimeException(throwable.get());
            }
        }
    }
}
