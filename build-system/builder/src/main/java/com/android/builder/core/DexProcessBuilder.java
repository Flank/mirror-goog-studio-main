/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.builder.core;

import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.process.JavaProcessInfo;
import com.android.ide.common.process.ProcessEnvBuilder;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.repository.Revision;
import com.android.sdklib.BuildToolInfo;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * A builder to create a dex-specific ProcessInfoBuilder
 */
public class DexProcessBuilder extends ProcessEnvBuilder<DexProcessBuilder> {
    public static final Revision MIN_MULTIDEX_BUILD_TOOLS_REV = new Revision(21, 0, 0);
    public static final Revision MIN_MULTI_THREADED_DEX_BUILD_TOOLS_REV = new Revision(22, 0, 2);
    public static final Revision FIXED_DX_MERGER = new Revision(23, 0, 2);

    @NonNull
    private final File mOutputFile;
    private boolean mVerbose = false;
    private boolean mMultiDex = false;
    private File mMainDexList = null;
    private Set<File> mInputs = Sets.newHashSet();

    public DexProcessBuilder(@NonNull File outputFile) {
        mOutputFile = outputFile;
    }

    @NonNull
    public DexProcessBuilder setVerbose(boolean verbose) {
        mVerbose = verbose;
        return this;
    }

    @NonNull
    public DexProcessBuilder setMultiDex(boolean multiDex) {
        mMultiDex = multiDex;
        return this;
    }

    @NonNull
    public DexProcessBuilder setMainDexList(File mainDexList) {
        mMainDexList = mainDexList;
        return this;
    }

    @NonNull
    public DexProcessBuilder addInput(File input) {
        mInputs.add(input);
        return this;
    }

    @NonNull
    public DexProcessBuilder addInputs(@NonNull Collection<File> inputs) {
        mInputs.addAll(inputs);
        return this;
    }

    @NonNull
    public File getOutputFile() {
        return mOutputFile;
    }

    public boolean isVerbose() {
        return mVerbose;
    }

    public boolean isMultiDex() {
        return mMultiDex;
    }

    public File getMainDexList() {
        return mMainDexList;
    }

    public Set<File> getInputs() {
        return mInputs;
    }

    @NonNull
    public JavaProcessInfo build(
            @NonNull BuildToolInfo buildToolInfo,
            @NonNull DexOptions dexOptions) throws ProcessException {

        Revision buildToolsRevision = buildToolInfo.getRevision();
        checkState(
                !mMultiDex
                        || buildToolsRevision.compareTo(MIN_MULTIDEX_BUILD_TOOLS_REV) >= 0,
                "Multi dex requires Build Tools " +
                        MIN_MULTIDEX_BUILD_TOOLS_REV.toString() +
                        " / Current: " +
                        buildToolsRevision.toShortString());


        ProcessInfoBuilder builder = new ProcessInfoBuilder();
        builder.addEnvironments(mEnvironment);

        String dx = buildToolInfo.getPath(BuildToolInfo.PathId.DX_JAR);
        if (dx == null || !new File(dx).isFile()) {
            throw new IllegalStateException("dx.jar is missing");
        }

        builder.setClasspath(dx);
        builder.setMain("com.android.dx.command.Main");

        if (dexOptions.getJavaMaxHeapSize() != null) {
            builder.addJvmArg("-Xmx" + dexOptions.getJavaMaxHeapSize());
        } else {
            builder.addJvmArg("-Xmx1024M");
        }

        builder.addArgs("--dex");

        if (mVerbose) {
            builder.addArgs("--verbose");
        }

        if (dexOptions.getJumboMode()) {
            builder.addArgs("--force-jumbo");
        }

        // only change thread count is build tools is 22.0.2+
        if (buildToolsRevision.compareTo(MIN_MULTI_THREADED_DEX_BUILD_TOOLS_REV) >= 0) {
            Integer threadCount = dexOptions.getThreadCount();
            if (threadCount == null) {
                builder.addArgs("--num-threads=4");
            } else {
                builder.addArgs("--num-threads=" + threadCount);
            }
        }

        if (mMultiDex) {
            builder.addArgs("--multi-dex");

            if (mMainDexList != null ) {
                builder.addArgs("--main-dex-list", mMainDexList.getAbsolutePath());
            }
        }

        for (String arg : dexOptions.getAdditionalParameters()) {
            builder.addArgs(arg);
        }

        builder.addArgs("--output", mOutputFile.getAbsolutePath());

        // input
        builder.addArgs(getFilesToAdd(buildToolsRevision));

        return builder.createJavaProcess();
    }

    @NonNull
    public List<String> getFilesToAdd(@Nullable Revision buildToolsRevision)
            throws ProcessException {
        // remove non-existing files.
        Set<File> existingFiles = Sets.filter(mInputs, new Predicate<File>() {
            @Override
            public boolean apply(@Nullable File input) {
                return input != null && input.exists();
            }
        });

        if (existingFiles.isEmpty()) {
            throw new ProcessException("No files to pass to dex.");
        }

        Collection<File> files = existingFiles;

        // sort the inputs
        if (buildToolsRevision != null && buildToolsRevision.compareTo(FIXED_DX_MERGER) < 0) {
            List<File> sortedList = Lists.newArrayList(existingFiles);
            Collections.sort(sortedList, new Comparator<File>() {
                @Override
                public int compare(File file, File file2) {
                    boolean file2IsDir = file2.isDirectory();
                    if (file.isDirectory()) {
                        return file2IsDir ? 0 : -1;
                    } else if (file2IsDir) {
                        return 1;
                    }

                    long diff = file.length() - file2.length();
                    return diff > 0 ? 1 : (diff < 0 ? -1 : 0);
                }
            });

            files = sortedList;
        }

        // convert to String-based paths.
        List<String> filePathList = Lists.newArrayListWithCapacity(files.size());
        for (File f : files) {
            filePathList.add(f.getAbsolutePath());
        }

        return filePathList;
    }
}
