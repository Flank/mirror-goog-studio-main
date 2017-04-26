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

package com.android.tools.utils;

import com.android.repository.impl.meta.Archive;
import com.android.sdklib.tool.SdkManagerCli;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A utility class which manages updating an Android SDK for all supported platforms at the same
 * time. For example, if you have SDKs in...
 *
 * <pre>
 *   /path/to/sdk-root/darwin/...
 *   /path/to/sdk-root/linux/...
 *   /path/to/sdk-root/windows/...
 * </pre>
 *
 * and you want to add a new SDK component to all of them, say build-tools;19.0.3, then this script
 * helps manage that.
 *
 * Essentially, this is a utility that sits on top of {@link SdkManagerCli}, which itself is
 * responsible for managing an SDK for a single platform.
 *
 * Run "DevSdkUpdater --help" or see the {@link #usage()} method for more information.
 */
public final class DevSdkUpdater {

    private static final List<OsEntry> OS_ENTRIES = Arrays.asList(
            new OsEntry("macosx", "darwin"),
            new OsEntry("linux"),
            new OsEntry("windows")
    );

    public static void main(String[] args) throws IOException {
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
        System.out.println("Usage: DevSdkUpdater <options> --dest <sdk-dest-root>");
        System.out.println();
        System.out.println("<sdk-dest-root>: A path to a root folder which will contain an SDK");
        System.out.println("                 for each supported OS (darwin/linux/windows)");
        System.out.println();
        System.out.println("Valid options:");
        System.out.println("  --help                  Prints this information and quits");
        System.out.println("  --package <pkg>         A single SDK path to update/download,");
        System.out.println("                          e.g. build-tools;23.0.1 or platform-tools");
        System.out.println("                          Filters using a glob syntax can be");
        System.out.println("                          included after a colon,");
        System.out.println("                          e.g. platform-tools:{adb*,systrace/**}");
        System.out.println("                          Here, 'adb*' matches 'adb' and 'adb.exe'");
        System.out.println("                          and 'systrace/**' matches all dir contents.");
        System.out.println("                          An exclude filter can also be included,");
        System.out.println("                          e.g. build-tools:**:**.jar, which means");
        System.out.println("                          include everything but jar files.");
        System.out.println("  --package-file <file>   A file where each line is an SDK package.");
        System.out.println("                          # comments are allowed in this file.");
        System.out.println("  --platform              darwin, windows, or linux. Useful for");
        System.out.println("                          debugging on a single platform at a time.");
        System.out.println();
        System.out.println("Example usages:");
        System.out.println();
        System.out.println("  # Updating a bunch of packages");
        System.out.println("  $ DevSdkUpdater \\");
        System.out.println("    --package-file dev-sdk-packages \\");
        System.out.println("    --dest /path/to/studio-master-dev/prebuilts/studio/sdk");
        System.out.println();
        System.out.println("  # Updating to only the top folder of a single package");
        System.out.println("  $ DevSdkUpdater \\");
        System.out.println("    --package \"build-tools;23.0.1:*\" \\");
        System.out.println("    --dest /path/to/studio-master-dev/prebuilts/studio/sdk");
        System.out.println();
    }

    private static Status run(String[] args) throws IOException {

        File sdkDest = null;
        String platform = null; // If null, update all platforms
        List<String> packageLines = new ArrayList<>();
        for (int i = 0; i < args.length; ++i) {
            String arg = args[i];
            ++i;
            switch (arg) {
                case "--package-file":
                    try {
                        if (!processPackageFile(Paths.get(args[i]), packageLines)) {
                            return Status.ERROR;
                        }
                    } catch (ArrayIndexOutOfBoundsException ignored) {
                        usage("Package file not set");
                        return Status.ERROR;
                    }
                    break;
                case "--package":
                    try {
                        packageLines.add(args[i]);
                    } catch (ArrayIndexOutOfBoundsException ignored) {
                        usage("Package not set");
                        return Status.ERROR;
                    }
                    break;
                case "--platform":
                    platform = args[i];
                    break;
                case "--dest":
                    try {
                        sdkDest = new File(args[i]);
                    } catch (ArrayIndexOutOfBoundsException ignored) {
                        usage("Dest path not set");
                        return Status.ERROR;
                    }
                    break;
                case "--help":
                    usage();
                    return Status.SUCCESS;
                default:
                    usage("Unknown option: " + arg);
                    return Status.ERROR;
            }
        }

        // If the paths were not provided, use the default repo layout.
        if (sdkDest == null) {
            sdkDest = WorkspaceUtils.findPrebuiltsSdks().toFile();
        }
        if (!checkSdkDest(sdkDest, platform)) {
          return Status.ERROR;
        }

        if (packageLines.isEmpty()) {
            if (!processPackageFile(WorkspaceUtils.findSdkPackagesFile(), packageLines)) {
                return Status.ERROR;
            }
        }

        System.out.println("Downloading SDKs into " + sdkDest.getAbsolutePath());
        downloadSdkPackages(sdkDest, packageLines, platform);
        System.out.println("done!");

        return Status.SUCCESS;
    }

    private static boolean checkSdkDest(File sdkDest, String platform) {
        for (OsEntry osEntry : OS_ENTRIES) {
            if (platform != null && !Objects.equals(platform, osEntry.mFolder)) {
                continue;
            }
            File f = new File(sdkDest, osEntry.mFolder);
            if (!f.exists()) {
                usage(
                        "Invalid SDK path \""
                                + sdkDest.getPath()
                                + "\" doesn't contain expected subdir: "
                                + osEntry.mFolder);
                return false;
            }
        }
        return true;
    }

    /**
     * Reads the package file contents into the given list, skipping comments.
     *
     * @return true on success, false if the file could not be read.
     */
    private static boolean processPackageFile(Path packageFile, List<String> packageLines) {
        try {
            // Keep only non-empty lines (after # comments are removed)
            try (Stream<String> lines = Files.lines(packageFile)) {
                packageLines.addAll(
                        lines.map(line -> line.replaceAll("#.*", ""))
                                .map(String::trim)
                                .filter(line -> !line.isEmpty())
                                .collect(Collectors.toList()));
            }
        } catch (Exception e) {
            usage(
                    "Could not successfully read package-file: "
                            + packageFile
                            + "\n\nException: "
                            + e);
            return false;
        }

        return true;
    }

    /**
     * @param packageLines A list of package entries with an (optional) filter appended to them,
     *                     e.g. "platform-tools:adb*"
     * @param platform Can be {@code null}. If set, only update for that platform.
     */
    private static void downloadSdkPackages(
            File sdkDest, List<String> packageLines, String platform) throws IOException {
        Set<String> packages = new LinkedHashSet<>(); // Just the packages, with filters stripped
        // The following is a package -> filter mapping (if a filter present)
        // If no filter is found, then all downloaded files will be kept
        Map<String, Filters> filterMap = new HashMap<>();

        for (String packageLine : packageLines) {
            String[] packageFilters = packageLine.split(":", 3);
            String pkg = packageFilters[0];
            if (packages.contains(pkg)) {
                usage(String.format("Package %s specified twice in the package file.", pkg));
            }
            packages.add(pkg);
            if (packageFilters.length > 1) {
                if (packageFilters.length == 2) {
                    filterMap.put(pkg, new Filters(packageFilters[1]));
                }
                else {
                    filterMap.put(pkg, new Filters(packageFilters[1], packageFilters[2]));
                }
            }
        }

        for (OsEntry osEntry : OS_ENTRIES) {
            if (platform != null && !Objects.equals(platform, osEntry.mFolder)) {
                continue;
            }
            File osSdkDest = new File(sdkDest, osEntry.mFolder);
            // Delegate download operation to SdkManagerCli program
            List<String> args = new ArrayList<>();
            args.add("--sdk_root=" + osSdkDest.getAbsolutePath());
            args.add("--verbose");
            args.add("--channel=2");
            args.addAll(packages);

            Archive.sHostConfig = new Archive.HostConfig(osEntry.mName);
            SdkManagerCli.main(args.toArray(new String[0]));
        }

        if (!filterMap.isEmpty()) {
            filterSdkFiles(sdkDest, filterMap, platform);
        }
    }

    /**
     * Given an SDK root directory and a mappings of package -> filters, walk through each SDK
     * package and remove files that don't match the filters. This will also remove any directories
     * left empty as a result of the filtering.
     */
    private static void filterSdkFiles(
            File sdkRoot, Map<String, Filters> filterMap, String platform) throws IOException {
        for (Map.Entry<String, Filters> pkgFilterEntry : filterMap.entrySet()) {
            String pkg = pkgFilterEntry.getKey();
            Filters filters = pkgFilterEntry.getValue();

            PathMatcher includeMatcher = filters.getIncludePathMatcher();
            PathMatcher excludeMatcher = filters.getExcludePathMatcher();
            System.out.print(String.format("Filtering %s with \"%s\"... ", pkg, filters));
            System.out.flush();
            for (OsEntry osEntry : OS_ENTRIES) {
                if (platform != null && !Objects.equals(platform, osEntry.mFolder)) {
                    continue;
                }

                File osSdkDest = new File(sdkRoot, osEntry.mFolder);
                // Convert package format to path format, e.g. a;b;c -> a/b/c
                File pkgRoot = new File(osSdkDest, pkg.replaceAll(";", "/"));

                Files.walkFileTree(pkgRoot.toPath(), new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file,
                            BasicFileAttributes attrs) throws IOException {
                        Path relPath = pkgRoot.toPath().relativize(file);
                        if (!includeMatcher.matches(relPath) || excludeMatcher.matches(relPath)) {
                            Files.delete(file);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                            throws IOException {
                        boolean isDirectoryEmpty;
                        try (DirectoryStream<Path> dirStream =
                                     Files.newDirectoryStream(dir)) {
                            isDirectoryEmpty = !dirStream.iterator().hasNext();
                        }
                        if (isDirectoryEmpty) {
                            Files.delete(dir);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            System.out.println("done!");
        }
    }

    private enum Status {
        SUCCESS(0),
        ERROR(1);

        private final int mResultCode;

        Status(int resultCode) {
            mResultCode = resultCode;
        }
    }

    /**
     * Glob filters which will be applied to each file in a package.
     */
    private static final class Filters {

        public final String mInclude;
        public final String mExclude;
        final PathMatcher mIncludePathMatcher;
        final PathMatcher mExcludePathMatcher;

        private Filters(String include, String exclude) {
            mInclude = include;
            mExclude = exclude;
            mIncludePathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + include);
            mExcludePathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + exclude);
        }
        private Filters(String include) {
            this(include, "");
        }

        PathMatcher getIncludePathMatcher() {
            return mIncludePathMatcher;
        }

        PathMatcher getExcludePathMatcher() {
            return mExcludePathMatcher;
        }

        @Override
        public String toString() {
            if (mExclude.isEmpty()) {
                return mInclude;
            }
            else {
                return String.format("%s (excluding %s)", mInclude, mExclude);
            }
        }
    }

    private static final class OsEntry {

        /**
         * Names that the SdkManagerCli utility expects for specifying target OS.
         */
        public final String mName;

        /**
         * Subfolders that the SDKs will be installed within, usually but not always
         * the same as {@link #mName}
         */
        public final String mFolder;

        public OsEntry(String name) {
            this(name, name);
        }

        public OsEntry(String name, String folder) {
            mName = name;
            mFolder = folder;
        }
    }
}
