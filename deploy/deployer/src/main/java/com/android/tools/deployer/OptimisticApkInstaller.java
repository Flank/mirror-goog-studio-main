/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.tools.deployer.model.FileDiff;
import com.android.tools.idea.protobuf.ByteString;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

class OptimisticApkInstaller {

    private static final Map<String, Deploy.Arch> ABI_MAP =
            ImmutableMap.of(
                    "arm64-v8a", Deploy.Arch.ARCH_64_BIT,
                    "armeabi-v7a", Deploy.Arch.ARCH_32_BIT,
                    "x86_64", Deploy.Arch.ARCH_64_BIT,
                    "x86", Deploy.Arch.ARCH_32_BIT);

    private static final String DUMP_METRIC = "IWI_INSTALL_DUMP";
    private static final String DIFF_METRIC = "IWI_INSTALL_DIFF";
    private static final String EXTRACT_METRIC = "IWI_INSTALL_EXTRACT";
    private static final String UPDATE_METRIC = "IWI_INSTALL_UPDATE_OVERLAYS";

    private final Installer installer;
    private final AdbClient adb;
    private final DeploymentCacheDatabase cache;
    private final MetricsRecorder metrics;
    private final DeployerOption options;
    private final ILogger logger;

    public OptimisticApkInstaller(
            Installer installer,
            AdbClient adb,
            DeploymentCacheDatabase cache,
            MetricsRecorder metrics,
            DeployerOption options,
            ILogger logger) {
        this.installer = installer;
        this.adb = adb;
        this.cache = cache;
        this.metrics = metrics;
        this.options = options;
        this.logger = logger;
    }

    public OverlayId install(String packageName, List<Apk> apks) throws DeployerException {
        try {
            return tracedInstall(packageName, apks);
        } catch (DeployerException ex) {
            metrics.finish(ex.getError());
            throw ex;
        } catch (Exception ex) {
            DeployerException wrapper = DeployerException.runtimeException(ex);
            metrics.finish(wrapper.getError());
            throw wrapper;
        }
    }

    private OverlayId tracedInstall(String packageName, List<Apk> apks) throws DeployerException {
        final String deviceSerial = adb.getSerial();
        final Deploy.Arch targetArch = getArch(apks);

        metrics.start(DUMP_METRIC);
        DeploymentCacheDatabase.Entry entry = cache.get(deviceSerial, packageName);

        // If we have no cache data or an install without OID file, we use the classic dump.
        if (entry == null || entry.getOverlayId().isBaseInstall()) {
            ApplicationDumper dumper = new ApplicationDumper(installer);
            List<Apk> deviceApks = dumper.dump(apks).apks;
            cache.store(deviceSerial, packageName, deviceApks, new OverlayId(deviceApks));
            entry = cache.get(deviceSerial, packageName);
        }
        metrics.finish();

        metrics.start(DIFF_METRIC);

        // Quick and dirty fallback on to normal install if we see a swap has happened.
        // TODO: Remove this when we properly diff w/ overlays.
        if (!entry.getOverlayId().getSwappedDexFiles().isEmpty()) {
            throw DeployerException.runAfterSwapNotSupported();
        }
        List<FileDiff> diffs = new ApkDiffer().specDiff(entry, apks);

        // Ensure that we currently have IWI support enabled for every change; otherwise, throw an
        // exception to cause a fallback to delta install. This has one exception - if a diff is
        // present only because a resource is absent from the overlay, and resource support is
        // disabled, we can simply throw away the diff.
        List<FileDiff> filteredDiffs = new ArrayList<>();
        for (FileDiff diff : diffs) {
            ChangeType type = ChangeType.getType(diff);
            if (options.optimisticInstallSupport.contains(type)) {
                filteredDiffs.add(diff);
            } else if (diff.status != FileDiff.Status.RESOURCE_NOT_IN_OVERLAY) {
                logger.warning("File '%s' [%s] is not supported by IWI", diff.getName(), type);
                throw DeployerException.changeNotSupportedByIWI(type);
            }
        }
        metrics.finish();

        metrics.start(EXTRACT_METRIC);
        Map<ApkEntry, ByteString> overlayFiles = new ApkEntryExtractor().extract(filteredDiffs);
        metrics.finish();

        metrics.start(UPDATE_METRIC);
        final OverlayId overlayId = entry.getOverlayId();
        final OverlayId.Builder nextIdBuilder = OverlayId.builder(overlayId);

        Deploy.OverlayInstallRequest.Builder request =
                Deploy.OverlayInstallRequest.newBuilder()
                        .setPackageName(packageName)
                        .setArch(targetArch)
                        .setExpectedOverlayId(overlayId.isBaseInstall() ? "" : overlayId.getSha());

        for (Map.Entry<ApkEntry, ByteString> file : overlayFiles.entrySet()) {
            request.addOverlayFiles(
                    Deploy.OverlayFile.newBuilder()
                            .setPath(file.getKey().getQualifiedPath())
                            .setContent(file.getValue()));
            nextIdBuilder.addOverlayFile(
                    file.getKey().getQualifiedPath(), file.getKey().getChecksum());
        }

        for (FileDiff diff : diffs) {
            if (diff.status == FileDiff.Status.DELETED) {
                request.addDeletedFiles(diff.oldFile.getQualifiedPath());
                nextIdBuilder.removeOverlayFile(diff.oldFile.getQualifiedPath());
            }
        }

        // Clear out any swapped dex from the overlay directory, since they will override the dex
        // inside the exploded APKs.
        for (String dexFile : overlayId.getSwappedDexFiles()) {
            request.addDeletedFiles(dexFile);
        }

        OverlayId nextOverlayId = nextIdBuilder.build();
        request.setOverlayId(nextOverlayId.getSha());

        Deploy.OverlayInstallResponse response;
        try {
            response = installer.overlayInstall(request.build());
        } catch (IOException ex) {
            throw DeployerException.installerIoException(ex);
        }

        if (response.getStatus() != Deploy.OverlayInstallResponse.Status.OK) {
            throw DeployerException.installFailed(response.getStatus(), "Overlay update failed");
        }

        metrics.finish();
        metrics.add(response.getAgentLogsList());

        return nextOverlayId;
    }

    // The application will run with the most-preferred device ABI that the application also
    // natively supports. An application with no native libraries automatically runs with the
    // most-preferred device ABI.
    private Deploy.Arch getArch(List<Apk> apks) throws DeployerException {
        HashSet<String> appSupported = new HashSet<>();
        for (Apk apk : apks) {
            appSupported.addAll(apk.libraryAbis);
        }

        List<String> deviceSupported = adb.getAbis();
        if (deviceSupported.isEmpty()) {
            throw DeployerException.unsupportedArch();
        }

        // No native libraries means we use the first device-preferred ABI.
        if (appSupported.isEmpty()) {
            String abi = deviceSupported.get(0);
            return ABI_MAP.get(abi);
        }

        for (String abi : deviceSupported) {
            if (appSupported.contains(abi)) {
                return ABI_MAP.get(abi);
            }
        }

        throw DeployerException.unsupportedArch();
    }
}
