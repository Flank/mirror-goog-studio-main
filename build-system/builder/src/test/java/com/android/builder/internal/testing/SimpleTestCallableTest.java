/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.builder.internal.testing;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.android.builder.testing.StubTestData;
import com.android.builder.testing.TestData;
import com.android.builder.testing.api.DeviceConnector;
import com.android.ddmlib.InstallException;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.testutils.MockLog;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

public class SimpleTestCallableTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private DeviceConnector deviceConnector;
    private ILogger logger;
    private TestData testData;
    private File testApk;
    private List<File> testedApks;

    @Before
    public void setUpMocks() {
        deviceConnector = Mockito.mock(DeviceConnector.class);
        logger = new MockLog();
        testData =
                new StubTestData(
                        "com.example.app", "android.support.test.runner.AndroidJUnitRunner");
        testData.getInstrumentationRunnerArguments().put("numShards", "10");
        testData.getInstrumentationRunnerArguments().put("shardIndex", "2");
    }

    @Test
    public void checkSplitBehaviorWithPre21() throws Exception {
        when(deviceConnector.getApiLevel()).thenReturn(19);
        call(1);
        verify(deviceConnector).installPackage(eq(testedApks.get(0)), any(), anyInt(), any());
        verify(deviceConnector).installPackage(eq(testApk), any(), anyInt(), any());
        verifyNoMoreInteractions(deviceConnector);
        try {
            call(2);
            fail("Should ");
        } catch (InstallException e) {
            assertThat(e.getMessage()).contains("Internal error");
        }
    }

    @Test
    public void checkNonSplitBehaviorWith21() throws Exception {
        when(deviceConnector.getApiLevel()).thenReturn(21);
        call(1);
        verify(deviceConnector).installPackage(eq(testedApks.get(0)), any(), anyInt(), any());
        verify(deviceConnector).installPackage(eq(testApk), any(), anyInt(), any());
        verifyNoMoreInteractions(deviceConnector);
    }

    @Test
    public void checkSplitBehaviorWith21() throws Exception {
        when(deviceConnector.getApiLevel()).thenReturn(21);
        call(2);
        verify(deviceConnector).installPackages(eq(testedApks), any(), anyInt(), any());
        verify(deviceConnector).installPackage(eq(testApk), any(), anyInt(), any());
        verifyNoMoreInteractions(deviceConnector);
    }

    private void call(int apkCount) throws Exception {
        File dir = temporaryFolder.newFolder();
        testedApks = new ArrayList<>();
        for (int i = 0; i < apkCount; i++) {
            testedApks.add(new File(dir, "app" + i + ".apk"));
        }

        testApk = new File(dir, "test.apk");
        File resultsDir = temporaryFolder.newFile();
        File coverageDir = temporaryFolder.newFile();
        List<String> installOptions = ImmutableList.of();
        SimpleTestCallable callable =
                new SimpleTestCallable(
                        deviceConnector,
                        "project",
                        new RemoteAndroidTestRunner(
                                testData.getApplicationId(),
                                testData.getInstrumentationRunner(),
                                deviceConnector),
                        "flavor",
                        testApk,
                        testedApks,
                        testData,
                        resultsDir,
                        coverageDir,
                        4000,
                        installOptions,
                        logger);
        callable.call();
        verify(deviceConnector, atLeastOnce()).getName();
        verify(deviceConnector, atLeast(0)).getApiLevel();
        verify(deviceConnector).connect(anyInt(), eq(logger));
        verify(deviceConnector).disconnect(anyInt(), eq(logger));
        verify(deviceConnector)
                .executeShellCommand(
                        eq(
                                "am instrument -w -r   -e shardIndex 2 -e numShards 10 "
                                        + "com.example.app/android.support.test.runner.AndroidJUnitRunner"),
                        any(),
                        anyLong(),
                        any());
        verify(deviceConnector).uninstallPackage(eq("com.example.app"), anyInt(), eq(logger));
    }
}
