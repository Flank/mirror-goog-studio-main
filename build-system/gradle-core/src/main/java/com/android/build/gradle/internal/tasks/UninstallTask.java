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
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.ApkVariantData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.builder.sdk.SdkInfo;
import com.android.builder.testing.ConnectedDeviceProvider;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.DeviceException;
import com.android.builder.testing.api.DeviceProvider;
import com.android.utils.StringHelper;
import java.io.File;
import java.util.List;
import java.util.function.Supplier;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

public class UninstallTask extends AndroidBuilderTask {

    private BaseVariantData variant;

    private int mTimeOutInMs = 0;

    private Supplier<File> adbSupplier = TaskInputHelper.memoize(() -> {
                SdkInfo sdkInfo = getBuilder().getSdkInfo();
                if (sdkInfo == null) {
                    return null;
                }
                return sdkInfo.getAdb();
            }
    );

    public UninstallTask() {
        this.getOutputs().upToDateWhen(task -> {
            getLogger().debug("Uninstall task is always run.");
            return false;
        });
    }

    @TaskAction
    public void uninstall() throws DeviceException {
        final Logger logger = getLogger();
        final String applicationId = variant.getApplicationId();

        logger.info("Uninstalling app: {}", applicationId);

        final DeviceProvider deviceProvider = new ConnectedDeviceProvider(
                adbSupplier.get(),
                getTimeOutInMs(),
                getILogger());

        deviceProvider.init();
        final List<? extends DeviceConnector> devices = deviceProvider.getDevices();

        for (DeviceConnector device : devices) {
            device.uninstallPackage(applicationId, getTimeOutInMs(), getILogger());
            logger.lifecycle(
                    "Uninstalling {} (from {}:{}) from device '{}' ({}).",
                    applicationId, getProject().getName(),
                    variant.getVariantConfiguration().getFullName(),
                    device.getName(), device.getSerialNumber());
        }

        int n = devices.size();
        logger.quiet("Uninstalled {} from {} device{}.",
                applicationId, n, n == 1 ? "" : "s");

    }

    @InputFile
    public File getAdbExe() {
        return adbSupplier.get();
    }

    public BaseVariantData getVariant() {
        return variant;
    }

    public void setVariant(BaseVariantData variant) {
        this.variant = variant;
    }

    @Input
    public int getTimeOutInMs() {
        return mTimeOutInMs;
    }

    public void setTimeOutInMs(int timeoutInMs) {
        mTimeOutInMs = timeoutInMs;
    }

    public static class ConfigAction implements TaskConfigAction<UninstallTask> {

        private final VariantScope scope;

        public ConfigAction(VariantScope scope) {
            this.scope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            String prefix = "uninstall";

            return prefix + StringHelper.capitalize(scope.getVariantConfiguration().getFullName());
        }

        @NonNull
        @Override
        public Class<UninstallTask> getType() {
            return UninstallTask.class;
        }

        @Override
        public void execute(@NonNull UninstallTask uninstallTask) {

            uninstallTask.setDescription(
                    "Uninstalls the " + scope.getVariantData().getDescription() + ".");
            uninstallTask.setGroup(TaskManager.INSTALL_GROUP);
            uninstallTask.setVariant(scope.getVariantData());
            uninstallTask.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            uninstallTask.setVariantName(scope.getVariantConfiguration().getFullName());
            uninstallTask.setTimeOutInMs(
                    scope.getGlobalScope().getExtension().getAdbOptions().getTimeOutInMs());

            uninstallTask.adbSupplier = TaskInputHelper.memoize(() -> {
                // SDK is loaded somewhat dynamically, plus we don't want to do all this logic
                // if the task is not going to run, so use a supplier.
                final SdkInfo info = scope.getGlobalScope().getSdkHandler().getSdkInfo();
                return (info == null ? null : info.getAdb());
            });

            ((ApkVariantData) scope.getVariantData()).uninstallTask = uninstallTask;
        }
    }
}
