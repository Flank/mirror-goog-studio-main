/*
 * Copyright (C) 2013 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkArgument;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.annotations.concurrency.GuardedBy;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * A utility wrapper around a {@link CompletionService} using an ThreadPoolExecutor so that it
 * is possible to wait on all the tasks.
 *
 * Tasks are submitted as {@link Callable} with {@link #execute(java.util.concurrent.Callable)}.
 *
 * After executing all tasks, it is possible to wait on them with
 * {@link #waitForTasksWithQuickFail(boolean)}, or {@link #waitForAllTasks()}.
 *
 * This class is not Thread safe!
 *
 * @param <T> Result type of all the tasks.
 */
public class WaitableExecutor<T> {

    @Nullable private final ExecutorService mExecutorService;
    @NonNull private final CompletionService<T> mCompletionService;
    @NonNull private final Set<Future<T>> mFutureSet = Sets.newConcurrentHashSet();
    private int parallelism;

    WaitableExecutor(
            @Nullable ExecutorService mExecutorService,
            @NonNull CompletionService<T> mCompletionService,
            int parallelism) {
        this.mExecutorService = mExecutorService;
        this.mCompletionService = mCompletionService;
        this.parallelism = parallelism;
    }

    /**
     * Creates a new {@link WaitableExecutor} which uses a globally shared thread pool.
     *
     * <p>Calling {@link #waitForAllTasks()} on this instance will only block on tasks submitted to
     * this instance, but the tasks themselves will compete for threads with tasks submitted to
     * other {@link WaitableExecutor} instances created with this factory method.
     *
     * <p>This is the recommended way of getting a {@link WaitableExecutor}, since it makes sure the
     * total number of threads running doesn't exceed the value configured by the user.
     *
     * @see ExecutorSingleton#sThreadPoolSize
     */
    public static <T> WaitableExecutor<T> useGlobalSharedThreadPool() {
        return new WaitableExecutor<>(
                null,
                new ExecutorCompletionService<T>(ExecutorSingleton.getExecutor()),
                ExecutorSingleton.getThreadPoolSize());
    }

    /**
     * Creates a new {@link WaitableExecutor} which uses a globally shared thread pool, but limits
     * the number of tasks (scheduled through this {@link WaitableExecutor}) that can execute in
     * parallel. The thread submitting tasks will not block, but tasks may be queued before being
     * passed to the {@link CompletionService}.
     *
     * @param parallelTaskLimit number of tasks that can execute in parallel
     * @see #useGlobalSharedThreadPool()
     */
    public static <T> WaitableExecutor<T> useGlobalSharedThreadPoolWithLimit(
            int parallelTaskLimit) {
        checkArgument(parallelTaskLimit > 0, "parallelTaskLimit needs to be a positive number.");
        return new BoundedWaitableExecutor<>(
                null,
                new ExecutorCompletionService<>(ExecutorSingleton.getExecutor()),
                parallelTaskLimit);
    }

    /**
     * Creates a new {@link WaitableExecutor} which uses a newly allocated thread pool of the given
     * size.
     *
     * <p>If you can, use the {@link #useGlobalSharedThreadPool()} factory method instead.
     *
     * @see #useGlobalSharedThreadPool()
     */
    public static <T> WaitableExecutor<T> useNewFixedSizeThreadPool(int nThreads) {
        checkArgument(nThreads > 0, "Number of threads needs to be a positive number.");
        ExecutorService executorService = Executors.newFixedThreadPool(nThreads);
        return new WaitableExecutor<>(
                executorService, new ExecutorCompletionService<T>(executorService), nThreads);
    }

    /**
     * Creates a new {@link WaitableExecutor} that executes all jobs on the thread that schedules
     * them, removing any concurrency.
     *
     * @see MoreExecutors#newDirectExecutorService()
     */
    @VisibleForTesting
    @SuppressWarnings("unused") // Temporarily used when debugging.
    public static <T> WaitableExecutor<T> useDirectExecutor() {
        return new WaitableExecutor<>(
                null,
                new ExecutorCompletionService<T>(MoreExecutors.newDirectExecutorService()),
                1);
    }

    /**
     * Submits a Callable for execution.
     *
     * @param callable the callable to run.
     */
    public void execute(Callable<T> callable) {
        boolean added = mFutureSet.add(mCompletionService.submit(callable));
        Preconditions.checkState(added, "Failed to add callable");
    }

    /**
     * Returns the number of tasks that have been submitted for execution but the results have not
     * been fetched yet.
     */
    int getUnprocessedTasksCount() {
        return mFutureSet.size();
    }

    /**
     * Waits for all tasks to be executed. If a tasks throws an exception, it will be thrown from
     * this method inside a RuntimeException, preventing access to the result of the other threads.
     *
     * <p>If you want to get the results of all tasks (result and/or exception), use {@link
     * #waitForAllTasks()}
     *
     * @param cancelRemaining if true, and a task fails, cancel all remaining tasks.
     * @return a list of all the return values from the tasks.
     * @throws InterruptedException if this thread was interrupted. Not if the tasks were
     *     interrupted.
     */
    public List<T> waitForTasksWithQuickFail(boolean cancelRemaining) throws InterruptedException {
        List<T> results = Lists.newArrayListWithCapacity(getUnprocessedTasksCount());
        try {
            while (getUnprocessedTasksCount() > 0) {
                Future<T> future = mCompletionService.take();

                assert mFutureSet.contains(future);
                mFutureSet.remove(future);

                // Get the result from the task. If the task threw an exception,
                // this will throw it, wrapped in an ExecutionException, caught below.
                results.add(future.get());
            }
        } catch (ExecutionException e) {
            if (cancelRemaining) {
                cancelAllTasks();
            }

            // get the original exception and throw that one.
            throw new RuntimeException(e.getCause());
        } finally {
            if (mExecutorService != null) {
                mExecutorService.shutdownNow();
            }
        }

        return results;
    }

    public static final class TaskResult<T> {
        public T value;
        public Throwable exception;

        static <T> TaskResult<T> withValue(T value) {
            TaskResult<T> result = new TaskResult<>(null);
            result.value = value;
            return result;
        }

        TaskResult(Throwable cause) {
            exception = cause;
        }
    }

    /**
     * Waits for all tasks to be executed, and returns a {@link TaskResult} for each, containing
     * either the result or the exception thrown by the task.
     *
     * <p>If a task is cancelled (and it threw InterruptedException) then the result for the task is
     * *not* included.
     *
     * @return a list of all the return values from the tasks.
     * @throws InterruptedException if this thread was interrupted. Not if the tasks were
     *     interrupted.
     */
    @NonNull
    public List<TaskResult<T>> waitForAllTasks() throws InterruptedException {
        List<TaskResult<T>> results = Lists.newArrayListWithCapacity(getUnprocessedTasksCount());
        try {
            while (getUnprocessedTasksCount() > 0) {
                Future<T> future = mCompletionService.take();

                assert mFutureSet.contains(future);
                mFutureSet.remove(future);

                // Get the result from the task.
                try {
                    results.add(TaskResult.withValue(future.get()));
                } catch (ExecutionException e) {
                    // the original exception thrown by the task is the cause of this one.
                    Throwable cause = e.getCause();

                    //noinspection StatementWithEmptyBody
                    if (cause instanceof InterruptedException) {
                        // if the task was cancelled we probably don't care about its result.
                    } else {
                        // there was an error.
                        results.add(new TaskResult<>(cause));
                    }
                }
            }
        } finally {
            if (mExecutorService != null) {
                mExecutorService.shutdownNow();
            }
        }

        return results;
    }

    /**
     * Cancel all remaining tasks.
     */
    public void cancelAllTasks() {
        for (Future<T> future : mFutureSet) {
            future.cancel(true /*mayInterruptIfRunning*/);
        }
    }

    /** Returns the parallelism of this executor i.e. how many tasks can run in parallel. */
    public int getParallelism() {
        return parallelism;
    }

    /**
     * A {@link WaitableExecutor} that limits the number of tasks (scheduled through this executor)
     * that can execute in parallel.
     */
    private static class BoundedWaitableExecutor<T> extends WaitableExecutor<T> {
        private final int bound;

        @GuardedBy("overflow")
        private final Queue<Callable<T>> overflow;

        @GuardedBy("overflow")
        private int inCompletionService;

        BoundedWaitableExecutor(
                @Nullable ExecutorService mExecutorService,
                @NonNull CompletionService<T> mCompletionService,
                int bound) {
            super(mExecutorService, mCompletionService, bound);
            this.bound = bound;
            this.inCompletionService = 0;
            this.overflow = new LinkedList<>();
        }

        @Override
        public void execute(Callable<T> callable) {
            // Amend the callable to schedule more work once it's done.
            Callable<T> wrapper =
                    () -> {
                        try {
                            return callable.call();
                        } finally {
                            synchronized (overflow) {
                                Callable<T> next = overflow.poll();
                                if (next != null) {
                                    super.execute(next);
                                } else {
                                    inCompletionService--;
                                }
                            }
                        }
                    };

            synchronized (overflow) {
                if (inCompletionService < bound) {
                    inCompletionService++;
                    super.execute(wrapper);
                } else {
                    overflow.add(wrapper);
                }
            }
        }

        @Override
        public void cancelAllTasks() {
            synchronized (overflow) {
                overflow.clear();
            }
            super.cancelAllTasks();
        }

        @Override
        int getUnprocessedTasksCount() {
            synchronized (overflow) {
                return super.getUnprocessedTasksCount() + overflow.size();
            }
        }
    }
}
