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

import com.android.tools.deployer.DeployerException;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

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
        return create(id, function, null, input);
    }

    public <I, O, E extends Enum> Task<O> create(
            E id,
            ThrowingFunction<I, O> function,
            ThrowingFunction<I, Void> errorFunction,
            Task<I> input) {
        Callable<O> callable =
                () -> {
                    try {
                        return function.apply(input.future.get());
                    } catch (Exception e) {
                        if (errorFunction != null) {
                            I value = getTaskValue(input);
                            errorFunction.apply(value);
                        }
                        throw e;
                    }
                };
        Task<O> task = new Task<>(id.name(), callable, input);
        tasks.add(task);
        return task;
    }

    public <T, U, O, E extends Enum> Task<O> create(
            E id, ThrowingBiFunction<T, U, O> function, Task<T> input1, Task<U> input2) {
        return create(id, function, null, input1, input2);
    }

    public <T, U, O, E extends Enum> Task<O> create(
            E id,
            ThrowingBiFunction<T, U, O> function,
            ThrowingBiFunction<T, U, Void> errorFunction,
            Task<T> input1,
            Task<U> input2) {
        Callable<O> callable =
                () -> {
                    try {
                        // The input value is already done
                        T value1 = input1.future.get();
                        U value2 = input2.future.get();
                        return function.apply(value1, value2);
                    } catch (Exception e) {
                        T value1 = getTaskValue(input1);
                        U value2 = getTaskValue(input2);
                        if (errorFunction != null) {
                            errorFunction.apply(value1, value2);
                        }
                        throw e;
                    }
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
        return create(id, function, null, input1, input2, input3);
    }

    public <T, U, V, O, E extends Enum> Task<O> create(
            E id,
            ThrowingTriFunction<T, U, V, O> function,
            ThrowingTriFunction<T, U, V, Void> errorFunction,
            Task<T> input1,
            Task<U> input2,
            Task<V> input3) {
        Callable<O> callable =
                () -> {
                    try {
                        // The input value is already done
                        T value1 = input1.future.get();
                        U value2 = input2.future.get();
                        V value3 = input3.future.get();
                        return function.apply(value1, value2, value3);
                    } catch (Exception e) {
                        T value1 = getTaskValue(input1);
                        U value2 = getTaskValue(input2);
                        V value3 = getTaskValue(input3);
                        if (errorFunction != null) {
                            errorFunction.apply(value1, value2, value3);
                        }
                        throw e;
                    }
                };
        Task<O> task = new Task<>(id.name(), callable, input1, input2, input3);
        tasks.add(task);
        return task;
    }

    public <T, U, V, W, O, E extends Enum> Task<O> create(
            E id,
            ThrowingQuadFunction<T, U, V, W, O> function,
            Task<T> input1,
            Task<U> input2,
            Task<V> input3,
            Task<W> input4) {
        return create(id, function, null, input1, input2, input3, input4);
    }

    public <T, U, V, W, O, E extends Enum> Task<O> create(
            E id,
            ThrowingQuadFunction<T, U, V, W, O> function,
            ThrowingQuadFunction<T, U, V, W, Void> errorFunction,
            Task<T> input1,
            Task<U> input2,
            Task<V> input3,
            Task<W> input4) {
        Callable<O> callable =
                () -> {
                    try {
                        // The input value is already done
                        T value1 = input1.future.get();
                        U value2 = input2.future.get();
                        V value3 = input3.future.get();
                        W value4 = input4.future.get();
                        return function.apply(value1, value2, value3, value4);
                    } catch (Exception e) {
                        T value1 = getTaskValue(input1);
                        U value2 = getTaskValue(input2);
                        V value3 = getTaskValue(input3);
                        W value4 = getTaskValue(input4);
                        if (errorFunction != null) {
                            errorFunction.apply(value1, value2, value3, value4);
                        }
                        throw e;
                    }
                };
        Task<O> task = new Task<>(id.name(), callable, input1, input2, input3, input4);
        tasks.add(task);
        return task;
    }

    public <T, U, V, W, X, O, E extends Enum> Task<O> create(
            E id,
            ThrowingPentaFunction<T, U, V, W, X, O> function,
            Task<T> input1,
            Task<U> input2,
            Task<V> input3,
            Task<W> input4,
            Task<X> input5) {
        return create(id, function, null, input1, input2, input3, input4, input5);
    }

    public <T, U, V, W, X, O, E extends Enum> Task<O> create(
            E id,
            ThrowingPentaFunction<T, U, V, W, X, O> function,
            ThrowingPentaFunction<T, U, V, W, X, Void> errorFunction,
            Task<T> input1,
            Task<U> input2,
            Task<V> input3,
            Task<W> input4,
            Task<X> input5) {
        Callable<O> callable =
                () -> {
                    try {
                        // The input value is already done
                        T value1 = input1.future.get();
                        U value2 = input2.future.get();
                        V value3 = input3.future.get();
                        W value4 = input4.future.get();
                        X value5 = input5.future.get();
                        return function.apply(value1, value2, value3, value4, value5);
                    } catch (Exception e) {
                        T value1 = getTaskValue(input1);
                        U value2 = getTaskValue(input2);
                        V value3 = getTaskValue(input3);
                        W value4 = getTaskValue(input4);
                        X value5 = getTaskValue(input5);
                        if (errorFunction != null) {
                            errorFunction.apply(value1, value2, value3, value4, value5);
                        }
                        throw e;
                    }
                };
        Task<O> task = new Task<>(id.name(), callable, input1, input2, input3, input4, input5);
        tasks.add(task);
        return task;
    }

    public <T, U, V, W, X, Y, O, E extends Enum> Task<O> create(
            E id,
            ThrowingHexFunction<T, U, V, W, X, Y, O> function,
            Task<T> input1,
            Task<U> input2,
            Task<V> input3,
            Task<W> input4,
            Task<X> input5,
            Task<Y> input6) {
        return create(id, function, null, input1, input2, input3, input4, input5, input6);
    }

    public <T, U, V, W, X, Y, O, E extends Enum> Task<O> create(
            E id,
            ThrowingHexFunction<T, U, V, W, X, Y, O> function,
            ThrowingHexFunction<T, U, V, W, X, Y, Void> errorFunction,
            Task<T> input1,
            Task<U> input2,
            Task<V> input3,
            Task<W> input4,
            Task<X> input5,
            Task<Y> input6) {
        Callable<O> callable =
                () -> {
                    try {
                        // The input value is already done
                        T value1 = input1.future.get();
                        U value2 = input2.future.get();
                        V value3 = input3.future.get();
                        W value4 = input4.future.get();
                        X value5 = input5.future.get();
                        Y value6 = input6.future.get();
                        return function.apply(value1, value2, value3, value4, value5, value6);
                    } catch (Exception e) {
                        T value1 = getTaskValue(input1);
                        U value2 = getTaskValue(input2);
                        V value3 = getTaskValue(input3);
                        W value4 = getTaskValue(input4);
                        X value5 = getTaskValue(input5);
                        Y value6 = getTaskValue(input6);
                        if (errorFunction != null) {
                            errorFunction.apply(value1, value2, value3, value4, value5, value6);
                        }
                        throw e;
                    }
                };
        Task<O> task =
                new Task<>(id.name(), callable, input1, input2, input3, input4, input5, input6);
        tasks.add(task);
        return task;
    }

    private <O> O getTaskValue(Task<O> task) {
        try {
            return task.get();
        } catch (Exception e) {
            return null;
        }
    }

    private void runInternal(ArrayList<Task<?>> batch) throws DeployerException {
        for (Task<?> task : batch) {
            task.run(executor);
        }

        joinAllTasks(batch, false);

        // If any task has throw an exception, retrieve it and throw the first one here.
        joinAllTasks(batch, true);
    }

    private void joinAllTasks(ArrayList<Task<?>> batch, boolean throwExceptionOnTaskFail)
            throws DeployerException {
        for (Task<?> task : batch) {
            try {
                task.get();
            } catch (Exception e) {
                if (throwExceptionOnTaskFail) {
                    throw e;
                }
            }
        }
    }

    /**
     * Runs and waits for all the pending tasks to be executed.
     *
     * <p>If no tasks are pending this is a no-op, except that it will wait for the existing running
     * tasks to end.
     */
    public TaskResult run() {
        running.acquireUninterruptibly();
        ArrayList<Task<?>> batch = new ArrayList<>(tasks);
        try {
            tasks.clear();
            runInternal(batch);
            return new TaskResult(batch);
        } catch (DeployerException e) {
            return new TaskResult(batch, e);
        } catch (Exception e) {
            return new TaskResult(batch, DeployerException.runtimeException(e));
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

    public interface ThrowingFunction<I, O> {
        O apply(I i) throws Exception;
    }

    public interface ThrowingBiFunction<T, U, R> {
        R apply(T t, U u) throws Exception;
    }

    public interface ThrowingTriFunction<T, U, V, R> {
        R apply(T t, U u, V v) throws Exception;
    }

    public interface ThrowingQuadFunction<T, U, V, W, R> {
        R apply(T t, U u, V v, W w) throws Exception;
    }

    public interface ThrowingPentaFunction<T, U, V, W, X, R> {
        R apply(T t, U u, V v, W w, X x) throws Exception;
    }

    public interface ThrowingHexFunction<T, U, V, W, X, Y, R> {
        R apply(T t, U u, V v, W w, X x, Y y) throws Exception;
    }
}
