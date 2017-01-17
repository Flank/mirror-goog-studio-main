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
import static com.android.build.gradle.integration.common.utils.DeviceHelper.DEFAULT_ADB_TIMEOUT_MSEC;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.internal.incremental.ColdswapMode;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.InstantRun;
import com.android.builder.model.OptionalCompilationStep;
import com.android.builder.model.Variant;
import com.android.builder.testing.api.DeviceException;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.InstallException;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.sdklib.AndroidVersion;
import com.android.testutils.apk.Apk;
import com.android.testutils.apk.SplitApks;
import com.android.tools.fd.client.AppState;
import com.android.tools.fd.client.InstantRunArtifact;
import com.android.tools.fd.client.InstantRunArtifactType;
import com.android.tools.fd.client.InstantRunBuildInfo;
import com.android.tools.fd.client.InstantRunClient;
import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class InstantRunTestUtils {

    // each test class, has a port used for IR to avoid interference
    public static final Map<String, Integer> PORTS =
            ImmutableMap.<String, Integer>builder()
                    .put("ConnectedColdSwapTest", 8115)
                    .put("NativeLibraryInstantRunTest", 8116)
                    .put("HotSwapTest", 8117)
                    .put("ButterKnifeTest", 8118)
                    .put("DaggerTest", 8119)
                    .put("ResourcesSwapTest", 8120)
                    .build();
    private static final int SLEEP_TIME_MSEC = 200;

    @NonNull
    public static InstantRunBuildInfo loadContext(@NonNull InstantRun instantRunModel)
            throws Exception {
        InstantRunBuildInfo context = InstantRunBuildInfo.get(
                Files.toString(instantRunModel.getInfoFile(), Charsets.UTF_8));
        assertNotNull(context);
        return context;
    }

    @NonNull
    public static InstantRunBuildContext loadBuildContext(
            int apiLevel,
            @NonNull InstantRun instantRunModel) throws Exception {
        InstantRunBuildContext context = new InstantRunBuildContext();
        context.setApiLevel(apiLevel, null, null);
        context.loadFromXml(Files.toString(instantRunModel.getInfoFile(), Charsets.UTF_8));
        return context;
    }

    @NonNull
    public static InstantRun getInstantRunModel(@NonNull AndroidProject project) {
        Collection<Variant> variants = project.getVariants();
        for (Variant variant : variants) {
            if ("debug".equals(variant.getName())) {
                return variant.getMainArtifact().getInstantRun();
            }
        }
        throw new AssertionError("Could not find debug variant.");
    }

    static void doInstall(
            @NonNull IDevice device,
            @NonNull List<InstantRunArtifact> artifacts) throws DeviceException,
            InstallException {
        if (artifacts.size() == 1 && artifacts.get(0).type == InstantRunArtifactType.MAIN) {
            device.installPackage(artifacts.get(0).file.getAbsolutePath(), true /*reinstall*/);
            return;
        }

        assertThat(device.getVersion()).isAtLeast(AndroidVersion.ART_RUNTIME);
        List<File> apkFiles = Lists.newArrayList();
        for (InstantRunArtifact artifact : artifacts) {
            switch (artifact.type) {
                case SPLIT_MAIN:
                    apkFiles.add(0, artifact.file);
                    break;
                case SPLIT:
                    apkFiles.add(artifact.file);
                    break;
                default:
                    throw new AssertionError("Unexpected artifact to install: " + artifact);
            }
        }
        device.installPackages(
                apkFiles,
                true /*reinstall*/,
                ImmutableList.<String>of(),
                DEFAULT_ADB_TIMEOUT_MSEC,
                MILLISECONDS);
    }

    static void runApp(IDevice device, String target) throws Exception {
        IShellOutputReceiver receiver = new CollectingOutputReceiver();
        String command = "am start" +
                " -n " + target +
                " -a android.intent.action.MAIN" +
                " -c android.intent.category.LAUNCHER";
        device.executeShellCommand(
                command, receiver, DEFAULT_ADB_TIMEOUT_MSEC, MILLISECONDS);
    }

    static void stopApp(@NonNull IDevice device, @NonNull String target) throws Exception {
        IShellOutputReceiver receiver = new CollectingOutputReceiver();
        String command = "am start" +
                " -n " + target +
                " -a android.intent.action.MAIN" +
                " -c android.intent.category.LAUNCHER";
        device.executeShellCommand(
                command, receiver, DEFAULT_ADB_TIMEOUT_MSEC, MILLISECONDS);
    }

    static void unlockDevice(@NonNull IDevice device) throws Exception {
        IShellOutputReceiver receiver = new CollectingOutputReceiver();
        device.executeShellCommand(
                "input keyevent KEYCODE_WAKEUP", receiver,
                DEFAULT_ADB_TIMEOUT_MSEC, MILLISECONDS);
        device.executeShellCommand(
                "wm dismiss-keyguard", receiver,
                DEFAULT_ADB_TIMEOUT_MSEC, MILLISECONDS);
    }

    @NonNull
    static InstantRun doInitialBuild(
            @NonNull GradleTestProject project,
            int apiLevel,
            @NonNull ColdswapMode coldswapMode) {
        project.execute("clean");
        InstantRun instantRunModel = getInstantRunModel(
                Iterables.getOnlyElement(project.model().getSingle().getModelMap().values()));

        project.executor()
                .withInstantRun(apiLevel, coldswapMode, OptionalCompilationStep.FULL_APK)
                .run("assembleDebug");

        return instantRunModel;
    }

    @NonNull
    public static InstantRunArtifact getOnlyArtifact(@NonNull InstantRun instantRunModel)
            throws Exception {
        InstantRunBuildInfo context = loadContext(instantRunModel);

        assertThat(context.getArtifacts()).hasSize(1);
        return Iterables.getOnlyElement(context.getArtifacts());
    }

    /**
     * Gets the RELOAD_DEX {@link InstantRunArtifact} produced by last build.
     */
    @NonNull
    public static InstantRunArtifact getReloadDexArtifact(@NonNull InstantRun instantRunModel)
            throws Exception {
        InstantRunArtifact artifact = getOnlyArtifact(instantRunModel);
        assertThat(artifact.type).isEqualTo(InstantRunArtifactType.RELOAD_DEX);
        return artifact;
    }

    /**
     * Gets the RESOURCES {@link InstantRunArtifact} produced by last build.
     */
    @NonNull
    public static InstantRunArtifact getResourcesArtifact(
            @NonNull InstantRun instantRunModel) throws Exception {
        InstantRunArtifact artifact = getOnlyArtifact(instantRunModel);
        assertThat(artifact.type).isEqualTo(InstantRunArtifactType.RESOURCES);
        return artifact;
    }

    @NonNull
    public static SplitApks getCompiledColdSwapChange(@NonNull InstantRun instantRunModel)
            throws Exception {
        return getCompiledColdSwapChange(loadContext(instantRunModel).getArtifacts());
    }

    @NonNull
    public static SplitApks getCompiledColdSwapChange(@NonNull List<InstantRunArtifact> artifacts)
            throws Exception {
        assertThat(artifacts).isNotEmpty();

        EnumSet<InstantRunArtifactType> allowedArtifactTypes =
                EnumSet.of(
                        InstantRunArtifactType.SPLIT,
                        InstantRunArtifactType.SPLIT_MAIN,
                        InstantRunArtifactType.MAIN);

        List<Apk> apks = new ArrayList<>();
        for (InstantRunArtifact artifact : artifacts) {
            if (!allowedArtifactTypes.contains(artifact.type)) {
                throw new AssertionError("Unexpected artifact " + artifact);
            }
            apks.add(new Apk(artifact.file.toPath()));
        }
        return new SplitApks(apks);
    }

    public static void printBuildInfoFile(@Nullable InstantRun instantRunModel) {
        if (instantRunModel == null) {
            System.err.println("Cannot print build info file as model is null");
            return;
        }
        try {
            System.out.println("------------ build info file ------------\n"
                    + Files.toString(instantRunModel.getInfoFile(), Charsets.UTF_8)
                    + "---------- end build info file ----------\n");
        } catch (IOException e) {
            System.err.println("Unable to print build info xml file: \n" +
                    Throwables.getStackTraceAsString(e));
        }
    }

    static void startService(@NonNull IDevice device, @NonNull String packageName)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
            IOException {
        IShellOutputReceiver receiver = new CollectingOutputReceiver();
        String startIrServiceCmd =
                String.format(
                        "am startservice %1$s/com.android.tools.fd.runtime.InstantRunService",
                        packageName);
        device.executeShellCommand(
                startIrServiceCmd, receiver, DEFAULT_ADB_TIMEOUT_MSEC, MILLISECONDS);
    }

    static void waitForAppStart(
            @NonNull InstantRunClient client, @NonNull IDevice device)
            throws InterruptedException, IOException {
        AppState appState = null;
        int times = 0;
        while (appState != AppState.FOREGROUND) {
            if (times > TimeUnit.SECONDS.toMillis(15)) {
                throw new AssertionError("App did not start");
            }
            Thread.sleep(SLEEP_TIME_MSEC);
            times += SLEEP_TIME_MSEC;
            try {
                appState = client.getAppState(device);
            } catch (IOException e) {
                System.err.println(Throwables.getStackTraceAsString(e));
            }
        }
    }
}
