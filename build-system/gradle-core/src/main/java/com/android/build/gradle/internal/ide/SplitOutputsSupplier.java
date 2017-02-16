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
import com.android.build.gradle.internal.scope.SplitScope;
import com.android.build.gradle.internal.scope.TaskOutputHolder;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.ide.common.build.ApkInfo;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * FIX ME : implement equals() and hashCode()
 *
 * <p>Supplier of {@link com.android.build.gradle.internal.scope.SplitScope.SplitOutput} for built
 * artifacts.
 */
public class SplitOutputsSupplier
        implements SerializableSupplier<Collection<SplitScope.SplitOutput>> {

    @NonNull private final List<File> outputFolders;
    @NonNull private final List<VariantScope.OutputType> outputTypes;

    public SplitOutputsSupplier(
            @NonNull List<VariantScope.OutputType> outputTypes,
            @NonNull List<File> outputFolders) {
        this.outputFolders = outputFolders;
        this.outputTypes = outputTypes;
    }

    @Override
    @NonNull
    public Collection<SplitScope.SplitOutput> get() {
        ImmutableList.Builder<SplitScope.SplitOutput> outputs = ImmutableList.builder();
        outputFolders.forEach(
                outputFolder -> {
                    if (!outputFolder.exists()) {
                        return;
                    }
                    Collection<SplitScope.SplitOutput> previous =
                            SplitScope.load(outputTypes, outputFolder);
                    if (previous.isEmpty()) {
                        outputTypes.forEach(
                                taskOutputType -> {
                                    // take the FileCollection content as face value.
                                    // FIX ME : we should do better than this, maybe make sure output.gson
                                    // is always produced for those items.
                                    File[] files = outputFolder.listFiles();
                                    if (files != null && files.length > 0) {
                                        for (File file : files) {
                                            processFile(taskOutputType, file, outputs);
                                        }
                                    }
                                });
                    } else {
                        outputs.addAll(previous);
                    }
                });
        return outputs.build();
    }

    private static void processFile(
            VariantScope.OutputType taskOutputType,
            File file,
            ImmutableList.Builder<SplitScope.SplitOutput> outputs) {
        if (taskOutputType == TaskOutputHolder.TaskOutputType.MERGED_MANIFESTS) {
            if (file.getName().equals(SdkConstants.ANDROID_MANIFEST_XML)) {
                outputs.add(
                        new SplitScope.SplitOutput(
                                taskOutputType,
                                ApkInfo.of(VariantOutput.OutputType.MAIN, ImmutableList.of(), 0),
                                file));
            }
        } else {
            VariantOutput.OutputType fileOutputType =
                    taskOutputType == TaskOutputHolder.TaskOutputType.AAR
                                    || taskOutputType == TaskOutputHolder.TaskOutputType.APK
                                    || taskOutputType == TaskOutputHolder.TaskOutputType.APKB
                            ? VariantOutput.OutputType.MAIN
                            : VariantOutput.OutputType.SPLIT;
            outputs.add(
                    new SplitScope.SplitOutput(
                            taskOutputType,
                            ApkInfo.of(fileOutputType, ImmutableList.of(), 0),
                            file));
        }
    }
}
