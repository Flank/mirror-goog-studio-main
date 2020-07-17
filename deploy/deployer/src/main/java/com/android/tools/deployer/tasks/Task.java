/*
 * Copyright (C) 2019 The Android Open Source Project
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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

public class Task<T> {
    private final Callable<T> callable;
    private final Task<?>[] inputs;
    final SettableFuture<T> future;
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
                throw DeployerException.runtimeException(e);
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
