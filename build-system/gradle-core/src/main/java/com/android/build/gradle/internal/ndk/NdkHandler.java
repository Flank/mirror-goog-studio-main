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

import static com.android.SdkConstants.FN_LOCAL_PROPERTIES;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.SdkHandler;
import com.android.repository.Revision;
import com.android.utils.Pair;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;
import org.gradle.api.logging.Logging;

/**
 * Handles NDK related information.
 */
public class NdkHandler {
    @NonNull private final File projectDir;
    @NonNull private final String compileSdkVersion;
    @Nullable private NdkPlatform ndkPlatform;

    // TODO(jomof): wire up to side-by-side NDK finder
    @Nullable private final String ndkVersionFromDsl;
    private final boolean enableSideBySideNdk;

    public NdkHandler(
            boolean enableSideBySideNdk,
            @Nullable String ndkVersionFromDsl,
            @NonNull String compileSdkVersion,
            @NonNull File projectDir) {
        this.enableSideBySideNdk = enableSideBySideNdk;
        this.ndkVersionFromDsl = ndkVersionFromDsl;
        this.projectDir = projectDir;
        this.compileSdkVersion = compileSdkVersion;
        this.ndkPlatform = null;
    }

    @NonNull
    public NdkPlatform getNdkPlatform() {
        if (ndkPlatform != null) {
            return ndkPlatform;
        }
        File ndkDirectory = findNdkDirectory(projectDir);
        NdkInfo ndkInfo;
        Revision revision;

        if (ndkDirectory == null || !ndkDirectory.exists()) {
            ndkInfo = null;
            revision = null;
        } else {
            revision = findRevision(ndkDirectory);
            if (revision == null) {
                ndkInfo = new DefaultNdkInfo(ndkDirectory);
            } else {
                ndkInfo = new NdkR14Info(ndkDirectory);
            }
        }
        ndkPlatform = new NdkPlatform(ndkDirectory, ndkInfo, revision, compileSdkVersion);
        return ndkPlatform;
    }

    /** Schedule the NDK to be rediscovered the next time it's needed */
    public void invalidateNdk() {
        this.ndkPlatform = null;
    }

    private static Properties readProperties(File file) {
        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader reader = new InputStreamReader(fis, Charsets.UTF_8)) {
            properties.load(reader);
        } catch (FileNotFoundException ignored) {
            // ignore since we check up front and we don't want to fail on it anyway
            // in case there's an env var.
        } catch (IOException e) {
            throw new RuntimeException(String.format("Unable to read %1$s.", file), e);
        }
        return properties;
    }

    @VisibleForTesting
    @Nullable
    public static Revision findRevision(@Nullable File ndkDirectory) {
        if (ndkDirectory == null) {
            return null;
        } else {
            File sourceProperties = new File(ndkDirectory, "source.properties");
            if (!sourceProperties.exists()) {
                // source.properties does not exist.  It's probably r10.  Use the DefaultNdkInfo.
                return null;
            }
            Properties properties = readProperties(sourceProperties);
            String version = properties.getProperty("Pkg.Revision");
            if (version != null) {
                return Revision.parseRevision(version);
            } else {
                return null;
            }
        }
    }

    @Nullable
    private static File findNdkDirectory(@NonNull File projectDir) {
        File localProperties = new File(projectDir, FN_LOCAL_PROPERTIES);
        Properties properties = new Properties();
        if (localProperties.isFile()) {
            properties = readProperties(localProperties);
        }

        File ndkDir = findNdkDirectory(properties, projectDir);
        if (ndkDir == null) {
            return null;
        }
        return checkNdkDir(ndkDir) ? ndkDir : null;
    }

    /**
     * Perform basic verification on the NDK directory.
     */
    private static boolean checkNdkDir(File ndkDir) {
        if (!new File(ndkDir, "platforms").isDirectory()) {
            invalidNdkWarning("NDK is missing a \"platforms\" directory.", ndkDir);
            return false;
        }
        if (!new File(ndkDir, "toolchains").isDirectory()) {
            invalidNdkWarning("NDK is missing a \"toolchains\" directory.", ndkDir);
            return false;
        }
        return true;
    }

    private static void invalidNdkWarning(String message, File ndkDir) {
        Logging.getLogger(NdkHandler.class)
                .warn(
                        "{}\n"
                                + "If you are using NDK, verify the ndkPlatform.dir is set to a valid NDK "
                                + "directory.  It is currently set to {}.\n"
                                + "If you are not using NDK, unset the NDK variable from ANDROID_NDK_HOME "
                                + "or local.properties to remove this warning.\n",
                        message,
                        ndkDir.getAbsolutePath());
    }

    /**
     * Determine the location of the NDK directory.
     *
     * <p>The NDK directory can be set in the local.properties file, using the ANDROID_NDK_HOME
     * environment variable or come bundled with the SDK.
     *
     * <p>Return null if NDK directory is not found.
     */
    @Nullable
    private static File findNdkDirectory(@NonNull Properties properties, @NonNull File projectDir) {
        String ndkDirProp = properties.getProperty("ndk.dir");
        if (ndkDirProp != null) {
            return new File(ndkDirProp);
        }

        String ndkEnvVar = System.getenv("ANDROID_NDK_HOME");
        if (ndkEnvVar != null) {
            return new File(ndkEnvVar);
        }

        Pair<File, Boolean> sdkLocation = SdkHandler.findSdkLocation(properties, projectDir);
        File sdkFolder = sdkLocation.getFirst();
        if (sdkFolder != null) {
            // Worth checking if the NDK came bundled with the SDK
            File ndkBundle = new File(sdkFolder, SdkConstants.FD_NDK);
            if (ndkBundle.isDirectory()) {
                return ndkBundle;
            }
        }
        return null;
    }
}
