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

package com.android.build.gradle.internal.ide;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.VariantOutput;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.BuildOutputs;
import com.android.build.gradle.internal.scope.InstantAppOutputScope;
import com.android.build.gradle.internal.scope.TaskOutputHolder;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.ide.common.build.ApkInfo;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Supplier of {@link BuildOutput} for built artifacts. */
public class BuildOutputsSupplier implements BuildOutputSupplier<Collection<BuildOutput>> {

    @NonNull private final List<File> outputFolders;
    @NonNull private final List<VariantScope.OutputType> outputTypes;

    public BuildOutputsSupplier(
            @NonNull List<VariantScope.OutputType> outputTypes, @NonNull List<File> outputFolders) {
        this.outputFolders = outputFolders;
        this.outputTypes = outputTypes;
    }

    @Override
    @NonNull
    public Collection<BuildOutput> get() {
        ImmutableList.Builder<BuildOutput> outputs = ImmutableList.builder();
        outputFolders.forEach(
                outputFolder -> {
                    if (!outputFolder.exists()) {
                        return;
                    }
                    Collection<BuildOutput> previous = BuildOutputs.load(outputTypes, outputFolder);
                    if (previous.isEmpty()) {
                        outputTypes.forEach(
                                taskOutputType -> {
                                    if (taskOutputType
                                            == TaskOutputHolder.TaskOutputType.INSTANTAPP_BUNDLE) {
                                        processInstantAppFolder(outputFolder, outputs);
                                    } else {
                                        // take the FileCollection content as face value.
                                        // FIX ME : we should do better than this, maybe make sure output.gson
                                        // is always produced for those items.
                                        File[] files = outputFolder.listFiles();
                                        if (files != null && files.length > 0) {
                                            for (File file : files) {
                                                processFile(taskOutputType, file, outputs);
                                            }
                                        }
                                    }
                                });
                    } else {
                        outputs.addAll(previous);
                    }
                });
        return outputs.build();
    }

    @Override
    public File guessOutputFile(String relativeFileName) {
        return outputFolders.isEmpty()
                ? new File(relativeFileName)
                : new File(outputFolders.get(0), relativeFileName);
    }

    private static void processFile(
            VariantScope.OutputType taskOutputType,
            File file,
            ImmutableList.Builder<BuildOutput> outputs) {
        if (taskOutputType == TaskOutputHolder.TaskOutputType.MERGED_MANIFESTS) {
            if (file.getName().equals(SdkConstants.ANDROID_MANIFEST_XML)) {
                outputs.add(
                        new BuildOutput(
                                taskOutputType,
                                ApkInfo.of(VariantOutput.OutputType.MAIN, ImmutableList.of(), 0),
                                file));
            }
        } else {
            VariantOutput.OutputType fileOutputType =
                    taskOutputType == TaskOutputHolder.TaskOutputType.AAR
                                    || taskOutputType == TaskOutputHolder.TaskOutputType.APK
                            ? VariantOutput.OutputType.MAIN
                            : VariantOutput.OutputType.SPLIT;
            outputs.add(
                    new BuildOutput(
                            taskOutputType,
                            ApkInfo.of(fileOutputType, ImmutableList.of(), 0),
                            file));
        }
    }

    // FIXME: Remove this when instantApps no longer output an AndroidProject model.
    private static void processInstantAppFolder(
            File outputFolder, ImmutableList.Builder<BuildOutput> outputs) {
        InstantAppOutputScope instantAppOutputScope = InstantAppOutputScope.load(outputFolder);
        if (instantAppOutputScope != null) {
            outputs.add(
                    new BuildOutput(
                            TaskOutputHolder.TaskOutputType.INSTANTAPP_BUNDLE,
                            ApkInfo.of(VariantOutput.OutputType.MAIN, ImmutableList.of(), 0),
                            instantAppOutputScope.getInstantAppBundle()));
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(outputFolders, outputTypes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BuildOutputsSupplier that = (BuildOutputsSupplier) o;
        return Objects.equals(outputFolders, that.outputFolders)
                && Objects.equals(outputTypes, that.outputTypes);
    }
}
