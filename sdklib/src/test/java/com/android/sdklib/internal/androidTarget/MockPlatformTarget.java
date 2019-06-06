/*
 * Copyright (C) 2009 The Android Open Source Project
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
import com.android.sdklib.ISystemImage;
import com.android.sdklib.OptionalLibrary;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * A mock PlatformTarget.
 * This reimplements the minimum needed from the interface for our limited testing needs.
 */
public class MockPlatformTarget implements IAndroidTarget {
    private final int mApiLevel;
    private final int mRevision;
    private ISystemImage[] mSystemImages;

    public MockPlatformTarget(int apiLevel, int revision) {
        mApiLevel = apiLevel;
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
        return "/sdk/platforms/android-" + getVersion().getApiString();
    }

    @Override
    @NonNull
    public List<OptionalLibrary> getOptionalLibraries() {
        return ImmutableList.of();
    }

    @Override
    @NonNull
    public List<OptionalLibrary> getAdditionalLibraries() {
        return ImmutableList.of();
    }

    @Override
    public IAndroidTarget getParent() {
        return null;
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

    /**
     * Returns a vendor that depends on the parent *platform* API.
     * This works well in Unit Tests where we'll typically have different
     * platforms as unique identifiers.
     */
    @Override
    public String getVendor() {
        return "vendor " + Integer.toString(mApiLevel);
    }

    /**
     * Create a synthetic name using the target API level.
     */
    @Override
    public String getName() {
        return "platform r" + Integer.toString(mApiLevel);
    }

    @Override
    @NonNull
    public AndroidVersion getVersion() {
        return new AndroidVersion(mApiLevel, null /*codename*/);
    }

    @Override
    public String getVersionName() {
        return String.format("android-%1$d", mApiLevel);
    }

    @Override
    public String hashString() {
        return getVersionName();
    }

    /** Returns true for a platform. */
    @Override
    public boolean isPlatform() {
        return true;
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
