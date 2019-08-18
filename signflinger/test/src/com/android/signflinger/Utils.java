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

package com.android.signflinger;

import com.android.apksig.ApkVerifier;
import com.android.apksig.apk.ApkFormatException;
import com.android.apksig.util.RunnablesExecutor;
import com.android.apksig.util.RunnablesProvider;
import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import org.junit.Assert;

public final class Utils {

    private Utils() {}

    public static RunnablesExecutor createExecutor() {
        RunnablesExecutor executor =
                (RunnablesProvider provider) -> {
                    ForkJoinPool forkJoinPool = ForkJoinPool.commonPool();
                    int jobCount = forkJoinPool.getParallelism();
                    List<Future<?>> jobs = new ArrayList<>(jobCount);

                    for (int i = 0; i < jobCount; i++) {
                        jobs.add(forkJoinPool.submit(provider.createRunnable()));
                    }

                    try {
                        for (Future<?> future : jobs) {
                            future.get();
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                };
        return executor;
    }

    public static void verifyApk(File file)
            throws ApkFormatException, NoSuchAlgorithmException, IOException {
        ApkVerifier.Builder apkVerifierBuilder = new ApkVerifier.Builder(file);
        // Note, Android Nougat (API 24) introduced v2 signing (it still supports
        // v1 signing).
        apkVerifierBuilder.setMinCheckedPlatformVersion(24);
        ApkVerifier verifier = apkVerifierBuilder.build();
        ApkVerifier.Result result = verifier.verify();
        if (result.containsErrors()) {
            String errors = result.getErrors().toString();
            String warning = result.getWarnings().toString();
            String message = String.format("Errors: '%s', Warnings: '%s'", errors, warning);
            Assert.fail(message);
        }
        Assert.assertTrue(result.isVerified());
    }
}
