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

package com.android.build.gradle.managed;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.io.File;
import java.util.List;
import java.util.Set;
import org.gradle.api.Named;
import org.gradle.model.Managed;
import org.gradle.model.ModelSet;

/**
 * Configurations for a native library.
 */
@Managed
public interface NativeLibrary extends Named {

    /**
     * Command for building the library.
     */
    String getBuildCommand();
    void setBuildCommand(String command);

    String getBuildType();
    void setBuildType(String buildType);

    @Nullable
    String getGroupName();
    void setGroupName(String groupName);

    @Nullable
    String getAssembleTaskName();
    void setAssembleTaskName(String taskName);

    /**
     * Target ABI.
     */
    @Nullable
    String getAbi();
    void setAbi(String abi);

    /**
     * Target name.
     */
    @Nullable
    String getArtifactName();
    void setArtifactName(String target);

    /**
     * Name of the toolchain.
     */
    @Nullable
    String getToolchain();
    void setToolchain(String toolchain);

    /**
     * Folders containing source files.
     */
    @NonNull
    ModelSet<NativeSourceFolder> getFolders();

    /**
     * Source files.
     */
    @NonNull
    ModelSet<NativeSourceFile> getFiles();

    @NonNull
    List<File> getExportedHeaders();

    /**
     * The output file.
     */
    @Nullable
    File getOutput();
    void setOutput(File output);

    @NonNull
    Set<File> getRuntimeFiles();
}
