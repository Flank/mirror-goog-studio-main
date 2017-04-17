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

package com.android.build.gradle.tasks.factory;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.TaskOutputHolder;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.TestServerTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.builder.testing.api.TestServer;
import com.android.utils.StringHelper;
import org.gradle.api.plugins.JavaBasePlugin;

/**
 * Configuration Action for a TestServerTask.
 */
public class TestServerTaskConfigAction implements TaskConfigAction<TestServerTask> {

    private final VariantScope scope;

    private final TestServer testServer;

    public TestServerTaskConfigAction(VariantScope scope, TestServer testServer) {
        this.scope = scope;
        this.testServer = testServer;
    }

    @NonNull
    @Override
    public String getName() {
        return scope.getVariantConfiguration().hasFlavors()
                ? scope.getTaskName(testServer.getName() + "Upload")
                : testServer.getName() + ("Upload");
    }

    @NonNull
    @Override
    public Class<TestServerTask> getType() {
        return TestServerTask.class;
    }
    @Override
    public void execute(@NonNull TestServerTask serverTask) {

        final BaseVariantData testedVariantData = scope.getTestedVariantData();

        final String variantName = scope.getVariantConfiguration().getFullName();
        serverTask.setDescription(
                "Uploads APKs for Build \'"
                        + variantName
                        + "\' to Test Server \'"
                        + StringHelper.capitalize(testServer.getName()) + "\'.");
        serverTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
        serverTask.setVariantName(variantName);

        serverTask.setTestServer(testServer);

        if (testedVariantData != null
                && testedVariantData.getScope().hasOutput(TaskOutputHolder.TaskOutputType.APK)) {
            serverTask.setTestedApks(
                    testedVariantData.getScope().getOutputs(TaskOutputHolder.TaskOutputType.APK));
        }

        serverTask.setTestApks(scope.getOutputs(TaskOutputHolder.TaskOutputType.APK));

        if (!testServer.isConfigured()) {
            serverTask.setEnabled(false);
        }
    }
}
