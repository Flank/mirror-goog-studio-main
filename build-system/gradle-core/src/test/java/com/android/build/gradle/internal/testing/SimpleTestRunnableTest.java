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

package com.android.build.gradle.internal.testing;

import static com.android.build.gradle.internal.testing.SimpleTestRunnable.FILE_COVERAGE_EC;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.android.builder.testing.api.DeviceConnector;
import com.android.ddmlib.InstallException;
import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.testutils.MockLog;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

public class SimpleTestRunnableTest {

    private static final int TIMEOUT = 4000;

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
    private DeviceConnector deviceConnector;
    private ILogger logger;
    private StubTestData testData;
    private File testApk;
    private List<File> testedApks;

    @Before
    public void setUpMocks() throws Exception {
        deviceConnector = Mockito.mock(DeviceConnector.class);
        logger = new MockLog();
        testData =
                new StubTestData(
                        "com.example.app", "android.support.test.runner.AndroidJUnitRunner");
        testData.getInstrumentationRunnerArguments().put("numShards", "10");
        testData.getInstrumentationRunnerArguments().put("shardIndex", "2");

        testApk = new File(temporaryFolder.newFolder(), "test.apk");
        testData.setTestApk(testApk);
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
        } catch (Exception e) {
            assertThat(e.getCause() instanceof InstallException).isTrue();
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

    @Test
    public void checkAdditionalTestOutputWith15() throws Exception {
        testData.setTestedApplicationId("com.example.app.test");

        when(deviceConnector.getApiLevel()).thenReturn(15);
        when(deviceConnector.getName()).thenReturn("FakeDevice");

        File prodApks = temporaryFolder.newFolder();
        testedApks = new ArrayList<>();
        testedApks.add(new File(prodApks, "app" + 1 + ".apk"));
        File buddyApk = new File(temporaryFolder.newFolder(), "buddy.apk");
        File resultsDir = temporaryFolder.newFile();
        File additionalTestOutputDir = temporaryFolder.newFolder();
        File coverageDir = temporaryFolder.newFile();
        SimpleTestRunnable runnable =
                getSimpleTestRunnable(
                        buddyApk,
                        resultsDir,
                        true,
                        additionalTestOutputDir,
                        coverageDir,
                        ImmutableList.of());

        runnable.run();

        verify(deviceConnector, times(0)).pullFile(anyString(), anyString());
        verify(deviceConnector, times(0))
                .executeShellCommand(startsWith("content query"), any(), anyLong(), any());
        verify(deviceConnector, times(0))
                .executeShellCommand(startsWith("ls"), any(), anyLong(), any());
    }

    @Test
    public void checkAdditionalTestOutputWith16() throws Exception {
        testData.setTestedApplicationId("com.example.app.test");

        when(deviceConnector.getApiLevel()).thenReturn(16);
        when(deviceConnector.getName()).thenReturn("FakeDevice");

        Answer<Void> contentQueryAnswer =
                invocation -> {
                    MultiLineReceiver receiver = invocation.getArgument(1);
                    receiver.processNewLines(new String[] {"Row: 0 _data=/fake_path/Android"});
                    receiver.flush();
                    return null;
                };
        doAnswer(contentQueryAnswer)
                .when(deviceConnector)
                .executeShellCommand(startsWith("content query"), any(), anyLong(), any());

        Answer<Void> lsAnswer =
                invocation -> {
                    MultiLineReceiver receiver = invocation.getArgument(1);
                    receiver.processNewLines(
                            new String[] {
                                testData.getTestedApplicationId() + "-benchmarkData.json"
                            });
                    receiver.flush();
                    return null;
                };
        doAnswer(lsAnswer)
                .when(deviceConnector)
                .executeShellCommand(startsWith("ls"), any(), anyLong(), any());

        File prodApks = temporaryFolder.newFolder();
        testedApks = new ArrayList<>();
        testedApks.add(new File(prodApks, "app" + 1 + ".apk"));
        File buddyApk = new File(temporaryFolder.newFolder(), "buddy.apk");
        File resultsDir = temporaryFolder.newFile();
        File additionalTestOutputDir = temporaryFolder.newFolder();
        File coverageDir = temporaryFolder.newFile();
        SimpleTestRunnable runnable =
                getSimpleTestRunnable(
                        buddyApk,
                        resultsDir,
                        true,
                        additionalTestOutputDir,
                        coverageDir,
                        ImmutableList.of());

        Answer<Void> verifyPullFileAnswer =
                invocation -> {
                    String remote = invocation.getArgument(0);
                    String expectedRemote =
                            Paths.get(
                                            "/fake_path/Android/data/com.example.app.test/files/test_data/com.example.app.test-benchmarkData.json")
                                    .toString();
                    assertThat(remote).isEqualTo(expectedRemote);

                    String local = invocation.getArgument(1);
                    String expectedLocal =
                            Paths.get(
                                            additionalTestOutputDir.getAbsolutePath(),
                                            "FakeDevice/com.example.app.test-benchmarkData.json")
                                    .toString();
                    assertThat(local).isEqualTo(expectedLocal);
                    return null;
                };
        doAnswer(verifyPullFileAnswer).when(deviceConnector).pullFile(anyString(), anyString());

        runnable.run();

        verify(deviceConnector, times(1))
                .executeShellCommand(startsWith("content query"), any(), anyLong(), any());
        verify(deviceConnector, times(1))
                .executeShellCommand(startsWith("ls"), any(), anyLong(), any());
    }

    @Test
    public void instrumentationArgsCanOverridesCoverageFile() throws Exception {
        String customCoverageFilePath = "path/to/custom/coverage/" + FILE_COVERAGE_EC;
        testData.setInstrumentationRunnerArguments(
                ImmutableMap.of("coverageFile", customCoverageFilePath));

        File buddyApk = temporaryFolder.newFile();
        File resultsDir = temporaryFolder.newFile();
        File additionalTestOutputDir = temporaryFolder.newFolder();
        File coverageDir = temporaryFolder.newFile();
        List<String> installOptions = ImmutableList.of();
        SimpleTestRunnable runnable =
                getSimpleTestRunnable(
                        buddyApk,
                        resultsDir,
                        false,
                        additionalTestOutputDir,
                        coverageDir,
                        installOptions);

        assertThat(runnable.getCoverageFile()).isEqualTo(customCoverageFilePath);
    }

    @Test
    public void defaultCoverageFile() throws Exception {
        File buddyApk = temporaryFolder.newFile();
        File resultsDir = temporaryFolder.newFile();
        File additionalTestOutputDir = temporaryFolder.newFolder();
        File coverageDir = temporaryFolder.newFile();
        List<String> installOptions = ImmutableList.of();
        SimpleTestRunnable runnable =
                getSimpleTestRunnable(
                        buddyApk,
                        resultsDir,
                        false,
                        additionalTestOutputDir,
                        coverageDir,
                        installOptions);

        assertThat(runnable.getCoverageFile())
                .isEqualTo(
                        String.format(
                                "/data/data/%s/%s",
                                testData.getTestedApplicationId(), FILE_COVERAGE_EC));
    }

    private SimpleTestRunnable getSimpleTestRunnable(
            File buddyApk,
            File resultsDir,
            boolean additionalTestOutputEnabled,
            File additionalTestOutputDir,
            File coverageDir,
            List<String> installOptions) {
        SimpleTestRunnable.SimpleTestParams params =
                new SimpleTestRunnable.SimpleTestParams(
                        deviceConnector,
                        "project",
                        new RemoteAndroidTestRunner(
                                testData.getApplicationId(),
                                testData.getInstrumentationRunner(),
                                deviceConnector),
                        "flavor",
                        testedApks,
                        testData,
                        Collections.singleton(buddyApk),
                        resultsDir,
                        additionalTestOutputEnabled,
                        additionalTestOutputDir,
                        coverageDir,
                        TIMEOUT,
                        installOptions,
                        logger,
                        new BaseTestRunner.TestResult());
        return new SimpleTestRunnable(params);
    }

    private void call(int apkCount) throws Exception {
        File prodApks = temporaryFolder.newFolder();
        testedApks = new ArrayList<>();
        for (int i = 0; i < apkCount; i++) {
            testedApks.add(new File(prodApks, "app" + i + ".apk"));
        }

        File buddyApk = new File(temporaryFolder.newFolder(), "buddy.apk");

        File resultsDir = temporaryFolder.newFile();
        File additionalTestOutputDir = temporaryFolder.newFolder();
        File coverageDir = temporaryFolder.newFile();
        List<String> installOptions = ImmutableList.of();
        SimpleTestRunnable runnable =
                getSimpleTestRunnable(
                        buddyApk,
                        resultsDir,
                        false,
                        additionalTestOutputDir,
                        coverageDir,
                        installOptions);
        runnable.run();

        verify(deviceConnector, atLeastOnce()).getName();
        verify(deviceConnector).connect(TIMEOUT, logger);
        verify(deviceConnector).installPackage(testApk, installOptions, TIMEOUT, logger);

        if (apkCount == 1) {
            verify(deviceConnector)
                    .installPackage(testedApks.get(0), installOptions, TIMEOUT, logger);
        } else {
            verify(deviceConnector).getApiLevel();
            verify(deviceConnector).installPackages(testedApks, installOptions, TIMEOUT, logger);
        }

        verify(deviceConnector).installPackage(buddyApk, installOptions, TIMEOUT, logger);
        verify(deviceConnector)
                .executeShellCommand(
                        eq(
                                "am instrument -w -r   -e shardIndex 2 -e numShards 10 "
                                        + "com.example.app/android.support.test.runner.AndroidJUnitRunner"),
                        any(),
                        anyLong(),
                        anyLong(),
                        any());
        verify(deviceConnector).uninstallPackage("com.example.app", TIMEOUT, logger);
        verify(deviceConnector).disconnect(TIMEOUT, logger);
        verifyNoMoreInteractions(deviceConnector);
    }
}
