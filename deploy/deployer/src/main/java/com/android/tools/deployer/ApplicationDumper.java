/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.deployer;

import com.android.tools.deploy.proto.Deploy;
import com.android.tools.deployer.model.Apk;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class ApplicationDumper {

    private final Installer installer;

    public ApplicationDumper(Installer installer) {
        this.installer = installer;
    }

    /** Fetch the single package name of a list of apks. */
    public static String getPackageName(List<Apk> apks) throws DeployerException {
        // The name of the package being swapped (the one that will actually be installed).
        String packageName = null;

        for (Apk apk : apks) {
            if (packageName == null) {
                packageName = apk.packageName;
            }

            if (!apk.packageName.equals(packageName)) {
                // This is intentionally a swap failure, not a dump failure; we just discover it
                // during dump.
                throw DeployerException.swapMultiplePackages();
            }
        }

        return packageName;
    }
    /*
     * This method retrieves information on the application to be swapped; specifically, process ids
     * and currently installed files.
     *
     * Process ids are determined by first looking at the manifests of the local APKs:
     *  - If the local APK contains instrumentation targeting a specific package, we look up
     *    process ids for that package.
     *  - If the local APK does not contain instrumentation, we look up process ids for the APK
     *    package.
     *
     * Currently installed files are always retrieved from the package manager on device.
     *
     * The final result is an application dump consisting of:
     *  - The files that will be diffed when performing the swap.
     *  - The process ids of the ART instances that the agents will attach to.
     *
     *  The returned process ids are in the form of a map of [package name --> list of packages], as
     *  there may be multiple instrumentation target packages.
     */
    public Dump dump(List<Apk> apks) throws DeployerException {
        // The name of the package being swapped (the one that will actually be installed).
        String packageName = null;

        // Additional packages whose processes should be targeted while swapping. Used to account
        // for instrumentation which doesn't run in its own process but instead the processes of
        // other packages.
        HashSet<String> targetPackages = new HashSet<>();

        for (Apk apk : apks) {
            if (packageName == null) {
                packageName = apk.packageName;
            }

            if (!apk.packageName.equals(packageName)) {
                // This is intentionally a swap failure, not a dump failure; we just discover it during dump.
                throw DeployerException.swapMultiplePackages();
            }

            targetPackages.addAll(apk.targetPackages);
        }

        ArrayList<String> packagesToDump = new ArrayList<>();
        packagesToDump.add(packageName);
        packagesToDump.addAll(targetPackages);

        Deploy.DumpResponse response;
        try {
            response = installer.dump(packagesToDump);
        } catch (IOException e) {
            throw DeployerException.dumpFailed(e.getMessage());
        }

        // TODO: To throw an exception here makes this component hard to re-use.
        // The component using it should be the one to check apkEntries.size() and throw
        // and exception if necessary. This check should be moved further down the
        // pipeline.
        if (response.getStatus() == Deploy.DumpResponse.Status.ERROR_PACKAGE_NOT_FOUND) {
            throw DeployerException.unknownPackage(response.getFailedPackage());
        }

        return new Dump(GetApks(response.getPackages(0)), GetPids(response), GetArch(response));
    }

    public static class Dump {
        public final List<Apk> apks;
        public final Map<String, List<Integer>> packagePids;
        public final Deploy.Arch arch;

        public Dump(List<Apk> apks, Map<String, List<Integer>> packagePids, Deploy.Arch arch) {
            this.apks = apks;
            this.packagePids = packagePids;
            this.arch = arch;
        }
    }

    private static List<Apk> GetApks(Deploy.PackageDump packageDump) {
        List<Apk> dumps = new ArrayList<>();
        for (Deploy.ApkDump dump : packageDump.getApksList()) {
            ByteBuffer cd = dump.getCd().asReadOnlyByteBuffer();
            ByteBuffer signature = dump.getSignature().asReadOnlyByteBuffer();
            List<ZipUtils.ZipEntry> zipEntries = ZipUtils.readZipEntries(cd);
            cd.rewind();
            String digest = ZipUtils.digest(signature.remaining() != 0 ? signature : cd);

            Apk.Builder builder =
                    Apk.builder()
                            .setName(dump.getName())
                            .setChecksum(digest)
                            .setPath(dump.getAbsolutePath());

            for (ZipUtils.ZipEntry entry : zipEntries) {
                builder.addApkEntry(entry);
            }

            dumps.add(builder.build());
        }
        return dumps;
    }

    private static Map<String, List<Integer>> GetPids(Deploy.DumpResponse response) {
        Map<String, List<Integer>> pids = new HashMap<>();
        for (Deploy.PackageDump packageDump : response.getPackagesList()) {
            // Skip packages with no processes.
            if (!packageDump.getProcessesList().isEmpty()) {
                pids.put(packageDump.getName(), packageDump.getProcessesList());
            }
        }
        return pids;
    }

    private static Deploy.Arch GetArch(Deploy.DumpResponse response) throws DeployerException {
        // TODO: DUMP should not return ARCH_UNKNOWN. We should on the host side determine
        // exactly which app / process needs to dump ProcID / ARCH before calling the installer.
        // For instrumentation test, we should only dump the APK's CD and the targeted package's
        // process / arch.
        Deploy.Arch result = Deploy.Arch.ARCH_UNKNOWN;
        String lastPackageWithKnowArch = null;
        for (Deploy.PackageDump pkg : response.getPackagesList()) {
            Deploy.Arch arch = pkg.getArch();
            if (arch.equals(Deploy.Arch.ARCH_UNKNOWN)) {
                continue;
            } else if (!result.equals(Deploy.Arch.ARCH_UNKNOWN) && !result.equals(arch)) {
                throw DeployerException.dumpMixedArch(
                        lastPackageWithKnowArch
                                + " is "
                                + result
                                + " while "
                                + pkg.getName()
                                + " is "
                                + arch
                                + ".");
            } else {
                result = arch;
                lastPackageWithKnowArch = pkg.getName();
            }
        }
        return result;
    }
}
