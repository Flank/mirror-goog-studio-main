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

import static com.google.common.truth.Truth.*;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.tools.deploy.proto.Deploy;
import com.android.tools.deployer.model.Apk;
import com.android.utils.ILogger;
import com.android.utils.NullLogger;
import com.android.zipflinger.BytesSource;
import com.android.zipflinger.ZipArchive;
import com.android.zipflinger.ZipInfo;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class OptimisticApkInstallerTest {

    private static final String TEST_ABI = "x86_64";
    private static final String TEST_PACKAGE = "test-package";
    private static final String TEST_SERIAL = "test-serial";

    private static final DeployerOption IWI_ON =
            new DeployerOption.Builder()
                    .setUseOptimisticSwap(true)
                    .setOptimisticInstallSupport(EnumSet.allOf(ChangeType.class))
                    .build();

    private static final DeployerOption IWI_OFF =
            new DeployerOption.Builder()
                    .setUseOptimisticSwap(true)
                    .setOptimisticInstallSupport(EnumSet.noneOf(ChangeType.class))
                    .build();

    private AdbClient adb;
    private Installer installer;
    private DeploymentCacheDatabase cache;
    private MetricsRecorder metrics;
    private ILogger logger;

    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Rule public ExpectedException thrown = ExpectedException.none();

    @Before
    public void beforeTest() throws IOException {
        IDevice device = Mockito.mock(IDevice.class);
        when(device.getSerialNumber()).thenReturn(TEST_SERIAL);
        when(device.getAbis()).thenReturn(ImmutableList.of(TEST_ABI));

        installer = Mockito.mock(Installer.class);
        when(installer.overlayInstall(ArgumentMatchers.any()))
                .thenReturn(
                        Deploy.OverlayInstallResponse.newBuilder()
                                .setStatus(Deploy.OverlayInstallResponse.Status.OK)
                                .build());

        adb = new AdbClient(device, logger);
        cache = new DeploymentCacheDatabase(DeploymentCacheDatabase.DEFAULT_SIZE);
        metrics = new MetricsRecorder();
        logger = new NullLogger();
    }

    @Test
    public void addRemoveOverlayFile() throws IOException, DeployerException {
        OptimisticApkInstaller apkInstaller =
                new OptimisticApkInstaller(installer, adb, cache, metrics, IWI_ON, logger);
        // Populate the cache. To prevent us from having to mock dump, we create a cache entry with
        // an empty overlay, which prevents the cache entry from being treated as a base install.
        Apk installedApk =
                buildApk(
                        "base",
                        "0",
                        ImmutableMap.of(
                                "file1", "0",
                                "file2", "1"));
        OverlayId baseId = OverlayId.builder(new OverlayId(ImmutableList.of(installedApk))).build();
        cache.store(TEST_SERIAL, TEST_PACKAGE, ImmutableList.of(installedApk), baseId);

        // Test adding two new files and modifying an existing one.
        Apk nextApk =
                buildApk(
                        "base",
                        "1",
                        ImmutableMap.of(
                                "file1", "0",
                                "file2", "99",
                                "file3", "2",
                                "file4", "2"));
        OverlayId nextId = apkInstaller.install(TEST_PACKAGE, ImmutableList.of(nextApk));
        assertOverlay(nextId, "base/file2", "base/file3", "base/file4");
        cache.store(TEST_SERIAL, TEST_PACKAGE, ImmutableList.of(nextApk), nextId);

        // Test modifying installed files and files that only exist in the overlay.
        nextApk =
                buildApk(
                        "base",
                        "2",
                        ImmutableMap.of(
                                "file1", "99",
                                "file2", "99",
                                "file3", "99",
                                "file4", "99"));
        nextId = apkInstaller.install(TEST_PACKAGE, ImmutableList.of(nextApk));
        assertOverlay(nextId, "base/file1", "base/file2", "base/file3", "base/file4");
        cache.store(TEST_SERIAL, TEST_PACKAGE, ImmutableList.of(nextApk), nextId);

        // Test undoing changes to installed files and overlay files, and removing overlay files.
        nextApk =
                buildApk(
                        "base",
                        "3",
                        ImmutableMap.of(
                                "file1", "0",
                                "file2", "1",
                                "file4", "2"));
        nextId = apkInstaller.install(TEST_PACKAGE, ImmutableList.of(nextApk));
        assertOverlay(nextId, "base/file1", "base/file2", "base/file4");
    }

    @Test
    public void deleteInstalledFile() throws IOException, DeployerException {
        OptimisticApkInstaller apkInstaller =
                new OptimisticApkInstaller(installer, adb, cache, metrics, IWI_ON, logger);
        // Populate the cache. To prevent us from having to mock dump, we create a cache entry with
        // an empty overlay, which prevents the cache entry from being treated as a base install.
        Apk installedApk =
                buildApk(
                        "base",
                        "0",
                        ImmutableMap.of(
                                "file1", "0",
                                "file2", "1"));
        OverlayId baseId = OverlayId.builder(new OverlayId(ImmutableList.of(installedApk))).build();
        cache.store(TEST_SERIAL, TEST_PACKAGE, ImmutableList.of(installedApk), baseId);

        // Test adding two new files and modifying an existing one.
        Apk nextApk =
                buildApk(
                        "base",
                        "1",
                        ImmutableMap.of(
                                "file1", "0",
                                "file2", "99",
                                "file3", "2",
                                "file4", "2"));
        OverlayId nextId = apkInstaller.install(TEST_PACKAGE, ImmutableList.of(nextApk));
        assertOverlay(nextId, "base/file2", "base/file3", "base/file4");
        cache.store(TEST_SERIAL, TEST_PACKAGE, ImmutableList.of(nextApk), nextId);

        // Test deleting an installed file.
        nextApk =
                buildApk(
                        "base",
                        "2",
                        ImmutableMap.of(
                                "file1", "0",
                                "file4", "2"));
        thrown.expect(DeployerException.class);
        apkInstaller.install(TEST_PACKAGE, ImmutableList.of(nextApk));
    }

    @Test
    public void iwiDisabled() throws IOException, DeployerException {
        OptimisticApkInstaller apkInstaller =
                new OptimisticApkInstaller(installer, adb, cache, metrics, IWI_OFF, logger);
        // Populate the cache. To prevent us from having to mock dump, we create a cache entry with
        // an empty overlay, which prevents the cache entry from being treated as a base install.
        Apk installedApk =
                buildApk(
                        "base",
                        "0",
                        ImmutableMap.of(
                                "file1", "0",
                                "file2", "1"));
        OverlayId baseId = OverlayId.builder(new OverlayId(ImmutableList.of(installedApk))).build();
        cache.store(TEST_SERIAL, TEST_PACKAGE, ImmutableList.of(installedApk), baseId);

        // Test that we throw when IWI is off.
        Apk nextApk =
                buildApk(
                        "base",
                        "1",
                        ImmutableMap.of(
                                "file1", "0",
                                "file2", "99"));
        thrown.expect(DeployerException.class);
        apkInstaller.install(TEST_PACKAGE, ImmutableList.of(nextApk));
    }

    private static void assertOverlay(OverlayId id, String... files) {
        assertThat(id.getOverlayContents().allFiles()).containsExactlyElementsIn(files);
    }

    private Apk buildApk(String name, String checksum, Map<String, String> files)
            throws IOException {
        Path apkFile = folder.getRoot().toPath().resolve(name + "-" + checksum);

        ZipArchive zip = new ZipArchive(apkFile);
        for (Map.Entry<String, String> entry : files.entrySet()) {
            zip.add(
                    new BytesSource(
                            entry.getValue().getBytes(StandardCharsets.UTF_8), entry.getKey(), 0));
        }
        ZipInfo info = zip.closeWithInfo();

        Apk.Builder builder =
                Apk.builder()
                        .setName(name)
                        .setChecksum(checksum)
                        .setPath(apkFile.toAbsolutePath().toString())
                        .addLibraryAbi(TEST_ABI)
                        .setPackageName(TEST_PACKAGE)
                        .setTargetPackages(ImmutableList.of())
                        .setIsolatedServices(ImmutableList.of());

        ByteBuffer buffer = ByteBuffer.wrap(Files.readAllBytes(apkFile));
        buffer.position((int) info.cd.first);
        List<ZipUtils.ZipEntry> entries = ZipUtils.readZipEntries(buffer);
        for (ZipUtils.ZipEntry entry : entries) {
            builder.addApkEntry(entry);
        }

        return builder.build();
    }
}
