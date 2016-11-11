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
package com.android.sdklib.repository.installer;

import static com.android.sdklib.IAndroidTarget.SOURCES;
import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.repository.api.Installer;
import com.android.repository.api.InstallerFactory;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.Uninstaller;
import com.android.repository.impl.installer.BasicInstallerFactory;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.testframework.FakeDownloader;
import com.android.repository.testframework.FakeInstallListenerFactory;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.FakeRepoManager;
import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import junit.framework.TestCase;

/**
 * Tests for {@link SourceInstallListener}
 */
public class SourceInstallListenerTest extends TestCase {

    private static final File ROOT = new File("/sdk");

    public void testSourcePathUpdatedWithInstall() throws Exception {
        MockFileOp fop = new MockFileOp();

        // Create remote package for sources;android-23
        FakePackage remote = new FakePackage("sources;android-23");
        URL archiveUrl = new URL("http://www.example.com/plat23/sources.zip");
        remote.setCompleteUrl(archiveUrl.toString());
        DetailsTypes.SourceDetailsType sourceDetails =
                AndroidSdkHandler.getRepositoryModule().createLatestFactory()
                        .createSourceDetailsType();
        sourceDetails.setApiLevel(23);
        remote.setTypeDetails((TypeDetails) sourceDetails);
        Map<String, RemotePackage> remotes = ImmutableMap.of(remote.getPath(), remote);

        // Create local package for platform;android-23
        FakePackage local = getLocalPlatformPackage(fop);
        Map<String, LocalPackage> locals = ImmutableMap.of(local.getPath(), local);

        FakeRepoManager mgr = new FakeRepoManager(ROOT, new RepositoryPackages(locals, remotes));
        mgr.registerSchemaModule(AndroidSdkHandler.getCommonModule());
        mgr.registerSchemaModule(AndroidSdkHandler.getRepositoryModule());

        // Create the archive and register the URL
        FakeDownloader downloader = new FakeDownloader(fop);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(256);
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("top-level/sources"));
            zos.write("contents".getBytes(Charsets.UTF_8));
            zos.closeEntry();
        }
        ByteArrayInputStream is = new ByteArrayInputStream(baos.toByteArray());
        downloader.registerUrl(archiveUrl, is);

        FakeProgressIndicator progress = new FakeProgressIndicator();

        // Assert that before installation, source path is not set thus deprecated value is returned
        AndroidSdkHandler mockHandler = new AndroidSdkHandler(ROOT, null, fop, mgr);
        IAndroidTarget target = mockHandler.getAndroidTargetManager(progress)
                .getTargetFromHashString("android-23", progress);
        assertNotNull(target);
        assertThat(target.getPath(SOURCES)).isEqualTo("/sdk/platforms/android-23/sources");

        // Install
        InstallerFactory factory = SdkInstallerUtil.findBestInstallerFactory(remote, mockHandler);
        Installer installer = factory.createInstaller(remote, mgr, downloader, fop);
        installer.prepare(progress);
        installer.complete(progress);

        // Assert that source path is updated to correct value
        progress.assertNoErrorsOrWarnings();
        assertThat(target.getPath(SOURCES)).isEqualTo("/sdk/sources/android-23");
    }

    public void testSourcePathUpdatedWithUnInstall() throws Exception {
        MockFileOp fop = new MockFileOp();

        // Create local packages for platform;android-23 and sources;android-23
        Map<String, LocalPackage> locals = new HashMap<>();
        FakePackage localPlatform = getLocalPlatformPackage(fop);
        FakePackage localSource = getLocalSourcePackage(fop);

        locals.put(localPlatform.getPath(), localPlatform);
        locals.put(localSource.getPath(), localSource);

        FakeRepoManager mgr = new FakeRepoManager(ROOT,
                new RepositoryPackages(locals, Collections.emptyMap()));
        mgr.registerSchemaModule(AndroidSdkHandler.getCommonModule());
        mgr.registerSchemaModule(AndroidSdkHandler.getRepositoryModule());

        FakeProgressIndicator progress = new FakeProgressIndicator();

        // Assert that before un-installation, source path is set to correct value
        AndroidSdkHandler mockHandler = new AndroidSdkHandler(ROOT, null, fop, mgr);
        IAndroidTarget target = mockHandler.getAndroidTargetManager(progress)
                .getTargetFromHashString("android-23", progress);
        assertNotNull(target);
        assertThat(target.getPath(SOURCES)).isEqualTo("/sdk/sources/android-23");

        // Uninstall
        BasicInstallerFactory factory = new BasicInstallerFactory();
        factory.setListenerFactory(
                new FakeInstallListenerFactory(new SourceInstallListener(mockHandler)));
        RepositoryPackages pkgs = mgr.getPackages();
        LocalPackage p = pkgs.getLocalPackages().get("sources;android-23");

        assertNotNull(p);
        Uninstaller uninstaller = factory.createUninstaller(p, mgr, fop);
        uninstaller.prepare(progress);
        uninstaller.complete(progress);

        // Assert that source path is set back to null thus deprecated value is returned
        progress.assertNoErrorsOrWarnings();
        assertThat(target.getPath(SOURCES)).isEqualTo("/sdk/platforms/android-23/sources");
    }

    @NonNull
    private FakePackage getLocalPlatformPackage(MockFileOp fop) {
        fop.recordExistingFile("/sdk/platforms/android-23/build.prop", "");
        FakePackage local = new FakePackage("platforms;android-23");
        local.setInstalledPath(new File("/sdk/platforms/android-23"));

        DetailsTypes.PlatformDetailsType platformDetails =
                AndroidSdkHandler.getRepositoryModule().createLatestFactory()
                        .createPlatformDetailsType();
        platformDetails.setApiLevel(23);
        local.setTypeDetails((TypeDetails) platformDetails);
        return local;
    }

    @NonNull
    private FakePackage getLocalSourcePackage(MockFileOp fop) {
        fop.recordExistingFile("/sdk/sources/android-23/build.prop", "");
        FakePackage local = new FakePackage("sources;android-23");
        local.setInstalledPath(new File("/sdk/sources/android-23"));

        DetailsTypes.SourceDetailsType sourceDetails =
                AndroidSdkHandler.getRepositoryModule().createLatestFactory()
                        .createSourceDetailsType();
        sourceDetails.setApiLevel(23);
        local.setTypeDetails((TypeDetails) sourceDetails);
        return local;
    }
}
