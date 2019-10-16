/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.InstantAppOutputScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction;
import com.android.builder.testing.ConnectedDevice;
import com.android.builder.testing.ConnectedDeviceProvider;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.DeviceException;
import com.android.builder.testing.api.DeviceProvider;
import com.android.ddmlib.IDevice;
import com.android.instantapp.run.InstantAppRunException;
import com.android.instantapp.run.InstantAppSideLoader;
import com.android.instantapp.run.RunListener;
import com.android.sdklib.AndroidVersion;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

/**
 * Task side loading an instant app variant. It looks at connected device, checks if preO or postO
 * and either multi-install the feature APKs or upload the bundle.
 */
public abstract class InstantAppSideLoadTask extends NonIncrementalTask {

    private Provider<File> adbExecutableProvider;

    public InstantAppSideLoadTask() {
        this.getOutputs()
                .upToDateWhen(
                        task -> {
                            getLogger().debug("Side load task is always run.");
                            return false;
                        });
    }

    @Override
    protected void doTaskAction() throws DeviceException, ExecutionException, IOException {
        if (!adbExecutableProvider.isPresent()) {
            throw new GradleException("No adb file found.");
        }

        File inputBundleDirectory = getBundleDir().get().getAsFile();
        InstantAppOutputScope outputScope = InstantAppOutputScope.load(inputBundleDirectory);

        if (outputScope == null) {
            throw new GradleException(
                    "Instant app outputs not found in "
                            + inputBundleDirectory.getAbsolutePath()
                            + ".");
        }

        DeviceProvider deviceProvider =
                new ConnectedDeviceProvider(
                        adbExecutableProvider.get(), 0, new LoggerWrapper(getLogger()));

        RunListener runListener =
                new RunListener() {
                    @Override
                    public void printMessage(@NonNull String message) {
                        getLogger().info(message);
                    }

                    @Override
                    public void logMessage(
                            @NonNull String message, @Nullable InstantAppRunException e) {
                        if (e == null) {
                            getLogger().debug(message);
                        } else {
                            getLogger().debug(message, e);
                            getLogger().error(message, e);
                        }
                    }

                    @Override
                    public void setProgress(double fraction) {}

                    @Override
                    public boolean isCancelled() {
                        return false;
                    }
                };

        String appId = outputScope.getApplicationId();
        File bundleFile = outputScope.getInstantAppBundle();

        deviceProvider.use(
                () -> {
                    List<? extends DeviceConnector> devices = deviceProvider.getDevices();
                    for (DeviceConnector device : devices) {
                        if (device instanceof ConnectedDevice) {
                            IDevice iDevice = ((ConnectedDevice) device).getIDevice();

                            InstantAppSideLoader sideLoader;
                            if (iDevice.getVersion()
                                    .isGreaterOrEqualThan(AndroidVersion.VersionCodes.O)) {
                                // List of apks to install in postO rather than unzipping the bundle
                                // It will be computed only if there's at least one device postO
                                final List<File> apks = new ArrayList<>();
                                for (File apkDirectory : outputScope.getApkDirectories()) {
                                    for (BuildOutput buildOutput :
                                            ExistingBuildElements.from(
                                                    InternalArtifactType.APK.INSTANCE,
                                                    apkDirectory)) {
                                        apks.add(buildOutput.getOutputFile());
                                    }
                                }
                                sideLoader = new InstantAppSideLoader(appId, apks, runListener);
                            } else {
                                sideLoader =
                                        new InstantAppSideLoader(appId, bundleFile, runListener);
                            }
                            sideLoader.install(iDevice);
                        }
                    }
                    return null;
                });
    }

    @InputFile
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public Provider<File> getAdbExe() {
        return adbExecutableProvider;
    }

    @InputFiles
    @NonNull
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getBundleDir();

    public static class CreationAction extends TaskCreationAction<InstantAppSideLoadTask> {

        @NonNull private final VariantScope scope;

        public CreationAction(@NonNull VariantScope scope) {
            this.scope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("sideLoad", "InstantApp");
        }

        @NonNull
        @Override
        public Class<InstantAppSideLoadTask> getType() {
            return InstantAppSideLoadTask.class;
        }

        @Override
        public void configure(@NonNull InstantAppSideLoadTask task) {
            task.setDescription("Side loads the " + scope.getVariantData().getDescription() + ".");
            task.setVariantName(scope.getFullVariantName());

            task.setGroup(TaskManager.INSTALL_GROUP);

            task.adbExecutableProvider =
                    scope.getGlobalScope().getSdkComponents().getAdbExecutableProvider();
            scope.getArtifacts()
                    .setTaskInputToFinalProduct(
                            InternalArtifactType.INSTANTAPP_BUNDLE.INSTANCE, task.getBundleDir());
        }
    }
}
