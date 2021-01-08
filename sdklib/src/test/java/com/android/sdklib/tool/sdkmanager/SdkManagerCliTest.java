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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.Version;
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
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.FakeRepoManager;
import com.android.repository.testframework.FakeRepositorySourceProvider;
import com.android.repository.testframework.FakeSettingsController;
import com.android.repository.testframework.MockFileOp;
import com.android.repository.util.InstallerUtil;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.testutils.file.InMemoryFileSystems;
import com.android.utils.PathUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.Proxy;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link SdkManagerCli} */
@SuppressWarnings("resource")
public class SdkManagerCliTest {

    private final MockFileOp mFileOp = new MockFileOp();
    private final Path mSdkLocation = mFileOp.toPath("/sdk");

    private AndroidSdkHandler mSdkHandler;
    private FakeDownloader mDownloader;

    @Before
    public void setUp() throws Exception {
        mDownloader = new FakeDownloader(mFileOp);

        RemoteRepoLoader loader = createRemoteRepo();

        RepoManager repoManager = new RepoManagerImpl(null, progress -> loader);
        repoManager.setLocalPath(mSdkLocation);

        createLocalRepo(repoManager);

        mSdkHandler = new AndroidSdkHandler(mSdkLocation, null, mFileOp, repoManager);

        // Doesn't actually need to provide anything, since the remote loader gets them directly.
        repoManager.registerSourceProvider(new FakeRepositorySourceProvider(null));
    }

    private void createLocalRepo(@NonNull RepoManager repoManager) throws IOException {
        ProgressIndicator progress = new FakeProgressIndicator();
        Files.createDirectories(mSdkLocation);
        FakeRemotePackage installed = new FakeRemotePackage("test;p1");
        installed.setDisplayName("package 1");
        Path p1Path = mSdkLocation.resolve("test/p1");
        Files.createDirectories(p1Path);
        InstallerUtil.writePackageXml(installed, p1Path, repoManager, progress);
        installed = new FakeRemotePackage("upgrade");
        installed.setDisplayName("upgrade v1");
        Files.createDirectories(mSdkLocation.resolve("upgrade"));
        InstallerUtil.writePackageXml(
                installed, mSdkLocation.resolve("upgrade"), repoManager, progress);
        installed = new FakeRemotePackage("obsolete");
        installed.setDisplayName("obsolete local");
        installed.setObsolete(true);
        Files.createDirectories(mSdkLocation.resolve("obsolete"));
        InstallerUtil.writePackageXml(
                installed, mSdkLocation.resolve("obsolete"), repoManager, progress);
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

    @Test
    public void determineRoot() throws Exception {
        System.setProperty(
                "com.android.sdklib.toolsdir",
                mFileOp.getPlatformSpecificPath("/sdk/cmdline-tools/1.0.0"));
        SdkManagerCliSettings settings =
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--list"), mFileOp.getFileSystem());
        assertEquals(mSdkLocation, settings.getLocalPath());

        System.setProperty(
                "com.android.sdklib.toolsdir", mFileOp.getPlatformSpecificPath("/sdk/foo/bar"));
        try {
            // This is expected to fail, since the path isn't what's expected
            SdkManagerCliSettings.createSettings(
                    ImmutableList.of("--list"), mFileOp.getFileSystem());
            fail();
        } catch (SdkManagerCliSettings.FailSilentlyException expected) {
        }

        System.setProperty(
                "com.android.sdklib.toolsdir", mFileOp.getPlatformSpecificPath("/sdk/foo/bar"));
        settings =
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of(
                                "--list", "--sdk_root=" + mFileOp.getPlatformSpecificPath("/sdk2")),
                        mFileOp.getFileSystem());
        assertEquals(mFileOp.getPlatformSpecificPath("/sdk2"), settings.getLocalPath().toString());

        // There was a problem when tools was installed directly in the root
        System.setProperty(
                "com.android.sdklib.toolsdir", mFileOp.getPlatformSpecificPath("/tools"));
        settings =
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of(
                                "--list", "--sdk_root=" + mFileOp.getPlatformSpecificPath("/sdk3")),
                        mFileOp.getFileSystem());
        assertEquals(mFileOp.getPlatformSpecificPath("/sdk3"), settings.getLocalPath().toString());
    }

    /**
     * List the packages we have installed and available.
     */
    @Test
    public void basicList() throws Exception {
        SdkManagerCliSettings settings =
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--list", "--sdk_root=" + mSdkLocation),
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

    /** List the packages we have installed only. */
    @Test
    public void listInstalled() throws Exception {
        SdkManagerCliSettings settings =
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--list_installed", "--sdk_root=" + mSdkLocation),
                        mFileOp.getFileSystem());
        assertNotNull("Arguments should be valid", settings);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        SdkManagerCli downloader =
                new SdkManagerCli(settings, new PrintStream(out), null, mDownloader, mSdkHandler);
        downloader.run(new FakeProgressIndicator());
        String expected =
                "Installed packages:\n"
                        + "  Path    | Version | Description | Location\n"
                        + "  ------- | ------- | -------     | ------- \n"
                        + "  test;p1 | 1       | package 1   | test"
                        + File.separator
                        + "p1 \n"
                        + "  upgrade | 1       | upgrade v1  | upgrade \n";
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
        Path p2Path = mSdkLocation.resolve("test/p2/is-also/installed-in-a/path-with-a-long-name/");
        Files.createDirectories(p2Path);
        ProgressIndicator progress = new FakeProgressIndicator();
        InstallerUtil.writePackageXml(
                installed, p2Path, mSdkHandler.getSdkManager(progress), progress);

        SdkManagerCliSettings settings =
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--list", "--sdk_root=" + mSdkLocation),
                        mFileOp.getFileSystem());
        assertNotNull("Arguments should be valid", settings);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SdkManagerCli downloader =
                new SdkManagerCli(settings, new PrintStream(out), null, mDownloader, mSdkHandler);
        downloader.run(new FakeProgressIndicator());
        String p1RelativePath = new File("test/p1").getPath();
        String p2RelativePath = new File("test/p2/is-also/installed-in-a/path-with-a-long-name").getPath();
        String expected =
                "Installed packages:\n"
                        + "  Path                                                                 | Version | Description                                                      | Location                                            \n"
                        + "  -------                                                              | ------- | -------                                                          | -------                                             \n"
                        + "  test;p1                                                              | 1       | package 1                                                        | " + p1RelativePath + "                                             \n"
                        + "  test;p2;which;has;a;really;long;name;which;should;still;be;displayed | 1       | package 2 has a long display name that should still be displayed | " + p2RelativePath + "\n"
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
                        ImmutableList.of("--list", "--sdk_root=" + mSdkLocation, "--verbose"),
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
                                + "    Installed Location: %2$s%1$stest%1$sp1\n"
                                + "\n"
                                + "upgrade\n"
                                + "    Description:        upgrade v1\n"
                                + "    Version:            1\n"
                                + "    Installed Location: %2$s%1$supgrade\n"
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
                        File.separator, mSdkLocation);
        assertEquals(expected, out.toString().replaceAll("\\r\\n", "\n"));
    }

    /** Verify that the --channel sets us up with the right channel. */
    @Test
    public void channel() throws Exception {
        SdkManagerCliSettings settings =
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--list", "--channel=1", "--sdk_root=" + mSdkLocation),
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
                        ImmutableList.of(
                                "--list", "--include_obsolete", "--sdk_root=" + mSdkLocation),
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
                        ImmutableList.of("--sdk_root=" + mSdkLocation, "test;remote1"),
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
                        ImmutableList.of(
                                "--sdk_root=" + mSdkLocation, "test;remote1", "depends_on"),
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
                        ImmutableList.of("--update", "--sdk_root=" + mSdkLocation),
                        mFileOp.getFileSystem());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FakeProgressIndicator progress = new FakeProgressIndicator();
        mSdkHandler
                .getSdkManager(new FakeProgressIndicator())
                .loadSynchronously(
                        0,
                        new FakeProgressIndicator(),
                        mDownloader,
                        new FakeSettingsController(false));

        assertEquals(1,
                mSdkHandler.getLocalPackage("upgrade", progress).getVersion().getMajor());
        assertNotNull("Arguments should be valid", settings);
        SdkManagerCli downloader = new SdkManagerCli(settings,
                new PrintStream(out),
                null,
                mDownloader,
                mSdkHandler);
        downloader.run(new FakeProgressIndicator());

        assertTrue(out.toString().replaceAll("\\r\\n", "\n").contains("Updating:\nupgrade\n"));
        mSdkHandler.getSdkManager(progress).reloadLocalIfNeeded(progress);
        assertEquals(2,
                mSdkHandler.getLocalPackage("upgrade", progress).getVersion().getMajor());
        assertEquals(1,
                mSdkHandler.getLocalPackage("obsolete", progress).getVersion().getMajor());
        assertEquals(3,
                mSdkHandler.getSdkManager(progress).getPackages().getLocalPackages().size());
    }

    @Test
    public void testNoUpdates() throws Exception {
        PathUtils.deleteRecursivelyIfExists(mSdkLocation.resolve("upgrade"));
        FakeProgressIndicator progress = new FakeProgressIndicator();
        mSdkHandler.getSdkManager(progress).reloadLocalIfNeeded(progress);

        SdkManagerCliSettings settings =
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--update", "--sdk_root=" + mSdkLocation),
                        mFileOp.getFileSystem());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        assertNotNull("Arguments should be valid", settings);
        SdkManagerCli downloader =
                new SdkManagerCli(settings, new PrintStream(out), null, mDownloader, mSdkHandler);
        downloader.run(new FakeProgressIndicator());

        assertTrue(out.toString().replaceAll("\\r\\n", "\n").contains("No updates available\n"));
    }

    /**
     * Install a package into a subdirectory of an existing package should fail.
     */
    @Test
    public void basicInstallBroken() throws Exception {
        // Move a valid package to the containing directory that the other package will
        // try to be installed in.
        Files.move(mSdkLocation.resolve("test/p1"), mSdkLocation.resolve("test2"));
        Files.delete(mSdkLocation.resolve("test"));
        Files.move(mSdkLocation.resolve("test2"), mSdkLocation.resolve("test"));

        SdkManagerCliSettings settings =
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--sdk_root=" + mSdkLocation, "test;remote1"),
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
                        ImmutableList.of(
                                "--update", "--include_obsolete", "--sdk_root=" + mSdkLocation),
                        mFileOp.getFileSystem());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FakeProgressIndicator progress = new FakeProgressIndicator();
        mSdkHandler
                .getSdkManager(progress)
                .loadSynchronously(
                        0,
                        new FakeProgressIndicator(),
                        mDownloader,
                        new FakeSettingsController(false));
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
                        ImmutableList.of("--uninstall", "--sdk_root=" + mSdkLocation, "obsolete"),
                        mFileOp.getFileSystem());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FakeProgressIndicator progress = new FakeProgressIndicator();
        mSdkHandler
                .getSdkManager(progress)
                .loadSynchronously(
                        0,
                        new FakeProgressIndicator(),
                        mDownloader,
                        new FakeSettingsController(false));
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
                        ImmutableList.of(
                                "--uninstall", "--sdk_root=" + mSdkLocation, "obsolete", "upgrade"),
                        mFileOp.getFileSystem());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FakeProgressIndicator progress = new FakeProgressIndicator(true);
        mSdkHandler
                .getSdkManager(progress)
                .loadSynchronously(
                        0,
                        new FakeProgressIndicator(),
                        mDownloader,
                        new FakeSettingsController(false));
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
                        ImmutableList.of("--sdk_root=" + mSdkLocation, "depended_on"),
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
                        ImmutableList.of("--sdk_root=" + mSdkLocation, "--licenses"),
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
                        ImmutableList.of("--sdk_root=" + mSdkLocation, "--licenses", "--verbose"),
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
                        ImmutableList.of("--sdk_root=" + mSdkLocation, "--licenses"),
                        mFileOp.getFileSystem());
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
                        ImmutableList.of("--sdk_root=" + mSdkLocation, "--licenses"),
                        mFileOp.getFileSystem());
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

    /** Verify that new versions of the same license show as unaccepted */
    @Test
    public void checkNewLicenseVersion() throws Exception {
        SdkManagerCliSettings settings =
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--sdk_root=" + mSdkLocation, "--licenses"),
                        mFileOp.getFileSystem());
        assertNotNull("Arguments should be valid", settings);

        // Accept the existing licenses
        new SdkManagerCli(
                        settings,
                        new PrintStream(new ByteArrayOutputStream()),
                        new ByteArrayInputStream("y\ny\ny\n".getBytes()),
                        mDownloader,
                        mSdkHandler)
                .run(new FakeProgressIndicator());

        // Create a new version of an existing license
        CommonFactory factory = RepoManager.getCommonModule().createLatestFactory();
        RemotePackage obsoletePackage =
                mSdkHandler
                        .getSdkManager(new FakeProgressIndicator())
                        .getPackages()
                        .getRemotePackages()
                        .get("obsolete");
        License license =
                factory.createLicenseType("my new license", obsoletePackage.getLicense().getId());
        ((FakeRemotePackage) obsoletePackage).setLicense(license);

        // Verify that we're prompted for the new version
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new SdkManagerCli(
                        settings,
                        new PrintStream(out),
                        new ByteArrayInputStream("y\ny\n".getBytes()),
                        mDownloader,
                        mSdkHandler)
                .run(new FakeProgressIndicator());
        assertEquals(
                "1 of 3 SDK package license not accepted.\n"
                        + "Review license that has not been accepted (y/N)? \n"
                        + "1/1: License lic2:\n"
                        + "---------------------------------------\n"
                        + "my new license\n"
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
                        ImmutableList.of(
                                "--sdk_root=" + mSdkLocation, "depends_on", "test;remote1"),
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
    public void printVersion() throws Exception {
        SdkManagerCliSettings settings =
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--sdk_root=" + mSdkLocation, "--version"),
                        mFileOp.getFileSystem());

        AndroidSdkHandler handler = new AndroidSdkHandler(mSdkLocation, null, mFileOp);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SdkManagerCli sdkmanager =
                new SdkManagerCli(settings, new PrintStream(out), null, mDownloader, handler);

        sdkmanager.run(new FakeProgressIndicator());
        assertNotNull(Version.TOOLS_VERSION);
        assertEquals(Version.TOOLS_VERSION, out.toString().replaceAll("[\n\r]", ""));
    }

    @Test
    public void unknownArgument() {
        assertThrows(
                SdkManagerCliSettings.ShowUsageException.class,
                () ->
                        SdkManagerCliSettings.createSettings(
                                ImmutableList.of("--sdk_root=" + mSdkLocation, "--foo"),
                                mFileOp.getFileSystem()));
        assertThrows(
                SdkManagerCliSettings.ShowUsageException.class,
                () ->
                        SdkManagerCliSettings.createSettings(
                                ImmutableList.of("--sdk_root=" + mSdkLocation, "--list", "foo"),
                                mFileOp.getFileSystem()));
        assertThrows(
                SdkManagerCliSettings.ShowUsageException.class,
                () ->
                        SdkManagerCliSettings.createSettings(
                                ImmutableList.of("--sdk_root=" + mSdkLocation, "--update", "foo"),
                                mFileOp.getFileSystem()));
        assertThrows(
                SdkManagerCliSettings.ShowUsageException.class,
                () ->
                        SdkManagerCliSettings.createSettings(
                                ImmutableList.of("--sdk_root=" + mSdkLocation, "--version", "foo"),
                                mFileOp.getFileSystem()));
        assertThrows(
                SdkManagerCliSettings.ShowUsageException.class,
                () ->
                        SdkManagerCliSettings.createSettings(
                                ImmutableList.of("--sdk_root=" + mSdkLocation, "--licenses", "foo"),
                                mFileOp.getFileSystem()));
    }

    // TODO: remove when we move past junit 4.12 in tools/idea
    private interface ThrowableRunnable {
        void run() throws Exception;
    }

    // TODO: remove when we move past junit 4.12 in tools/idea
    private void assertThrows(
            Class<? extends Exception> exceptionClass, ThrowableRunnable runnable) {
        try {
            runnable.run();
            fail("Expected " + exceptionClass.getName());
        } catch (Exception e) {
            assertTrue(e.getClass().isAssignableFrom(exceptionClass));
        }
    }

    @Test
    public void unknownPackage() throws Exception {
        SdkManagerCliSettings settings =
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--sdk_root=" + mSdkLocation, "test;bad"),
                        mFileOp.getFileSystem());
        FakeProgressIndicator progress = new FakeProgressIndicator();
        SdkManagerCli downloader =
                new SdkManagerCli(
                        settings,
                        new PrintStream(new ByteArrayOutputStream()),
                        new ByteArrayInputStream(new byte[0]),
                        mDownloader,
                        mSdkHandler);
        try {
            downloader.run(progress);
            fail("expected exception");
        } catch (SdkManagerCli.CommandFailedException expected) {
            assertTrue(progress.getWarnings().contains("Failed to find package 'test;bad'"));
        }
    }

    @Test
    public void proxySettings() throws Exception {
        final String HTTP_PROXY = "http://studio-unittest.name:2340";
        final String HTTPS_PROXY = "https://studio-other-unittest.name:2341";
        String httpProxyHost = new URL(HTTP_PROXY).getHost();
        String httpsProxyHost = new URL(HTTPS_PROXY).getHost();

        Map<String, String> environment =
                ImmutableMap.of(
                        "HTTP_PROXY", HTTP_PROXY,
                        "HTTPS_PROXY", HTTPS_PROXY,
                        "STUDIO_UNITTEST_DO_NOT_RESOLVE_PROXY", "1");

        assertThrows(
                SdkManagerCliSettings.FailSilentlyException.class,
                () ->
                        SdkManagerCliSettings.createSettings(
                                ImmutableList.of(
                                        "--sdk_root=" + mSdkLocation,
                                        "--no_proxy",
                                        "--proxy_port=80"),
                                mFileOp.getFileSystem(),
                                environment));
        assertThrows(
                SdkManagerCliSettings.FailSilentlyException.class,
                () ->
                        SdkManagerCliSettings.createSettings(
                                ImmutableList.of(
                                        "--sdk_root=" + mSdkLocation,
                                        "--no_proxy",
                                        "--proxy_host=foo.bar"),
                                mFileOp.getFileSystem(),
                                environment));
        assertThrows(
                SdkManagerCliSettings.FailSilentlyException.class,
                () ->
                        SdkManagerCliSettings.createSettings(
                                ImmutableList.of(
                                        "--sdk_root=" + mSdkLocation,
                                        "--no_proxy",
                                        "--proxy=bar.baz"),
                                mFileOp.getFileSystem(),
                                environment));

        {
            SdkManagerCliSettings settings =
                    SdkManagerCliSettings.createSettings(
                            ImmutableList.of("--sdk_root=" + mSdkLocation, "--no_proxy"),
                            mFileOp.getFileSystem(),
                            environment);
            assertNotNull(settings);
            assertTrue(settings.getForceNoProxy());
            assertSame(Proxy.NO_PROXY, settings.getProxy());
        }

        {
            SdkManagerCliSettings settings =
                    SdkManagerCliSettings.createSettings(
                            ImmutableList.of("--sdk_root=" + mSdkLocation, "--no_https"),
                            mFileOp.getFileSystem(),
                            environment);
            assertNotNull(settings);
            assertTrue(settings.getForceHttp());
            assertEquals(httpProxyHost, settings.getProxyHostStr());
        }

        {
            SdkManagerCliSettings settings =
                    SdkManagerCliSettings.createSettings(
                            ImmutableList.of("--sdk_root=" + mSdkLocation),
                            mFileOp.getFileSystem(),
                            environment);
            assertNotNull(settings);
            assertFalse(settings.getForceNoProxy());
            assertFalse(settings.getForceHttp());
            assertEquals(httpsProxyHost, settings.getProxyHostStr());
        }

        {
            Map<String, String> environmentHttpOnly =
                    ImmutableMap.of(
                            "HTTP_PROXY", HTTP_PROXY, "STUDIO_UNITTEST_DO_NOT_RESOLVE_PROXY", "1");
            SdkManagerCliSettings settings =
                    SdkManagerCliSettings.createSettings(
                            ImmutableList.of("--sdk_root=" + mSdkLocation),
                            mFileOp.getFileSystem(),
                            environmentHttpOnly);
            assertNotNull(settings);
            assertFalse(settings.getForceNoProxy());
            assertFalse(settings.getForceHttp());
            assertEquals(httpProxyHost, settings.getProxyHostStr());
        }

        {
            Map<String, String> environmentInvalidProxyUrl =
                    ImmutableMap.of(
                            "HTTP_PROXY", "Ти до мене не ходи",
                            "STUDIO_UNITTEST_DO_NOT_RESOLVE_PROXY", "1");
            assertThrows(
                    SdkManagerCliSettings.FailSilentlyException.class,
                    () ->
                            SdkManagerCliSettings.createSettings(
                                    ImmutableList.of("--sdk_root=" + mSdkLocation),
                                    mFileOp.getFileSystem(),
                                    environmentInvalidProxyUrl));
        }
    }

    @Test
    public void packageFile() throws Exception {
        String path = InMemoryFileSystems.getPlatformSpecificPath("/foo.bar");
        mFileOp.recordExistingFile(path, "package1\r\n package2 \r\n\r\n");
        SdkManagerCliSettings settings =
                SdkManagerCliSettings.createSettings(
                        ImmutableList.of("--package_file=" + path, "--sdk_root=" + mSdkLocation),
                        mFileOp.getFileSystem());

        assertNotNull(settings);
        SdkAction action = settings.getAction();
        assertNotNull(action);
        assertTrue(action instanceof SdkPackagesAction);
        SdkPackagesAction packagesAction = (SdkPackagesAction) action;

        FakeRepoManager fakeRepoManager = new FakeRepoManager(new RepositoryPackages());
        List<String> packages = packagesAction.getPaths(fakeRepoManager);
        assertEquals(2, packages.size());
        assertEquals("package1", packages.get(0));
        assertEquals("package2", packages.get(1));
    }
}
