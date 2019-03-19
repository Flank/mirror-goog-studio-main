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

import static com.android.SdkConstants.CMAKE_DIR_PROPERTY;
import static com.android.SdkConstants.FN_LOCAL_PROPERTIES;
import static com.android.SdkConstants.NDK_SYMLINK_DIR;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.errors.EvalIssueException;
import com.android.builder.errors.EvalIssueReporter;
import com.android.builder.errors.EvalIssueReporter.Type;
import com.android.builder.model.Version;
import com.android.builder.sdk.DefaultSdkLoader;
import com.android.builder.sdk.InstallFailedException;
import com.android.builder.sdk.LicenceNotAcceptedException;
import com.android.builder.sdk.PlatformLoader;
import com.android.builder.sdk.SdkInfo;
import com.android.builder.sdk.SdkLibData;
import com.android.builder.sdk.SdkLoader;
import com.android.builder.sdk.TargetInfo;
import com.android.repository.Revision;
import com.android.repository.api.ConsoleProgressIndicator;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RepoPackage;
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
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;

/**
 * Handles the all things SDK for the Gradle plugin. There is one instance per project, around
 * a singleton {@link com.android.builder.sdk.SdkLoader}.
 */
public class SdkHandler {

    // Used for injecting SDK location in tests.
    public static File sTestSdkFolder;

    @NonNull
    private final ILogger logger;

    private SdkLoader sdkLoader;
    private File sdkFolder;
    private File cmakePathInLocalProp = null;
    private File ndkSymlinkDirInLocalProp = null;
    private SdkLibData sdkLibData = SdkLibData.dontDownload();
    private boolean isRegularSdk = true;

    // Regex pattern to find quotes
    private static final Pattern PATTERN_FIND_QUOTES = Pattern.compile("\"([^\"]*)\"");

    public static void setTestSdkFolder(File testSdkFolder) {
        sTestSdkFolder = testSdkFolder;
    }

    public SdkHandler(
            @NonNull Project project,
            @NonNull ILogger logger,
            @NonNull EvalIssueReporter evalIssueReporter) {
        this.logger = logger;
        findLocation(project, evalIssueReporter);
    }

    /**
     * Initialize the SDK target, build tools and library requests.
     *
     * @return true on success, false on failure after reporting errors via the issue reporter,
     *     which throws exceptions when not in sync mode.
     */
    public Pair<SdkInfo, TargetInfo> initTarget(
            @NonNull String targetHash,
            @NonNull Revision buildToolRevision,
            @NonNull EvalIssueReporter evalIssueReporter) {
        Preconditions.checkNotNull(targetHash, "android.compileSdkVersion is missing!");
        Preconditions.checkNotNull(buildToolRevision, "android.buildToolsVersion is missing!");

        SdkLoader sdkLoader = getSdkLoader();
        if (sdkLoader == null) {
            // SdkLoader couldn't be constructed, probably because we're missing the sdk dir configuration.
            // If so, getSdkLoader() already reported to the evalIssueReporter.
            return null;
        }

        if (buildToolRevision.compareTo(AndroidBuilder.MIN_BUILD_TOOLS_REV) < 0) {
            evalIssueReporter
                    .reportWarning(
                            Type.BUILD_TOOLS_TOO_LOW,
                            String.format(
                                    "The specified Android SDK Build Tools version (%1$s) is "
                                            + "ignored, as it is below the minimum supported "
                                            + "version (%2$s) for Android Gradle Plugin %3$s.\n"
                                            + "Android SDK Build Tools %4$s will be used.\n"
                                            + "To suppress this warning, "
                                            + "remove \"buildToolsVersion '%1$s'\" "
                                            + "from your build.gradle file, as each "
                                            + "version of the Android Gradle Plugin now has a "
                                            + "default version of the build tools.",
                                    buildToolRevision,
                                    AndroidBuilder.MIN_BUILD_TOOLS_REV,
                                    Version.ANDROID_GRADLE_PLUGIN_VERSION,
                                    AndroidBuilder.DEFAULT_BUILD_TOOLS_REVISION),
                            AndroidBuilder.DEFAULT_BUILD_TOOLS_REVISION.toString());
            buildToolRevision = AndroidBuilder.DEFAULT_BUILD_TOOLS_REVISION;
        }


        Stopwatch stopwatch = Stopwatch.createStarted();
        SdkInfo sdkInfo = sdkLoader.getSdkInfo(logger);

        TargetInfo targetInfo;
        try {
            targetInfo = sdkLoader.getTargetInfo(targetHash, buildToolRevision, logger, sdkLibData);
        } catch (LicenceNotAcceptedException e) {
            evalIssueReporter
                    .reportError(
                            EvalIssueReporter.Type.MISSING_SDK_PACKAGE,
                            new EvalIssueException(
                                    e.getMessage(),
                                    e.getAffectedPackages()
                                            .stream()
                                            .map(RepoPackage::getPath)
                                            .collect(Collectors.joining(" "))));
            return null;
        } catch (InstallFailedException e) {
            evalIssueReporter
                    .reportError(
                            EvalIssueReporter.Type.MISSING_SDK_PACKAGE,
                            new EvalIssueException(
                                    e.getMessage(),
                                    e.getAffectedPackages()
                                            .stream()
                                            .map(RepoPackage::getPath)
                                            .collect(Collectors.joining(" "))));
            return null;
        } catch (IllegalStateException e) {
            evalIssueReporter.reportError(
                    EvalIssueReporter.Type.MISSING_SDK_PACKAGE,
                    new EvalIssueException(e, e.getMessage()));
            return null;
        }

        logger.verbose("SDK initialized in %1$d ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
        return Pair.of(sdkInfo, targetInfo);
    }

    /**
     * Try and make sure the platform tools are present.
     *
     * <p>Reports an evaluation warning if the platform tools package is not present and could not
     * be automatically downloaded and installed.
     *
     * @return true if some download operation was attempted.
     */
    public boolean ensurePlatformToolsIsInstalledWarnOnFailure(
            @NonNull EvalIssueReporter issueReporter) {
        // Check if platform-tools are installed. We check here because realistically, all projects
        // should have platform-tools in order to build.
        ProgressIndicator progress = new ConsoleProgressIndicator();
        AndroidSdkHandler sdk = AndroidSdkHandler.getInstance(getSdkFolder());
        LocalPackage platformToolsPackage =
                sdk.getLatestLocalPackageForPrefix(
                        SdkConstants.FD_PLATFORM_TOOLS, null, true, progress);
        if (platformToolsPackage == null && sdkLoader != null) {
            if (sdkLibData.useSdkDownload()) {
                try {
                    sdkLoader.installSdkTool(sdkLibData, SdkConstants.FD_PLATFORM_TOOLS);
                    return true;
                } catch (LicenceNotAcceptedException e) {
                    issueReporter.reportWarning(
                            EvalIssueReporter.Type.MISSING_SDK_PACKAGE,
                            SdkConstants.FD_PLATFORM_TOOLS
                                    + " package is not installed. Please accept the installation licence to continue",
                            SdkConstants.FD_PLATFORM_TOOLS);
                } catch (InstallFailedException e) {
                    issueReporter.reportWarning(
                            EvalIssueReporter.Type.MISSING_SDK_PACKAGE,
                            SdkConstants.FD_PLATFORM_TOOLS
                                    + " package is not installed, and automatic installation failed.",
                            SdkConstants.FD_PLATFORM_TOOLS);
                }
            } else {
                issueReporter.reportWarning(
                        Type.MISSING_SDK_PACKAGE,
                        SdkConstants.FD_PLATFORM_TOOLS + " package is not installed.",
                        SdkConstants.FD_PLATFORM_TOOLS);
            }
        }
        return false;
    }

    @Nullable
    public File getSdkFolder() {
        return sdkFolder;
    }

    // Returns the Cmake folder set in local.properties.
    @Nullable
    public File getCmakePathInLocalProp() {
        return cmakePathInLocalProp;
    }

    // Returns the NDK symlink folder in local.properties. The may be relative, in which
    // case the caller should resolve it relative to the C++ variant build system folder
    // which is like .cxx/cmake/debug.
    @Nullable
    public File getNdkSymlinkDirInLocalProp() {
        return ndkSymlinkDirInLocalProp;
    }

    @Nullable
    private synchronized SdkLoader getSdkLoader() {
        if (sdkLoader == null) {
            if (isRegularSdk) {
                if (sdkFolder == null) {
                    return null;
                }

                // check if the SDK folder actually exist.
                // For internal test we provide a fake SDK location through
                // setTestSdkFolder in order to have an SDK, even though we don't use it
                // so in this case we ignore the check.
                if (sTestSdkFolder == null && !sdkFolder.isDirectory()) {
                    throw new IllegalStateException(
                            String.format("The SDK directory '%1$s' does not exist.", sdkFolder));
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
            File sdk = new File(sdkDirProp);
            if (!sdk.isAbsolute()) {
                sdk = new File(rootDir, sdkDirProp);
            }
            return Pair.of(sdk, false);
        }

        String envVar = System.getenv(SdkConstants.ANDROID_SDK_ROOT_ENV);
        if (envVar == null) {
            envVar = System.getenv(SdkConstants.ANDROID_HOME_ENV);
        }
        if (envVar != null) {
            File sdk = new File(envVar);
            if (!sdk.isAbsolute()) {
                sdk = new File(rootDir, envVar);
            }
            return Pair.of(sdk, true);
        }

        String property = System.getProperty("android.home");
        if (property != null) {
            return Pair.of(new File(property), true);
        }
        return Pair.of(null, true);
    }

    private void findLocation(
            @NonNull Project project, @NonNull EvalIssueReporter evalIssueReporter) {
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

        if (sdkFolder == null) {
            String filePath =
                    new File(project.getRootDir(), SdkConstants.FN_LOCAL_PROPERTIES)
                            .getAbsolutePath();
            String message =
                    "SDK location not found. Define location with an ANDROID_SDK_ROOT environment "
                            + "variable or by setting the sdk.dir path in your project's local "
                            + "properties file at '"
                            + filePath
                            + "'.";
            evalIssueReporter.reportError(
                    Type.SDK_NOT_SET, new EvalIssueException(message, filePath, null));
        }

        // Check if the user has specified a cmake directory in local properties and assign the
        // cmake folder.
        String cmakeProperty = properties.getProperty(CMAKE_DIR_PROPERTY);
        if (cmakeProperty != null) {
            // cmake.dir can be specified one of two ways
            // 1. cmake.dir="value"
            // 2. cmake.dir=value
            // Inorder to create a file, we just need the value without the quotes, so if the CMake
            // directory's value is within quotes, extract that value.
            Matcher m = PATTERN_FIND_QUOTES.matcher(cmakeProperty);
            if (m.find()) {
                cmakePathInLocalProp = new File(m.group(1));
            } else {
                cmakePathInLocalProp = new File(cmakeProperty);
            }
        }

        String symLinkDirPath = properties.getProperty(NDK_SYMLINK_DIR);
        ndkSymlinkDirInLocalProp = symLinkDirPath != null ? new File(symLinkDirPath) : null;
    }

    public void setSdkLibData(SdkLibData sdkLibData) {
        this.sdkLibData = sdkLibData;
    }

    /** Installs the NDK. */
    public void installNdk(@NonNull NdkHandler ndkHandler) {
        if (!sdkLibData.useSdkDownload()) {
            return;
        }
        ndkHandler.installFromSdk(sdkLoader, sdkLibData);
    }

    /** Installs CMake. */
    public void installCMake(String version) {
        if (!sdkLibData.useSdkDownload()) {
            return;
        }
        try {
            sdkLoader.installSdkTool(sdkLibData, SdkConstants.FD_CMAKE + ";" + version);
        } catch (LicenceNotAcceptedException | InstallFailedException e) {
            throw new RuntimeException(e);
        }
    }

    public void addLocalRepositories(Project project) {
        SdkLoader sdkLoader = getSdkLoader();
        if (sdkLoader == null) {
            // SdkLoader couldn't be constructed, probably because we're missing the sdk dir configuration.
            // If so, getSdkLoader() already reported to the evalIssueReporter.
            return;
        }

        for (final File repository : sdkLoader.getRepositories()) {
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
}
