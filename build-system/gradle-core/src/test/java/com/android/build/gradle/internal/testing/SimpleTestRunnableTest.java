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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.build.api.variant.AndroidVersion;
import com.android.build.api.variant.impl.AndroidVersionImpl;
import com.android.builder.testing.api.DeviceConfigProvider;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
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
    private List<File> testedApks;

    // Test data fields
    @NonNull private String instrumentationRunner;
    @NonNull private String applicationId;

    private boolean animationsDisabled;
    private String testedApplicationId;
    private String instrumentationTargetPackageId;
    @NonNull private String flavorName;
    @NonNull private File testApk;
    @NonNull private List<File> testDirectories;
    @NonNull private Map<String, String> instrumentationRunnerArguments;

    @NonNull private AndroidVersion minSdkVersion;

    private int apiVersion;

    @Before
    public void setUpMocks() throws Exception {
        deviceConnector = Mockito.mock(DeviceConnector.class);
        logger = new MockLog();

        applicationId = "com.example.app";
        instrumentationTargetPackageId = "com.example.app";
        instrumentationRunner = "android.support.test.runner.AndroidJUnitRunner";
        flavorName = "";
        animationsDisabled = false;
        testDirectories = new ArrayList<>();
        testedApks = new ArrayList<>();
        instrumentationRunnerArguments = new HashMap<>();
        minSdkVersion = new AndroidVersionImpl(1, null);

        instrumentationRunnerArguments.put("numShards", "10");
        instrumentationRunnerArguments.put("shardIndex", "2");

        testApk = new File(temporaryFolder.newFolder(), "test.apk");
    }

    private void useApiVersion(int apiVersion) {
        this.apiVersion = apiVersion;
        when(deviceConnector.getApiLevel()).thenReturn(apiVersion);
    }

    @Test
    public void checkSplitBehaviorWithPre21() throws Exception {
        useApiVersion(19);
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
        useApiVersion(21);
        call(1);
        verify(deviceConnector).installPackage(eq(testedApks.get(0)), any(), anyInt(), any());
        verify(deviceConnector).installPackage(eq(testApk), any(), anyInt(), any());
        verifyNoMoreInteractions(deviceConnector);
    }

    @Test
    public void checkSplitBehaviorWith21() throws Exception {
        useApiVersion(21);
        call(2);
        verify(deviceConnector).installPackages(eq(testedApks), any(), anyInt(), any());
        verify(deviceConnector).installPackage(eq(testApk), any(), anyInt(), any());
        verifyNoMoreInteractions(deviceConnector);
    }

    @Test
    public void checkAdditionalTestOutputWith15() throws Exception {
        testedApplicationId = "com.example.app.test";
        instrumentationTargetPackageId = "com.example.app.test";

        useApiVersion(15);
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
        testedApplicationId = "com.example.app.test";
        instrumentationTargetPackageId = "com.example.app.test";

        useApiVersion(16);
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
                            new String[] {testedApplicationId + "-benchmarkData.json"});
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
    public void checkAdditionalTestOutputWith29() throws Exception {
        testedApplicationId = "com.example.app.test";
        instrumentationTargetPackageId = "com.example.app.test";

        useApiVersion(29);
        when(deviceConnector.getName()).thenReturn("FakeDevice");

        Answer<Void> lsAnswer =
                invocation -> {
                    MultiLineReceiver receiver = invocation.getArgument(1);
                    receiver.processNewLines(
                            new String[] {testedApplicationId + "-benchmarkData.json"});
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
                                            "/sdcard/Android/media/com.example.app.test/additional_test_output/com.example.app.test-benchmarkData.json")
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

        verify(deviceConnector, times(0))
                .executeShellCommand(startsWith("content query"), any(), anyLong(), any());
        verify(deviceConnector, times(1))
                .executeShellCommand(startsWith("ls"), any(), anyLong(), any());
    }

    @Test
    public void checkAdditionalTestOutputNotSupportedWith29() throws Exception {
        testedApplicationId = "com.example.app.test";
        instrumentationTargetPackageId = "com.example.app.test";

        useApiVersion(29);
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

        verify(deviceConnector, times(0))
                .executeShellCommand(startsWith("content query"), any(), anyLong(), any());
        verify(deviceConnector, times(1))
                .executeShellCommand(startsWith("ls"), any(), anyLong(), any());
        verify(deviceConnector, never()).pullFile(anyString(), anyString());
    }

    @Test
    public void instrumentationArgsCanOverridesCoverageFile() throws Exception {
        String customCoverageFilePath = "path/to/custom/coverage/" + FILE_COVERAGE_EC;
        instrumentationRunnerArguments =
                ImmutableMap.of("coverageFilePath", customCoverageFilePath);

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

        assertThat(runnable.getCoverageFile("0")).isEqualTo(customCoverageFilePath);
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

        assertThat(runnable.getCoverageFile("12"))
                .isEqualTo(String.format("/data/user/12/%s/%s", null, FILE_COVERAGE_EC));
    }

    @Test
    public void installOptionsForBuddyApkWithPre23() throws Exception {
        useApiVersion(22);

        File prodApks = temporaryFolder.newFolder();
        testedApks = ImmutableList.of(new File(prodApks, "app.apk"));

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

        verify(deviceConnector)
                .installPackage(testedApks.get(0), ImmutableList.of(), TIMEOUT, logger);
        verify(deviceConnector).installPackage(buddyApk, ImmutableList.of(), TIMEOUT, logger);
    }

    @Test
    public void installOptionsForBuddyApkWith23() throws Exception {
        useApiVersion(23);

        File prodApks = temporaryFolder.newFolder();
        testedApks = ImmutableList.of(new File(prodApks, "app.apk"));

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

        verify(deviceConnector)
                .installPackage(testedApks.get(0), ImmutableList.of(), TIMEOUT, logger);
        verify(deviceConnector).installPackage(buddyApk, ImmutableList.of("-g"), TIMEOUT, logger);
    }

    @Test
    public void installOptionsForBuddyApkWith30() throws Exception {
        useApiVersion(30);

        File prodApks = temporaryFolder.newFolder();
        testedApks = ImmutableList.of(new File(prodApks, "app.apk"));

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

        verify(deviceConnector)
                .installPackage(testedApks.get(0), ImmutableList.of("--user 42"), TIMEOUT, logger);
        verify(deviceConnector)
                .installPackage(
                        buddyApk,
                        ImmutableList.of("--user 42", "-g", "--force-queryable"),
                        TIMEOUT,
                        logger);
    }

    @Test
    public void verifyAnimationsDisabled() throws Exception {
        useApiVersion(21);

        animationsDisabled = true;
        call(
                1,
                "am instrument -w -r --no_window_animation "
                        + "  -e shardIndex 2 -e numShards 10 "
                        + "com.example.app/android.support.test.runner.AndroidJUnitRunner");
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
                                applicationId, instrumentationRunner, deviceConnector),
                        "flavor",
                        testedApks,
                        getStaticData(),
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
        call(
                apkCount,
                String.format(
                        "am instrument -w -r %s  -e shardIndex 2 -e numShards 10 "
                                + "com.example.app/android.support.test.runner.AndroidJUnitRunner",
                        (apiVersion >= 24 ? "--user 42" : "")));
    }

    private void call(int apkCount, String expectedShellCommand) throws Exception {
        File prodApks = temporaryFolder.newFolder();
        testedApks = new ArrayList<>();
        for (int i = 0; i < apkCount; i++) {
            testedApks.add(new File(prodApks, "app" + i + ".apk"));
        }

        File buddyApk = new File(temporaryFolder.newFolder(), "buddy.apk");

        File resultsDir = temporaryFolder.newFile();
        File additionalTestOutputDir = temporaryFolder.newFolder();
        File coverageDir = temporaryFolder.newFile();
        List<String> installOptions =
                apiVersion >= 24 ? ImmutableList.of("--user 42") : ImmutableList.of();
        SimpleTestRunnable runnable =
                getSimpleTestRunnable(
                        buddyApk,
                        resultsDir,
                        false,
                        additionalTestOutputDir,
                        coverageDir,
                        ImmutableList.of());
        runnable.run();

        verify(deviceConnector, atLeastOnce()).getName();
        verify(deviceConnector).connect(TIMEOUT, logger);
        verify(deviceConnector).installPackage(testApk, installOptions, TIMEOUT, logger);

        if (apkCount == 1) {
            verify(deviceConnector, times(2)).getApiLevel();
            verify(deviceConnector)
                    .installPackage(testedApks.get(0), installOptions, TIMEOUT, logger);
        } else {
            verify(deviceConnector, times(3)).getApiLevel();
            verify(deviceConnector).installPackages(testedApks, installOptions, TIMEOUT, logger);
        }

        verify(deviceConnector).installPackage(buddyApk, installOptions, TIMEOUT, logger);
        verify(deviceConnector)
                .executeShellCommand(eq(expectedShellCommand), any(), anyLong(), anyLong(), any());
        verify(deviceConnector).uninstallPackage("com.example.app", TIMEOUT, logger);
        verify(deviceConnector).disconnect(TIMEOUT, logger);
        verifyNoMoreInteractions(deviceConnector);
    }

    @NonNull
    private StaticTestData getStaticData() {
        return new StaticTestData(
                applicationId,
                testedApplicationId,
                instrumentationTargetPackageId,
                instrumentationRunner,
                instrumentationRunnerArguments,
                animationsDisabled,
                false,
                minSdkVersion,
                false,
                flavorName,
                testApk,
                testDirectories,
                (@NonNull DeviceConfigProvider deviceConfigProvider, ILogger logger) ->
                        ImmutableList.copyOf(testedApks));
    }

    static class SimpleTestRunnable
            extends com.android.build.gradle.internal.testing.SimpleTestRunnable {

        public SimpleTestRunnable(SimpleTestParams params) {
            super(params);
        }

        @NotNull
        @Override
        protected String getUserId() {
            return "42";
        }
    }
}
