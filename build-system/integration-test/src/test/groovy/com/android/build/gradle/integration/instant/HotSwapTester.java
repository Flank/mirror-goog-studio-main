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

package com.android.build.gradle.integration.instant;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.UninstallOnClose;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.Logcat;
import com.android.build.gradle.integration.common.fixture.Packaging;
import com.android.build.gradle.internal.incremental.ColdswapMode;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.InstantRun;
import com.android.builder.packaging.PackagingUtils;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.tools.fd.client.InstantRunArtifact;
import com.android.tools.fd.client.InstantRunBuildInfo;
import com.android.tools.fd.client.InstantRunClient;
import com.android.tools.fd.client.InstantRunClient.FileTransfer;
import com.android.tools.fd.client.UpdateMode;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;

import org.mockito.Mockito;

import java.io.Closeable;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Helper for automating HotSwap testing.
 */
public class HotSwapTester {

    public static final ColdswapMode COLDSWAP_MODE = ColdswapMode.MULTIDEX;
    @NonNull
    private final GradleTestProject project;
    @NonNull
    private final Packaging packaging;
    @NonNull
    private final String packageName;
    @NonNull
    private final String activityName;
    @NonNull
    private final String logTag;
    @NonNull
    private final IDevice device;
    @NonNull
    private final Logcat logcat;

    public HotSwapTester(
            @NonNull GradleTestProject project,
            @NonNull Packaging packaging,
            @NonNull String packageName,
            @NonNull String activityName,
            @NonNull String logTag,
            @NonNull IDevice device,
            @NonNull Logcat logcat) {
        this.project = project;
        this.packaging = packaging;
        this.packageName = packageName;
        this.activityName = activityName;
        this.logTag = logTag;
        this.device = device;
        this.logcat = logcat;
    }

    /**
     * @see #run(Consumer, Iterable)
     */
    public void run(
            @NonNull Runnable verifyOriginalCode,
            @NonNull Change... changes)
            throws Exception {
        run(verifyOriginalCode, Arrays.asList(changes));
    }

    /**
     * Runs the project, applies changes and verifies results are as expected.
     *
     * <p>Logcat is cleared between every change.
     */
    public void run(
            @NonNull Runnable verifyOriginalCode,
            @NonNull Iterable<Change> changes)
            throws Exception {
        try (Closeable ignored = new UninstallOnClose(device, packageName)) {
            logcat.start(device, logTag);

            // Open project in simulated IDE
            AndroidProject model = project.model().getSingle();
            long token = PackagingUtils.computeApplicationHash(model.getBuildFolder());
            InstantRun instantRunModel = InstantRunTestUtils.getInstantRunModel(model);

            // Run first time on device
            InstantRunTestUtils.doInitialBuild(
                    project, packaging, device.getVersion().getApiLevel(), COLDSWAP_MODE);

            // Deploy to device
            InstantRunBuildInfo info = InstantRunTestUtils.loadContext(instantRunModel);
            InstantRunTestUtils.doInstall(device, info.getArtifacts());

            logcat.clearFiltered();

            // Run app
            InstantRunTestUtils.unlockDevice(device);

            InstantRunTestUtils.runApp(
                    device,
                    String.format("%s/.%s", packageName, activityName));

            ILogger iLogger = Mockito.mock(ILogger.class);

            //Connect to device
            InstantRunClient client =
                    new InstantRunClient(packageName, iLogger, token, 8125);

            // Give the app a chance to start
            InstantRunTestUtils.waitForAppStart(client, device);

            verifyOriginalCode.run();

            for (Change change : changes) {
                change.makeChange();

                // Now build the hot swap patch.
                project.executor()
                        .withPackaging(packaging)
                        .withInstantRun(device.getVersion().getApiLevel(), COLDSWAP_MODE)
                        .run("assembleDebug");

                InstantRunArtifact artifact =
                        InstantRunTestUtils.getReloadDexArtifact(instantRunModel);

                FileTransfer fileTransfer = FileTransfer.createHotswapPatch(artifact.file);

                logcat.clearFiltered();

                UpdateMode updateMode = client.pushPatches(
                        device,
                        info.getTimeStamp(),
                        ImmutableList.of(fileTransfer.getPatch()),
                        UpdateMode.HOT_SWAP,
                        false /*restartActivity*/,
                        true /*showToast*/);

                assertEquals(UpdateMode.HOT_SWAP, updateMode);

                InstantRunTestUtils.waitForAppStart(client, device);

                change.verifyChange(client, logcat, device);
            }
        }
    }

    interface Change {
        void makeChange() throws Exception;
        void verifyChange(
                @NonNull InstantRunClient client,
                @NonNull Logcat logcat,
                @NonNull IDevice device) throws Exception;
    }

    /**
     * Simple {@link Change} implementation that verifies that the expected message was logged.
     */
    public abstract static class LogcatChange implements Change {
        public static final String CHANGE_PREFIX = "CHANGE ";

        protected final String originalMessage;
        protected final int changeId;

        protected LogcatChange(int changeId, String originalMessage) {
            this.originalMessage = originalMessage;
            this.changeId = changeId;
        }

        /**
         * Change the code in a way that CHANGE_PREFIX + changeId are logged on activity restart.
         */
        @Override
        public abstract void makeChange() throws Exception;

        @Override
        public void verifyChange(
                @NonNull InstantRunClient client,
                @NonNull Logcat logcat,
                @NonNull IDevice device) throws Exception {
            // Should not have restarted activity
            for (LogCatMessage logCatMessage : logcat.getFilteredLogCatMessages()) {
                String message = logCatMessage.getMessage();
                assertThat(message).isNotEqualTo(originalMessage);
                assertThat(message).doesNotContain(CHANGE_PREFIX);
            }

            client.restartActivity(device);
            InstantRunTestUtils.waitForAppStart(client, device);

            for (LogCatMessage logCatMessage : logcat.getFilteredLogCatMessages()) {
                String message = logCatMessage.getMessage();
                assertThat(message).isNotEqualTo(originalMessage);
                if (message.contains(CHANGE_PREFIX)) {
                    assertThat(message).isEqualTo(CHANGE_PREFIX + changeId);
                }
            }
        }
    };
}
