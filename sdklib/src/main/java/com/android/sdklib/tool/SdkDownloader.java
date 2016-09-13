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
import com.android.repository.api.Downloader;
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
import com.android.repository.api.UpdatablePackage;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.io.FileOpUtils;
import com.android.repository.io.impl.FileSystemFileOp;
import com.android.repository.util.InstallerUtil;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.installer.SdkInstallerUtil;
import com.android.sdklib.repository.legacy.LegacyDownloader;
import com.android.utils.FileUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Simple tool for downloading SDK packages, to be used in the windows studio release process.
 *
 * Can be built with a convenient wrapper script from the commandline like
 * gradlew :base:sdk-downloader:assemble
 */
public class SdkDownloader {

    private final Settings mSettings;
    private final AndroidSdkHandler mHandler;
    private final RepoManager mRepoManager;
    private final PrintStream mOut;
    private final BufferedReader mIn;
    private Downloader mDownloader;

    public static void main(@NonNull String args[]) {
        FileSystemFileOp fop = (FileSystemFileOp)FileOpUtils.create();
        Settings settings = Settings.createSettings(args, fop.getFileSystem());
        if (settings == null) {
            usage(System.err);
            return;
        }
        File localPath = new File(settings.getLocalPath().toString());
        AndroidSdkHandler handler = new AndroidSdkHandler(localPath, fop);
        new SdkDownloader(settings, System.out, System.in, new LegacyDownloader(fop), handler)
                .run();
        System.out.println("done");
    }

    public SdkDownloader(Settings settings, PrintStream out, InputStream in,
            Downloader downloader, AndroidSdkHandler handler) {
        mSettings = settings;
        mOut = out;
        mIn = in == null ? null : new BufferedReader(new InputStreamReader(in));
        mDownloader = downloader;
        ConsoleProgressIndicator progress = new ConsoleProgressIndicator();
        // Unfortunately AndroidSdkHandler etc. doesn't use nio (yet). We have to convert like this
        // to deal with the non-default provider (test) case.
        mHandler = handler;
        mRepoManager = mHandler.getSdkManager(progress);
    }

    void run() {
        if (mSettings == null) {
            return;
        }
        if (mSettings.isList()) {
            listPackages();
        } else {
            if (mSettings.isInstallAction()) {
                installPackages();
            }
            else {
                uninstallPackages();
            }
        }
    }

    private void listPackages() {
        ProgressIndicator progress = new ConsoleProgressIndicator();
        mRepoManager.loadSynchronously(0, progress, mDownloader, mSettings);

        RepositoryPackages packages = mRepoManager.getPackages();

        Collection<LocalPackage> locals = new TreeSet<>(packages.getLocalPackages().values());
        Collection<LocalPackage> localObsoletes = new TreeSet<>(locals);
        locals.removeIf(RepoPackage::obsolete);
        localObsoletes.removeAll(locals);

        TableFormatter<LocalPackage> localTable = new TableFormatter<>();
        localTable.addColumn("Path", RepoPackage::getPath, 15, 15);
        localTable.addColumn("Version", p -> p.getVersion().toString(), 100, 0);
        localTable.addColumn("Description", RepoPackage::getDisplayName, 30, 0);
        localTable.addColumn("Location",
                p -> FileUtils.relativePossiblyNonExistingPath(
                        p.getLocation(), mRepoManager.getLocalPath()), 15, 15);

        if (!locals.isEmpty()) {
            mOut.println("Installed packages:");
            localTable.print(locals, mOut);
        }

        if (mSettings.includeObsolete() && !localObsoletes.isEmpty()) {
            mOut.println();
            mOut.println("Installed Obsolete Packages:");
            localTable.print(localObsoletes, mOut);
        }

        Collection<RemotePackage> remotes = new TreeSet<>(packages.getRemotePackages().values());
        Collection<RemotePackage> remoteObsoletes = new TreeSet<>(remotes);
        remotes.removeIf(RepoPackage::obsolete);
        remoteObsoletes.removeAll(remotes);

        TableFormatter<RemotePackage> remoteTable = new TableFormatter<>();
        remoteTable.addColumn("Path", RepoPackage::getPath, 15, 15);
        remoteTable.addColumn("Version", p -> p.getVersion().toString(), 100, 0);
        remoteTable.addColumn("Description", RepoPackage::getDisplayName, 30, 0);

        if (!remotes.isEmpty()) {
            mOut.println();
            mOut.println("Available Packages:");
            remoteTable.print(remotes, mOut);
        }
        if (mSettings.includeObsolete() && !remoteObsoletes.isEmpty()) {
            mOut.println();
            mOut.println("Available Obsolete Packages:");
            remoteTable.print(remoteObsoletes, mOut);
        }

        Set<UpdatablePackage> updates = new TreeSet<>(packages.getUpdatedPkgs());
        if (!updates.isEmpty()) {
            mOut.println();
            mOut.println("Available Updates:");
            TableFormatter<UpdatablePackage> updateTable = new TableFormatter<>();
            updateTable.addColumn("ID", UpdatablePackage::getPath, 30, 30);
            updateTable.addColumn("Installed", p -> p.getLocal().getVersion().toString(), 20, 0);
            updateTable.addColumn("Available", p -> p.getRemote().getVersion().toString(), 20, 0);
            if (!mSettings.includeObsolete()) {
                updates.removeIf(updatable -> updatable.getRemote().obsolete());
            }
            updateTable.print(updates, mOut);
        }
    }

    private void installPackages() {
        ProgressIndicator progress = new ConsoleProgressIndicator();
        mRepoManager.loadSynchronously(0, progress, mDownloader, mSettings);

        List<RemotePackage> remotes = new ArrayList<>();
        for (String path : mSettings.getPaths(mRepoManager)) {
            RemotePackage p = mRepoManager.getPackages().getRemotePackages().get(path);
            if (p == null) {
                progress.logError("Failed to find package " + path);
                return;
            }
            remotes.add(p);
        }
        remotes = InstallerUtil.computeRequiredPackages(
                remotes, mRepoManager.getPackages(), progress);
        if (remotes != null) {
            List<RemotePackage> acceptedRemotes = checkLicenses(progress, remotes);
            if (!acceptedRemotes.equals(remotes)) {
                mOut.println("The following packages can not be installed since their "
                        + "licenses or those of the packages they depend on were not accepted:");
                remotes.stream()
                        .filter(p -> !acceptedRemotes.contains(p))
                        .forEach(p -> mOut.println("  " + p.getPath()));
                if (!acceptedRemotes.isEmpty()) {
                    mOut.print("Continue installing the remaining packages? (y/N): ");
                    if (!askYesNo()) {
                        return;
                    }
                }
                remotes = acceptedRemotes;
            }
            for (RemotePackage p : remotes) {
                Installer installer = SdkInstallerUtil.findBestInstallerFactory(p, mHandler)
                        .createInstaller(p, mRepoManager, mDownloader, mHandler.getFileOp());
                if (!applyPackageOperation(installer, progress)) {
                    // there was an error, abort.
                    return;
                }
            }
        } else {
            progress.logError("Unable to compute a complete list of dependencies.");
        }
    }

    /**
     * Checks whether the licenses for the given packages are accepted. If they are not, request
     * that the user accept them.
     * 
     * @return A list of packages that have had their licenses accepted. If some licenses are not
     * accepted, both the package with the unaccepted license and any packages that depend on it
     * are excluded from this list.
     */
    @NonNull
    private List<RemotePackage> checkLicenses(@NonNull ProgressIndicator progress,
            @NonNull List<RemotePackage> remotes) {
        Multimap<License, RemotePackage> unacceptedLicenses = HashMultimap.create();
        remotes.forEach(remote -> {
            License l = remote.getLicense();
            if (l != null && !l.checkAccepted(mHandler.getLocation(), mHandler.getFileOp())) {
                unacceptedLicenses.put(l, remote);
            }
        });
        for (License l : new TreeSet<>(unacceptedLicenses.keySet())) {
            if (askForLicense(l)) {
                unacceptedLicenses.removeAll(l);
                l.setAccepted(mRepoManager.getLocalPath(), mHandler.getFileOp());
            }
        }
        if (!unacceptedLicenses.isEmpty()) {
            List<RemotePackage> acceptedPackages = new ArrayList<>(remotes);
            Set<RemotePackage> problemPackages = new HashSet<>(unacceptedLicenses.values());
            acceptedPackages.removeAll(problemPackages);
            Iterator<RemotePackage> acceptedIter = acceptedPackages.iterator();

            while (acceptedIter.hasNext()) {
                RemotePackage accepted = acceptedIter.next();
                List<RemotePackage> required = InstallerUtil.computeRequiredPackages(
                        Collections.singletonList(accepted), mRepoManager.getPackages(),
                        progress);
                if (!Collections.disjoint(required, problemPackages)) {
                    acceptedIter.remove();
                    problemPackages.add(accepted);
                }
            }
            remotes = acceptedPackages;
        }
        return remotes;
    }

    private boolean askForLicense(License license) {
        mOut.printf("License %s:%n", license.getId());
        mOut.println("---------------------------------------");
        mOut.println(license.getValue());
        mOut.println("---------------------------------------");
        mOut.print("Accept? (y/N): ");
        return askYesNo();
    }

    private boolean askYesNo() {
        try {
            String result = mIn.readLine();
            return result.equalsIgnoreCase("y") || result.equalsIgnoreCase("yes");
        } catch (IOException e) {
            return false;
        }
    }

    private void uninstallPackages() {
        ProgressIndicator progress = new ConsoleProgressIndicator();
        mRepoManager.loadSynchronously(0, progress, null, mSettings);

        for (String path : mSettings.getPaths(mRepoManager)) {
            LocalPackage p = mRepoManager.getPackages().getLocalPackages().get(path);
            if (p == null) {
                progress.logWarning("Unable to find package " + path);
            } else {
                Uninstaller uninstaller = SdkInstallerUtil.findBestInstallerFactory(p, mHandler)
                        .createUninstaller(p, mRepoManager, mHandler.getFileOp());
                if (!applyPackageOperation(uninstaller, progress)) {
                    // there was an error, abort.
                    return;
                }
            }
        }
    }

    private static boolean applyPackageOperation(
            @NonNull PackageOperation operation,
            @NonNull ProgressIndicator progress) {
        return operation.prepare(progress) && operation.complete(progress);
    }

    private static void usage(PrintStream out) {
        out.println("Usage: ");
        out.println("  sdk-downloader [--uninstall] [--update] [--channel=<channelId>] \\");
        out.println("    [--package_file <package-file>] <sdk path> [<packages>...]");
        out.println("  sdk-downloader --list [--include_obsolete] <sdk path>");
        out.println();
        out.println("In its first form, installs, uninstalls, or updates packages.");
        out.println("    <package> is a sdk-style path (e.g. \"build-tools;23.0.0\" or "
                + "\"platforms;android-23\")");
        out.println("    <package-file> is a text file where each line is a sdk-style path "
                + " of a package to install or uninstall.");
        out.println("    Multiple --package_file arguments may be specified in combination "
                + "with explicit paths.");
        out.println("    If --update is specified, currently installed packages are "
                + "updated, and no further processing is done.");
        out.println("    <channelId> is the id of the least stable channel to check");
        out.println();
        out.println("In its second form, all installed and available packages are printed "
                + "out.");
        out.println("    If --include_obsolete is specified, obsolete packages are "
                + "included.");
        out.println();
        out.println("* If the env var REPO_OS_OVERRIDE is set to \"windows\",\n"
                + "  \"macosx\", or \"linux\", packages will be downloaded for that OS");
    }

    @VisibleForTesting
    static class Settings implements SettingsController {

        private static final String CHANNEL_ARG = "--channel=";
        private static final String UNINSTALL_ARG = "--uninstall";
        private static final String UPDATE_ARG = "--update";
        private static final String PKG_FILE_ARG = "--package_file=";
        private static final String LIST_ARG = "--list";
        private static final String INCLUDE_OBSOLETE_ARG = "--include_obsolete";
        private static final String HELP_ARG = "--help";

        private Path mLocalPath;
        private List<String> mPackages = new ArrayList<>();
        private int mChannel = 0;
        private boolean mIsInstall = true;
        private boolean mIsUpdate = false;
        private boolean mIsList = false;
        private boolean mIncludeObsolete = false;

        @Nullable
        public static Settings createSettings(@NonNull String[] args,
                @NonNull FileSystem fileSystem) {
            Settings result = new Settings();
            for (String arg : args) {
                if (arg.equals(HELP_ARG)) {
                    return null;
                } else if (arg.equals(UNINSTALL_ARG)) {
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
                        result.mPackages.addAll(
                                Files.readAllLines(fileSystem.getPath(packageFile)));
                    } catch (IOException e) {
                        ConsoleProgressIndicator progress = new ConsoleProgressIndicator();
                        progress.logError(
                                String.format("Invalid package file \"%s\" threw exception:%n%s%n",
                                        packageFile, e));
                        return null;
                    }
                } else if (result.mLocalPath == null) {
                    Path path = fileSystem.getPath(arg);
                    if (!Files.exists(path)) {
                        try {
                            Files.createDirectories(path);
                        } catch (IOException e) {
                            ConsoleProgressIndicator progress = new ConsoleProgressIndicator();
                            progress.logError("Failed to create SDK root dir: " + path);
                            progress.logError(e.getMessage());
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
                        .filter(p -> mIncludeObsolete || !p.getRemote().obsolete())
                        .map(p -> p.getRepresentative().getPath())
                        .collect(Collectors.toList());
            }
            return mPackages;
        }

        @NonNull
        public Path getLocalPath() {
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
