/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.deploy.liveedit;

import static com.android.tools.deploy.liveedit.Utils.buildClass;
import static com.android.tools.deploy.liveedit.Utils.classToType;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Assert;

public class TestMonitor {
    public void testInvokeObjectMonitor(Object lock) throws Exception {
        byte[] byteCode = buildClass(InvokeMonitor.class);

        CountDownLatch latch = new CountDownLatch(1);
        InvokeMonitor monitor = new InvokeMonitor(lock, latch);

        synchronized (lock) {
            Runnable runnable =
                    () -> {
                        MethodBodyEvaluator body =
                                new MethodBodyEvaluator(byteCode, "invoke", "()V");
                        String type = classToType(InvokeMonitor.class);
                        Object result = body.eval(monitor, type, new Object[] {});
                    };
            Thread t = new Thread(runnable);
            t.start();

            long start = System.currentTimeMillis();
            while (t.getState() != Thread.State.BLOCKED) {
                Thread.sleep(50);
                long now = System.currentTimeMillis();
                if (now - start > 1000) {
                    Assert.fail("Concurrent thread failed to acquire lock");
                }
            }
        }

        // Wait until the other thread exit its lock-object-synchronized block
        latch.await(1, TimeUnit.SECONDS);

        // Make sure the lock was released
        synchronized (lock) {
        }
    }

    @org.junit.Test
    public void testInvokeObjectMonitor() throws Exception {
        testInvokeObjectMonitor(new Object());
    }

    @org.junit.Test
    public void testInvokeClassMonitor() throws Exception {
        testInvokeObjectMonitor(Object.class);
    }
}
