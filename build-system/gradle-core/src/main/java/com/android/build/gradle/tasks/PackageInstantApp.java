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

package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.PackagingScope;
import com.android.build.gradle.internal.tasks.FileSupplier;
import com.android.builder.packaging.NativeLibrariesPackagingMode;

import org.gradle.api.tasks.ParallelizableTask;

/**
 * Task to package an iapk.
 */
@ParallelizableTask
public class PackageInstantApp extends PackageAndroidArtifact implements FileSupplier {
    // ----- ConfigAction -----

    public static class ConfigAction
            extends PackageAndroidArtifact.ConfigAction<PackageInstantApp> {

        public ConfigAction(
                @NonNull PackagingScope packagingscope) {
            super(packagingscope, null, NativeLibrariesPackagingMode.COMPRESSED);
        }

        @Override
        @NonNull
        public String getName() {
            return packagingScope.getTaskName("package", "InstantApp");
        }

        @Override
        @NonNull
        public Class<PackageInstantApp> getType() {
            return PackageInstantApp.class;
        }

        @Override
        public void execute(@NonNull final PackageInstantApp packageInstantApp) {
            super.execute(packageInstantApp);

            ConventionMappingHelper.map(packageInstantApp, "atomMetadataFolder",
                    packagingScope::getAtomMetadataBaseFolder);

            ConventionMappingHelper.map(packageInstantApp, "outputFile",
                    packagingScope::getPackageInstantApp);
        }
    }
}
