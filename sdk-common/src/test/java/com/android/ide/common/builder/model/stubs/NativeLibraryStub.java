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
package com.android.ide.common.builder.model.stubs;

import com.android.annotations.NonNull;
import com.android.builder.model.NativeLibrary;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.List;
import java.util.Objects;

public final class NativeLibraryStub extends BaseStub implements NativeLibrary {
    @NonNull private final String myName;
    @NonNull private final String myAbi;
    @NonNull private final String myToolchainName;
    @NonNull private final List<File> myCIncludeDirs;
    @NonNull private final List<File> myCppIncludeDirs;
    @NonNull private final List<File> myCSystemIncludeDirs;
    @NonNull private final List<File> myCppSystemIncludeDirs;
    @NonNull private final List<String> myCDefines;
    @NonNull private final List<String> myCppDefines;
    @NonNull private final List<String> myCCompilerFlags;
    @NonNull private final List<String> myCppCompilerFlags;
    @NonNull private final List<File> myDebuggableLibraryFolders;

    public NativeLibraryStub() {
        this(
                "name",
                "abi",
                "toolchain",
                Lists.newArrayList(new File("cInclude")),
                Lists.newArrayList(new File("cppInclude")),
                Lists.newArrayList(new File("cSystemInclude")),
                Lists.newArrayList(new File("cppSystemInclude")),
                Lists.newArrayList("cDefine"),
                Lists.newArrayList("cppDefine"),
                Lists.newArrayList("cCompilerFlag"),
                Lists.newArrayList("cppCompilerFlag"),
                Lists.newArrayList(new File("debuggableLibrary")));
    }

    public NativeLibraryStub(
            @NonNull String name,
            @NonNull String abi,
            @NonNull String toolchainName,
            @NonNull List<File> cIncludeDirs,
            @NonNull List<File> cppIncludeDirs,
            @NonNull List<File> cSystemIncludeDirs,
            @NonNull List<File> cppSystemIncludeDirs,
            @NonNull List<String> cDefines,
            @NonNull List<String> cppDefines,
            @NonNull List<String> cCompilerFlags,
            @NonNull List<String> cppCompilerFlags,
            @NonNull List<File> debuggableLibraryFolders) {
        myName = name;
        myAbi = abi;
        myToolchainName = toolchainName;
        myCIncludeDirs = cIncludeDirs;
        myCppIncludeDirs = cppIncludeDirs;
        myCSystemIncludeDirs = cSystemIncludeDirs;
        myCppSystemIncludeDirs = cppSystemIncludeDirs;
        myCDefines = cDefines;
        myCppDefines = cppDefines;
        myCCompilerFlags = cCompilerFlags;
        myCppCompilerFlags = cppCompilerFlags;
        myDebuggableLibraryFolders = debuggableLibraryFolders;
    }

    @Override
    @NonNull
    public String getName() {
        return myName;
    }

    @Override
    @NonNull
    public String getAbi() {
        return myAbi;
    }

    @Override
    @NonNull
    public String getToolchainName() {
        return myToolchainName;
    }

    @Override
    @NonNull
    public List<File> getCIncludeDirs() {
        return myCIncludeDirs;
    }

    @Override
    @NonNull
    public List<File> getCppIncludeDirs() {
        return myCppIncludeDirs;
    }

    @Override
    @NonNull
    public List<File> getCSystemIncludeDirs() {
        return myCSystemIncludeDirs;
    }

    @Override
    @NonNull
    public List<File> getCppSystemIncludeDirs() {
        return myCppSystemIncludeDirs;
    }

    @Override
    @NonNull
    public List<String> getCDefines() {
        return myCDefines;
    }

    @Override
    @NonNull
    public List<String> getCppDefines() {
        return myCppDefines;
    }

    @Override
    @NonNull
    public List<String> getCCompilerFlags() {
        return myCCompilerFlags;
    }

    @Override
    @NonNull
    public List<String> getCppCompilerFlags() {
        return myCppCompilerFlags;
    }

    @Override
    @NonNull
    public List<File> getDebuggableLibraryFolders() {
        return myDebuggableLibraryFolders;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NativeLibrary)) {
            return false;
        }
        NativeLibrary stub = (NativeLibrary) o;
        return Objects.equals(getName(), stub.getName())
                && Objects.equals(getAbi(), stub.getAbi())
                && Objects.equals(getToolchainName(), stub.getToolchainName())
                && Objects.equals(getCIncludeDirs(), stub.getCIncludeDirs())
                && Objects.equals(getCppIncludeDirs(), stub.getCppIncludeDirs())
                && Objects.equals(getCSystemIncludeDirs(), stub.getCSystemIncludeDirs())
                && Objects.equals(getCppSystemIncludeDirs(), stub.getCppSystemIncludeDirs())
                && Objects.equals(getCDefines(), stub.getCDefines())
                && Objects.equals(getCppDefines(), stub.getCppDefines())
                && Objects.equals(getCCompilerFlags(), stub.getCCompilerFlags())
                && Objects.equals(getCppCompilerFlags(), stub.getCppCompilerFlags())
                && Objects.equals(
                        getDebuggableLibraryFolders(), stub.getDebuggableLibraryFolders());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getName(),
                getAbi(),
                getToolchainName(),
                getCIncludeDirs(),
                getCppIncludeDirs(),
                getCSystemIncludeDirs(),
                getCppSystemIncludeDirs(),
                getCDefines(),
                getCppDefines(),
                getCCompilerFlags(),
                getCppCompilerFlags(),
                getDebuggableLibraryFolders());
    }

    @Override
    public String toString() {
        return "NativeLibraryStub{"
                + "myName='"
                + myName
                + '\''
                + ", myAbi='"
                + myAbi
                + '\''
                + ", myToolchainName='"
                + myToolchainName
                + '\''
                + ", myCIncludeDirs="
                + myCIncludeDirs
                + ", myCppIncludeDirs="
                + myCppIncludeDirs
                + ", myCSystemIncludeDirs="
                + myCSystemIncludeDirs
                + ", myCppSystemIncludeDirs="
                + myCppSystemIncludeDirs
                + ", myCDefines="
                + myCDefines
                + ", myCppDefines="
                + myCppDefines
                + ", myCCompilerFlags="
                + myCCompilerFlags
                + ", myCppCompilerFlags="
                + myCppCompilerFlags
                + ", myDebuggableLibraryFolders="
                + myDebuggableLibraryFolders
                + "}";
    }
}
