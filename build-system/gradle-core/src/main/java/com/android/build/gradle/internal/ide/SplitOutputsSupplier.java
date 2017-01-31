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

    @NonNull private final SplitScope splitScope;
    @NonNull private final List<File> outputFolders;
    @NonNull private final List<VariantScope.OutputType> outputTypes;
    @NonNull private final SplitScope.SplitCreator splitCreator;

    public SplitOutputsSupplier(
            @NonNull SplitScope splitScope,
            @NonNull SplitScope.SplitCreator splitCreator,
            @NonNull List<VariantScope.OutputType> outputTypes,
            @NonNull List<File> outputFolders) {
        this.outputFolders = outputFolders;
        this.splitScope = splitScope;
        this.outputTypes = outputTypes;
        this.splitCreator = splitCreator;
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
                    boolean loaded = splitScope.load(outputTypes, outputFolder, splitCreator);
                    if (!loaded) {
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
                    }
                });
        outputTypes.forEach(
                taskOutputType -> outputs.addAll(splitScope.getOutputs(taskOutputType)));

        return outputs.build();
    }

    private void processFile(
            VariantScope.OutputType taskOutputType,
            File file,
            ImmutableList.Builder<SplitScope.SplitOutput> outputs) {
        if (taskOutputType == TaskOutputHolder.TaskOutputType.MERGED_MANIFESTS) {
            if (file.getName().equals(SdkConstants.ANDROID_MANIFEST_XML)) {
                outputs.add(
                        new SplitScope.SplitOutput(
                                taskOutputType,
                                splitCreator.create(
                                        VariantOutput.OutputType.MAIN, ImmutableList.of()),
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
                            splitCreator.create(fileOutputType, ImmutableList.of()),
                            file));
        }
    }
}
