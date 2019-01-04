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

    // TODO: Improve this method signature; it's unintuitive what it does.
    public Dump dump(List<ApkEntry> apkEntries) throws DeployerException {
        // The name of the package being swapped (the one that will actually be installed).
        String packageName = null;

        // Additional packages whose processes should be targeted while swapping. Used to account for instrumentation
        // which doesn't run in its own process but instead the processes of other packages.
        HashSet<String> targetPackages = new HashSet<>();

        for (ApkEntry entry : apkEntries) {
            if (packageName == null) {
                packageName = entry.apk.packageName;
            }

            if (!entry.apk.packageName.equals(packageName)) {
                throw new DeployerException(
                        DeployerException.Error.DUMP_FAILED, "Cannot deploy multiple packages");
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
            throw new DeployerException(DeployerException.Error.DUMP_FAILED, e);
        }

        // TODO: To throw an exception here makes this component hard to re-use.
        // The component using it should be the one to check apkEntries.size() and throw
        // and exception if necessary. This check should be moved further down the
        // pipeline.
        if (response.getStatus() == Deploy.DumpResponse.Status.ERROR_PACKAGE_NOT_FOUND) {
            throw new DeployerException(
                    DeployerException.Error.DUMP_UNKNOWN_PACKAGE,
                    "Cannot list apks for package "
                            + response.getFailedPackage()
                            + ". Is the app installed?");
        }

        return new Dump(GetApkEntries(response.getPackages(0)), GetPids(response));
    }

    public static class Dump {
        public final List<ApkEntry> apkEntries;
        public final Map<String, List<Integer>> pids;

        public Dump(List<ApkEntry> apkEntries, Map<String, List<Integer>> pids) {
            this.apkEntries = apkEntries;
            this.pids = pids;
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
