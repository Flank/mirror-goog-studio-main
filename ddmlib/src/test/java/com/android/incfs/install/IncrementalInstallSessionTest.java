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

package com.android.incfs.install;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.annotations.concurrency.GuardedBy;
import com.google.common.base.Charsets;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import junit.framework.TestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;

public class IncrementalInstallSessionTest extends TestCase {
    private static int TEST_TIMEOUT = 5;

    @Rule private final ExpectedException mExpectedException = ExpectedException.none();

    private Path mTestApk0;
    private Path mTestSignature0;
    private Path mTestApk1;
    private Path mTestSignature1;

    private FileChannel mOpenTestApk0;
    private FileChannel mOpenTestSignature0;
    private FileChannel mOpenTestApk1;
    private FileChannel mOpenTestSignature1;

    // Data constants
    private static final short APK_ID_0 = 0;
    private static final short APK_ID_1 = 1;
    private static final byte BLOCK_DATA = 0;
    private static final byte BLOCK_TREE = 1;
    private static final short TYPE_MISSING_BLOCK = 1;
    private static final short TYPE_PREFETCH = 2;
    private static final byte COMPRESSION_NONE = 0;

    // Control requests
    private final Request REQUEST_STREAMING_COMPLETED = new Request(APK_ID_0, (short) 0, 0);
    private final Request REQUEST_DESTROY = new Request(APK_ID_0, (short) 3, 0);

    // Control responses
    private final Response RESPONSE_CLOSE =
            new Response((short) -1, (byte) 0, COMPRESSION_NONE, 0, (short) 0, null, 0);

    // Apk 0 requests
    private final String APK_0 = "testdata/com/android/ddmlib/inc-test-0.test-apk";
    private final Request PREFETCH_0_601 = new Request(APK_ID_0, TYPE_PREFETCH, 601);
    private final Request REQUEST_BLOCK_MISSING_0_601 =
            new Request(APK_ID_0, TYPE_MISSING_BLOCK, 601);
    private final Request REQUEST_BLOCK_0_MISSING_602 =
            new Request(APK_ID_0, TYPE_MISSING_BLOCK, 602);
    private final Request REQUEST_BLOCK_0_MISSING_617 =
            new Request(APK_ID_0, TYPE_MISSING_BLOCK, 617);

    // Apk 0 responses
    private final Response RESPONSE_TREE_0_0 =
            new Response(
                    APK_ID_0,
                    BLOCK_TREE,
                    COMPRESSION_NONE,
                    0,
                    (short) 4096,
                    () -> mOpenTestSignature0,
                    1450);

    private final Response RESPONSE_TREE_0_5 =
            new Response(
                    APK_ID_0,
                    BLOCK_TREE,
                    COMPRESSION_NONE,
                    5,
                    (short) 4096,
                    () -> mOpenTestSignature0,
                    21930);

    private final Response RESPONSE_DATA_0_601 =
            new Response(
                    APK_ID_0,
                    BLOCK_DATA,
                    COMPRESSION_NONE,
                    601,
                    (short) 4096,
                    () -> mOpenTestApk0,
                    2461696);

    private final Response RESPONSE_DATA_0_602 =
            new Response(
                    APK_ID_0,
                    BLOCK_DATA,
                    COMPRESSION_NONE,
                    602,
                    (short) 4096,
                    () -> mOpenTestApk0,
                    2465792);

    private final Response RESPONSE_DATA_0_617 =
            new Response(
                    APK_ID_0,
                    BLOCK_DATA,
                    COMPRESSION_NONE,
                    617,
                    (short) 1684,
                    () -> mOpenTestApk0,
                    2527232);

    // Apk 1 requests
    private final String APK_1 = "testdata/com/android/ddmlib/inc-test-1.test-apk";
    private final Request REQUEST_BLOCK_MISSING_1_775 =
            new Request(APK_ID_1, TYPE_MISSING_BLOCK, 775 /* blockIndex */);

    // Apk 1 responses
    private final Response RESPONSE_TREE_1_0 =
            new Response(
                    APK_ID_1,
                    BLOCK_TREE,
                    COMPRESSION_NONE,
                    0 /* blockIndex */,
                    (short) 4096,
                    () -> mOpenTestSignature1,
                    1450);
    private final Response RESPONSE_TREE_1_7 =
            new Response(
                    APK_ID_1,
                    BLOCK_TREE,
                    COMPRESSION_NONE,
                    7 /* blockIndex */,
                    (short) 4096,
                    () -> mOpenTestSignature1,
                    30122);
    private final Response RESPONSE_DATA_1_775 =
            new Response(
                    APK_ID_1,
                    (byte) 0,
                    COMPRESSION_NONE,
                    775 /* blockIndex */,
                    (short) 4096,
                    () -> mOpenTestApk1,
                    3174400);

    @Before
    public void setUp() throws Exception {
        final String APK_0_TEMP = "test0";
        mTestApk0 = copyResourceToTemp(APK_0, APK_0_TEMP, "apk");
        mTestSignature0 = copyResourceToTemp(APK_0 + ".idsig", APK_0_TEMP, "apk.idsig");
        mOpenTestApk0 = FileChannel.open(mTestApk0);
        mOpenTestSignature0 = FileChannel.open(mTestSignature0);

        final String APK_1_TEMP = "test1";
        mTestApk1 = copyResourceToTemp(APK_1, APK_1_TEMP, "apk");
        mTestSignature1 = copyResourceToTemp(APK_1 + ".idsig", APK_1_TEMP, "apk.idsig");
        mOpenTestApk1 = FileChannel.open(mTestApk1);
        mOpenTestSignature1 = FileChannel.open(mTestSignature1);
    }

    @After
    public void tearDown() throws Exception {
        mOpenTestApk0.close();
        mOpenTestSignature0.close();
        mOpenTestApk1.close();
        mOpenTestSignature1.close();
    }

    public void testArguments() throws Exception {
        final MockDevice mockDevice = new MockDevice();
        try (IncrementalInstallSession session =
                new IncrementalInstallSession.Builder()
                        .addApk(mTestApk0, mTestSignature0)
                        .build()) {
            session.start(Executors.newCachedThreadPool(), mockDevice.getFactory());
            mockDevice.expectHandsHake();
            assertEquals("package", mockDevice.mService);
            assertEquals("install-incremental", mockDevice.mParams[0]);
            assertThat(mockDevice.mParams[1])
                    .endsWith(
                            ":2528916:0:AgAAAC0AAAABAAAADAAAAAAgAAAA+Tr4V/i3mydUtLCdqN/sJR6KqCVnsRX"
                                    + "mqqSrTpmrA4FtBQAAIAAAAD3DzGjGE+nC480t2NBn/sf1zMymEqQGfbk258/"
                                    + "pBPY/DwMAADCCAwswggHzoAMCAQICFH6YSPgululekaJrD+/gluXTuXBnMA0"
                                    + "GCSqGSIb3DQEBCwUAMBUxEzARBgNVBAMMCnJyb190ZXN0X2EwHhcNMjAwNTE"
                                    + "0MTYzODAwWhcNNDcwOTMwMTYzODAwWjAVMRMwEQYDVQQDDApycm9fdGVzdF9"
                                    + "hMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAr1zPLXKkBcB+4kB"
                                    + "uvvJgMdgA4cG7avvEXJos+8L4lmH/3hQ0br2f5mupcXlSe5nGxewOa4pxCnY"
                                    + "DeYZVtlJW05rl+WDol5192NiebfvD4mqu/OyL1sZGsMJI9DstR5NNWeMNx02"
                                    + "J7WKot+m6uEMjacIedeVaoLxMgh0+yD7UIl438HE1tLYVJUg8g5IMavTcTRC"
                                    + "YGhxzmpipHjAjvApAVpIZXD1743DPAzxZsswyPQs7/CGBWUG3Bvzdhjx/YUr"
                                    + "XjJ7gvs8qDIqNFf1UXLUBpQAPAE7IXgZJ4NM3DUbvWRiGr5OQGgpZgRU3kMd"
                                    + "NY9xxUjvFA3QyeR5PLg06nnDy6QIDAQABo1MwUTAdBgNVHQ4EFgQUj2yHc0p"
                                    + "D285bY96iEtdyzQ4siIgwHwYDVR0jBBgwFoAUj2yHc0pD285bY96iEtdyzQ4"
                                    + "siIgwDwYDVR0TAQH/BAUwAwEB/zANBgkqhkiG9w0BAQsFAAOCAQEAh0L7zBs"
                                    + "jL1SWljBCcFXVJ7M3V4gJVLQ3f9h6XRjsVusVVQL73j/ISe9YFhUO0LNzK94"
                                    + "8XJWwt+6lQD4kG/JJA/KlIKEGDAuN2rIn+iZ+uTlNUJA0Rd2xRz/q2VUjxML"
                                    + "b5WDGzsxzzi6+xxEsJTO0wehKGeKDrKPvo02CPzkon9TDijjGzreF/wxPz13"
                                    + "/NWshbLkY6aHAapy8DqLqwATHGimwPdowdC0U7YL+Z4Did/uT9XE4h5LRW56"
                                    + "lVmvPwN7kgHTUeXaTlIx/pwh08D5euG+0e9D4A8Z6KjLNMSO1bSaL16K+uDm"
                                    + "cvT/Fk3bq8+Vf85NiFUotN9m39Gk8Wpr6lQAAAAAmAQAAMIIBIjANBgkqhki"
                                    + "G9w0BAQEFAAOCAQ8AMIIBCgKCAQEAr1zPLXKkBcB+4kBuvvJgMdgA4cG7avv"
                                    + "EXJos+8L4lmH/3hQ0br2f5mupcXlSe5nGxewOa4pxCnYDeYZVtlJW05rl+WD"
                                    + "ol5192NiebfvD4mqu/OyL1sZGsMJI9DstR5NNWeMNx02J7WKot+m6uEMjacI"
                                    + "edeVaoLxMgh0+yD7UIl438HE1tLYVJUg8g5IMavTcTRCYGhxzmpipHjAjvAp"
                                    + "AVpIZXD1743DPAzxZsswyPQs7/CGBWUG3Bvzdhjx/YUrXjJ7gvs8qDIqNFf1"
                                    + "UXLUBpQAPAE7IXgZJ4NM3DUbvWRiGr5OQGgpZgRU3kMdNY9xxUjvFA3QyeR5"
                                    + "PLg06nnDy6QIDAQABAwEAAAABAACgA9ez3/nbOPPJXIwvR88lWVQnIh/OuGm"
                                    + "iSDE3EptwZ61vTXFrSS6GjwUXI8tem8aYDFTH1DYWd1IM7//6/cuS+CdSUwe"
                                    + "cGM1ISRJ7TtM/rg6sk9ovxJR3pzCs8f65k5YyuHTU0RYx+x4G2PCWyIyj7Zt"
                                    + "LgffvepBSowbqJxUUR4MLfj3mFUe9UpJZ5iFKannDpdGCmxwek8mzmZHmOQj"
                                    + "5/me3IBuoD2QPk+GvugNmwxd3rBPpcKRRu6HxFtfIv0EX2ts+lGvZSYb+1GT"
                                    + "3oxhR5ynNVEzI92/urSGKLj4jdegmhXv7FONtjZGuufBP1rP8oa3zI+x85GD"
                                    + "dRCDhnCgL:1");
        }
    }

    public void testExtras() throws Exception {
        final MockDevice mockDevice = new MockDevice();
        try (IncrementalInstallSession session =
                new IncrementalInstallSession.Builder()
                        .addApk(mTestApk0, mTestSignature0)
                        .setAllowReinstall(true)
                        .addExtraArgs("-d")
                        .build()) {
            session.start(Executors.newCachedThreadPool(), mockDevice.getFactory());
            mockDevice.expectHandsHake();
            assertEquals("-r", mockDevice.mParams[1]);
            assertEquals("-d", mockDevice.mParams[2]);
        }
    }

    public void testClose() throws Exception {
        final MockDevice mockDevice = new MockDevice();
        try (IncrementalInstallSession session =
                new IncrementalInstallSession.Builder()
                        .addApk(mTestApk0, mTestSignature0)
                        .build()) {
            session.start(Executors.newCachedThreadPool(), mockDevice.getFactory());
            mockDevice.expectHandsHake();
        }
        mockDevice.expectResponse(RESPONSE_CLOSE);
    }

    public void testSendDataAndTreeBlocks() throws Exception {
        final MockDevice mockDevice = new MockDevice();
        try (IncrementalInstallSession session =
                new IncrementalInstallSession.Builder()
                        .addApk(mTestApk0, mTestSignature0)
                        .build()) {
            session.start(Executors.newCachedThreadPool(), mockDevice.getFactory());
            mockDevice.expectHandsHake();
            mockDevice.sendRequest(REQUEST_BLOCK_MISSING_0_601);
            mockDevice.expectResponse(RESPONSE_TREE_0_5, RESPONSE_TREE_0_0, RESPONSE_DATA_0_601);

            // Test that requesting the same block again does not send any data.
            mockDevice.sendRequest(REQUEST_BLOCK_MISSING_0_601);

            // Test second a second request that requires sending a different block.
            mockDevice.sendRequest(REQUEST_BLOCK_0_MISSING_617);
            mockDevice.expectResponse(RESPONSE_DATA_0_617);
        }
        mockDevice.expectResponse(RESPONSE_CLOSE);
    }

    public void testPrefetchSendDataAndTreeBlocks() throws Exception {
        final MockDevice mockDevice = new MockDevice();
        try (IncrementalInstallSession session =
                new IncrementalInstallSession.Builder()
                        .addApk(mTestApk0, mTestSignature0)
                        .build()) {
            session.start(Executors.newCachedThreadPool(), mockDevice.getFactory());
            mockDevice.expectHandsHake();
            mockDevice.sendRequest(PREFETCH_0_601);
            mockDevice.expectResponse(RESPONSE_TREE_0_5, RESPONSE_TREE_0_0, RESPONSE_DATA_0_601);

            // Test that requesting the same block again does not send any data.
            mockDevice.sendRequest(REQUEST_BLOCK_MISSING_0_601);
        }
        mockDevice.expectResponse(RESPONSE_CLOSE);
    }

    public void testMultipleApks() throws Exception {
        final MockDevice mockDevice = new MockDevice();
        try (IncrementalInstallSession session =
                new IncrementalInstallSession.Builder()
                        .addApk(mTestApk0, mTestSignature0)
                        .addApk(mTestApk1, mTestSignature1)
                        .build()) {
            session.start(Executors.newCachedThreadPool(), mockDevice.getFactory());
            mockDevice.expectHandsHake();
            mockDevice.sendRequest(REQUEST_BLOCK_MISSING_0_601);
            mockDevice.expectResponse(RESPONSE_TREE_0_5, RESPONSE_TREE_0_0, RESPONSE_DATA_0_601);

            // Request a block from the other APK
            mockDevice.sendRequest(REQUEST_BLOCK_MISSING_1_775);
            mockDevice.expectResponse(RESPONSE_TREE_1_7, RESPONSE_TREE_1_0, RESPONSE_DATA_1_775);
        }
        mockDevice.expectResponse(RESPONSE_CLOSE);
    }

    public void testPendingBlock() throws Exception {
        final MockDevice mockDevice = new MockDevice();
        final PendingBlock.Type PENDING_BLOCK_TYPE_DATA = PendingBlock.Type.APK_DATA;
        try (IncrementalInstallSession session =
                new IncrementalInstallSession.Builder()
                        .addApk(mTestApk0, mTestSignature0)
                        .setBlockFilter(
                                (PendingBlock b) -> {
                                    if (b.getType() == PENDING_BLOCK_TYPE_DATA) {
                                        assertEquals(mTestApk0, b.getPath());
                                        assertEquals(618, b.getFileBlockCount());
                                    } else {
                                        assertEquals(mTestSignature0, b.getPath());
                                        assertEquals(6, b.getFileBlockCount());
                                    }
                                    return true;
                                })
                        .build()) {
            session.start(Executors.newCachedThreadPool(), mockDevice.getFactory());
            mockDevice.expectHandsHake();
            mockDevice.sendRequest(REQUEST_BLOCK_MISSING_0_601);
            mockDevice.expectResponse(RESPONSE_TREE_0_5, RESPONSE_TREE_0_0, RESPONSE_DATA_0_601);
        }
        mockDevice.expectResponse(RESPONSE_CLOSE);
    }

    public void testSkipNonLeafTreeBlock() throws Exception {
        final AtomicBoolean firstTime = new AtomicBoolean(true);
        final MockDevice mockDevice = new MockDevice();
        try (IncrementalInstallSession session =
                new IncrementalInstallSession.Builder()
                        .addApk(mTestApk0, mTestSignature0)
                        .setBlockFilter(
                                (PendingBlock b) ->
                                        (b.getBlockIndex() != 0) || !firstTime.getAndSet(false))
                        .build()) {
            session.start(Executors.newCachedThreadPool(), mockDevice.getFactory());
            mockDevice.expectHandsHake();
            mockDevice.sendRequest(REQUEST_BLOCK_MISSING_0_601);
            mockDevice.expectResponse(RESPONSE_TREE_0_5, RESPONSE_DATA_0_601);

            // The block server allows the block to be sent the second time it is requested.
            mockDevice.sendRequest(REQUEST_BLOCK_MISSING_0_601);
            mockDevice.expectResponse(RESPONSE_TREE_0_0);
        }
        mockDevice.expectResponse(RESPONSE_CLOSE);
    }

    public void testSkipLeafTreeBlock() throws Exception {
        final AtomicBoolean firstTime = new AtomicBoolean(true);
        final MockDevice mockDevice = new MockDevice();
        try (IncrementalInstallSession session =
                new IncrementalInstallSession.Builder()
                        .addApk(mTestApk0, mTestSignature0)
                        .setBlockFilter(
                                (PendingBlock b) ->
                                        (b.getBlockIndex() != 5) || !firstTime.getAndSet(false))
                        .build()) {
            session.start(Executors.newCachedThreadPool(), mockDevice.getFactory());
            mockDevice.expectHandsHake();
            mockDevice.sendRequest(REQUEST_BLOCK_MISSING_0_601);
            mockDevice.expectResponse(RESPONSE_TREE_0_0, RESPONSE_DATA_0_601);

            // The block server allows the block to be sent the second time it is requested.
            mockDevice.sendRequest(REQUEST_BLOCK_MISSING_0_601);
            mockDevice.expectResponse(RESPONSE_TREE_0_5);
        }
        mockDevice.expectResponse(RESPONSE_CLOSE);
    }

    public void testSkipDataBlock() throws Exception {
        final AtomicBoolean firstTime = new AtomicBoolean(true);
        final MockDevice mockDevice = new MockDevice();
        try (IncrementalInstallSession session =
                new IncrementalInstallSession.Builder()
                        .addApk(mTestApk0, mTestSignature0)
                        .setBlockFilter(
                                (PendingBlock b) ->
                                        (b.getBlockIndex() != 601) || !firstTime.getAndSet(false))
                        .build()) {
            session.start(Executors.newCachedThreadPool(), mockDevice.getFactory());
            mockDevice.expectHandsHake();
            mockDevice.sendRequest(REQUEST_BLOCK_MISSING_0_601);
            mockDevice.expectResponse(RESPONSE_TREE_0_5, RESPONSE_TREE_0_0);

            // The block server allows the block to be sent the second time it is requested.
            mockDevice.sendRequest(REQUEST_BLOCK_MISSING_0_601);
            mockDevice.expectResponse(RESPONSE_DATA_0_601);
        }
        mockDevice.expectResponse(RESPONSE_CLOSE);
    }

    public void testPartialRequest() throws Exception {
        final MockDevice mockDevice = new MockDevice();
        try (IncrementalInstallSession session =
                new IncrementalInstallSession.Builder()
                        .addApk(mTestApk0, mTestSignature0)
                        .build()) {
            session.start(Executors.newCachedThreadPool(), mockDevice.getFactory());
            mockDevice.expectHandsHake();

            // Simulate a read request being broken up and sent to the host in two separate chunks.
            ByteBuffer request = getBufferForRequests(REQUEST_BLOCK_MISSING_0_601);
            ByteBuffer firstHalf = request.duplicate();
            firstHalf.limit(6);
            ByteBuffer secondHalf = request.duplicate();
            secondHalf.position(6);

            mockDevice.sendData(firstHalf);
            mockDevice.sendData(secondHalf);
            mockDevice.expectResponse(RESPONSE_TREE_0_5, RESPONSE_TREE_0_0, RESPONSE_DATA_0_601);
        }
        mockDevice.expectResponse(RESPONSE_CLOSE);
    }

    public void testSuccessfulServing() throws Exception {
        final MockDevice mockDevice = new MockDevice();
        try (IncrementalInstallSession session =
                new IncrementalInstallSession.Builder()
                        .addApk(mTestApk0, mTestSignature0)
                        .build()) {
            session.start(Executors.newCachedThreadPool(), mockDevice.getFactory());
            mockDevice.expectHandsHake();
            mockDevice.sendRequest(REQUEST_BLOCK_MISSING_0_601);
            mockDevice.sendData(ByteBuffer.wrap("Success\n".getBytes(Charsets.UTF_8)));
            mockDevice.expectResponse(RESPONSE_TREE_0_5, RESPONSE_TREE_0_0, RESPONSE_DATA_0_601);
            session.waitForInstallCompleted(TEST_TIMEOUT, TimeUnit.SECONDS);

            // Send a request after the install is completed.
            mockDevice.sendRequest(REQUEST_BLOCK_0_MISSING_602);
            mockDevice.expectResponse(RESPONSE_DATA_0_602);

            // Send streaming completed.
            mockDevice.sendRequest(REQUEST_STREAMING_COMPLETED);
            session.waitForServingCompleted(TEST_TIMEOUT, TimeUnit.SECONDS);
        }
    }

    public void testInstallSuccessAfterCompletedServing() throws Exception {
        final MockDevice mockDevice = new MockDevice();
        try (IncrementalInstallSession session =
                new IncrementalInstallSession.Builder()
                        .addApk(mTestApk0, mTestSignature0)
                        .build()) {
            session.start(Executors.newCachedThreadPool(), mockDevice.getFactory());
            mockDevice.expectHandsHake();
            mockDevice.sendRequest(REQUEST_STREAMING_COMPLETED);
            session.waitForServingCompleted(TEST_TIMEOUT, TimeUnit.SECONDS);

            // Serving has completed, but installation has not.
            assertThrows(
                    () -> session.waitForInstallCompleted(TEST_TIMEOUT, TimeUnit.SECONDS),
                    "timeout");

            // Now installation has completed.
            mockDevice.sendData(ByteBuffer.wrap("Success\n".getBytes(Charsets.UTF_8)));
            session.waitForInstallCompleted(TEST_TIMEOUT, TimeUnit.SECONDS);
        }
    }

    public void testPartialSuccessMessage() throws Exception {
        final MockDevice mockDevice = new MockDevice();
        try (IncrementalInstallSession session =
                new IncrementalInstallSession.Builder()
                        .addApk(mTestApk0, mTestSignature0)
                        .build()) {
            session.start(Executors.newCachedThreadPool(), mockDevice.getFactory());
            mockDevice.expectHandsHake();
            mockDevice.sendRequest(REQUEST_BLOCK_MISSING_0_601);
            mockDevice.sendData(ByteBuffer.wrap("Succ".getBytes(Charsets.UTF_8)));
            mockDevice.sendData(ByteBuffer.wrap("ess".getBytes(Charsets.UTF_8)));
            mockDevice.expectResponse(RESPONSE_TREE_0_5, RESPONSE_TREE_0_0, RESPONSE_DATA_0_601);
            session.waitForInstallCompleted(TEST_TIMEOUT, TimeUnit.SECONDS);
        }
    }

    public void testFailureMessage() throws Exception {
        final MockDevice mockDevice = new MockDevice();
        try (IncrementalInstallSession session =
                new IncrementalInstallSession.Builder()
                        .addApk(mTestApk0, mTestSignature0)
                        .build()) {
            session.start(Executors.newCachedThreadPool(), mockDevice.getFactory());
            mockDevice.expectHandsHake();
            mockDevice.sendRequest(REQUEST_BLOCK_MISSING_0_601);
            mockDevice.sendData(
                    ByteBuffer.wrap(
                            "Failure [INSTALL_PARSE_FAILED_NOT_APK: Failed to parse]\n"
                                    .getBytes(Charsets.UTF_8)));
            mockDevice.expectResponse(RESPONSE_TREE_0_5, RESPONSE_TREE_0_0, RESPONSE_DATA_0_601);

            assertThrows(
                    () -> session.waitForInstallCompleted(TEST_TIMEOUT, TimeUnit.SECONDS),
                    "INSTALL_PARSE_FAILED_NOT_APK: Failed to parse");
        }
        mockDevice.expectResponse(RESPONSE_CLOSE);
    }

    public void testPartialFailureMessage() throws Exception {
        final MockDevice mockDevice = new MockDevice();
        try (IncrementalInstallSession session =
                new IncrementalInstallSession.Builder()
                        .addApk(mTestApk0, mTestSignature0)
                        .build()) {
            session.start(Executors.newCachedThreadPool(), mockDevice.getFactory());
            mockDevice.expectHandsHake();
            mockDevice.sendRequest(REQUEST_BLOCK_MISSING_0_601);
            mockDevice.sendData(
                    ByteBuffer.wrap(
                            "Failure [INSTALL_PARSE_FAILED_NOT_APK:".getBytes(Charsets.UTF_8)));
            mockDevice.sendData(ByteBuffer.wrap(" Failed to parse]\n".getBytes(Charsets.UTF_8)));
            mockDevice.expectResponse(RESPONSE_TREE_0_5, RESPONSE_TREE_0_0, RESPONSE_DATA_0_601);

            assertThrows(
                    () -> session.waitForServingCompleted(TEST_TIMEOUT, TimeUnit.SECONDS),
                    "INSTALL_PARSE_FAILED_NOT_APK: Failed to parse");
        }
        mockDevice.expectResponse(RESPONSE_CLOSE);
    }

    public void testInstallFailAfterCompletedServing() throws Exception {
        final MockDevice mockDevice = new MockDevice();
        try (IncrementalInstallSession session =
                new IncrementalInstallSession.Builder()
                        .addApk(mTestApk0, mTestSignature0)
                        .build()) {
            session.start(Executors.newCachedThreadPool(), mockDevice.getFactory());
            mockDevice.expectHandsHake();
            mockDevice.sendRequest(REQUEST_STREAMING_COMPLETED);
            session.waitForServingCompleted(TEST_TIMEOUT, TimeUnit.SECONDS);

            // Serving has completed, but installation has not.
            assertThrows(
                    () -> session.waitForInstallCompleted(TEST_TIMEOUT, TimeUnit.SECONDS),
                    "timeout");

            // Now installation has failed.
            mockDevice.sendData(
                    ByteBuffer.wrap(
                            "Failure [INSTALL_PARSE_FAILED_NOT_APK: Failed to parse]\n"
                                    .getBytes(Charsets.UTF_8)));
            assertThrows(
                    () -> session.waitForInstallCompleted(TEST_TIMEOUT, TimeUnit.SECONDS),
                    "INSTALL_PARSE_FAILED_NOT_APK: Failed to parse");
        }
    }

    public void testFailureWithOtherMagics() throws Exception {
        final MockDevice mockDevice = new MockDevice();
        try (IncrementalInstallSession session =
                new IncrementalInstallSession.Builder()
                        .addApk(mTestApk0, mTestSignature0)
                        .build()) {
            session.start(Executors.newCachedThreadPool(), mockDevice.getFactory());
            mockDevice.expectHandsHake();
            mockDevice.sendRequest(REQUEST_STREAMING_COMPLETED);

            mockDevice.sendData(
                    ByteBuffer.wrap("Failure [INCR Success Failure[]\n".getBytes(Charsets.UTF_8)));
            assertThrows(
                    () -> session.waitForInstallCompleted(TEST_TIMEOUT, TimeUnit.SECONDS),
                    "INCR Success Failure[");
        }
    }

    public void testDestroy() throws Exception {
        final MockDevice mockDevice = new MockDevice();
        try (IncrementalInstallSession session =
                new IncrementalInstallSession.Builder()
                        .addApk(mTestApk0, mTestSignature0)
                        .build()) {
            session.start(Executors.newCachedThreadPool(), mockDevice.getFactory());
            mockDevice.expectHandsHake();
            mockDevice.sendRequest(REQUEST_BLOCK_MISSING_0_601);
            mockDevice.expectResponse(RESPONSE_TREE_0_5, RESPONSE_TREE_0_0, RESPONSE_DATA_0_601);

            mockDevice.sendRequest(REQUEST_DESTROY);
            assertThrows(
                    () -> session.waitForServingCompleted(TEST_TIMEOUT, TimeUnit.SECONDS),
                    "Destroy request received");
        }
        mockDevice.expectResponse(RESPONSE_CLOSE);
    }

    private static class Request {
        final short mApkId;
        final short mType;
        final int mBlockIndex;

        Request(short apkId, short type, int blockIndex) {
            mApkId = apkId;
            mType = type;
            mBlockIndex = blockIndex;
        }
    }

    private static class Response {
        final short mApkId;
        final byte mBlockType;
        final byte mCompression;
        final int mBlockIndex;
        final short mSize;
        final Supplier<FileChannel> mContentsFile;
        final long mContentsOffset;

        Response(
                short apkId,
                byte blockType,
                byte compression,
                int blockIndex,
                short size,
                Supplier<FileChannel> contentsFile,
                long contentsOffset) {
            mApkId = apkId;
            mBlockType = blockType;
            mCompression = compression;
            mBlockIndex = blockIndex;
            mSize = size;
            mContentsFile = contentsFile;
            mContentsOffset = contentsOffset;
        }
    }

    private static class MockDevice implements IDeviceConnection {
        @GuardedBy("this")
        final ArrayList<ByteBuffer> mFromDeviceData = new ArrayList<>();

        @GuardedBy("this")
        final ArrayList<ByteBuffer> mToDeviceData = new ArrayList<>();

        private String mService;
        private String[] mParams;

        Factory getFactory() {
            return (service, parameters) -> {
                mService = service;
                mParams = parameters;
                return MockDevice.this;
            };
        }

        @Override
        public int read(@NonNull ByteBuffer dst, long timeoutMs) {
            synchronized (this) {
                if (mFromDeviceData.isEmpty()) {
                    return 0;
                }
                int count = 0;
                final ByteBuffer buffer = mFromDeviceData.get(0);
                while (dst.hasRemaining() && buffer.hasRemaining()) {
                    dst.put(buffer.get());
                    count++;
                }

                if (!buffer.hasRemaining()) {
                    mFromDeviceData.remove(0);
                }
                return count;
            }
        }

        @Override
        public int write(@NonNull ByteBuffer src, long timeoutMs) {
            synchronized (this) {
                final int count = src.remaining();
                final byte[] dest = new byte[count];
                src.get(dest);
                mToDeviceData.add(ByteBuffer.wrap(dest));
                return count;
            }
        }

        @Override
        public void close() {}

        void sendData(ByteBuffer data) {
            synchronized (this) {
                mFromDeviceData.add(data);
            }
        }

        void sendRequest(Request... requests) {
            synchronized (this) {
                mFromDeviceData.add(getBufferForRequests(requests));
            }
        }

        private void waitForResponse() {
            final long endMillis = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (System.nanoTime() <= endMillis) {
                synchronized (this) {
                    if (!mToDeviceData.isEmpty()) {
                        return;
                    }
                }
            }
            throw new IllegalStateException("timeout");
        }

        public void expectHandsHake() {
            waitForResponse();
            synchronized (this) {
                final ByteBuffer actual = mToDeviceData.remove(0);
                assertEquals('O', actual.get());
                assertEquals('K', actual.get());
                assertEquals('A', actual.get());
                assertEquals('Y', actual.get());
            }
        }

        void expectResponse(Response... responses) throws IOException {
            waitForResponse();
            int expectedChunkSize = 0;
            for (final Response response : responses) {
                expectedChunkSize += 10 + response.mSize;
            }
            final int expectedChunkSizeAndHeader = expectedChunkSize + 4;
            final ByteBuffer buffer;
            synchronized (this) {
                buffer = mToDeviceData.remove(0);
            }
            assertEquals(expectedChunkSizeAndHeader, buffer.limit());
            assertEquals(expectedChunkSize, buffer.getInt());
            for (final Response response : responses) {
                assertEquals(response.mApkId, buffer.getShort());
                assertEquals(response.mBlockType, buffer.get());
                assertEquals(response.mCompression, buffer.get());
                assertEquals(response.mBlockIndex, buffer.getInt());
                assertEquals(response.mSize, buffer.getShort());

                if (response.mContentsFile != null) {
                    final FileChannel file = response.mContentsFile.get();
                    final ByteBuffer expectedBuffer = ByteBuffer.allocate(response.mSize);
                    assertEquals(
                            response.mSize, file.read(expectedBuffer, response.mContentsOffset));
                    expectedBuffer.rewind();
                    for (int i = 0; i < response.mSize; i++) {
                        assertEquals("Failed at position " + i, expectedBuffer.get(), buffer.get());
                    }
                }
            }
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void assertThrows(ThrowingRunnable runnable, String substring) {
        Exception thrownException = null;
        try {
            runnable.run();
        } catch (Exception e) {
            assertThat(e).hasMessageThat().contains(substring);
            thrownException = e;
        }
        assertNotNull(thrownException);
    }

    private static ByteBuffer getBufferForRequests(Request... requests) {
        final ByteBuffer buffer = ByteBuffer.allocateDirect(12 * requests.length);
        for (Request request : requests) {
            for (byte c : new byte[] {'I', 'N', 'C', 'R'}) {
                buffer.put(c);
            }
            buffer.putShort(request.mType);
            buffer.putShort(request.mApkId);
            buffer.putInt(request.mBlockIndex);
        }
        buffer.flip();
        return buffer;
    }

    /** Copies a file within the host test jar to a temporary file on the host machine. */
    private Path copyResourceToTemp(String resourcePath, String prefix, String suffix)
            throws IOException {
        final Path tempFile = Files.createTempFile(prefix, suffix);
        final ClassLoader classLoader = getClass().getClassLoader();
        try (InputStream assetIs = classLoader.getResource(resourcePath).openStream();
                OutputStream assetOs = Files.newOutputStream(tempFile)) {
            if (assetIs == null) {
                throw new IllegalStateException("Failed to find resource " + resourcePath);
            }
            byte[] b = new byte[4092];
            int l;
            while ((l = assetIs.read(b)) > 0) {
                assetOs.write(b, 0, l);
            }
        }
        return tempFile;
    }
}
