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
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction;
import com.android.builder.testing.ConnectedDeviceProvider;
import com.android.builder.testing.api.DeviceException;
import com.android.builder.testing.api.DeviceProvider;
import com.android.instantapp.provision.ProvisionException;
import com.android.repository.api.ConsoleProgressIndicator;
import com.android.repository.api.LocalPackage;
import com.android.sdklib.repository.AndroidSdkHandler;
import java.io.File;
import java.util.function.Supplier;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * Task to provision connected devices for Instant App. All the connected devices are provisioned.
 */
public class InstantAppProvisionTask extends DefaultTask {

    private Supplier<File> instantAppSdk;

    private Provider<File> adbExecutableProvider;

    @TaskAction
    public void provisionDevices() throws ProvisionException, DeviceException {
        if (instantAppSdk.get() == null) {
            throw new GradleException("No Instant App Sdk found.");
        }

        if (!adbExecutableProvider.isPresent()) {
            throw new GradleException("No adb file found.");
        }

        DeviceProvider deviceProvider =
                new ConnectedDeviceProvider(
                        adbExecutableProvider.get(), 0, new LoggerWrapper(getLogger()));

        InstantAppProvisioner provisioner =
                new InstantAppProvisioner(instantAppSdk.get(), deviceProvider, getLogger());

        provisioner.provisionDevices();
    }

    @InputFile
    public Provider<File> getAdbExe() {
        return adbExecutableProvider;
    }

    @InputDirectory
    @Nullable
    public File getInstantAppSdk() {
        return instantAppSdk.get();
    }

    public static class CreationAction extends TaskCreationAction<InstantAppProvisionTask> {

        @NonNull private final GlobalScope globalScope;

        public CreationAction(@NonNull GlobalScope globalScope) {
            this.globalScope = globalScope;
        }

        @NonNull
        @Override
        public String getName() {
            return "provisionInstantApp";
        }

        @NonNull
        @Override
        public Class<InstantAppProvisionTask> getType() {
            return InstantAppProvisionTask.class;
        }

        @Override
        public void configure(@NonNull InstantAppProvisionTask task) {
            task.setDescription("Provision all connected devices for Instant App.");

            task.adbExecutableProvider = globalScope.getSdkComponents().getAdbExecutableProvider();

            task.instantAppSdk =
                    TaskInputHelper.memoize(
                            () -> {
                                File sdkFolder = globalScope.getSdkComponents().getSdkFolder();
                                if (sdkFolder != null) {
                                    LocalPackage instantAppSdk =
                                            AndroidSdkHandler.getInstance(sdkFolder)
                                                    .getLocalPackage(
                                                            "extras;google;instantapps",
                                                            new ConsoleProgressIndicator());
                                    if (instantAppSdk != null) {
                                        return instantAppSdk.getLocation();
                                    }
                                }
                                return null;
                            });
        }
    }
}
