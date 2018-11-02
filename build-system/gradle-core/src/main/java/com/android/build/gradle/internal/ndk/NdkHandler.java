/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.internal.ndk;

import static com.android.build.gradle.internal.cxx.configure.NdkLocatorKt.findNdkPath;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.cxx.configure.SdkSourceProperties;
import com.android.repository.Revision;
import com.android.sdklib.AndroidVersion;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Collection;

/**
 * Handles NDK related information.
 *
 * <p>This class has three phases of initialization: 1) Constructor is called with project
 * directory. It's constructed by GlobalScope object *before* NDK version has been populated into
 * the DSL. 2) Once NDK version has been populated setExtensionValues is called with the additional
 * information. 3) Later, when information about the NDK is needed, the initialize() function is
 * called.
 *
 * <p>If, at some point, the SDK manager installs NDK that didn't exist before on disk then
 * invalidate() will be called to indicate that initilization is needed again.
 */
public class NdkHandler {
    @NonNull private final File projectDir;
    @Nullable private String compileSdkVersion;
    @Nullable private String ndkVersionFromDsl;
    @Nullable private Deferred deferred;

    /** NdkHandler fields that are deferred-initialized. */
    private static class Deferred {
        @Nullable final File ndkDirectory;
        @Nullable final NdkInfo ndkInfo;
        @Nullable final Revision revision;

        Deferred(
                @Nullable File ndkDirectory,
                @Nullable NdkInfo ndkInfo,
                @Nullable Revision revision) {
            this.ndkDirectory = ndkDirectory;
            this.ndkInfo = ndkInfo;
            this.revision = revision;
        }
    }

    public NdkHandler(@NonNull File projectDir) {
        this.projectDir = projectDir;
        this.deferred = null;
    }

    /**
     * Called to indicate that initialization is needed again. This should be due to SDK manager
     * installing NDK on disk or if native build needs to initialize in order to register sync
     * errors.
     */
    public void invalidate() {
        this.deferred = null;
    }

    /** If not yet initialized, then initialize. It finds the NDK folder */
    private void initialize() {
        if (this.deferred != null) {
            return;
        }
        File ndkDirectory = findNdkPath(ndkVersionFromDsl, projectDir);
        Revision revision = null;
        NdkInfo ndkInfo = null;
        if (ndkDirectory != null && ndkDirectory.exists()) {
            revision = SdkSourceProperties.Companion.fromInstallFolder(ndkDirectory).getRevision();
            if (revision == null) {
                ndkInfo = new DefaultNdkInfo(ndkDirectory);
            } else {
                ndkInfo = new NdkR14Info(ndkDirectory);
            }
        }
        this.deferred = new Deferred(ndkDirectory, ndkInfo, revision);
    }

    @Nullable
    public Revision getRevision() {
        initialize();
        assert deferred != null;
        return deferred.revision;
    }

    @Nullable
    public String getPlatformVersion() {
        initialize();
        assert deferred != null;
        assert compileSdkVersion != null;
        return ndkInfo().findLatestPlatformVersion(compileSdkVersion);
    }

    /** Values from the "extension" DSL. */
    public void setExtensionValues(
            @Nullable String ndkVersionFromDsl, @NonNull String compileSdkVersion) {
        this.ndkVersionFromDsl = ndkVersionFromDsl;
        this.compileSdkVersion = compileSdkVersion;
        this.deferred = null;
    }

    /**
     * Returns the directory of the NDK.
     */
    @Nullable
    public File getNdkDirectory() {
        initialize();
        assert deferred != null;
        return deferred.ndkDirectory;
    }

    /**
     * Return true if NDK directory is configured.
     */
    public boolean isConfigured() {
        File ndkDirectory = getNdkDirectory();
        return ndkDirectory != null && ndkDirectory.isDirectory();
    }

    /**
     * Return true if compiledSdkVersion supports 64 bits ABI.
     */
    private boolean supports64Bits() {
        if (getPlatformVersion() == null) {
            return false;
        }
        String targetString = getPlatformVersion().replace("android-", "");
        try {
            return Integer.parseInt(targetString) >= 20;
        } catch (NumberFormatException ignored) {
            // "android-L" supports 64-bits.
            return true;
        }
    }

    /**
     * Returns a list of all ABI.
     */
    @NonNull
    public static Collection<Abi> getAbiList() {
        return ImmutableList.copyOf(Abi.values());
    }

    /** Returns a list of default ABIs. */
    @NonNull
    public static Collection<Abi> getDefaultAbiList() {
        return Abi.getDefaultValues();
    }

    /**
     * Returns a list of 32-bits ABI.
     */
    @NonNull
    private static Collection<Abi> getAbiList32() {
        ImmutableList.Builder<Abi> builder = ImmutableList.builder();
        for (Abi abi : Abi.values()) {
            if (!abi.supports64Bits()) {
                builder.add(abi);
            }
        }
        return builder.build();
    }

    /**
     * Returns a list of supported ABI.
     */
    @NonNull
    public Collection<Abi> getSupportedAbis() {
        initialize();
        if (ndkInfo() != null) {
            return supports64Bits()
                    ? ndkInfo().getSupportedAbis()
                    : ndkInfo().getSupported32BitsAbis();
        }
        return supports64Bits() ? getAbiList() : getAbiList32();
    }

    /** Returns a list of supported ABI. */
    @NonNull
    public Collection<Abi> getDefaultAbis() {
        if (ndkInfo() != null) {
            return supports64Bits() ? ndkInfo().getDefaultAbis() : ndkInfo().getDefault32BitsAbis();
        }
        return supports64Bits() ? getAbiList() : getAbiList32();
    }

    /**
     * Return the executable for removing debug symbols from a shared object.
     */
    @NonNull
    public File getStripExecutable(Abi abi) {
        checkNotNull(getNdkDirectory());
        return ndkInfo().getStripExecutable(abi);
    }

    public int findSuitablePlatformVersion(
            @NonNull String abi,
            @Nullable AndroidVersion androidVersion) {
        checkNotNull(ndkInfo());
        return ndkInfo().findSuitablePlatformVersion(abi, androidVersion);
    }

    private NdkInfo ndkInfo() {
        initialize();
        assert this.deferred != null;
        return this.deferred.ndkInfo;
    }
}
