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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.android.annotations.NonNull;
import com.android.repository.Revision;
import com.android.repository.api.License;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.manager.RemoteRepoLoader;
import com.android.repository.impl.manager.RepoManagerImpl;
import com.android.repository.impl.meta.CommonFactory;
import com.android.repository.testframework.FakeDependency;
import com.android.repository.testframework.FakeDownloader;
import com.android.repository.testframework.FakeLoader;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.FakeRepositorySourceProvider;
import com.android.repository.testframework.FakeSettingsController;
import com.android.repository.testframework.MockFileOp;
import com.android.repository.util.InstallerUtil;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.google.common.base.Charsets;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Tests for {@link SdkManagerCli}
 */
public class SdkManagerCliTest {

    private static final String SDK_LOCATION = "/sdk";

    private MockFileOp mFileOp;
    private AndroidSdkHandler mSdkHandler;
    private FakeDownloader mDownloader;
    private FileSystem mFileSystem;

    @Before
    public void setUp() throws Exception {
        mFileOp = new MockFileOp();
        mFileSystem = mFileOp.getFileSystem();
        mDownloader = new FakeDownloader(mFileOp);

        RemoteRepoLoader loader = createRemoteRepo();

        RepoManager repoManager = new RepoManagerImpl(mFileOp, null, progress -> loader);
        repoManager.setLocalPath(new File(SDK_LOCATION));

        createLocalRepo(repoManager);

        mSdkHandler = new AndroidSdkHandler(new File(SDK_LOCATION), mFileOp, repoManager);

        // Doesn't actually need to provide anything, since the remote loader gets them directly.
        repoManager.registerSourceProvider(new FakeRepositorySourceProvider(null));

        repoManager.loadSynchronously(0, new FakeProgressIndicator(), mDownloader,
                new FakeSettingsController(false));
    }

    private void createLocalRepo(@NonNull RepoManager repoManager) throws IOException {
        Path root = mFileSystem.getPath(SDK_LOCATION);
        ProgressIndicator progress = new FakeProgressIndicator();
        Files.createDirectories(root);
        FakePackage installed = new FakePackage("test;p1");
        installed.setDisplayName("package 1");
        Path p1Path = root.resolve("test/p1");
        Files.createDirectories(p1Path);
        InstallerUtil.writePackageXml(installed, new File(p1Path.toString()), repoManager, mFileOp,
                progress);
        installed = new FakePackage("upgrade");
        installed.setDisplayName("upgrade v1");
        Files.createDirectories(root.resolve("upgrade"));
        InstallerUtil.writePackageXml(installed, new File(SDK_LOCATION, "upgrade"), repoManager,
                mFileOp, progress);
        installed = new FakePackage("obsolete");
        installed.setDisplayName("obsolete local");
        installed.setObsolete(true);
        Files.createDirectories(root.resolve("obsolete"));
        InstallerUtil.writePackageXml(installed, new File(SDK_LOCATION, "obsolete"), repoManager,
                mFileOp, progress);
    }

    @NonNull
    private RemoteRepoLoader createRemoteRepo() throws IOException {
        CommonFactory factory = RepoManager.getCommonModule().createLatestFactory();
        License license = factory.createLicenseType("my license", "lic1");
        License license2 = factory.createLicenseType("my license 2", "lic2");

        Map<String, RemotePackage> remotes = new HashMap<>();
        FakePackage remote1 = new FakePackage("test;remote1");
        remote1.setLicense(license);
        String archiveUrl = "http://www.example.com/package1";
        remote1.setCompleteUrl(archiveUrl);
        remotes.put(remote1.getPath(), remote1);

        FakePackage upgrade = new FakePackage("upgrade");
        upgrade.setRevision(new Revision(2));
        upgrade.setDisplayName("upgrade v2");
        upgrade.setCompleteUrl(archiveUrl);
        remotes.put(upgrade.getPath(), upgrade);

        FakePackage obsoleteRemote = new FakePackage("obsolete");
        obsoleteRemote.setRevision(new Revision(2));
        obsoleteRemote.setDisplayName("obsolete package");
        obsoleteRemote.setLicense(license2);
        obsoleteRemote.setObsolete(true);
        obsoleteRemote.setCompleteUrl(archiveUrl);
        remotes.put(obsoleteRemote.getPath(), obsoleteRemote);

        FakePackage dependsOn = new FakePackage("depends_on");
        dependsOn.setLicense(license);
        dependsOn.setDependencies(Collections.singletonList(new FakeDependency("depended_on")));
        dependsOn.setCompleteUrl(archiveUrl);
        remotes.put(dependsOn.getPath(), dependsOn);

        FakePackage dependedOn = new FakePackage("depended_on");
        dependedOn.setLicense(license2);
        dependedOn.setCompleteUrl(archiveUrl);
        remotes.put(dependedOn.getPath(), dependedOn);

        ByteArrayOutputStream baos = new ByteArrayOutputStream(1000);
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("top-level/a"));
            zos.write("contents1".getBytes(Charsets.UTF_8));
            zos.closeEntry();
        }
        ByteArrayInputStream is = new ByteArrayInputStream(baos.toByteArray());
        mDownloader.registerUrl(new URL(archiveUrl), is);

        return new FakeLoader<>(remotes);
    }

    /**
     * List the packages we have installed and available.
     */
    @Test
    public void basicList() throws Exception {
        SdkManagerCli.Settings settings = SdkManagerCli.Settings
                .createSettings(new String[]{"--list", "/sdk"}, mFileOp.getFileSystem());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SdkManagerCli downloader = new SdkManagerCli(settings,
                new PrintStream(out),
                null,
                mDownloader,
                mSdkHandler);
        downloader.run();
        String expected = "Installed packages:\n"
                + "  Path    | Version | Description | Location\n"
                + "  ------- | ------- | -------     | ------- \n"
                + "  test;p1 | 1       | package 1   | test/p1 \n"
                + "  upgrade | 1       | upgrade v1  | upgrade \n"
                + "\n"
                + "Available Packages:\n"
                + "  Path         | Version | Description \n"
                + "  -------      | ------- | -------     \n"
                + "  depended_on  | 1       | fake package\n"
                + "  depends_on   | 1       | fake package\n"
                + "  test;remote1 | 1       | fake package\n"
                + "  upgrade      | 2       | upgrade v2  \n"
                + "\n"
                + "Available Updates:\n"
                + "  ID      | Installed | Available\n"
                + "  ------- | -------   | -------  \n"
                + "  upgrade | 1         | 2        \n";
        assertEquals(expected, out.toString());
    }

    /**
     * Verify that the --channel sets us up with the right channel.
     */
    @Test
    public void channel() throws Exception {
        SdkManagerCli.Settings settings = SdkManagerCli.Settings
                .createSettings(new String[]{"--list", "--channel=1", "/sdk"},
                        mFileOp.getFileSystem());
        assertEquals("channel-1", settings.getChannel().getId());
    }

    /**
     * List packages including obsolete.
     */
    @Test
    public void obsoleteList() throws Exception {
        SdkManagerCli.Settings settings = SdkManagerCli.Settings
                .createSettings(new String[]{"--list", "--include_obsolete", "/sdk"},
                        mFileOp.getFileSystem());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SdkManagerCli downloader = new SdkManagerCli(settings,
                new PrintStream(out),
                null,
                mDownloader,
                mSdkHandler);
        downloader.run();
        String expected = "Installed packages:\n"
                + "  Path    | Version | Description | Location\n"
                + "  ------- | ------- | -------     | ------- \n"
                + "  test;p1 | 1       | package 1   | test/p1 \n"
                + "  upgrade | 1       | upgrade v1  | upgrade \n"
                + "\n"
                + "Installed Obsolete Packages:\n"
                + "  Path     | Version | Description    | Location\n"
                + "  -------  | ------- | -------        | ------- \n"
                + "  obsolete | 1       | obsolete local | obsolete\n"
                + "\n"
                + "Available Packages:\n"
                + "  Path         | Version | Description \n"
                + "  -------      | ------- | -------     \n"
                + "  depended_on  | 1       | fake package\n"
                + "  depends_on   | 1       | fake package\n"
                + "  test;remote1 | 1       | fake package\n"
                + "  upgrade      | 2       | upgrade v2  \n"
                + "\n"
                + "Available Obsolete Packages:\n"
                + "  Path     | Version | Description     \n"
                + "  -------  | ------- | -------         \n"
                + "  obsolete | 2       | obsolete package\n"
                + "\n"
                + "Available Updates:\n"
                + "  ID       | Installed | Available\n"
                + "  -------  | -------   | -------  \n"
                + "  obsolete | 1         | 2        \n"
                + "  upgrade  | 1         | 2        \n";
        assertEquals(expected, out.toString());

    }

    /**
     * Install a package.
     */
    @Test
    public void basicInstall() throws Exception {
        SdkManagerCli.Settings settings = SdkManagerCli.Settings
                .createSettings(new String[]{"/sdk", "test;remote1"},
                        mFileOp.getFileSystem());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FakeProgressIndicator progress = new FakeProgressIndicator();
        assertNull(mSdkHandler.getLocalPackage("test;remote1", progress));
        SdkManagerCli downloader = new SdkManagerCli(settings,
                new PrintStream(out),
                new ByteArrayInputStream("y\n".getBytes()),
                mDownloader,
                mSdkHandler);
        downloader.run();
        mSdkHandler.getSdkManager(progress).reloadLocalIfNeeded(progress);
        assertNotNull(mSdkHandler.getLocalPackage("test;remote1",
                progress));
    }

    /**
     * Install several packages, including packages that depend on others.
     */
    @Test
    public void multiInstallWithDeps() throws Exception {
        SdkManagerCli.Settings settings = SdkManagerCli.Settings
                .createSettings(new String[]{"/sdk", "test;remote1", "depends_on"},
                        mFileOp.getFileSystem());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FakeProgressIndicator progress = new FakeProgressIndicator();
        assertNull(mSdkHandler.getLocalPackage("test;remote1", progress));
        SdkManagerCli downloader = new SdkManagerCli(settings,
                new PrintStream(out),
                new ByteArrayInputStream("y\ny\n".getBytes()),
                mDownloader,
                mSdkHandler);
        downloader.run();
        mSdkHandler.getSdkManager(progress).reloadLocalIfNeeded(progress);
        assertNotNull(mSdkHandler.getLocalPackage("test;remote1", progress));
        assertNotNull(mSdkHandler.getLocalPackage("depends_on", progress));
        assertNotNull(mSdkHandler.getLocalPackage("depended_on", progress));
    }

    /**
     * Update packages
     */
    @Test
    public void update() throws Exception {
        SdkManagerCli.Settings settings = SdkManagerCli.Settings
                .createSettings(new String[]{"--update", "/sdk"},
                        mFileOp.getFileSystem());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FakeProgressIndicator progress = new FakeProgressIndicator();
        assertEquals(1,
                mSdkHandler.getLocalPackage("upgrade", progress).getVersion().getMajor());
        SdkManagerCli downloader = new SdkManagerCli(settings,
                new PrintStream(out),
                null,
                mDownloader,
                mSdkHandler);
        downloader.run();
        mSdkHandler.getSdkManager(progress).reloadLocalIfNeeded(progress);
        assertEquals(2,
                mSdkHandler.getLocalPackage("upgrade", progress).getVersion().getMajor());
        assertEquals(1,
                mSdkHandler.getLocalPackage("obsolete", progress).getVersion().getMajor());
        assertEquals(3,
                mSdkHandler.getSdkManager(progress).getPackages().getLocalPackages().size());
    }

    /**
     * Update packages including obsolete packages.
     */
    @Test
    public void updateObsolete() throws Exception {
        SdkManagerCli.Settings settings = SdkManagerCli.Settings
                .createSettings(new String[]{"--update", "--include_obsolete", "/sdk"},
                        mFileOp.getFileSystem());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FakeProgressIndicator progress = new FakeProgressIndicator();
        assertEquals(1,
                mSdkHandler.getLocalPackage("upgrade", progress).getVersion().getMajor());
        SdkManagerCli downloader = new SdkManagerCli(settings,
                new PrintStream(out),
                new ByteArrayInputStream("y\n".getBytes()),
                mDownloader,
                mSdkHandler);
        downloader.run();
        mSdkHandler.getSdkManager(progress).reloadLocalIfNeeded(progress);
        assertEquals(2,
                mSdkHandler.getLocalPackage("upgrade", progress).getVersion().getMajor());
        assertEquals(2,
                mSdkHandler.getLocalPackage("obsolete", progress).getVersion().getMajor());
        assertEquals(3,
                mSdkHandler.getSdkManager(progress).getPackages().getLocalPackages().size());
    }

    /**
     * Uninstall a package.
     */
    @Test
    public void uninstall() throws Exception {
        SdkManagerCli.Settings settings = SdkManagerCli.Settings
                .createSettings(new String[]{"--uninstall", "/sdk", "obsolete"},
                        mFileOp.getFileSystem());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FakeProgressIndicator progress = new FakeProgressIndicator();
        assertEquals(3,
                mSdkHandler.getSdkManager(progress).getPackages().getLocalPackages().size());
        assertNotNull(mSdkHandler.getLocalPackage("obsolete", progress));
        SdkManagerCli downloader = new SdkManagerCli(settings,
                new PrintStream(out),
                null,
                mDownloader,
                mSdkHandler);
        downloader.run();
        mSdkHandler.getSdkManager(progress).reloadLocalIfNeeded(progress);
        assertNull(mSdkHandler.getLocalPackage("obsolete", progress));
        assertEquals(2,
                mSdkHandler.getSdkManager(progress).getPackages().getLocalPackages().size());
    }

    /**
     * Uninstall multiple packages.
     */
    @Test
    public void multiUninstall() throws Exception {
        SdkManagerCli.Settings settings = SdkManagerCli.Settings
                .createSettings(new String[]{"--uninstall", "/sdk", "obsolete", "upgrade"},
                        mFileOp.getFileSystem());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FakeProgressIndicator progress = new FakeProgressIndicator();
        assertEquals(3,
                mSdkHandler.getSdkManager(progress).getPackages().getLocalPackages().size());
        SdkManagerCli downloader = new SdkManagerCli(settings,
                new PrintStream(out),
                null,
                mDownloader,
                mSdkHandler);
        downloader.run();
        mSdkHandler.getSdkManager(progress).reloadLocalIfNeeded(progress);
        assertNull(mSdkHandler.getLocalPackage("obsolete", progress));
        assertNull(mSdkHandler.getLocalPackage("upgrade", progress));
        assertNotNull(mSdkHandler.getLocalPackage("test;p1", progress));
        assertEquals(1,
                mSdkHandler.getSdkManager(progress).getPackages().getLocalPackages().size());

    }

    /**
     * Verify that not accepting a license results in the package not being installed.
     */
    @Test
    public void acceptOrRejectLicense() throws Exception {
        SdkManagerCli.Settings settings = SdkManagerCli.Settings
                .createSettings(new String[]{"/sdk", "depended_on"},
                        mFileOp.getFileSystem());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FakeProgressIndicator progress = new FakeProgressIndicator();
        assertNull(mSdkHandler.getLocalPackage("depended_on", progress));
        SdkManagerCli downloader = new SdkManagerCli(settings,
                new PrintStream(out),
                new ByteArrayInputStream("foo\n".getBytes()),
                mDownloader,
                mSdkHandler);
        downloader.run();
        mSdkHandler.getSdkManager(progress).reloadLocalIfNeeded(progress);
        assertNull(mSdkHandler.getLocalPackage("depended_on",
                progress));

        downloader = new SdkManagerCli(settings,
                new PrintStream(out),
                new ByteArrayInputStream("y\n".getBytes()),
                mDownloader,
                mSdkHandler);
        downloader.run();
        mSdkHandler.getSdkManager(progress).reloadLocalIfNeeded(progress);
        assertNotNull(mSdkHandler.getLocalPackage("depended_on",
                progress));
    }

    /**
     * Not accepting the license of a package that's depended on results in the depending package
     * not being installed either.
     */
    @Test
    public void rejectLicenseWithDeps() throws Exception {
        SdkManagerCli.Settings settings = SdkManagerCli.Settings
                .createSettings(new String[]{"/sdk", "depends_on", "test;remote1"},
                        mFileOp.getFileSystem());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FakeProgressIndicator progress = new FakeProgressIndicator();
        // Accept depending license but not depended-on license, then continue.
        SdkManagerCli downloader = new SdkManagerCli(settings,
                new PrintStream(out),
                new ByteArrayInputStream("y\nn\ny\n".getBytes()),
                mDownloader,
                mSdkHandler);
        downloader.run();
        mSdkHandler.getSdkManager(progress).reloadLocalIfNeeded(progress);
        assertNull(mSdkHandler.getLocalPackage("depended_on",
                progress));
        assertNull(mSdkHandler.getLocalPackage("depends_on",
                progress));
        assertNotNull(mSdkHandler.getLocalPackage("test;remote1",
                progress));
    }
}
