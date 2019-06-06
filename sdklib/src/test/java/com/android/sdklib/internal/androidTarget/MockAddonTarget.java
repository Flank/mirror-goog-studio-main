/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.sdklib.internal.androidTarget;

import com.android.annotations.NonNull;
import com.android.repository.io.FileOp;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.OptionalLibrary;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * A mock AddonTarget.
 * This reimplements the minimum needed from the interface for our limited testing needs.
 */
public class MockAddonTarget implements IAndroidTarget {
    private final IAndroidTarget mParentTarget;
    private final int mRevision;
    private final String mName;
    private ImmutableList<OptionalLibrary> mOptionalLibraries = ImmutableList.of();

    public MockAddonTarget(String name, IAndroidTarget parentTarget, int revision) {
        mName = name;
        mParentTarget = parentTarget;
        mRevision = revision;
    }

    @Override
    public String getClasspathName() {
        return getName();
    }

    @Override
    public String getShortClasspathName() {
        return getName();
    }

    @Override
    public File getDefaultSkin() {
        return null;
    }

    @Override
    public String getFullName() {
        return getName();
    }

    @Override
    @NonNull
    public String getLocation() {
        return "/sdk/add-ons/addon-" + mName;
    }

    @Override
    @NonNull
    public List<OptionalLibrary> getAdditionalLibraries() {
        return mOptionalLibraries;
    }

    @Override
    @NonNull
    public List<OptionalLibrary> getOptionalLibraries() {
        return ImmutableList.of();
    }

    public void setOptionalLibraries(@NonNull ImmutableList<OptionalLibrary> libraries) {
        mOptionalLibraries = libraries;
    }

    @Override
    public IAndroidTarget getParent() {
        return mParentTarget;
    }

    @Override
    @NonNull
    public String getPath(int pathId) {
        throw new UnsupportedOperationException("Implement this as needed for tests");
    }

    @Override
    public BuildToolInfo getBuildToolInfo() {
        return null;
    }

    @Override
    @NonNull
    public List<String> getBootClasspath() {
        throw new UnsupportedOperationException("Implement this as needed for tests");
    }

    @Override
    public String[] getPlatformLibraries() {
        return null;
    }

    @Override
    public String getProperty(String name) {
        return null;
    }

    @Override
    public Map<String, String> getProperties() {
        return null;
    }

    @Override
    public int getRevision() {
        return mRevision;
    }

    @Override
    @NonNull
    public File[] getSkins() {
        return FileOp.EMPTY_FILE_ARRAY;
    }

    @Override
    @NonNull
    public AndroidVersion getVersion() {
        return mParentTarget.getVersion();
    }

    @Override
    public String getName() {
        return mName;
    }

    @Override
    public String getVendor() {
        return mParentTarget.getVendor();
    }

    @Override
    public String getVersionName() {
        return String.format("mock-addon-%1$d", getVersion().getApiLevel());
    }

    @Override
    public String hashString() {
        return getVersionName();
    }

    /** Returns false for an add-on. */
    @Override
    public boolean isPlatform() {
        return false;
    }

    @Override
    public boolean canRunOn(IAndroidTarget target) {
        throw new UnsupportedOperationException("Implement this as needed for tests");
    }

    @Override
    public int compareTo(IAndroidTarget o) {
        throw new UnsupportedOperationException("Implement this as needed for tests");
    }

    @Override
    public boolean hasRenderingLibrary() {
        return false;
    }
}
