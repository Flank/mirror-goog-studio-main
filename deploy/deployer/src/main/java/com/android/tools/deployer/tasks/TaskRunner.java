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

import com.android.tools.deployer.DeployMetric;
import com.android.tools.deployer.DeployerException;
import com.android.tools.tracer.Trace;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

public class TaskRunner {

    private final ExecutorService executor;
    private final ArrayList<Task<?>> tasks;
    private final Semaphore running;

    public TaskRunner(ExecutorService executor) {
        this.executor = executor;
        this.tasks = new ArrayList<>();
        this.running = new Semaphore(1);
    }

    public <T> Task<T> create(T value) {
        Task<T> task = new Task<>("", () -> value);
        tasks.add(task);

        return task;
    }

    public <I, O, E extends Enum> Task<O> create(
            E id, ThrowingFunction<I, O> function, Task<I> input) {
        Callable<O> callable = () -> function.apply(input.future.get());
        Task<O> task = new Task<>(id.name(), callable, input);
        tasks.add(task);
        return task;
    }

    public <T, U, O, E extends Enum> Task<O> create(
            E id, ThrowingBiFunction<T, U, O> function, Task<T> input1, Task<U> input2) {
        Callable<O> callable =
                () -> {
                    // The input value is already done
                    T value1 = input1.future.get();
                    U value2 = input2.future.get();
                    return function.apply(value1, value2);
                };
        Task<O> task = new Task<>(id.name(), callable, input1, input2);
        tasks.add(task);
        return task;
    }

    public <T, U, V, O, E extends Enum> Task<O> create(
            E id,
            ThrowingTriFunction<T, U, V, O> function,
            Task<T> input1,
            Task<U> input2,
            Task<V> input3) {
        Callable<O> callable =
                () -> {
                    // The input value is already done
                    T value1 = input1.future.get();
                    U value2 = input2.future.get();
                    V value3 = input3.future.get();
                    return function.apply(value1, value2, value3);
                };
        Task<O> task = new Task<>(id.name(), callable, input1, input2, input3);
        tasks.add(task);
        return task;
    }

    private void runInternal(ArrayList<Task<?>> batch) throws DeployerException {
        for (Task<?> task : batch) {
            task.run(executor);
        }
        for (Task<?> task : batch) {
            task.get();
        }
    }

    /**
     * Runs and waits for all the pending tasks to be executed.
     *
     * <p>If no tasks are pending this is a no-op, except that it will wait for the existing running
     * tasks to end.
     *
     * @throws DeployerException if a task throws it while executing
     */
    public List<Task<?>> run() throws DeployerException {
        try {
            running.acquireUninterruptibly();
            ArrayList<Task<?>> batch = new ArrayList<>(tasks);
            tasks.clear();
            runInternal(batch);
            return batch;
        } finally {
            running.release();
        }
    }

    /**
     * Utility method to run the tasks on a separate executor. The exceptions are then thrown as
     * runtime exceptions.
     */
    public void runAsync(Executor executor) {
        ArrayList<Task<?>> batch = new ArrayList<>(tasks);
        tasks.clear();
        running.acquireUninterruptibly();
        executor.execute(
                () -> {
                    try {
                        runInternal(batch);
                    } catch (DeployerException e) {
                        throw new RuntimeException(e);
                    } finally {
                        running.release();
                    }
                });
    }

    /**
     * Runs the tasks asynchronously using the runner's executor for the controlling thread.
     * Subsequent work queued via run or runAsync on this TaskRunner will wait until the async batch
     * completes before beginning.
     */
    public void runAsync() {
        runAsync(executor);
    }

    public static class Task<T> {
        private final Callable<T> callable;
        private final Task<?>[] inputs;
        private final SettableFuture<T> future;
        private DeployMetric metric;

        // Only can be created through the interface enforcing a no-cycle dependency graph.
        Task(String name, Callable<T> callable, Task<?>... inputs) {
            this.future = SettableFuture.create();
            this.inputs = inputs;
            this.callable =
                    () -> {
                        String status = "Not Started";
                        metric = new DeployMetric(name);
                        try (Trace ignored = Trace.begin(name)) {
                            T value = callable.call();
                            status = "Success";
                            return value;
                        } catch (ExecutionException e) {
                            // Dropped this task because one of the previous task failed.
                            status = "Dropped";
                            throw e;
                        } catch (Throwable t) {
                            status = "Failed";
                            throw t;
                        } finally {
                            metric.finish(status);
                        }
                    };
        }

        public T get() throws DeployerException {
            try {
                return future.get();
            } catch (InterruptedException e) {
                throw DeployerException.interrupted(e.getMessage());
            } catch (ExecutionException e) {
                if (e.getCause() instanceof DeployerException) {
                    throw (DeployerException) e.getCause();
                } else {
                    throw new IllegalStateException(e);
                }
            }
        }

        public void run(Executor executor) {
            List<? extends SettableFuture<?>> futures =
                    Arrays.stream(inputs).map(t -> t.future).collect(Collectors.toList());
            future.setFuture(Futures.whenAllComplete(futures).call(callable, executor));
        }

        public DeployMetric getMetric() {
            return metric;
        }
    }

    public interface ThrowingFunction<I, O> {
        O apply(I i) throws Exception;
    }

    public interface ThrowingBiFunction<T, U, R> {
        R apply(T t, U u) throws Exception;
    }

    public interface ThrowingTriFunction<T, U, V, R> {
        R apply(T t, U u, V v) throws Exception;
    }
}
