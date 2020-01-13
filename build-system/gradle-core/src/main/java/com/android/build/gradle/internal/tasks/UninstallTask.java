/*
 * Copyright (C) 2012 The Android Open Source Project
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
import com.android.build.api.component.impl.ComponentPropertiesImpl;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.internal.testing.ConnectedDeviceProvider;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.DeviceException;
import com.android.builder.testing.api.DeviceProvider;
import com.android.utils.ILogger;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskProvider;

public abstract class UninstallTask extends NonIncrementalTask {

    // FIXME this should not be in the task
    private ComponentPropertiesImpl componentProperties;

    private int mTimeOutInMs = 0;

    private Provider<File> adbExecutableProvider;

    public UninstallTask() {
        this.getOutputs().upToDateWhen(task -> {
            getLogger().debug("Uninstall task is always run.");
            return false;
        });
    }

    @Override
    protected void doTaskAction() throws DeviceException, ExecutionException {
        final Logger logger = getLogger();
        final String applicationId = componentProperties.getVariantDslInfo().getApplicationId();

        logger.info("Uninstalling app: {}", applicationId);

        final ILogger iLogger = new LoggerWrapper(getLogger());
        final DeviceProvider deviceProvider =
                new ConnectedDeviceProvider(adbExecutableProvider.get(), getTimeOutInMs(), iLogger);

        deviceProvider.use(
                () -> {
                    final List<? extends DeviceConnector> devices = deviceProvider.getDevices();

                    for (DeviceConnector device : devices) {
                        device.uninstallPackage(applicationId, getTimeOutInMs(), iLogger);
                        logger.lifecycle(
                                "Uninstalling {} (from {}:{}) from device '{}' ({}).",
                                applicationId,
                                getProject().getName(),
                                componentProperties.getName(),
                                device.getName(),
                                device.getSerialNumber());
                    }

                    int n = devices.size();
                    logger.quiet(
                            "Uninstalled {} from {} device{}.",
                            applicationId,
                            n,
                            n == 1 ? "" : "s");

                    return null;
                });
    }

    @InputFile
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public File getAdbExe() {
        return adbExecutableProvider.get();
    }

    @Input
    public int getTimeOutInMs() {
        return mTimeOutInMs;
    }

    public void setTimeOutInMs(int timeoutInMs) {
        mTimeOutInMs = timeoutInMs;
    }

    public static class CreationAction
            extends VariantTaskCreationAction<UninstallTask, ComponentPropertiesImpl> {

        public CreationAction(@NonNull ComponentPropertiesImpl componentProperties) {
            super(componentProperties);
        }

        @NonNull
        @Override
        public String getName() {
            return computeTaskName("uninstall");
        }

        @NonNull
        @Override
        public Class<UninstallTask> getType() {
            return UninstallTask.class;
        }

        @Override
        public void configure(@NonNull UninstallTask task) {
            super.configure(task);

            task.componentProperties = component;
            task.setDescription(
                    "Uninstalls the " + component.getVariantData().getDescription() + ".");
            task.setGroup(TaskManager.INSTALL_GROUP);
            task.setTimeOutInMs(
                    component.getGlobalScope().getExtension().getAdbOptions().getTimeOutInMs());

            task.adbExecutableProvider =
                    component.getGlobalScope().getSdkComponents().getAdbExecutableProvider();

        }

        @Override
        public void handleProvider(
                @NonNull TaskProvider<? extends UninstallTask> taskProvider) {
            super.handleProvider(taskProvider);
            component.getTaskContainer().setUninstallTask(taskProvider);
        }
    }
}
