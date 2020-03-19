/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.zipflinger.Profiler.WARM_UP_ITERATION;
import static com.android.zipflinger.Profiler.prettyPrint;

import com.android.apksig.util.RunnablesExecutor;
import com.android.apksig.util.RunnablesProvider;
import com.android.tools.tracer.Trace;
import com.android.zipflinger.ApkMaker;
import com.android.zipflinger.Archive;
import com.android.zipflinger.BytesSource;
import com.android.zipflinger.Profiler;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.zip.Deflater;

public class ProfileV1 {

    private static final String BASE = "tools/base/signflinger/test/resources/";

    public static void main(String[] argv)
            throws InvalidKeyException, IOException, CertificateException, InvalidKeySpecException,
                    NoSuchAlgorithmException {
        Path src = Files.createTempDirectory("tmp" + System.nanoTime());
        src.toFile().mkdirs();

        String keyType = "dsa";
        int keySize = 1024;
        byte[] keyBytes = getResource(BASE + keyType + "-" + keySize + ".pk8");
        PrivateKey privateKey = SignedApkOptions.bytesToPrivateKey(keyType, keyBytes);

        byte[] certBytes = getResource(BASE + keyType + "-" + keySize + ".x509.pem");
        List<X509Certificate> certificates = SignedApkOptions.bytesToCertificateChain(certBytes);

        SignedApkOptions.Builder builder =
                new SignedApkOptions.Builder()
                        .setV2Enabled(false)
                        .setV1Enabled(true)
                        .setV1TrustManifest(true)
                        .setMinSdkVersion(21)
                        .setPrivateKey(privateKey)
                        .setCertificates(certificates)
                        .setExecutor(createExecutor());
        SignedApkOptions options = builder.build();

        for (int i = 0; i < WARM_UP_ITERATION; i++) {
            File zipFile = new File(src.toFile(), "profileCreate" + i + ".zip");
            try (Archive archive = new SignedApk(zipFile, options)) {
                ApkMaker.createArchive(archive);
            }
        }

        long start = System.nanoTime();
        File zipFile = new File(src.toFile(), "profileCreate.zip");
        byte[] dummyFile = new byte[1 << 14];
        Random r = new Random(0);
        r.nextBytes(dummyFile);
        BytesSource source = new BytesSource(dummyFile, "foo", Deflater.BEST_SPEED);

        try (Archive archive = new SignedApk(zipFile, options)) {
            ApkMaker.createArchive(archive);
        }

        Profiler.displayParameters();

        Trace.start();
        try (Trace t = Trace.begin("Creating archive");
                Archive archive = new SignedApk(zipFile, options)) {
            archive.delete("foo");
            archive.add(source);
        }
        long end = System.nanoTime();
        Trace.flush();
        prettyPrint("Create time (ms)", (int) ((end - start) / 1_000_000L));
    }

    private static byte[] getResource(String s) throws IOException {
        return Files.readAllBytes(Paths.get(s));
    }

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
}
