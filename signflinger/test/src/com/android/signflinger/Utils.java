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
import com.android.apksig.apk.ApkUtils;
import com.android.apksig.util.DataSource;
import com.android.apksig.util.DataSources;
import com.android.apksig.util.RunnablesExecutor;
import com.android.apksig.util.RunnablesProvider;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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

    public static void verifyApk(File toVerify)
            throws ApkFormatException, NoSuchAlgorithmException, IOException {
        verifyApk(toVerify, null);
    }

    public static void verifyApk(File toVerify, File v4SignatureFile)
            throws ApkFormatException, NoSuchAlgorithmException, IOException {
        ApkVerifier.Builder apkVerifierBuilder = new ApkVerifier.Builder(toVerify);
        apkVerifierBuilder.setV4SignatureFile(v4SignatureFile);
        // Note, Android Nougat (API 24) introduced v2 signing (it still supports
        // v1 signing).
        apkVerifierBuilder.setMinCheckedPlatformVersion(24);
        ApkVerifier verifier = apkVerifierBuilder.build();
        ApkVerifier.Result result = verifier.verify();
        if (result.containsErrors()) {
            String errors = result.getErrors().toString();
            String warning = result.getWarnings().toString();
            String message = String.format("Errors: '%s', Warnings: '%s'", errors, warning);
            for (ApkVerifier.Result.V1SchemeSignerInfo signer : result.getV1SchemeSigners()) {
                for (ApkVerifier.IssueWithParams error : signer.getErrors()) {
                    message += ", V1Signer Error:" + error.toString() + "\n";
                }
                for (ApkVerifier.IssueWithParams error : signer.getWarnings()) {
                    message += ", V1Signer Warning:" + error.toString() + "\n";
                }
            }
            for (ApkVerifier.Result.V2SchemeSignerInfo signer : result.getV2SchemeSigners()) {
                for (ApkVerifier.IssueWithParams error : signer.getErrors()) {
                    message += ", V2Signer Error:" + error.toString() + "\n";
                }
                for (ApkVerifier.IssueWithParams error : signer.getWarnings()) {
                    message += ", V2Signer Warning:" + error.toString() + "\n";
                }
            }
            Assert.fail(message);
        }
        Assert.assertTrue(result.isVerified());
    }

    public static void verifySdkDependencyBlock(File file) throws Exception {
        DataSource apk = DataSources.asDataSource(new RandomAccessFile(file, "r"));
        ApkUtils.ZipSections zipSections = ApkUtils.findZipSections(apk);

        ApkUtils.ApkSigningBlock apkSigningBlock = ApkUtils.findApkSigningBlock(apk, zipSections);
        DataSource signingBlockContents = apkSigningBlock.getContents();
        ByteBuffer signingBlockContent =
                signingBlockContents.getByteBuffer(0, Math.toIntExact(signingBlockContents.size()));
        signingBlockContent.order(ByteOrder.LITTLE_ENDIAN);

        signingBlockContent.getLong(); // Size of entire block.
        long sizeOfSigner = signingBlockContent.getLong();
        signingBlockContent.get(new byte[(int) sizeOfSigner]); // skip ID and Value of singer
        signingBlockContent.getLong(); // Size of Dependency info block.
        Assert.assertEquals(SignedApk.DEPENDENCY_INFO_BLOCK_ID, signingBlockContent.getInt());
    }
}
