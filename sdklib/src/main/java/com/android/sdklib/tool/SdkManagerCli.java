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
import com.android.repository.api.Dependency;
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
import com.android.repository.impl.meta.RevisionType;
import com.android.repository.io.FileOpUtils;
import com.android.repository.io.impl.FileSystemFileOp;
import com.android.repository.util.InstallerUtil;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.installer.SdkInstallerUtil;
import com.android.sdklib.repository.legacy.LegacyDownloader;
import com.android.utils.FileUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Simple tool for installing, uninstalling, etc. SDK packages.
 *
 * Can be built with a convenient wrapper script from the commandline like
 * gradlew :base:sdkmanager-cli:installDist
 */
public class SdkManagerCli {

    private static final String TOOLSDIR = "com.android.sdklib.toolsdir";

    private final Settings mSettings;
    private final AndroidSdkHandler mHandler;
    private final RepoManager mRepoManager;
    private final PrintStream mOut;
    private final BufferedReader mIn;
    private Downloader mDownloader;

    public static void main(@NonNull String args[]) {
        try {
            main(Arrays.asList(args));
        } catch (CommandFailedException | UncheckedCommandFailedException e) {
            System.exit(1);
        }
    }

    private static void main(@NonNull List<String> args) throws CommandFailedException {
        FileSystemFileOp fop = (FileSystemFileOp)FileOpUtils.create();
        Settings settings = Settings.createSettings(args, fop.getFileSystem());

        if (settings == null) {
            usage(System.err);
            throw new CommandFailedException();
        }
        Path localPath = settings.getLocalPath();
        if (!Files.exists(localPath)) {
            try {
                Files.createDirectories(localPath);
            } catch (IOException e) {
                System.err.println("Failed to create SDK root dir: " + localPath);
                System.err.println(e.getMessage());
                throw new CommandFailedException();
            }
        }
        AndroidSdkHandler handler = AndroidSdkHandler.getInstance(localPath.toFile());
        new SdkManagerCli(
                        settings,
                        System.out,
                        System.in,
                        new LegacyDownloader(fop, settings),
                        handler)
                .run(settings.getProgressIndicator());
        System.out.println();
        System.out.println("done");
    }

    public SdkManagerCli(@NonNull Settings settings, @NonNull PrintStream out,
            @Nullable InputStream in, @Nullable Downloader downloader,
            @NonNull AndroidSdkHandler handler) {
        mSettings = settings;
        mOut = out;
        mIn = in == null ? null : new BufferedReader(new InputStreamReader(in));
        mDownloader = downloader;
        // Unfortunately AndroidSdkHandler etc. doesn't use nio (yet). We have to convert like this
        // to deal with the non-default provider (test) case.
        mHandler = handler;
        mRepoManager = mHandler.getSdkManager(settings.getProgressIndicator());
    }

    void run(@NonNull ProgressIndicator progress) throws CommandFailedException {
        if (mSettings == null) {
            throw new CommandFailedException();
        }
        if (mSettings.isList()) {
            listPackages(progress);
        } else if (mSettings.islicenses()) {
            showLicenses(progress);
        } else {
            if (mSettings.isInstallAction()) {
                installPackages(progress);
            }
            else {
                uninstallPackages(progress);
            }
        }
    }

    private void listPackages(@NonNull ProgressIndicator progress) {
        progress.setText("Loading package information...");
        mRepoManager.loadSynchronously(0, progress, mDownloader, mSettings);

        RepositoryPackages packages = mRepoManager.getPackages();

        Collection<LocalPackage> locals = new TreeSet<>();
        Collection<LocalPackage> localObsoletes = new TreeSet<>();
        for (LocalPackage local : packages.getLocalPackages().values()) {
            if (local.obsolete()) {
                localObsoletes.add(local);
            }
            else {
                locals.add(local);
            }
        }


        Collection<RemotePackage> remotes = new TreeSet<>();
        Collection<RemotePackage> remoteObsoletes = new TreeSet<>();
        for (RemotePackage remote : packages.getRemotePackages().values()) {
            if (remote.obsolete()) {
                remoteObsoletes.add(remote);
            }
            else {
                remotes.add(remote);
            }
        }

        Set<UpdatablePackage> updates = new TreeSet<>(packages.getUpdatedPkgs());

        if (mSettings.isVerbose()) {
            printListVerbose(locals, localObsoletes, remotes, remoteObsoletes, updates);
        }
        else {
            printList(locals, localObsoletes, remotes, remoteObsoletes, updates);
        }
    }

    private void printListVerbose(@NonNull Collection<LocalPackage> locals,
            @NonNull Collection<LocalPackage> localObsoletes,
            @NonNull Collection<RemotePackage> remotes,
            @NonNull Collection<RemotePackage> remoteObsoletes,
            @NonNull Set<UpdatablePackage> updates) {

        if (!locals.isEmpty()) {
            mOut.println("Installed packages:");
            mOut.println("--------------------------------------");

            verboseListLocal(locals);
        }

        if (mSettings.includeObsolete() && !localObsoletes.isEmpty()) {
            mOut.println("Installed Obsolete Packages:");
            mOut.println("--------------------------------------");
            verboseListLocal(locals);
        }

        if (!remotes.isEmpty()) {
            mOut.println("Available Packages:");
            mOut.println("--------------------------------------");
            verboseListRemote(remotes);
        }

        if (mSettings.includeObsolete() && !remoteObsoletes.isEmpty()) {
            mOut.println();
            mOut.println("Available Obsolete Packages:");
            mOut.println("--------------------------------------");
            verboseListRemote(remoteObsoletes);
        }

        if (!updates.isEmpty()) {
            mOut.println("Available Updates:");
            mOut.println("--------------------------------------");
            for (UpdatablePackage update : updates) {
                mOut.println(update.getPath());
                mOut.println("    Installed Version: " + update.getLocal().getVersion());
                mOut.println("    Available Version: " + update.getRemote().getVersion());
                if (update.getRemote().obsolete()) {
                    mOut.println("    (Obsolete)");
                }
            }
        }
    }

    private void verboseListLocal(@NonNull Collection<LocalPackage> locals) {
        for (LocalPackage local : locals) {
            mOut.println(local.getPath());
            mOut.println("    Description:        " + local.getDisplayName());
            mOut.println("    Version:            " + local.getVersion());
            mOut.println("    Installed Location: " + local.getLocation());
            mOut.println();
        }
    }

    private void verboseListRemote(@NonNull Collection<RemotePackage> remotes) {
        for (RemotePackage remote : remotes) {
            mOut.println(remote.getPath());
            mOut.println("    Description:        " + remote.getDisplayName());
            mOut.println("    Version:            " + remote.getVersion());
            if (!remote.getAllDependencies().isEmpty()) {
                mOut.println("    Dependencies:");
                for (Dependency dependency : remote.getAllDependencies()) {
                    RevisionType minRevision = dependency.getMinRevision();
                    mOut.print("        " + dependency.getPath());
                    if (minRevision != null) {
                        mOut.println(" Revision " + minRevision.toRevision());
                    } else {
                        mOut.println();
                    }
                }
            }
            mOut.println();
        }
    }

    private void printList(@NonNull Collection<LocalPackage> locals,
            @NonNull Collection<LocalPackage> localObsoletes,
            @NonNull Collection<RemotePackage> remotes,
            @NonNull Collection<RemotePackage> remoteObsoletes,
            @NonNull Set<UpdatablePackage> updates) {
        TableFormatter<LocalPackage> localTable = new TableFormatter<>();
        localTable.addColumn("Path", RepoPackage::getPath, 9999, 0);
        localTable.addColumn("Version", p -> p.getVersion().toString(), 100, 0);
        localTable.addColumn("Description", RepoPackage::getDisplayName, 100, 0);
        localTable.addColumn(
                "Location",
                p ->
                        FileUtils.relativePossiblyNonExistingPath(
                                p.getLocation(), mRepoManager.getLocalPath()),
                9999,
                0);

        if (!locals.isEmpty()) {
            mOut.println("Installed packages:");
            localTable.print(locals, mOut);
        }

        if (mSettings.includeObsolete() && !localObsoletes.isEmpty()) {
            mOut.println();
            mOut.println("Installed Obsolete Packages:");
            localTable.print(localObsoletes, mOut);
        }

        TableFormatter<RemotePackage> remoteTable = new TableFormatter<>();
        remoteTable.addColumn("Path", RepoPackage::getPath, 9999, 0);
        remoteTable.addColumn("Version", p -> p.getVersion().toString(), 100, 0);
        remoteTable.addColumn("Description", RepoPackage::getDisplayName, 100, 0);

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

        if (!updates.isEmpty()) {
            mOut.println();
            mOut.println("Available Updates:");
            TableFormatter<UpdatablePackage> updateTable = new TableFormatter<>();
            updateTable.addColumn("ID", UpdatablePackage::getPath, 9999, 0);
            updateTable.addColumn("Installed", p -> p.getLocal().getVersion().toString(), 20, 0);
            updateTable.addColumn("Available", p -> p.getRemote().getVersion().toString(), 20, 0);
            if (!mSettings.includeObsolete()) {
                updates.removeIf(updatable -> updatable.getRemote().obsolete());
            }
            updateTable.print(updates, mOut);
        }
    }

    private void installPackages(@NonNull ProgressIndicator progress)
            throws CommandFailedException {
        progress.setText("Loading package information...");
        mRepoManager.loadSynchronously(0, progress.createSubProgress(0.1), mDownloader, mSettings);

        List<RemotePackage> remotes = new ArrayList<>();
        for (String path : mSettings.getPaths(mRepoManager)) {
            RemotePackage p = mRepoManager.getPackages().getRemotePackages().get(path);
            if (p == null) {
                progress.logWarning("Failed to find package " + path);
                throw new CommandFailedException();
            }
            remotes.add(p);
        }
        remotes =
                InstallerUtil.computeRequiredPackages(
                        remotes, mRepoManager.getPackages(), progress);
        if (remotes != null) {
            List<RemotePackage> acceptedRemotes = checkLicenses(remotes, progress);
            if (!acceptedRemotes.equals(remotes)) {
                mOut.println("The following packages can not be installed since their "
                        + "licenses or those of the packages they depend on were not accepted:");
                remotes.stream()
                        .filter(p -> !acceptedRemotes.contains(p))
                        .forEach(p -> mOut.println("  " + p.getPath()));
                if (!acceptedRemotes.isEmpty()) {
                    mOut.print("Continue installing the remaining packages? (y/N): ");
                    if (!askYesNo()) {
                        throw new CommandFailedException();
                    }
                }
                remotes = acceptedRemotes;
            }
            double progressMax = 0.1;
            double progressIncrement = 0.9 / (remotes.size());
            for (RemotePackage p : remotes) {
                progress.setText("Installing " + p.getDisplayName());
                Installer installer = SdkInstallerUtil.findBestInstallerFactory(p, mHandler)
                        .createInstaller(p, mRepoManager, mDownloader, mHandler.getFileOp());
                progressMax += progressIncrement;
                if (!applyPackageOperation(installer, progress.createSubProgress(progressMax))) {
                    // there was an error, abort.
                    throw new CommandFailedException();
                }
                progress.setFraction(progressMax);
            }
            progress.setFraction(1);
        } else {
            progress.logWarning("Unable to compute a complete list of dependencies.");
            throw new CommandFailedException();
        }
    }

    private void showLicenses(@NonNull ProgressIndicator progress) throws CommandFailedException {
        mRepoManager.loadSynchronously(0, progress, mDownloader, mSettings);

        Set<License> licenses =
                mRepoManager
                        .getPackages()
                        .getRemotePackages()
                        .values()
                        .stream()
                        .map(RemotePackage::getLicense)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(TreeSet::new));

        // Find licences that are not accepted yet.
        ImmutableList.Builder<License> licensesNotYetAcceptedBuilder = ImmutableList.builder();
        for (License license : licenses) {
            boolean accepted = license.checkAccepted(mHandler.getLocation(), mHandler.getFileOp());

            if (!accepted) {
                licensesNotYetAcceptedBuilder.add(license);
            }

            if (mSettings.isVerbose()) {
                printLicense(license);
                mOut.format(accepted ? "Accepted%n%n" : "Not yet accepted%n%n");
            }
        }
        ImmutableList<License> licensesNotYetAccepted = licensesNotYetAcceptedBuilder.build();

        if (licensesNotYetAccepted.isEmpty()) {
            mOut.println("All SDK package licenses accepted.");
            return;
        }

        mOut.format(
                "%1$d of %2$d SDK package license%3$s not accepted.%n"
                        + "Review license%3$s that ha%4$s not been accepted (y/N)? ",
                licensesNotYetAccepted.size(),
                licenses.size(),
                licensesNotYetAccepted.size() == 1 ? "" : "s",
                licensesNotYetAccepted.size() == 1 ? "s" : "ve");
        if (!askYesNo()) {
            return;
        }

        int newlyAcceptedCount = 0;
        for (int i = 0; i < licensesNotYetAccepted.size(); i++) {
            mOut.format("%n%1$d/%2$d: ", i + 1, licensesNotYetAccepted.size());
            License license = licensesNotYetAccepted.get(i);
            if (askForLicense(license)) {
                license.setAccepted(mRepoManager.getLocalPath(), mHandler.getFileOp());
                newlyAcceptedCount++;
            }
        }

        if (newlyAcceptedCount == licensesNotYetAccepted.size()) {
            mOut.println("All SDK package licenses accepted");
        } else {
            int notAccepted = licensesNotYetAccepted.size() - newlyAcceptedCount;
            mOut.format(
                    "%1$d license%2$s not accepted%n",
                    notAccepted,
                    notAccepted == 1 ? "" : "s");
        }
    }

    /**
     * Checks whether the licenses for the given packages are accepted. If they are not, request
     * that the user accept them.
     *
     * @return A list of packages that have had their licenses accepted. If some licenses are not
     *     accepted, both the package with the unaccepted license and any packages that depend on it
     *     are excluded from this list.
     */
    @NonNull
    private List<RemotePackage> checkLicenses(
            @NonNull List<RemotePackage> remotes, @NonNull ProgressIndicator progress) {
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
            mOut.println("Skipping following packages as the license is not accepted:");
            problemPackages.forEach(problem -> mOut.println(problem.getDisplayName()));
            acceptedPackages.removeAll(problemPackages);
            Iterator<RemotePackage> acceptedIter = acceptedPackages.iterator();

            while (acceptedIter.hasNext()) {
                RemotePackage accepted = acceptedIter.next();
                List<RemotePackage> required =
                        InstallerUtil.computeRequiredPackages(
                                Collections.singletonList(accepted),
                                mRepoManager.getPackages(),
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

    private boolean askForLicense(@NonNull License license) {
        printLicense(license);
        mOut.print("Accept? (y/N): ");
        return askYesNo();
    }

    private void printLicense(@NonNull License license) {
        mOut.printf("License %s:%n", license.getId());
        mOut.println("---------------------------------------");
        mOut.println(license.getValue());
        mOut.println("---------------------------------------");
    }

    private boolean askYesNo() {
        try {
            String result = mIn.readLine();
            return result != null
                    && (result.equalsIgnoreCase("y") || result.equalsIgnoreCase("yes"));
        } catch (IOException e) {
            return false;
        }
    }

    private void uninstallPackages(@NonNull ProgressIndicator progress)
            throws CommandFailedException {
        mRepoManager.loadSynchronously(0, progress.createSubProgress(0.1), null, mSettings);

        List<String> paths = mSettings.getPaths(mRepoManager);
        double progressMax = 0.1;
        double progressIncrement = 0.9 / paths.size();
        for (String path : paths) {
            LocalPackage p = mRepoManager.getPackages().getLocalPackages().get(path);
            if (p == null) {
                progress.logWarning("Unable to find package " + path);
            } else {
                Uninstaller uninstaller = SdkInstallerUtil.findBestInstallerFactory(p, mHandler)
                        .createUninstaller(p, mRepoManager, mHandler.getFileOp());
                progressMax += progressIncrement;
                if (!applyPackageOperation(uninstaller, progress.createSubProgress(progressMax))) {
                    // there was an error, abort.
                    throw new CommandFailedException();
                }
            }
            progress.setFraction(progressMax);
        }
        progress.setFraction(1);
    }

    private boolean applyPackageOperation(
            @NonNull PackageOperation operation, @NonNull ProgressIndicator progress) {
        return operation.prepare(progress.createSubProgress(0.5))
                && operation.complete(progress.createSubProgress(1));
    }

    private static void usage(@NonNull PrintStream out) {
        out.println("Usage: ");
        out.println("  sdkmanager [--uninstall] [<common args>] "
                + "[--package_file <file>] [<packages>...]");
        out.println("  sdkmanager --update [<common args>]");
        out.println("  sdkmanager --list [<common args>]");
        out.println("  sdkmanager --licenses [<common args>]");
        out.println();
        out.println("In its first form, installs, or uninstalls, or updates packages.");
        out.println("    By default, the listed packages are installed or (if already installed)");
        out.println("    updated to the latest version.");
        out.println();
        out.println("    --uninstall: uninstalled listed packages.");
        out.println();
        out.println("    <package> is a sdk-style path (e.g. \"build-tools;23.0.0\" or");
        out.println("             \"platforms;android-23\").");
        out.println("    <package-file> is a text file where each line is a sdk-style path");
        out.println("                   of a package to install or uninstall.");
        out.println("    Multiple --package_file arguments may be specified in combination");
        out.println("    with explicit paths.");
        out.println();
        out.println("In its second form (with --update), all installed packages are");
        out.println("    updated to the latest version.");
        out.println();
        out.println("In its third form, all installed and available packages are printed");
        out.println("    out.");
        out.println();
        out.println("In its fourth form (with --licenses), show and offer the option to");
        out.println("     accept licenses for all available packages that have not already been");
        out.println("     accepted.");
        out.println();
        out.println("Common Arguments:");
        out.println("    --sdk_root=<sdkRootPath>: Use the specified SDK root instead of the SDK ");
        out.println("                              containing this tool");
        out.println();
        out.println("    --channel=<channelId>: Include packages in channels up to <channelId>.");
        out.println("                           Common channels are:");
        out.println("                           0 (Stable), 1 (Beta), 2 (Dev), and 3 (Canary).");
        out.println();
        out.println("    --include_obsolete: With --list, show obsolete packages in the");
        out.println("                        package listing. With --update, update obsolete");
        out.println("                        packages as well as non-obsolete.");
        out.println();
        out.println("    --no_https: Force all connections to use http rather than https.");
        out.println();
        out.println("    --proxy=<http | socks>: Connect via a proxy of the given type.");
        out.println();
        out.println("    --proxy_host=<IP or DNS address>: IP or DNS address of the proxy to use.");
        out.println();
        out.println("    --proxy_port=<port #>: Proxy port to connect to.");
        out.println();
        out.println("    --verbose: Enable verbose output.");
        out.println();
        out.println("* If the env var REPO_OS_OVERRIDE is set to \"windows\",\n"
                + "  \"macosx\", or \"linux\", packages will be downloaded for that OS.");
    }

    @VisibleForTesting
    static class Settings implements SettingsController {

        private static final String CHANNEL_ARG = "--channel=";
        private static final String LICENSES_ARG = "--licenses";
        private static final String UNINSTALL_ARG = "--uninstall";
        private static final String UPDATE_ARG = "--update";
        private static final String PKG_FILE_ARG = "--package_file=";
        private static final String SDK_ROOT_ARG = "--sdk_root=";
        private static final String LIST_ARG = "--list";
        private static final String INCLUDE_OBSOLETE_ARG = "--include_obsolete";
        private static final String HELP_ARG = "--help";
        private static final String NO_HTTPS_ARG = "--no_https";
        private static final String VERBOSE_ARG = "--verbose";
        private static final String PROXY_TYPE_ARG = "--proxy=";
        private static final String PROXY_HOST_ARG = "--proxy_host=";
        private static final String PROXY_PORT_ARG = "--proxy_port=";

        private Path mLocalPath;
        private List<String> mPackages = new ArrayList<>();
        private int mChannel = 0;
        private boolean mIsLicenses = false;
        private boolean mIsInstall = true;
        private boolean mIsUpdate = false;
        private boolean mIsList = false;
        private boolean mIncludeObsolete = false;
        private boolean mForceHttp = false;
        private boolean mVerbose = false;
        private Proxy.Type mProxyType;
        private SocketAddress mProxyHost;

        @Nullable
        public static Settings createSettings(
                @NonNull List<String> args, @NonNull FileSystem fileSystem) {
            ProgressIndicator progress = new ConsoleProgressIndicator();
            Settings result = new Settings();
            String proxyHost = null;
            int proxyPort = -1;
            String toolsDir = System.getProperty(TOOLSDIR);
            if (toolsDir != null) {
                result.mLocalPath = fileSystem.getPath(toolsDir).normalize().getParent();
            }

            for (String arg : args) {
                if (arg.equals(HELP_ARG)) {
                    return null;
                } else if (arg.equals(LICENSES_ARG)) {
                    result.mIsLicenses = true;
                } else if (arg.equals(UNINSTALL_ARG)) {
                    result.mIsInstall = false;
                } else if (arg.equals(NO_HTTPS_ARG)) {
                    result.setForceHttp(true);
                } else if (arg.equals(UPDATE_ARG)) {
                    result.mIsUpdate = true;
                } else if (arg.equals(LIST_ARG)) {
                    result.mIsList = true;
                } else if (arg.equals(VERBOSE_ARG)) {
                    result.mVerbose = true;
                } else if (arg.equals(INCLUDE_OBSOLETE_ARG)) {
                    result.mIncludeObsolete = true;
                } else if (arg.startsWith(PROXY_HOST_ARG)) {
                    proxyHost = arg.substring(PROXY_HOST_ARG.length());
                } else if (arg.startsWith(PROXY_PORT_ARG)) {
                    String value = arg.substring(PROXY_PORT_ARG.length());
                    try {
                        proxyPort = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        progress.logWarning(String.format("Invalid port \"%s\"", value));
                        return null;
                    }
                } else if (arg.startsWith(PROXY_TYPE_ARG)) {
                    String type = arg.substring(PROXY_TYPE_ARG.length());
                    if (type.equals("socks")) {
                        result.mProxyType = Proxy.Type.SOCKS;
                    } else if (type.equals("http")) {
                        result.mProxyType = Proxy.Type.HTTP;
                    } else {
                        progress.logWarning("Valid proxy types are \"socks\" and \"http\".");
                        return null;
                    }
                } else if (arg.startsWith(CHANNEL_ARG)) {
                    String value = arg.substring(CHANNEL_ARG.length());
                    try {
                        result.mChannel = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        progress.logWarning(String.format("Invalid channel id \"%s\"", value));
                        return null;
                    }
                } else if (arg.startsWith(PKG_FILE_ARG)) {
                    String packageFile = arg.substring(PKG_FILE_ARG.length());
                    try {
                        result.mPackages.addAll(
                                Files.readAllLines(fileSystem.getPath(packageFile)));
                    } catch (IOException e) {
                        progress.logWarning(
                                String.format("Invalid package file \"%s\" threw exception:%n%s%n",
                                        packageFile, e));
                        return null;
                    }
                } else if (arg.startsWith(SDK_ROOT_ARG)) {
                    result.mLocalPath = fileSystem.getPath(arg.substring(SDK_ROOT_ARG.length()));
                } else if (arg.startsWith("--")) {
                    progress.logWarning(String.format("Unknown argument %s", arg));
                    return null;
                } else {
                    result.mPackages.add(arg);
                }
            }

            if (result.mLocalPath == null
                    || !result.mPackages.isEmpty()
                            == (result.mIsUpdate || result.mIsList || result.mIsLicenses)) {
                return null;
            }
            if (proxyHost == null ^ result.mProxyType == null ||
                    proxyPort == -1 ^ result.mProxyType == null) {
                progress.logWarning(
                        String.format("All of %1$s, %2$s, and %3$s must be specified if any are.",
                                PROXY_HOST_ARG, PROXY_PORT_ARG, PROXY_TYPE_ARG));
                return null;
            } else if (result.mProxyType != null) {
                SocketAddress address = createAddress(proxyHost, proxyPort);
                if (address == null) {
                    return null;
                }
                result.mProxyHost = address;
            }
            return result;
        }

        private static SocketAddress createAddress(String host, int port) {
            try {
                InetAddress address = InetAddress.getByName(host);
                return new InetSocketAddress(address, port);
            } catch (UnknownHostException e) {
                new ConsoleProgressIndicator().logWarning("Failed to parse host " + host);
                return null;
            }
        }

        @Nullable
        @Override
        public Channel getChannel() {
            return Channel.create(mChannel);
        }

        @Override
        public boolean getForceHttp() {
            return mForceHttp;
        }

        @Override
        public void setForceHttp(boolean force) {
            mForceHttp = force;
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

        public boolean islicenses() {
            return mIsLicenses;
        }

        public boolean isVerbose() {
            return mVerbose;
        }

        public boolean includeObsolete() {
            return mIncludeObsolete;
        }

        @NonNull
        public ProgressIndicator getProgressIndicator() {
            return new ConsoleProgressIndicator() {
                @Override
                public void logWarning(@NonNull String s, @Nullable Throwable e) {
                    if (mVerbose) {
                        super.logWarning(s, e);
                    } else {
                        super.logWarning(s, null);
                    }
                }

                @Override
                public void logError(@NonNull String s, @Nullable Throwable e) {
                    if (mVerbose) {
                        super.logWarning(s, e);
                    } else {
                        super.logWarning(s, null);
                    }
                    throw new UncheckedCommandFailedException();
                }

                @Override
                public void logInfo(@NonNull String s) {
                    if (mVerbose) {
                        super.logInfo(s);
                    }
                }

                @Override
                public void logVerbose(@NonNull String s) {
                    if (mVerbose) {
                        super.logVerbose(s);
                    }
                }
            };
        }

        @NonNull
        @Override
        public Proxy getProxy() {
            return mProxyType == null ? Proxy.NO_PROXY : new Proxy(mProxyType, mProxyHost);
        }
    }

    public static final class CommandFailedException extends Exception {}
    public static final class UncheckedCommandFailedException extends RuntimeException {}
}
