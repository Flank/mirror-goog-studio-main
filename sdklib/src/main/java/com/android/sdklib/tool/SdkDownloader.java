/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.sdklib.tool;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.Channel;
import com.android.repository.api.ConsoleProgressIndicator;
import com.android.repository.api.Installer;
import com.android.repository.api.License;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.PackageOperation;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.SettingsController;
import com.android.repository.api.Uninstaller;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.util.InstallerUtil;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.installer.SdkInstallerUtil;
import com.android.sdklib.repository.legacy.LegacyDownloader;
import com.android.utils.FileUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Simple tool for downloading SDK packages, to be used in the windows studio release process.
 *
 * Can be built with a convenient wrapper script from the commandline like
 * gradlew :base:sdk-downloader:assemble
 */
public class SdkDownloader {

    public static void main(@NonNull String args[]) {
        Settings settings = Settings.createSettings(args);
        if (settings == null) {
            usageAndExit();
        }

        ConsoleProgressIndicator progress = new ConsoleProgressIndicator();
        AndroidSdkHandler handler = AndroidSdkHandler.getInstance(settings.getLocalPath());
        RepoManager mgr = handler.getSdkManager(progress);

        if (settings.isList()) {
            listPackages(progress, handler, mgr, settings);
        } else {
            if (settings.isInstallAction()) {
                installPackages(settings, progress, handler, mgr);
            }
            else {
                uninstallPackages(settings, progress, handler, mgr);
            }
        }
        System.out.println("done");
    }

    private static void listPackages(@NonNull ProgressIndicator progress,
            @NonNull AndroidSdkHandler handler, @NonNull RepoManager mgr,
            @NonNull Settings settings) {
        LegacyDownloader downloader = new LegacyDownloader(handler.getFileOp());
        mgr.loadSynchronously(0, progress, downloader, settings);

        RepositoryPackages packages = mgr.getPackages();
        int maxPath = 0;
        int maxDescription = 0;
        int maxLocation = 0;
        int maxVersion = 0;

        Collection<LocalPackage> locals = new TreeSet<>(packages.getLocalPackages().values());
        Collection<LocalPackage> localObsoletes = new TreeSet<>(locals);
        locals.removeIf(RepoPackage::obsolete);
        localObsoletes.removeAll(locals);

        for (LocalPackage p : locals) {
            maxPath = Math.max(maxPath, p.getPath().length());
            maxDescription = Math.max(maxDescription, p.getDisplayName().length());
            maxLocation = Math.max(maxLocation,
                    FileUtils.relativePath(p.getLocation(), mgr.getLocalPath()).length());
            maxVersion = Math.max(maxVersion, p.getVersion().toString().length());
        }
        maxPath = Math.min(30, maxPath);
        maxLocation = Math.min(30, maxLocation);
        String pattern = String.format("  %%-%ds | %%-%ds | %%-%ds | %%-%ds\n", maxPath, maxVersion,
                maxDescription, maxLocation);
        String separator = "-------";
        System.out.println("Installed packages:");
        System.out.printf(pattern, "ID", "Version", "Description", "Location");
        System.out.printf(pattern, separator, separator, separator, separator);
        for (LocalPackage p : locals) {
            System.out.printf(pattern, shortenValue(p.getPath()), p.getVersion(),
                    p.getDisplayName(),
                    shortenValue(FileUtils.relativePath(p.getLocation(), mgr.getLocalPath())));
        }
        if (settings.includeObsolete() && !localObsoletes.isEmpty()) {
            System.out.println("Obsolete installed packages:");
            System.out.printf(pattern, "ID", "Version", "Description", "Location");
            System.out.printf(pattern, separator, separator, separator, separator);
            for (LocalPackage p : localObsoletes) {
                System.out.printf(pattern, shortenValue(p.getPath()), p.getVersion(),
                        shortenValue(p.getDisplayName()),
                        shortenValue(FileUtils.relativePath(p.getLocation(), mgr.getLocalPath())));
            }
        }

        System.out.println();

        Collection<RemotePackage> remotes = new TreeSet<>(packages.getRemotePackages().values());
        Collection<RemotePackage> remoteObsoletes = new TreeSet<>(remotes);
        remotes.removeIf(RepoPackage::obsolete);
        remoteObsoletes.removeAll(remotes);

        maxPath = 0;
        maxDescription = 0;
        maxVersion = 0;

        for (RemotePackage p : remotes) {
            maxPath = Math.max(maxPath, p.getPath().length());
            maxDescription = Math.max(maxDescription, p.getDisplayName().length());
            maxVersion = Math.max(maxVersion, p.getVersion().toString().length());
        }
        pattern = String.format("  %%-%ds | %%-%ds | %%-%ds\n", maxPath, maxVersion,
                maxDescription);

        System.out.println("Available packages:");
        System.out.printf(pattern, "ID", "Version", "Description");
        System.out.printf(pattern, separator, separator, separator);
        for (RemotePackage p : Sets.newTreeSet(remotes)) {
            System.out.printf(pattern, p.getPath(), p.getVersion(), p.getDisplayName());
        }
        if (settings.includeObsolete() && !remoteObsoletes.isEmpty()) {
            System.out.println("Obsolete Available Packages:");
            System.out.printf(pattern, "ID", "Version", "Description");
            System.out.printf(pattern, separator, separator, separator);
            for (RemotePackage p : remoteObsoletes) {
                System.out.printf(pattern, shortenValue(p.getPath()), p.getVersion(),
                        shortenValue(p.getDisplayName()));
            }
        }
    }

    @NonNull
    private static String shortenValue(@NonNull String value) {
        if (value.length() > 25) {
            value = value.substring(0, 11) + "..." + value.substring(value.length() - 11);
        }
        return value;
    }

    private static void installPackages(
            @NonNull Settings settings,
            @NonNull ConsoleProgressIndicator progress,
            @NonNull AndroidSdkHandler handler,
            @NonNull RepoManager mgr) {
        LegacyDownloader downloader = new LegacyDownloader(handler.getFileOp());
        mgr.loadSynchronously(0, progress, downloader, settings);

        List<RemotePackage> remotes = Lists.newArrayList();
        for (String path : settings.getPaths(mgr)) {
            RemotePackage p = mgr.getPackages().getRemotePackages().get(path);
            if (p == null) {
                progress.logError("Failed to find package " + path);
                usageAndExit();
            }
            remotes.add(p);
        }
        remotes = InstallerUtil.computeRequiredPackages(remotes, mgr.getPackages(), progress);
        if (remotes != null) {
            for (RemotePackage p : remotes) {
                License l = p.getLicense();
                if (l != null) {
                    if (!l.checkAccepted(handler.getLocation(), handler.getFileOp())) {
                        progress.logError(String.format(
                                "License for %1$s (%2$s) is not accepted. Please install using "
                                        + "studio, then copy <studio sdk path>/licenses/* to "
                                        + "%3$s/licenses/",
                                p.getDisplayName(), p.getPath(), handler.getLocation()));
                        System.exit(1);
                    }
                }
                Installer installer = SdkInstallerUtil.findBestInstallerFactory(p, handler)
                        .createInstaller(p, mgr, downloader, handler.getFileOp());
                applyPackageOperation(installer, progress);
            }
        } else {
            progress.logError("Unable to compute a complete list of dependencies.");
        }
    }

    private static void uninstallPackages(
            @NonNull Settings settings,
            @NonNull ConsoleProgressIndicator progress,
            @NonNull AndroidSdkHandler handler,
            @NonNull RepoManager mgr) {
        mgr.loadSynchronously(0, progress, null, settings);

        for (String path : settings.getPaths(mgr)) {
            LocalPackage p = mgr.getPackages().getLocalPackages().get(path);
            if (p == null) {
                progress.logWarning("Unable to find package " + path);
            } else {
                Uninstaller uninstaller = SdkInstallerUtil.findBestInstallerFactory(p, handler)
                        .createUninstaller(p, mgr, handler.getFileOp());
                applyPackageOperation(uninstaller, progress);
            }
        }
    }

    private static void applyPackageOperation(
            @NonNull PackageOperation operation,
            @NonNull ConsoleProgressIndicator progress) {
        if (!operation.prepare(progress)) {
            System.exit(1);
        }
        if (!operation.complete(progress)) {
            System.exit(1);
        }
    }

    private static void usageAndExit() {
        System.err.println("Usage: ");
        System.err.println("  sdk-downloader [--uninstall] [--update] [--channel=<channelId>] \\");
        System.err.println("    [--package_file <package-file>] <sdk path> [<packages>...]");
        System.err.println("  sdk-downloader --list [--include_obsolete] <sdk path>");
        System.err.println();
        System.err.println("In its first form, installs, uninstalls, or updates packages.");
        System.err.println("    <package> is a sdk-style path (e.g. \"build-tools;23.0.0\" or "
                + "\"platforms;android-23\")");
        System.err.println("    <package-file> is a text file where each line is a sdk-style path "
                + " of a package to install or uninstall.");
        System.err.println("    Multiple --package_file arguments may be specified in combination "
                + "with explicit paths.");
        System.err.println("    If --update is specified, currently installed packages are "
                + "updated, and no further processing is done.");
        System.err.println("    <channelId> is the id of the least stable channel to check");
        System.err.println();
        System.err.println("In its second form, all installed and available packages are printed "
                + "out.");
        System.err.println("    If --include_obsolete is specified, obsolete packages are "
                + "included.");
        System.err.println();
        System.err.println("* If the env var REPO_OS_OVERRIDE is set to \"windows\",\n"
                + "  \"macosx\", or \"linux\", packages will be downloaded for that OS");
        System.exit(1);
    }

    private static class Settings implements SettingsController {

        private static final String CHANNEL_ARG = "--channel=";
        private static final String UNINSTALL_ARG = "--uninstall";
        private static final String UPDATE_ARG = "--update";
        private static final String PKG_FILE_ARG = "--package_file=";
        private static final String LIST_ARG = "--list";
        private static final String INCLUDE_OBSOLETE_ARG = "--include_obsolete";

        private File mLocalPath;
        private List<String> mPackages = new ArrayList<>();
        private int mChannel = 0;
        private boolean mIsInstall = true;
        private boolean mIsUpdate = false;
        private boolean mIsList = false;
        private boolean mIncludeObsolete = false;

        @Nullable
        public static Settings createSettings(@NonNull String[] args) {
            Settings result = new Settings();
            for (String arg : args) {
                if (arg.equals(UNINSTALL_ARG)) {
                    result.mIsInstall = false;
                } else if (arg.equals(UPDATE_ARG)) {
                    result.mIsUpdate = true;
                } else if (arg.equals(LIST_ARG)) {
                    result.mIsList = true;
                } else if (arg.equals(INCLUDE_OBSOLETE_ARG)) {
                    result.mIncludeObsolete = true;
                } else if (arg.startsWith(CHANNEL_ARG)) {
                    try {
                        result.mChannel = Integer.parseInt(arg.substring(CHANNEL_ARG.length()));
                    } catch (NumberFormatException e) {
                        return null;
                    }
                } else if (arg.startsWith(PKG_FILE_ARG)) {
                    String packageFile = arg.substring(PKG_FILE_ARG.length());
                    try {
                        result.mPackages.addAll(Files.readAllLines(Paths.get(packageFile)));
                    } catch (IOException e) {
                        ConsoleProgressIndicator progress = new ConsoleProgressIndicator();
                        progress.logError(
                                String.format("Invalid package file \"%s\" threw exception:\n%s\n",
                                        packageFile, e));
                        return null;
                    }
                } else if (result.mLocalPath == null) {
                    File path = new File(arg);
                    if (!path.exists()) {
                        if (!path.mkdirs()) {
                            ConsoleProgressIndicator progress = new ConsoleProgressIndicator();
                            progress.logError("Failed to create SDK root dir: " + path);
                            return null;
                        }
                    }
                    result.mLocalPath = path;
                } else {
                    result.mPackages.add(arg);
                }
            }
            if (result.mLocalPath == null ||
                    !result.mPackages.isEmpty() == (result.mIsUpdate || result.mIsList)) {
                return null;
            }
            return result;
        }

        @Nullable
        @Override
        public Channel getChannel() {
            return Channel.create(mChannel);
        }

        @Override
        public boolean getForceHttp() {
            return false;
        }

        @Override
        public void setForceHttp(boolean force) {

        }

        @NonNull
        public List<String> getPaths(@NonNull RepoManager mgr) {
            if (mIsUpdate) {
                return mgr.getPackages().getUpdatedPkgs().stream()
                        .map(p -> p.getRepresentative().getPath())
                        .collect(Collectors.toList());
            }
            return mPackages;
        }

        @NonNull
        public File getLocalPath() {
            return mLocalPath;
        }

        public boolean isInstallAction() {
            return mIsInstall;
        }

        private Settings() {}

        public boolean isList() {
            return mIsList;
        }

        public boolean includeObsolete() {
            return mIncludeObsolete;
        }
    }
}
