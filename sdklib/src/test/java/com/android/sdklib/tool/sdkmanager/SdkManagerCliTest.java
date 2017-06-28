/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.sdklib.tool.sdkmanager;

import static com.android.repository.testframework.FakePackage.FakeRemotePackage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.repository.Revision;
import com.android.repository.api.License;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.manager.RemoteRepoLoader;
import com.android.repository.impl.manager.RepoManagerImpl;
import com.android.repository.impl.meta.CommonFactory;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.testframework.FakeDependency;
import com.android.repository.testframework.FakeDownloader;
import com.android.repository.testframework.FakeLoader;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.FakeRepoManager;
import com.android.repository.testframework.FakeRepositorySourceProvider;
import com.android.repository.testframework.FakeSettingsController;
import com.android.repository.testframework.MockFileOp;
import com.android.repository.util.InstallerUtil;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
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
import org.junit.Before;
import org.junit.Test;

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

        mSdkHandler =
                new AndroidSdkHandler(
                        new File(SDK_LOCATION),
                        null,
                        mFileOp,
                        repoManager);

        // Doesn't actually need to provide anything, since the remote loader gets them directly.
        repoManager.registerSourceProvider(new FakeRepositorySourceProvider(null));

        repoManager.loadSynchronously(0, new FakeProgressIndicator(), mDownloader,
                new FakeSettingsController(false));
    }

    private void createLocalRepo(@NonNull RepoManager repoManager) throws IOException {
        Path root = mFileSystem.getPath(SDK_LOCATION);
        ProgressIndicator progress = new FakeProgressIndicator();
        Files.createDirectories(root);
        FakeRemotePackage installed = new FakeRemotePackage("test;p1");
        installed.setDisplayName("package 1");
        Path p1Path = root.resolve("test/p1");
        Files.createDirectories(p1Path);
        InstallerUtil.writePackageXml(installed, new File(p1Path.toString()), repoManager, mFileOp,
                progress);
        installed = new FakeRemotePackage("upgrade");
        installed.setDisplayName("upgrade v1");
        Files.createDirectories(root.resolve("upgrade"));
        InstallerUtil.writePackageXml(
                installed, new File(SDK_LOCATION, "upgrade"), repoManager, mFileOp, progress);
        installed = new FakeRemotePackage("obsolete");
        installed.setDisplayName("obsolete local");
        installed.setObsolete(true);
        Files.createDirectories(root.resolve("obsolete"));
        InstallerUtil.writePackageXml(
                installed, new File(SDK_LOCATION, "obsolete"), repoManager, mFileOp, progress);
    }

    @NonNull
    private RemoteRepoLoader createRemoteRepo() throws IOException {
        CommonFactory factory = RepoManager.getCommonModule().createLatestFactory();
        License license = factory.createLicenseType("my license", "lic1");
        License license2 = factory.createLicenseType("my license 2", "lic2");

        Map<String, RemotePackage> remotes = new HashMap<>();
        FakeRemotePackage remote1 = new FakeRemotePackage("test;remote1");
        remote1.setLicense(license);
        String archiveUrl = "http://www.example.com/package1";
        remote1.setCompleteUrl(archiveUrl);
        remotes.put(remote1.getPath(), remote1);

        FakeRemotePackage upgrade = new FakeRemotePackage("upgrade");
        upgrade.setRevision(new Revision(2));
        upgrade.setDisplayName("upgrade v2");
        upgrade.setCompleteUrl(archiveUrl);
        remotes.put(upgrade.getPath(), upgrade);

        FakeRemotePackage obsoleteRemote = new FakeRemotePackage("obsolete");
        obsoleteRemote.setRevision(new Revision(2));
        obsoleteRemote.setDisplayName("obsolete package");
        obsoleteRemote.setLicense(license2);
        obsoleteRemote.setObsolete(true);
        obsoleteRemote.setCompleteUrl(archiveUrl);
        remotes.put(obsoleteRemote.getPath(), obsoleteRemote);

        FakeRemotePackage dependsOn = new FakeRemotePackage("depends_on");
        dependsOn.setLicense(license);
        dependsOn.setDependencies(Collections.singletonList(new FakeDependency("depended_on")));
        dependsOn.setCompleteUrl(archiveUrl);
        remotes.put(dependsOn.getPath(), dependsOn);

        FakeRemotePackage dependedOn = new FakeRemotePackage("depended_on");
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
        SdkManagerCliSettings settings =
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--list", "--sdk_root=/sdk"), mFileOp.getFileSystem());
        assertNotNull("Arguments should be valid", settings);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SdkManagerCli downloader = new SdkManagerCli(settings,
                new PrintStream(out),
                null,
                mDownloader,
                mSdkHandler);
        downloader.run(new FakeProgressIndicator());
        String expected =
                "Installed packages:\n"
                        + "  Path    | Version | Description | Location\n"
                        + "  ------- | ------- | -------     | ------- \n"
                        + "  test;p1 | 1       | package 1   | test"
                        + File.separator
                        + "p1 \n"
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
        assertEquals(expected, out.toString().replaceAll("\\r\\n", "\n"));
    }

    /** Verify that long names print correctly */
    @Test
    public void listWithLongName() throws Exception {
        FakeRemotePackage installed =
                new FakeRemotePackage(
                        "test;p2;which;has;a;really;long;name;which;should;still;be;displayed");
        installed.setDisplayName(
                "package 2 has a long display name that should still be displayed");
        Path p2Path =
                mFileSystem
                        .getPath(SDK_LOCATION)
                        .resolve("test/p2/is-also/installed-in-a/path-with-a-long-name/");
        Files.createDirectories(p2Path);
        ProgressIndicator progress = new FakeProgressIndicator();
        InstallerUtil.writePackageXml(
                installed,
                new File(p2Path.toString()),
                mSdkHandler.getSdkManager(progress),
                mFileOp,
                progress);

        SdkManagerCliSettings settings =
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--list", "--sdk_root=/sdk"), mFileOp.getFileSystem());
        assertNotNull("Arguments should be valid", settings);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SdkManagerCli downloader =
                new SdkManagerCli(settings, new PrintStream(out), null, mDownloader, mSdkHandler);
        downloader.run(new FakeProgressIndicator());
        String expected =
                "Installed packages:\n"
                        + "  Path                                                                 | Version | Description                                                      | Location                                            \n"
                        + "  -------                                                              | ------- | -------                                                          | -------                                             \n"
                        + "  test;p1                                                              | 1       | package 1                                                        | test/p1                                             \n"
                        + "  test;p2;which;has;a;really;long;name;which;should;still;be;displayed | 1       | package 2 has a long display name that should still be displayed | test/p2/is-also/installed-in-a/path-with-a-long-name\n"
                        + "  upgrade                                                              | 1       | upgrade v1                                                       | upgrade                                             \n"
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
        assertEquals(expected, out.toString().replaceAll("\\r\\n", "\n"));
    }

    /**
     * Verbosely list the packages we have installed and available.
     */
    @Test
    public void verboseList() throws Exception {
        SdkManagerCliSettings settings =
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--list", "--sdk_root=/sdk", "--verbose"),
                        mFileOp.getFileSystem());
        assertNotNull("Arguments should be valid", settings);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SdkManagerCli downloader = new SdkManagerCli(settings,
                new PrintStream(out),
                null,
                mDownloader,
                mSdkHandler);
        downloader.run(new FakeProgressIndicator());
        String expected =
                String.format(
                        "Installed packages:\n"
                                + "--------------------------------------\n"
                                + "test;p1\n"
                                + "    Description:        package 1\n"
                                + "    Version:            1\n"
                                + "    Installed Location: %1$ssdk%1$stest%1$sp1\n"
                                + "\n"
                                + "upgrade\n"
                                + "    Description:        upgrade v1\n"
                                + "    Version:            1\n"
                                + "    Installed Location: %1$ssdk%1$supgrade\n"
                                + "\n"
                                + "Available Packages:\n"
                                + "--------------------------------------\n"
                                + "depended_on\n"
                                + "    Description:        fake package\n"
                                + "    Version:            1\n"
                                + "\n"
                                + "depends_on\n"
                                + "    Description:        fake package\n"
                                + "    Version:            1\n"
                                + "    Dependencies:\n"
                                + "        depended_on\n"
                                + "\n"
                                + "test;remote1\n"
                                + "    Description:        fake package\n"
                                + "    Version:            1\n"
                                + "\n"
                                + "upgrade\n"
                                + "    Description:        upgrade v2\n"
                                + "    Version:            2\n"
                                + "\n"
                                + "Available Updates:\n"
                                + "--------------------------------------\n"
                                + "obsolete\n"
                                + "    Installed Version: 1\n"
                                + "    Available Version: 2\n"
                                + "    (Obsolete)\n"
                                + "upgrade\n"
                                + "    Installed Version: 1\n"
                                + "    Available Version: 2\n",
                        File.separator);
        assertEquals(expected, out.toString().replaceAll("\\r\\n", "\n"));
    }

    /**
     * Verify that the --channel sets us up with the right channel.
     */
    @Test
    public void channel() throws Exception {
        SdkManagerCliSettings settings =
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--list", "--channel=1", "--sdk_root=/sdk"),
                        mFileOp.getFileSystem());
        assertNotNull("Arguments should be valid", settings);
        assertEquals("channel-1", settings.getChannel().getId());
    }

    /**
     * List packages including obsolete.
     */
    @Test
    public void obsoleteList() throws Exception {
        SdkManagerCliSettings settings =
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--list", "--include_obsolete", "--sdk_root=/sdk"),
                        mFileOp.getFileSystem());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertNotNull("Arguments should be valid", settings);
        SdkManagerCli downloader = new SdkManagerCli(settings,
                new PrintStream(out),
                null,
                mDownloader,
                mSdkHandler);
        downloader.run(new FakeProgressIndicator());
        String expected =
                "Installed packages:\n"
                        + "  Path    | Version | Description | Location\n"
                        + "  ------- | ------- | -------     | ------- \n"
                        + "  test;p1 | 1       | package 1   | test"
                        + File.separator
                        + "p1 \n"
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
        assertEquals(
                expected, out.toString().replaceAll("\\r\\n", "\n").replaceAll("\\r\\n", "\n"));

    }

    /**
     * Install a package.
     */
    @Test
    public void basicInstall() throws Exception {
        SdkManagerCliSettings settings =
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--sdk_root=/sdk", "test;remote1"),
                        mFileOp.getFileSystem());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FakeProgressIndicator progress = new FakeProgressIndicator();
        assertNull(mSdkHandler.getLocalPackage("test;remote1", progress));
        assertNotNull("Arguments should be valid", settings);
        SdkManagerCli downloader = new SdkManagerCli(settings,
                new PrintStream(out),
                new ByteArrayInputStream("y\n".getBytes()),
                mDownloader,
                mSdkHandler);
        downloader.run(new FakeProgressIndicator());
        mSdkHandler.getSdkManager(progress).reloadLocalIfNeeded(progress);
        assertNotNull(mSdkHandler.getLocalPackage("test;remote1",
                progress));
    }

    /**
     * Install several packages, including packages that depend on others.
     */
    @Test
    public void multiInstallWithDeps() throws Exception {
        SdkManagerCliSettings settings =
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--sdk_root=/sdk", "test;remote1", "depends_on"),
                        mFileOp.getFileSystem());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FakeProgressIndicator progress = new FakeProgressIndicator();
        assertNull(mSdkHandler.getLocalPackage("test;remote1", progress));
        assertNotNull("Arguments should be valid", settings);
        SdkManagerCli downloader = new SdkManagerCli(settings,
                new PrintStream(out),
                new ByteArrayInputStream("y\ny\n".getBytes()),
                mDownloader,
                mSdkHandler);
        downloader.run(new FakeProgressIndicator(true));
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
        SdkManagerCliSettings settings =
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--update", "--sdk_root=/sdk"), mFileOp.getFileSystem());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FakeProgressIndicator progress = new FakeProgressIndicator();
        assertEquals(1,
                mSdkHandler.getLocalPackage("upgrade", progress).getVersion().getMajor());
        assertNotNull("Arguments should be valid", settings);
        SdkManagerCli downloader = new SdkManagerCli(settings,
                new PrintStream(out),
                null,
                mDownloader,
                mSdkHandler);
        downloader.run(new FakeProgressIndicator());
        mSdkHandler.getSdkManager(progress).reloadLocalIfNeeded(progress);
        assertEquals(2,
                mSdkHandler.getLocalPackage("upgrade", progress).getVersion().getMajor());
        assertEquals(1,
                mSdkHandler.getLocalPackage("obsolete", progress).getVersion().getMajor());
        assertEquals(3,
                mSdkHandler.getSdkManager(progress).getPackages().getLocalPackages().size());
    }


    /**
     * Install a package into a subdirectory of an existing package should fail.
     */
    @Test
    public void basicInstallBroken() throws Exception {
        Path sdk = mFileSystem.getPath(SDK_LOCATION);
        // Move a valid package to the containing directory that the other package will
        // try to be installed in.
        Files.move(sdk.resolve("test/p1"), sdk.resolve("test2"));
        Files.delete(sdk.resolve("test"));
        Files.move(sdk.resolve("test2"), sdk.resolve("test"));

        SdkManagerCliSettings settings =
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--sdk_root=/sdk", "test;remote1"),
                        mFileOp.getFileSystem());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertNull(mSdkHandler.getLocalPackage("test;remote1", new FakeProgressIndicator()));
        assertNotNull("Arguments should be valid", settings);
        SdkManagerCli downloader = new SdkManagerCli(settings,
                new PrintStream(out),
                new ByteArrayInputStream("y\n".getBytes()),
                mDownloader,
                mSdkHandler);
        try {
            downloader.run(new FakeProgressIndicator());
            fail("expected downloader to fail");
        } catch (SdkManagerCli.CommandFailedException ignored) {
        }
        mSdkHandler
                .getSdkManager(new FakeProgressIndicator())
                .reloadLocalIfNeeded(new FakeProgressIndicator(true));
        assertNull(mSdkHandler.getLocalPackage("test;remote1", new FakeProgressIndicator()));
    }

    /**
     * Update packages including obsolete packages.
     */
    @Test
    public void updateObsolete() throws Exception {
        SdkManagerCliSettings settings =
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--update", "--include_obsolete", "--sdk_root=/sdk"),
                        mFileOp.getFileSystem());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FakeProgressIndicator progress = new FakeProgressIndicator();
        assertEquals(1,
                mSdkHandler.getLocalPackage("upgrade", progress).getVersion().getMajor());
        assertNotNull("Arguments should be valid", settings);
        SdkManagerCli downloader = new SdkManagerCli(settings,
                new PrintStream(out),
                new ByteArrayInputStream("y\n".getBytes()),
                mDownloader,
                mSdkHandler);
        downloader.run(new FakeProgressIndicator());
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
        SdkManagerCliSettings settings =
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--uninstall", "--sdk_root=/sdk", "obsolete"),
                        mFileOp.getFileSystem());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FakeProgressIndicator progress = new FakeProgressIndicator();
        assertEquals(3,
                mSdkHandler.getSdkManager(progress).getPackages().getLocalPackages().size());
        assertNotNull(mSdkHandler.getLocalPackage("obsolete", progress));
        assertNotNull("Arguments should be valid", settings);
        SdkManagerCli downloader = new SdkManagerCli(settings,
                new PrintStream(out),
                null,
                mDownloader,
                mSdkHandler);
        downloader.run(new FakeProgressIndicator());
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
        SdkManagerCliSettings settings =
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--uninstall", "--sdk_root=/sdk", "obsolete", "upgrade"),
                        mFileOp.getFileSystem());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FakeProgressIndicator progress = new FakeProgressIndicator(true);
        assertEquals(3,
                mSdkHandler.getSdkManager(progress).getPackages().getLocalPackages().size());
        assertNotNull("Arguments should be valid", settings);
        SdkManagerCli downloader = new SdkManagerCli(settings,
                new PrintStream(out),
                null,
                mDownloader,
                mSdkHandler);
        downloader.run(new FakeProgressIndicator(true));
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
        SdkManagerCliSettings settings =
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--sdk_root=/sdk", "depended_on"),
                        mFileOp.getFileSystem());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FakeProgressIndicator progress = new FakeProgressIndicator();
        assertNull(mSdkHandler.getLocalPackage("depended_on", progress));
        assertNotNull("Arguments should be valid", settings);
        SdkManagerCli downloader = new SdkManagerCli(settings,
                new PrintStream(out),
                new ByteArrayInputStream("foo\n".getBytes()),
                mDownloader,
                mSdkHandler);
        downloader.run(new FakeProgressIndicator());
        mSdkHandler.getSdkManager(progress).reloadLocalIfNeeded(progress);
        assertNull(mSdkHandler.getLocalPackage("depended_on",
                progress));

        downloader = new SdkManagerCli(settings,
                new PrintStream(out),
                new ByteArrayInputStream("y\n".getBytes()),
                mDownloader,
                mSdkHandler);
        downloader.run(new FakeProgressIndicator());
        mSdkHandler.getSdkManager(progress).reloadLocalIfNeeded(progress);
        assertNotNull(mSdkHandler.getLocalPackage("depended_on",
                progress));
    }


    /**
     * Verify the behavior of --licenses
     */
    @Test
    public void licenses() throws Exception {
        SdkManagerCliSettings settings =
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--sdk_root=/sdk", "--licenses"), mFileOp.getFileSystem());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertNotNull("Arguments should be valid", settings);
        new SdkManagerCli(
                        settings,
                        new PrintStream(out),
                        new ByteArrayInputStream("n\n".getBytes()),
                        mDownloader,
                        mSdkHandler)
                .run(new FakeProgressIndicator());

        assertEquals(
                "2 of 2 SDK package licenses not accepted.\n"
                        + "Review licenses that have not been accepted (y/N)? ",
                out.toString().replaceAll("\\r\\n", "\n"));
    }

    /**
     * Verify the behavior of --licenses with --verbose
     */
    @Test
    public void licensesVerbose() throws Exception {
        SdkManagerCliSettings settings =
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--sdk_root=/sdk", "--licenses", "--verbose"),
                        mFileOp.getFileSystem());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertNotNull("Arguments should be valid", settings);
        new SdkManagerCli(
                        settings,
                        new PrintStream(out),
                        new ByteArrayInputStream("n\n".getBytes()),
                        mDownloader,
                        mSdkHandler)
                .run(new FakeProgressIndicator());

        assertEquals(
                "License lic1:\n"
                        + "---------------------------------------\n"
                        + "my license\n"
                        + "---------------------------------------\n"
                        + "Not yet accepted\n"
                        + "\n"
                        + "License lic2:\n"
                        + "---------------------------------------\n"
                        + "my license 2\n"
                        + "---------------------------------------\n"
                        + "Not yet accepted\n"
                        + "\n"
                        + "2 of 2 SDK package licenses not accepted.\n"
                        + "Review licenses that have not been accepted (y/N)? ",
                out.toString().replaceAll("\\r\\n", "\n"));
    }

    /**
     * Verify can accept licenses via --licences
     */
    @Test
    public void acceptLicenses() throws Exception {
        SdkManagerCliSettings settings =
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--sdk_root=/sdk", "--licenses"), mFileOp.getFileSystem());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertNotNull("Arguments should be valid", settings);
        new SdkManagerCli(
                        settings,
                        new PrintStream(out),
                        new ByteArrayInputStream("y\ny\ny\n".getBytes()),
                        mDownloader,
                        mSdkHandler)
                .run(new FakeProgressIndicator());

        assertEquals(
                "2 of 2 SDK package licenses not accepted.\n"
                        + "Review licenses that have not been accepted (y/N)? \n"
                        + "1/2: License lic1:\n"
                        + "---------------------------------------\n"
                        + "my license\n"
                        + "---------------------------------------\n"
                        + "Accept? (y/N): \n"
                        + "2/2: License lic2:\n"
                        + "---------------------------------------\n"
                        + "my license 2\n"
                        + "---------------------------------------\n"
                        + "Accept? (y/N): All SDK package licenses accepted\n",
                out.toString().replaceAll("\\r\\n", "\n"));
        out.reset();
        // Subsequent call should pass without accepting again.
        new SdkManagerCli(
                        settings,
                        new PrintStream(out),
                        new ByteArrayInputStream("".getBytes()),
                        mDownloader,
                        mSdkHandler)
                .run(new FakeProgressIndicator());
        assertEquals(
                "All SDK package licenses accepted.\n", out.toString().replaceAll("\\r\\n", "\n"));
    }

    /**
     * Verify accepting some licences with --licences
     */
    @Test
    public void acceptSomeLicenses() throws Exception {
        SdkManagerCliSettings settings =
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--sdk_root=/sdk", "--licenses"), mFileOp.getFileSystem());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertNotNull("Arguments should be valid", settings);
        // Accept one of the licences
        new SdkManagerCli(
                        settings,
                        new PrintStream(out),
                        new ByteArrayInputStream("y\ny\nn\n".getBytes()),
                        mDownloader,
                        mSdkHandler)
                .run(new FakeProgressIndicator());
        assertEquals(
                "2 of 2 SDK package licenses not accepted.\n"
                        + "Review licenses that have not been accepted (y/N)? \n"
                        + "1/2: License lic1:\n"
                        + "---------------------------------------\n"
                        + "my license\n"
                        + "---------------------------------------\n"
                        + "Accept? (y/N): \n"
                        + "2/2: License lic2:\n"
                        + "---------------------------------------\n"
                        + "my license 2\n"
                        + "---------------------------------------\n"
                        + "Accept? (y/N): 1 license not accepted\n",
                out.toString().replaceAll("\\r\\n", "\n"));

        out.reset();

        // Then the other one
        new SdkManagerCli(
                        settings,
                        new PrintStream(out),
                        new ByteArrayInputStream("y\ny\n".getBytes()),
                        mDownloader,
                        mSdkHandler)
                .run(new FakeProgressIndicator());
        assertEquals(
                "1 of 2 SDK package license not accepted.\n"
                        + "Review license that has not been accepted (y/N)? \n"
                        + "1/1: License lic2:\n"
                        + "---------------------------------------\n"
                        + "my license 2\n"
                        + "---------------------------------------\n"
                        + "Accept? (y/N): All SDK package licenses accepted\n",
                out.toString().replaceAll("\\r\\n", "\n"));
    }


    /**
     * Not accepting the license of a package that's depended on results in the depending package
     * not being installed either.
     */
    @Test
    public void rejectLicenseWithDeps() throws Exception {
        SdkManagerCliSettings settings =
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--sdk_root=/sdk", "depends_on", "test;remote1"),
                        mFileOp.getFileSystem());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FakeProgressIndicator progress = new FakeProgressIndicator();
        // Accept depending license but not depended-on license, then continue.
        assertNotNull("Arguments should be valid", settings);
        SdkManagerCli downloader = new SdkManagerCli(settings,
                new PrintStream(out),
                new ByteArrayInputStream("y\nn\ny\n".getBytes()),
                mDownloader,
                mSdkHandler);
        downloader.run(new FakeProgressIndicator());
        mSdkHandler.getSdkManager(progress).reloadLocalIfNeeded(progress);
        assertNull(mSdkHandler.getLocalPackage("depended_on",
                progress));
        assertNull(mSdkHandler.getLocalPackage("depends_on",
                progress));
        assertNotNull(mSdkHandler.getLocalPackage("test;remote1",
                progress));
    }

    @Test
    public void unknownVersion() throws Exception {
        SdkManagerCliSettings settings =
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--sdk_root=/sdk", "--version"), mFileOp.getFileSystem());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SdkManagerCli sdkmanager =
                new SdkManagerCli(
                        settings,
                        new PrintStream(out),
                        new ByteArrayInputStream("y\nn\ny\n".getBytes()),
                        mDownloader,
                        mSdkHandler);

        FakeProgressIndicator progress = new FakeProgressIndicator();
        sdkmanager.run(progress);
        assertEquals("Unknown version", out.toString().replaceAll("[\n\r]", ""));
    }

    @Test
    public void printVersion() throws Exception {
        FakePackage.FakeLocalPackage tools = new FakePackage.FakeLocalPackage("tools");
        tools.setRevision(Revision.parseRevision("1.2.3"));

        SdkManagerCliSettings settings =
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--sdk_root=/sdk", "--version"), mFileOp.getFileSystem());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RepositoryPackages pkgs =
                new RepositoryPackages(ImmutableList.of(tools), ImmutableList.of());
        FakeRepoManager mgr = new FakeRepoManager(new File(SDK_LOCATION), pkgs);
        AndroidSdkHandler handler =
                new AndroidSdkHandler(new File(SDK_LOCATION), null, mFileOp, mgr);
        SdkManagerCli sdkmanager =
                new SdkManagerCli(
                        settings,
                        new PrintStream(out),
                        new ByteArrayInputStream("y\nn\ny\n".getBytes()),
                        mDownloader,
                        handler);

        sdkmanager.run(new FakeProgressIndicator());
        assertEquals("1.2.3", out.toString().replaceAll("[\n\r]", ""));
    }

    @Test
    public void unknownArgument() throws Exception {
        assertNull(
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--sdk_root=/sdk", "--foo"), mFileOp.getFileSystem()));
        assertNull(
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--sdk_root=/sdk", "--list", "foo"),
                        mFileOp.getFileSystem()));
        assertNull(
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--sdk_root=/sdk", "--update", "foo"),
                        mFileOp.getFileSystem()));
        assertNull(
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--sdk_root=/sdk", "--version", "foo"),
                        mFileOp.getFileSystem()));
        assertNull(
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--sdk_root=/sdk", "--licenses", "foo"),
                        mFileOp.getFileSystem()));
    }
}
