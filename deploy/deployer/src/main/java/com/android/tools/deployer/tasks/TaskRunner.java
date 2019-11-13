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
}
