/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.integration.application;

import static com.android.testutils.AssumeUtil.assumeNotWindows;
import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ProfileCapturer;
import com.android.build.gradle.integration.common.truth.ScannerSubjectUtils;
import com.android.build.gradle.options.BooleanOption;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.SettableFuture;
import com.google.wireless.android.sdk.stats.GradleBuildProfile;
import com.google.wireless.android.sdk.stats.GradleBuildProject;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import kotlin.Unit;
import org.junit.Rule;
import org.junit.Test;

/**
 * Check that the plugin remains compatible with the builder testing DeviceProvider API.
 *
 * <p>Run deviceCheck even without devices, since we use a fake DeviceProvider that doesn't use a
 * device, but only record the calls made to the DeviceProvider and the DeviceConnector.
 */
public class BuilderTestingApiTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("builderTestingApiUse")
                    .enableProfileOutput()
                    .create();

    @Test
    public void deviceCheck() throws Exception {
        assumeNotWindows(); // b/145233124
        ProfileCapturer capturer = new ProfileCapturer(project, ".rawproto");
        SettableFuture<GradleBuildResult> result = SettableFuture.create();
        GradleBuildProfile profile =
                Iterables.getOnlyElement(
                        capturer.capture(
                                () ->
                                        result.set(
                                                project.executor()
                                                        // The builder testing DeviceProvider API is
                                                        // not supported by UTP.
                                                        .with(
                                                                BooleanOption
                                                                        .ANDROID_TEST_USES_UNIFIED_TEST_PLATFORM,
                                                                false)
                                                        .run("deviceCheck"))));
        ImmutableList.Builder<String> listBuilder = new ImmutableList.Builder<>();
        ScannerSubjectUtils.forEachLine(
                result.get().getStdout(),
                it -> {
                    listBuilder.add(it);
                    return Unit.INSTANCE;
                });
        List<String> lines = listBuilder.build();

        List<String> expectedActionsDevice1 =
                ImmutableList.of(
                        "INIT CALLED",
                        "CONNECT(DEVICE1) CALLED",
                        "INSTALL(DEVICE1) CALLED", // Install the test...
                        "INSTALL(DEVICE1) CALLED", // ...and the tested APK
                        "EXECSHELL(DEVICE1) CALLED", // Run test,
                        "EXECSHELL(DEVICE1) CALLED", // Collect coverage data file (1)
                        "PULL_FILE(DEVICE1) CALLED", // Collect coverage data file (2)
                        "EXECSHELL(DEVICE1) CALLED", // Collect coverage data file (3)
                        "UNINSTALL(DEVICE1) CALLED", // Uninstall the test...
                        "UNINSTALL(DEVICE1) CALLED", // ...and the tested APK.
                        "DISCONNECTED(DEVICE1) CALLED",
                        "TERMINATE CALLED");
        List<String> expectedActionsDevice2 =
                expectedActionsDevice1
                        .stream()
                        .map(s -> s.replace('1', '2'))
                        .collect(Collectors.toList());

        // Allow for interleaving of device1 and device2.
        assertThat(lines).containsAllIn(expectedActionsDevice1);
        assertThat(lines).containsAllIn(expectedActionsDevice2);

        // And assert that the metrics were recorded for API use
        Map<GradleBuildProject.PluginType, GradleBuildProject> projects =
                profile.getProjectList().stream()
                        .collect(
                                Collectors.toMap(
                                        GradleBuildProject::getAndroidPlugin, Function.identity()));

        GradleBuildProject appProject = projects.get(GradleBuildProject.PluginType.APPLICATION);
        GradleBuildProject libraryProject = projects.get(GradleBuildProject.PluginType.LIBRARY);

        assertThat(appProject.getProjectApiUse().getBuilderTestApiDeviceProvider())
                .named("app projectApiUse.builderTestApiDeviceProvider")
                .isTrue();

        assertThat(libraryProject.getProjectApiUse().getBuilderTestApiDeviceProvider())
                .named("lib projectApiUse.builderTestApiDeviceProvider")
                .isTrue();

        assertThat(appProject.getProjectApiUse().getBuilderTestApiTestServer())
                .named("app projectApiUse.builderTestApiTestServer")
                .isTrue();

        assertThat(libraryProject.getProjectApiUse().getBuilderTestApiTestServer())
                .named("lib projectApiUse.builderTestApiTestServer")
                .isTrue();
    }
}
