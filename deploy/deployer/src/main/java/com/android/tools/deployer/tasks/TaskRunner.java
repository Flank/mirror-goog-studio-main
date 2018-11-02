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
import com.android.tools.deployer.Trace;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Phaser;
import java.util.function.BiFunction;

public class TaskRunner {

    private final ExecutorService executor;
    private final Phaser phaser;

    public TaskRunner(ExecutorService executor) {
        this.executor = executor;
        this.phaser = new Phaser(1);
    }

    public <T> Task<T> create(T value) {
        SettableFuture<T> future = SettableFuture.create();
        future.set(value);
        return new Task<>(future);
    }

    public <I, O> Task<O> create(String name, ThrowingFunction<I, O> task, Task<I> input) {
        ListenableFuture<O> future =
                Futures.whenAllSucceed(ImmutableList.of(input.future))
                        .call(
                                () -> {
                                    try {
                                        phaser.register();
                                        Trace.begin("Task: " + name);
                                        // The input value is already done
                                        I value = input.future.get();
                                        return task.apply(value);
                                    } finally {
                                        phaser.arriveAndDeregister();
                                        Trace.end();
                                    }
                                },
                                executor);
        return new Task<>(future);
    }

    public <T, U, O> Task<O> create(
            String name, BiFunction<T, U, O> task, Task<T> input1, Task<U> input2) {
        ListenableFuture<O> future =
                Futures.whenAllSucceed(ImmutableList.of(input1.future, input2.future))
                        .call(
                                () -> {
                                    try {
                                        phaser.register();
                                        // The input value is already done
                                        Trace.begin("Task: " + name);
                                        T value1 = input1.future.get();
                                        U value2 = input2.future.get();
                                        return task.apply(value1, value2);
                                    } finally {
                                        Trace.end();
                                        phaser.arriveAndDeregister();
                                    }
                                },
                                executor);
        return new Task<>(future);
    }

    public void join() {
        try {
            Trace.begin("TaskRunner#Join");
            phaser.arriveAndAwaitAdvance();
        } finally {
            Trace.end();
        }
    }

    public static class Task<T> {
        private ListenableFuture<T> future;
        // Only can be created through the interface enforcing a no-cycle dependency graph.
        private Task(ListenableFuture<T> future) {
            this.future = future;
        }

        public T get() throws DeployerException {
            try {
                return future.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new DeployerException(DeployerException.Error.INTERRUPTED, e);
            }
        }
    }

    public interface ThrowingFunction<I, O> {
        O apply(I i) throws DeployerException, IOException;
    }
}
