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

package com.android.build.gradle.internal.scope;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.tasks.PackageApplication;
import com.google.common.collect.Lists;
import java.util.List;
import org.gradle.api.DefaultTask;

/**
 * Partial implementation of the {@link InstantRunVariantScope} that contains generic implementation
 * of the interface for Gradle or an external build system.
 */
public abstract class GenericVariantScopeImpl extends TaskOutputHolderImpl implements InstantRunVariantScope {

    private List<AndroidTask<? extends DefaultTask>> coldSwapBuildTasks = Lists.newArrayList();

    @Override
    public List<AndroidTask<? extends DefaultTask>> getColdSwapBuildTasks() {
        return coldSwapBuildTasks;
    }

    @Override
    public void addColdSwapBuildTask(@NonNull AndroidTask<? extends DefaultTask> task) {
        this.coldSwapBuildTasks.add(task);
    }

    private AndroidTask<PackageApplication> packageApplicationTask;

    @Override
    public AndroidTask<PackageApplication> getPackageApplicationTask() {
        return packageApplicationTask;
    }

    @Override
    public void setPackageApplicationTask(
            AndroidTask<PackageApplication> packageApplicationTask) {
        this.packageApplicationTask = packageApplicationTask;
    }
}
