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

package com.android.build.gradle.internal;

import static com.android.SdkConstants.FN_LOCAL_PROPERTIES;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.GuardedBy;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.ErrorReporter;
import com.android.builder.core.LibraryRequest;
import com.android.builder.model.OptionalCompilationStep;
import com.android.builder.model.SyncIssue;
import com.android.builder.sdk.DefaultSdkLoader;
import com.android.builder.sdk.PlatformLoader;
import com.android.builder.sdk.SdkInfo;
import com.android.builder.sdk.SdkLibData;
import com.android.builder.sdk.SdkLoader;
import com.android.builder.sdk.TargetInfo;
import com.android.repository.Revision;
import com.android.repository.api.ConsoleProgressIndicator;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.utils.ILogger;
import com.android.utils.Pair;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.io.Closeables;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;

/**
 * Handles the all things SDK for the Gradle plugin. There is one instance per project, around
 * a singleton {@link com.android.builder.sdk.SdkLoader}.
 */
public class SdkHandler {

    // Used for injecting SDK location in tests.
    public static File sTestSdkFolder;

    private static final Object LOCK_FOR_SDK_HANDLER = new Object();
    @GuardedBy("LOCK_FOR_SDK_HANDLER")
    private static SdkLoader sSdkLoader;

    @NonNull
    private final ILogger logger;

    private SdkLoader sdkLoader;
    private File sdkFolder;
    private File ndkFolder;
    private SdkLibData sdkLibData = SdkLibData.dontDownload();
    private boolean isRegularSdk = true;

    public static void setTestSdkFolder(File testSdkFolder) {
        sTestSdkFolder = testSdkFolder;
    }

    /**
     * Returns true if we should use a cached SDK, false if we should force the re-parsing of the
     * SDK components.
     */
    public static boolean useCachedSdk(Project project) {
        // only used cached version of the sdk when in instant run mode but not
        // syncing.
        return AndroidGradleOptions.getOptionalCompilationSteps(project).contains(
                OptionalCompilationStep.INSTANT_DEV)
                && !AndroidGradleOptions.buildModelOnlyAdvanced(project);
    }

    public SdkHandler(@NonNull Project project,
                      @NonNull ILogger logger) {
        this.logger = logger;
        findLocation(project);
    }

    public SdkInfo getSdkInfo() {
        SdkLoader sdkLoader = getSdkLoader();
        return sdkLoader.getSdkInfo(logger);
    }

    public void initTarget(
            @NonNull String targetHash,
            @NonNull Revision buildToolRevision,
            @NonNull Collection<LibraryRequest> usedLibraries,
            @NonNull AndroidBuilder androidBuilder,
            boolean useCachedVersion) {
        Preconditions.checkNotNull(targetHash, "android.compileSdkVersion is missing!");
        Preconditions.checkNotNull(buildToolRevision, "android.buildToolsVersion is missing!");

        synchronized (LOCK_FOR_SDK_HANDLER) {
            if (useCachedVersion) {
                if (sSdkLoader == null) {
                    logger.verbose("Parsing the Sdk");
                    sSdkLoader = getSdkLoader();
                } else {
                    logger.verbose("Reusing the SdkLoader");
                }
            } else {
                logger.verbose("Parsing the SDK, no caching allowed");
                sSdkLoader = getSdkLoader();
            }
            sdkLoader = sSdkLoader;
        }


        Stopwatch stopwatch = Stopwatch.createStarted();
        SdkInfo sdkInfo = sdkLoader.getSdkInfo(logger);

        TargetInfo targetInfo;
        targetInfo = sdkLoader.getTargetInfo(
                targetHash,
                buildToolRevision,
                logger,
                sdkLibData);

        androidBuilder.setSdkInfo(sdkInfo);
        androidBuilder.setTargetInfo(targetInfo);
        androidBuilder.setLibraryRequests(usedLibraries);

        logger.verbose("SDK initialized in %1$d ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    public void ensurePlatformToolsIsInstalled(ErrorReporter errorReporter) {
        // Check if platform-tools are installed. We check here because realistically, all projects
        // should have platform-tools in order to build.
        ProgressIndicator progress = new ConsoleProgressIndicator();
        AndroidSdkHandler sdk = AndroidSdkHandler.getInstance(getSdkFolder());
        LocalPackage platformToolsPackage =
                sdk.getLatestLocalPackageForPrefix(SdkConstants.FD_PLATFORM_TOOLS, true, progress);
        if (platformToolsPackage == null) {
            if (sdkLibData.useSdkDownload()) {
                sdkLoader.installSdkTool(sdkLibData, SdkConstants.FD_PLATFORM_TOOLS);
            } else {
                errorReporter.handleSyncError(
                        null,
                        SyncIssue.TYPE_GENERIC,
                        SdkConstants.FD_PLATFORM_TOOLS
                                + " package is not installed and SDK auto-download is disabled.");
            }
        }
    }

    @Nullable
    public File getSdkFolder() {
        return sdkFolder;
    }

    @Nullable
    public File getAndCheckSdkFolder() {
        if (sdkFolder == null) {
            throw new RuntimeException(
                    "SDK location not found. Define location with sdk.dir in the local.properties file or with an ANDROID_HOME environment variable.");
        }

        return sdkFolder;
    }

    public synchronized SdkLoader getSdkLoader() {
        if (sdkLoader == null) {
            if (isRegularSdk) {
                getAndCheckSdkFolder();

                // check if the SDK folder actually exist.
                // For internal test we provide a fake SDK location through
                // setTestSdkFolder in order to have an SDK, even though we don't use it
                // so in this case we ignore the check.
                if (sTestSdkFolder == null && !sdkFolder.isDirectory()) {
                    throw new RuntimeException(String.format(
                            "The SDK directory '%1$s' does not exist.", sdkFolder));
                }

                sdkLoader = DefaultSdkLoader.getLoader(sdkFolder);
            } else {
                sdkLoader = PlatformLoader.getLoader(sdkFolder);
            }
        }

        return sdkLoader;
    }

    public synchronized void unload() {
        if (sdkLoader != null) {
            if (isRegularSdk) {
                DefaultSdkLoader.unload();
            } else {
                PlatformLoader.unload();
            }

            sdkLoader = null;
        }
    }

    @Nullable
    public File getNdkFolder() {
        return ndkFolder;
    }

    /**
     * Find the location of the SDK.
     *
     * Returns a Pair<File, Boolean>, where getFirst() returns a File of the SDK location, and
     * getSecond() returns a Boolean indicating whether it is a regular SDK.
     *
     * Returns Pair.of(null, true) when SDK is not found.
     *
     * @param properties Properties, usually configured in local.properties file.
     * @param rootDir directory for resolving relative paths.
     * @return Pair of SDK location and boolean indicating if it's a regular SDK.
     */
    @NonNull
    public static Pair<File, Boolean> findSdkLocation(
            @NonNull Properties properties,
            @NonNull File rootDir) {
        String sdkDirProp = properties.getProperty("sdk.dir");
        if (sdkDirProp != null) {
            File sdk = new File(sdkDirProp);
            if (!sdk.isAbsolute()) {
                sdk = new File(rootDir, sdkDirProp);
            }
            return Pair.of(sdk, true);
        }

        sdkDirProp = properties.getProperty("android.dir");
        if (sdkDirProp != null) {
            return Pair.of(new File(rootDir, sdkDirProp), false);
        }

        String envVar = System.getenv("ANDROID_HOME");
        if (envVar != null) {
            return Pair.of(new File(envVar), true);
        }

        String property = System.getProperty("android.home");
        if (property != null) {
            return Pair.of(new File(property), true);
        }
        return Pair.of(null, true);
    }

    private void findLocation(@NonNull Project project) {
        if (sTestSdkFolder != null) {
            sdkFolder = sTestSdkFolder;
            return;
        }

        File rootDir = project.getRootDir();
        File localProperties = new File(rootDir, FN_LOCAL_PROPERTIES);
        Properties properties = new Properties();

        if (localProperties.isFile()) {
            InputStreamReader reader = null;
            try {
                //noinspection IOResourceOpenedButNotSafelyClosed
                FileInputStream fis = new FileInputStream(localProperties);
                reader = new InputStreamReader(fis, Charsets.UTF_8);
                properties.load(reader);
            } catch (FileNotFoundException ignored) {
                // ignore since we check up front and we don't want to fail on it anyway
                // in case there's an env var.
            } catch (IOException e) {
                throw new RuntimeException(
                        String.format("Unable to read %1$s.", localProperties.getAbsolutePath()),
                        e);
            } finally {
                try {
                    Closeables.close(reader, true /* swallowIOException */);
                } catch (IOException e) {
                    // ignore.
                }
            }
        }

        Pair<File, Boolean> sdkLocation = findSdkLocation(properties, rootDir);
        sdkFolder = sdkLocation.getFirst();
        isRegularSdk = sdkLocation.getSecond();
        ndkFolder = NdkHandler.findNdkDirectory(properties, rootDir);
    }

    public void setSdkLibData(SdkLibData sdkLibData) {
        this.sdkLibData = sdkLibData;
    }

    /**
     * Installs CMake.
     */
    public void installCMake() {
        sdkLoader.installSdkTool(sdkLibData, SdkConstants.FD_CMAKE);
    }

    public void addLocalRepositories(Project project) {
        for (final File repository : getSdkLoader().getRepositories()) {
            MavenArtifactRepository mavenRepository = project.getRepositories()
                    .maven(newRepository -> {
                        newRepository.setName(repository.getPath());
                        newRepository.setUrl(repository);
                    });

            // move SDK repositories to top so they are looked up first before checking external
            // repositories like jcenter or maven which are guaranteed to not have the android
            // support libraries and associated.
            project.getRepositories().remove(mavenRepository);
            project.getRepositories().addFirst(mavenRepository);
        }
    }

    /**
     * This method checks if the cache of the local and remote repositories has been already reset
     * this build.
     */
    public boolean checkResetCache() {
        return sdkLibData.needsCacheReset();
    }
}
