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

import org.gradle.api.tasks.ParallelizableTask;

/**
 * Task to package an atom.
 */
@ParallelizableTask
public class PackageAtom extends PackageAndroidArtifact {
    // ----- ConfigAction -----

    public static class ConfigAction extends PackageAndroidArtifact.ConfigAction<PackageAtom> {

        public ConfigAction(@NonNull PackagingScope packagingscope) {
            super(packagingscope, null);
        }

        @Override
        @NonNull
        public String getName() {
            return packagingScope.getTaskName("package", "Atom");
        }

        @Override
        @NonNull
        public Class<PackageAtom> getType() {
            return PackageAtom.class;
        }

        @Override
        public void execute(@NonNull final PackageAtom packageAtom) {
            super.execute(packageAtom);

            ConventionMappingHelper.map(packageAtom, "atomMetadataFolder",
                    packagingScope::getAtomMetadataBaseFolder);

            ConventionMappingHelper.map(packageAtom, "outputFile",
                    packagingScope::getOutputPackage);
        }
    }
}
