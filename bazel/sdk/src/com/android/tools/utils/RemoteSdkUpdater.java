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

package com.android.tools.utils;

import static java.util.Objects.requireNonNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.Channel;
import com.android.repository.api.ConsoleProgressIndicator;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.SettingsController;
import com.android.repository.impl.meta.Archive;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.repository.util.InstallerUtil;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.legacy.LegacyDownloader;
import com.android.utils.PathUtils;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.io.MoreFiles;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A utility class which manages updating the remote Android SDK components.
 *
 * <p>The remote SDK is not platform specific, only packages are, so this only downloads to <code>
 * prebuilts/studio/sdk/remote/</code>.
 *
 * <p>This updater is driven by the contents of the remote-sdk-packages file, and it downloads the
 * index files and zips directly for those SDK packages.
 */
public final class RemoteSdkUpdater {

    private static final int DOWNLOAD_THREAD_COUNT = 6;

    public static void main(String[] args) throws IOException, InterruptedException {
        Status s = run(args);
        System.exit(s.mResultCode);
    }

    private static void usage(String message) {
        System.err.println();
        System.err.println("Error: " + message);
        System.err.println();
        usage();
    }

    private static void usage() {
        System.out.println("Usage: RemoteSdkUpdater <options>");
        System.out.println();
        System.out.println("Valid options:");
        System.out.println("  --help                  Prints this information and quits");
        System.out.println();
        System.out.println("Example usages:");
        System.out.println();
        System.out.println(
                "  # Update prebuilts/studio/sdk/remote/dl.google.com/ "
                        + "to include packages listed in remote-sdk-packages");
        System.out.println("  $ bazel run //tools/base/bazel/sdk:remote-sdk-updater");
        System.out.println();
    }

    private static Status run(String[] args) throws IOException, InterruptedException {
        Stopwatch stopwatch = Stopwatch.createStarted();

        if (args.length > 0) {
            switch (args[0]) {
                case "--help":
                    usage();
                    return Status.SUCCESS;
                default:
                    usage("Unknown option: " + args[0]);
                    return Status.ERROR;
            }
        }

        // If the paths were not provided, use the default repo layout.
        Path dest = WorkspaceUtils.findRemoteSdkMirror().resolve("dl.google.com");

        List<String> packages = processPackageFile(WorkspaceUtils.findRemoteSdkPackagesFile());

        if (packages == null) {
            return Status.ERROR;
        }

        System.out.format("Downloading SDK package files into '%1$s'", dest);
        PathUtils.deleteRecursivelyIfExists(dest);
        downloadSdkPackages(dest, packages);
        System.out.format(
                "Done: invocation took %1$.1f seconds%n",
                stopwatch.elapsed(TimeUnit.MILLISECONDS) / 1000.0);

        return Status.SUCCESS;
    }

    /**
     * Reads the package file contents into the given list, skipping comments.
     *
     * @return the packages on success, null on failure.
     */
    @Nullable
    private static List<String> processPackageFile(@NonNull Path packageFile) {
        try (Stream<String> lines = Files.lines(packageFile)) {
            return lines.map(line -> line.replaceAll("#.*", ""))
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            usage(
                    "Could not successfully read package-file: "
                            + packageFile
                            + "\n\nException: "
                            + e);
            return null;
        }
    }

    private static void downloadSdkPackages(
            @NonNull Path dest, @NonNull List<String> packageRequests) throws InterruptedException {
        Stopwatch stopwatch = Stopwatch.createStarted();
        Set<String> urlStrings = new HashSet<>();
        urlStrings.addAll(getArtifactsToCopy("linux", packageRequests));
        // noinspection SpellCheckingInspection "macosx"
        urlStrings.addAll(getArtifactsToCopy("macosx", packageRequests));
        urlStrings.addAll(getArtifactsToCopy("windows", packageRequests));
        System.out.format(
                "%nSDK artifact analysis took %1$.1f seconds%n",
                stopwatch.elapsed(TimeUnit.MILLISECONDS) / 1000.0);
        stopwatch = Stopwatch.createStarted();
        ForkJoinPool downloadPool = new ForkJoinPool(DOWNLOAD_THREAD_COUNT);
        for (String urlString : urlStrings) {
            downloadPool.execute(new DownloadRequest(urlString, dest));
        }
        downloadPool.shutdown();
        downloadPool.awaitTermination(5, TimeUnit.MINUTES);
        System.out.format(
                "Downloads took %1$.1f seconds%n",
                stopwatch.elapsed(TimeUnit.MILLISECONDS) / 1000.0);
    }

    private static class DownloadRequest implements Runnable {

        @NonNull private final String urlString;
        @NonNull private final Path destinationDirectory;

        public DownloadRequest(@NonNull String urlString, @NonNull Path destinationDirectory) {
            this.urlString = urlString;
            this.destinationDirectory = destinationDirectory;
        }

        @Override
        public void run() {
            try {
                download();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private void download() throws IOException {
            URL url = new URL(urlString);
            String relativePath = url.getPath().substring(1); // Remove first '/'
            Path localLocation = destinationDirectory.resolve(relativePath);
            Files.createDirectories(localLocation.getParent());
            try (InputStream input = url.openStream()) {
                MoreFiles.asByteSink(localLocation, StandardOpenOption.CREATE_NEW).writeFrom(input);
            }
            System.out.format("Downloaded %1$s%n", urlString);
        }
    }

    private static Set<String> getArtifactsToCopy(
            @NonNull String platform, @NonNull List<String> packageRequests) {
        Set<String> urlStrings = new HashSet<>();
        Archive.sHostConfig = new Archive.HostConfig(platform);

        ProgressIndicator progress = new ConsoleProgressIndicator(System.out, System.err);
        FileOp fop = FileOpUtils.create();
        SettingsController settings =
                new SettingsController() {
                    @Override
                    public boolean getForceHttp() {
                        return true;
                    }

                    @Override
                    public void setForceHttp(boolean force) {}

                    @Override
                    public boolean getDisableSdkPatches() {
                        return true;
                    }

                    @Override
                    public void setDisableSdkPatches(boolean disable) {}

                    @Override
                    public Channel getChannel() {
                        return Channel.create(3);
                    }
                };

        RepoManager repoManager = AndroidSdkHandler.getInstance(null).getSdkManager(progress);
        repoManager.loadSynchronously(0, progress, new LegacyDownloader(fop, settings), settings);

        RepositoryPackages packages = repoManager.getPackages();

        for (String packageRequest : packageRequests) {
            urlStrings.addAll(processPackageRequest(packageRequest, packages, progress));
        }
        return urlStrings;
    }

    private static Collection<String> processPackageRequest(
            @NonNull String packageRequest,
            @NonNull RepositoryPackages packages,
            @NonNull ProgressIndicator progress) {
        RemotePackage remote = packages.getRemotePackages().get(packageRequest);
        if (remote == null) {
            throw new RuntimeException("Unknown package " + packageRequest);
        }
        return ImmutableList.of(
                requireNonNull(
                                InstallerUtil.resolveUrl(
                                        remote.getSource().getUrl(), remote, progress))
                        .toExternalForm(),
                requireNonNull(
                                InstallerUtil.resolveUrl(
                                        requireNonNull(remote.getArchive()).getComplete().getUrl(),
                                        remote,
                                        progress))
                        .toExternalForm());
    }

    private enum Status {
        SUCCESS(0),
        ERROR(1);

        private final int mResultCode;

        Status(int resultCode) {
            mResultCode = resultCode;
        }
    }
}
