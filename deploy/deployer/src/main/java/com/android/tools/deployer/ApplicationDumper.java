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
import com.android.tools.deployer.model.ApkEntry;
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
    public Dump dump(List<ApkEntry> apkEntries) throws DeployerException {
        // The name of the package being swapped (the one that will actually be installed).
        String packageName = null;

        // Additional packages whose processes should be targeted while swapping. Used to account
        // for instrumentation which doesn't run in its own process but instead the processes of
        // other packages.
        HashSet<String> targetPackages = new HashSet<>();

        // In an ideal world, our model structure would allow us to obtain this from the Apk
        // object(s) directly; however, we currently must iterate over every entry, retrieve its
        // parent Apk, and check the package name/target packages. We cannot simply use the first
        // Apk we see, as we may have been passed the contents of a split.
        for (ApkEntry entry : apkEntries) {
            if (packageName == null) {
                packageName = entry.apk.packageName;
            }

            if (!entry.apk.packageName.equals(packageName)) {
                // This is intentionally a swap failure, not a dump failure; we just discover it during dump.
                throw DeployerException.swapMultiplePackages();
            }

            targetPackages.addAll(entry.apk.targetPackages);
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

        return new Dump(GetApkEntries(response.getPackages(0)), GetPids(response));
    }

    public static class Dump {
        public final List<ApkEntry> apkEntries;
        public final Map<String, List<Integer>> packagePids;

        public Dump(List<ApkEntry> apkEntries, Map<String, List<Integer>> packagePids) {
            this.apkEntries = apkEntries;
            this.packagePids = packagePids;
        }
    }

    private static List<ApkEntry> GetApkEntries(Deploy.PackageDump packageDump) {
        List<ApkEntry> dumps = new ArrayList<>();
        for (Deploy.ApkDump dump : packageDump.getApksList()) {
            ByteBuffer cd = dump.getCd().asReadOnlyByteBuffer();
            ByteBuffer signature = dump.getSignature().asReadOnlyByteBuffer();
            HashMap<String, ZipUtils.ZipEntry> zipEntries = ZipUtils.readZipEntries(cd);
            cd.rewind();
            String digest = ZipUtils.digest(signature.remaining() != 0 ? signature : cd);
            Apk apk =
                    Apk.builder()
                            .setName(dump.getName())
                            .setChecksum(digest)
                            .setPath(dump.getAbsolutePath())
                            .setZipEntries(zipEntries)
                            .build();
            for (Map.Entry<String, ZipUtils.ZipEntry> entry : zipEntries.entrySet()) {
                dumps.add(new ApkEntry(entry.getKey(), entry.getValue().crc, apk));
            }
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
}
