/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.repository.impl.installer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.Revision;
import com.android.repository.api.Checksum;
import com.android.repository.api.ConstantSourceProvider;
import com.android.repository.api.Downloader;
import com.android.repository.api.Installer;
import com.android.repository.api.InstallerFactory;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.Uninstaller;
import com.android.repository.impl.manager.RepoManagerImpl;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.testframework.FakeDownloader;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.FakeProgressRunner;
import com.android.repository.testframework.FakeRepoManager;
import com.android.repository.testframework.FakeSettingsController;
import com.android.repository.testframework.MockFileOp;
import com.android.testutils.file.InMemoryFileSystems;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import junit.framework.TestCase;

/**
 * Tests for {@link BasicInstallerFactory}.
 *
 * TODO: more tests.
 */
public class BasicInstallerTest extends TestCase {

    public void testDelete() throws Exception {
        MockFileOp fop = new MockFileOp();
        // Record package.xmls for two packages.
        fop.recordExistingFile(
                "/repo/mypackage/foo/package.xml",
                ByteStreams.toByteArray(getClass().getResourceAsStream("/testPackage.xml")));
        fop.recordExistingFile(
                "/repo/mypackage/bar/package.xml",
                ByteStreams.toByteArray(getClass().getResourceAsStream("/testPackage2.xml")));

        // Set up a RepoManager.
        RepoManager mgr = new RepoManagerImpl();
        mgr.setLocalPath(fop.toPath("/repo"));

        FakeProgressRunner runner = new FakeProgressRunner();
        // Load the local packages.
        mgr.loadSynchronously(
                0,
                ImmutableList.of(),
                ImmutableList.of(),
                ImmutableList.of(),
                runner,
                new FakeDownloader(fop),
                new FakeSettingsController(false));
        runner.getProgressIndicator().assertNoErrorsOrWarnings();
        RepositoryPackages pkgs = mgr.getPackages();

        // Get one of the packages to uninstall.
        LocalPackage p = pkgs.getLocalPackages().get("mypackage;foo");
        // Uninstall it
        InstallerFactory factory = new BasicInstallerFactory();
        Uninstaller uninstaller = factory.createUninstaller(p, mgr);
        uninstaller.prepare(new FakeProgressIndicator(true));
        uninstaller.complete(new FakeProgressIndicator(true));
        // Verify that the deleted dir is gone.
        assertFalse(fop.exists(new File("/repo/mypackage/foo")));
        assertTrue(fop.exists(new File("/repo/mypackage/bar/package.xml")));
    }

    public void testDeleteNonstandardLocation() throws IOException {
        Path sdkRoot = InMemoryFileSystems.createInMemoryFileSystemAndFolder("sdk");
        Path toDeleteDir = sdkRoot.resolve("foo with space");
        Path otherDir1 = sdkRoot.resolve("foo");
        Path otherDir2 = sdkRoot.resolve("bar");
        Files.createDirectories(otherDir1);
        Files.createDirectories(otherDir2);
        Files.createDirectories(toDeleteDir);
        Path toDeleteFile = toDeleteDir.resolve("a");
        InMemoryFileSystems.recordExistingFile(toDeleteFile);
        Path otherFile1 = otherDir1.resolve("b");
        InMemoryFileSystems.recordExistingFile(otherFile1);
        Path otherFile2 = otherDir2.resolve("c");
        InMemoryFileSystems.recordExistingFile(otherFile2);

        LocalPackage localPackage = new FakePackage.FakeLocalPackage("foo", toDeleteDir);
        RepositoryPackages packages =
                new RepositoryPackages(
                        ImmutableList.of(
                                new FakePackage.FakeLocalPackage("bar", otherDir2), localPackage),
                        ImmutableList.of());
        RepoManager mgr = new FakeRepoManager(sdkRoot, packages);
        InstallerFactory factory = new BasicInstallerFactory();
        Uninstaller uninstaller = factory.createUninstaller(localPackage, mgr);
        FakeProgressIndicator progress = new FakeProgressIndicator();
        uninstaller.prepare(progress);
        uninstaller.complete(progress);
        assertFalse(Files.exists(toDeleteFile));
        assertFalse(Files.exists(toDeleteDir));
        assertTrue(Files.exists(otherFile1));
        assertTrue(Files.exists(otherFile2));
        assertTrue(Files.exists(otherDir1));
        assertTrue(Files.exists(otherDir2));
    }

    // Test installing a new package
    public void testInstallFresh() throws Exception {
        MockFileOp fop = new MockFileOp();
        // We have a different package installed already.
        fop.recordExistingFile(
                "/repo/mypackage/foo/package.xml",
                ByteStreams.toByteArray(getClass().getResourceAsStream("/testPackage.xml")));
        RepoManager mgr = new RepoManagerImpl();
        Path root = fop.toPath("/repo");
        mgr.setLocalPath(root);
        FakeDownloader downloader = new FakeDownloader(fop);
        URL repoUrl = new URL("http://example.com/myrepo.xml");

        // The repo we're going to download
        downloader.registerUrl(repoUrl, getClass().getResourceAsStream("/testRepo.xml"));

        // Create the archive and register the URL
        URL archiveUrl = new URL("http://example.com/2/arch1");
        ByteArrayOutputStream baos = new ByteArrayOutputStream(1000);
        ZipOutputStream zos = new ZipOutputStream(baos);
        zos.putNextEntry(new ZipEntry("top-level/a"));
        zos.write("contents1".getBytes());
        zos.closeEntry();
        zos.putNextEntry(new ZipEntry("top-level/dir/b"));
        zos.write("contents2".getBytes());
        zos.closeEntry();
        zos.close();
        ByteArrayInputStream is = new ByteArrayInputStream(baos.toByteArray());
        downloader.registerUrl(archiveUrl, is);

        // Register a source provider to get the repo
        mgr.registerSourceProvider(
                new ConstantSourceProvider(
                        repoUrl.toString(),
                        "fake provider",
                        ImmutableList.of(RepoManager.getGenericModule())));
        FakeProgressRunner runner = new FakeProgressRunner();

        // Load
        mgr.loadSynchronously(
                RepoManager.DEFAULT_EXPIRATION_PERIOD_MS,
                ImmutableList.of(),
                ImmutableList.of(),
                ImmutableList.of(),
                runner,
                downloader,
                new FakeSettingsController(false));

        // Ensure we picked up the local package.
        RepositoryPackages pkgs = mgr.getPackages();
        runner.getProgressIndicator().assertNoErrorsOrWarnings();
        assertEquals(1, pkgs.getLocalPackages().size());
        assertEquals(2, pkgs.getRemotePackages().size());

        FakeProgressIndicator progress = new FakeProgressIndicator(true);
        // Install one of the packages.
        RemotePackage p = pkgs.getRemotePackages().get("mypackage;bar");
        Installer basicInstaller = new BasicInstallerFactory().createInstaller(p, mgr, downloader);
        File repoTempDir =
                new File(mgr.getLocalPath().toString(), AbstractPackageOperation.REPO_TEMP_DIR_FN);
        File packageOperationDir = new File(repoTempDir, String.format("%1$s01", AbstractPackageOperation.TEMP_DIR_PREFIX));
        File unzipDir = new File(packageOperationDir, BasicInstaller.FN_UNZIP_DIR);
        assertFalse(fop.exists(unzipDir));
        basicInstaller.prepare(progress.createSubProgress(0.5));
        // Verify that downloading & unzipping happens at the temp directory under the repo root, not at the system temp directory.
        assertTrue(fop.exists(unzipDir));
        basicInstaller.complete(progress.createSubProgress(1));
        progress.assertNoErrorsOrWarnings();

        runner = new FakeProgressRunner();
        // Reload the packages.
        mgr.loadSynchronously(
                0,
                ImmutableList.of(),
                ImmutableList.of(),
                ImmutableList.of(),
                runner,
                downloader,
                new FakeSettingsController(false));
        runner.getProgressIndicator().assertNoErrorsOrWarnings();
        Path[] contents = Files.list(root.resolve("mypackage")).toArray(Path[]::new);

        // Ensure it was installed on the filesystem
        assertEquals(2, contents.length);
        assertEquals(root.resolve("mypackage/bar"), contents[0]);
        assertEquals(root.resolve("mypackage/foo"), contents[1]);

        // Ensure it was recognized as a package.
        Map<String, ? extends LocalPackage> locals = mgr.getPackages().getLocalPackages();
        assertEquals(2, locals.size());
        assertTrue(locals.containsKey("mypackage;bar"));
        LocalPackage newPkg = locals.get("mypackage;bar");
        assertEquals("Test package 2", newPkg.getDisplayName());
        assertEquals("license text 2", newPkg.getLicense().getValue().trim());
        assertEquals(new Revision(4, 5, 6), newPkg.getVersion());
    }

    // Test cancellation along the way - the partial installation should be cleaned up.
    public void testCleanupWhenCancelled() throws Exception {
        MockFileOp fop = new MockFileOp();
        // We have a different package installed already.
        fop.recordExistingFile(
                "/repo/mypackage/foo/package.xml",
                ByteStreams.toByteArray(getClass().getResourceAsStream("/testPackage.xml")));
        RepoManager mgr = new RepoManagerImpl();
        Path root = fop.toPath("/repo");
        mgr.setLocalPath(root);
        FakeDownloader downloader = new FakeDownloader(fop);
        URL repoUrl = new URL("http://example.com/myrepo.xml");

        // The repo we're going to download
        downloader.registerUrl(repoUrl, getClass().getResourceAsStream("/testRepo.xml"));

        // Create the archive and register the URL
        URL archiveUrl = new URL("http://example.com/2/arch1");
        ByteArrayOutputStream baos = new ByteArrayOutputStream(1000);
        ZipOutputStream zos = new ZipOutputStream(baos);
        zos.putNextEntry(new ZipEntry("top-level/a"));
        zos.write("contents1".getBytes());
        zos.closeEntry();
        zos.putNextEntry(new ZipEntry("top-level/dir/b"));
        zos.write("contents2".getBytes());
        zos.closeEntry();
        zos.close();
        ByteArrayInputStream is = new ByteArrayInputStream(baos.toByteArray());
        downloader.registerUrl(archiveUrl, is);

        // Register a source provider to get the repo
        mgr.registerSourceProvider(
                new ConstantSourceProvider(
                        repoUrl.toString(),
                        "fake provider",
                        ImmutableList.of(RepoManager.getGenericModule())));
        FakeProgressRunner runner = new FakeProgressRunner();

        // Load
        mgr.loadSynchronously(
                RepoManager.DEFAULT_EXPIRATION_PERIOD_MS,
                ImmutableList.of(),
                ImmutableList.of(),
                ImmutableList.of(),
                runner,
                downloader,
                new FakeSettingsController(false));

        // Ensure we picked up the local package.
        RepositoryPackages pkgs = mgr.getPackages();
        runner.getProgressIndicator().assertNoErrorsOrWarnings();
        assertEquals(1, pkgs.getLocalPackages().size());
        assertEquals(2, pkgs.getRemotePackages().size());

        FakeProgressIndicator cancellingProgress1 =
                new FakeProgressIndicator(true) {
                    @Override
                    public void setFraction(double fraction) {
                        // Cancel somewhere in the middle during unzipping.
                        if (!isCanceled() && fraction > 0.2) {
                            cancel();
                        }
                        super.setFraction(fraction);
                    }
                };
        doTestCleanupWithProgress(pkgs, mgr, downloader, cancellingProgress1);

        FakeProgressIndicator cancellingProgress2 = new FakeProgressIndicator(true);
        // Cancel right away - this will lead to installer exit when it first checks for the cancellation marker.
        cancellingProgress2.cancel();
        doTestCleanupWithProgress(pkgs, mgr, downloader, cancellingProgress1);

        runner = new FakeProgressRunner();
        // Reload the packages.
        mgr.loadSynchronously(
                0,
                ImmutableList.of(),
                ImmutableList.of(),
                ImmutableList.of(),
                runner,
                downloader,
                new FakeSettingsController(false));
        runner.getProgressIndicator().assertNoErrorsOrWarnings();
        File[] contents = fop.listFiles(new File(root.toString(), "mypackage"));

        // Ensure the package we cancelled the installation for is not on the filesystem
        assertEquals(1, contents.length);
        assertEquals(new File(root.toString(), "mypackage/foo"), contents[0]);

        // Ensure it was not recognized as a package.
        Map<String, ? extends LocalPackage> locals = mgr.getPackages().getLocalPackages();
        assertEquals(1, locals.size());
        assertFalse(locals.containsKey("mypackage;bar"));
    }

    private void doTestCleanupWithProgress(
            RepositoryPackages pkgs,
            RepoManager mgr,
            Downloader downloader,
            FakeProgressIndicator progress) {
        RemotePackage p = pkgs.getRemotePackages().get("mypackage;bar");
        Installer basicInstaller =
                spy(new BasicInstallerFactory().createInstaller(p, mgr, downloader));
        Path repoTempDir = mgr.getLocalPath().resolve(AbstractPackageOperation.REPO_TEMP_DIR_FN);
        Path packageOperationDir =
                repoTempDir.resolve(
                        String.format("%1$s01", AbstractPackageOperation.TEMP_DIR_PREFIX));
        Path unzipDir = packageOperationDir.resolve(BasicInstaller.FN_UNZIP_DIR);
        assertFalse(Files.exists(unzipDir));
        basicInstaller.prepare(progress.createSubProgress(0.5));
        assertFalse(Files.exists(unzipDir));
        basicInstaller.complete(progress.createSubProgress(1));
        assertFalse(Files.exists(unzipDir));
        progress.assertNoErrorsOrWarnings();
        verify((BasicInstaller) basicInstaller, times(1)).cleanup(any());
    }


    // Test installing an upgrade to an existing package.
    public void testInstallUpgrade() throws Exception {
        MockFileOp fop = new MockFileOp();
        // Record a couple existing packages.
        fop.recordExistingFile(
                "/repo/mypackage/foo/package.xml",
                ByteStreams.toByteArray(getClass().getResourceAsStream("/testPackage.xml")));
        fop.recordExistingFile(
                "/repo/mypackage/bar/package.xml",
                ByteStreams.toByteArray(
                        getClass().getResourceAsStream("/testPackage2-lowerVersion.xml")));
        RepoManager mgr = new RepoManagerImpl();
        Path root = fop.toPath("/repo");
        mgr.setLocalPath(root);

        // Create the archive and register the repo to be downloaded.
        FakeDownloader downloader = new FakeDownloader(fop);
        URL repoUrl = new URL("http://example.com/myrepo.xml");
        downloader.registerUrl(repoUrl, getClass().getResourceAsStream("/testRepo.xml"));
        URL archiveUrl = new URL("http://example.com/2/arch1");
        ByteArrayOutputStream baos = new ByteArrayOutputStream(1000);
        ZipOutputStream zos = new ZipOutputStream(baos);
        zos.putNextEntry(new ZipEntry("top-level/a"));
        zos.write("contents1".getBytes());
        zos.closeEntry();
        zos.putNextEntry(new ZipEntry("top-level/dir/b"));
        zos.write("contents2".getBytes());
        zos.closeEntry();
        zos.close();
        ByteArrayInputStream is = new ByteArrayInputStream(baos.toByteArray());
        downloader.registerUrl(archiveUrl, is);

        // Register the source provider
        mgr.registerSourceProvider(
                new ConstantSourceProvider(
                        repoUrl.toString(),
                        "fake provider",
                        ImmutableList.of(RepoManager.getGenericModule())));
        FakeProgressRunner runner = new FakeProgressRunner();

        // Load
        mgr.loadSynchronously(
                RepoManager.DEFAULT_EXPIRATION_PERIOD_MS,
                ImmutableList.of(),
                ImmutableList.of(),
                ImmutableList.of(),
                runner,
                downloader,
                new FakeSettingsController(false));
        runner.getProgressIndicator().assertNoErrorsOrWarnings();
        RepositoryPackages pkgs = mgr.getPackages();

        // Ensure the old local package was found with the right version
        Map<String, ? extends LocalPackage> locals = mgr.getPackages().getLocalPackages();
        LocalPackage oldPkg = locals.get("mypackage;bar");
        assertEquals(new Revision(3), oldPkg.getVersion());

        // Ensure the new package is found with the right version
        RemotePackage update = pkgs.getRemotePackages().get("mypackage;bar");
        assertEquals(new Revision(4, 5, 6), update.getVersion());

        // Install the update
        FakeProgressIndicator progress = new FakeProgressIndicator();
        Installer basicInstaller =
                new BasicInstallerFactory().createInstaller(update, mgr, downloader);
        basicInstaller.prepare(progress);
        basicInstaller.complete(progress);

        // Reload the repo
        mgr.loadSynchronously(
                0,
                ImmutableList.of(),
                ImmutableList.of(),
                ImmutableList.of(),
                runner,
                downloader,
                new FakeSettingsController(false));
        runner.getProgressIndicator().assertNoErrorsOrWarnings();

        // Ensure the files are still there
        Path[] contents = Files.list(root.resolve("mypackage")).toArray(Path[]::new);
        assertEquals(2, contents.length);
        assertEquals(root.resolve("mypackage/bar"), contents[0]);
        assertEquals(root.resolve("mypackage/foo"), contents[1]);

        // Ensure the packages are still there
        locals = mgr.getPackages().getLocalPackages();
        assertEquals(2, locals.size());
        assertTrue(locals.containsKey("mypackage;bar"));
        LocalPackage newPkg = locals.get("mypackage;bar");
        assertEquals("Test package 2", newPkg.getDisplayName());
        assertEquals("license text 2", newPkg.getLicense().getValue().trim());

        // Ensure the update was installed
        assertEquals(new Revision(4, 5, 6), newPkg.getVersion());

        // TODO: verify the actual contents of the update?
    }

    public void testExistingDownload() throws Exception {
        MockFileOp fop = new MockFileOp();
        RepoManager mgr = new RepoManagerImpl();
        Path root = fop.toPath("/repo");
        mgr.setLocalPath(root);
        FakeDownloader downloader =
                new FakeDownloader(fop) {
                    @Override
                    public void downloadFully(
                            @NonNull URL url,
                            @NonNull Path target,
                            @Nullable Checksum checksum,
                            @NonNull ProgressIndicator indicator)
                            throws IOException {
                        super.downloadFully(url, target, checksum, indicator);
                        throw new IOException("expected");
                    }
                };
        URL repoUrl = new URL("http://example.com/myrepo.xml");

        // Create the archive and register the URL
        URL archiveUrl = new URL("http://example.com/2/arch1");
        ByteArrayOutputStream baos = new ByteArrayOutputStream(1000);
        ZipOutputStream zos = new ZipOutputStream(baos);
        ZipEntry e = new ZipEntry("top-level/a");
        e.setTime(123);
        zos.putNextEntry(e);
        zos.write("contents1".getBytes());
        zos.closeEntry();
        zos.close();
        byte[] zipBytes = baos.toByteArray();
        ByteArrayInputStream is = new ByteArrayInputStream(zipBytes);
        downloader.registerUrl(archiveUrl, is);

        String repo =
                "<repo:repository\n"
                        + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/02\"\n"
                        + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                        + "    <remotePackage path=\"mypackage;bar\">\n"
                        + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
                        + "        <revision>\n"
                        + "            <major>4</major>\n"
                        + "            <minor>5</minor>\n"
                        + "            <micro>6</micro>\n"
                        + "        </revision>\n"
                        + "        <display-name>Test package 2</display-name>\n"
                        + "        <archives>\n"
                        + "            <archive>\n"
                        + "                <complete>\n"
                        + "                    <size>2345</size>\n"
                        + "                    <checksum type='sha-256'>"
                        + Downloader.hash(
                                new ByteArrayInputStream(zipBytes),
                                zipBytes.length,
                                "sha-256",
                                new FakeProgressIndicator())
                        + "</checksum>\n"
                        + "                    <url>http://example.com/2/arch1</url>\n"
                        + "                </complete>\n"
                        + "            </archive>\n"
                        + "        </archives>\n"
                        + "    </remotePackage>\n"
                        + "</repo:repository>";

        // The repo we're going to download
        downloader.registerUrl(repoUrl, repo.getBytes());

        // Register a source provider to get the repo
        mgr.registerSourceProvider(
                new ConstantSourceProvider(
                        repoUrl.toString(),
                        "fake provider",
                        ImmutableList.of(RepoManager.getGenericModule())));
        FakeProgressRunner runner = new FakeProgressRunner();

        // Load
        mgr.loadSynchronously(
                RepoManager.DEFAULT_EXPIRATION_PERIOD_MS,
                ImmutableList.of(),
                ImmutableList.of(),
                ImmutableList.of(),
                runner,
                downloader,
                new FakeSettingsController(false));

        // Install one of the packages.
        RemotePackage p = mgr.getPackages().getRemotePackages().get("mypackage;bar");
        Installer basicInstaller = new BasicInstallerFactory().createInstaller(p, mgr, downloader);
        FakeProgressIndicator firstInstallProgress = new FakeProgressIndicator(true);
        boolean result = basicInstaller.prepare(firstInstallProgress);

        // be sure it was actually cancelled
        assertFalse(result);
        assertFalse(firstInstallProgress.getWarnings().isEmpty());
        Downloader failingDownloader =
                new Downloader() {
                    @Nullable
                    @Override
                    public InputStream downloadAndStream(
                            @NonNull URL url, @NonNull ProgressIndicator indicator) {
                        fail();
                        return null;
                    }

                    @Nullable
                    @Override
                    public Path downloadFully(
                            @NonNull URL url, @NonNull ProgressIndicator indicator) {
                        fail();
                        return null;
                    }

                    @Override
                    public void downloadFully(
                            @NonNull URL url,
                            @NonNull Path target,
                            @Nullable Checksum checksum,
                            @NonNull ProgressIndicator indicator)
                            throws IOException {
                        assertEquals(
                                checksum.getValue(),
                                Downloader.hash(
                                        Files.newInputStream(target),
                                        Files.size(target),
                                        checksum.getType(),
                                        indicator));
                    }
                };
        basicInstaller = new BasicInstallerFactory().createInstaller(p, mgr, failingDownloader);
        // Try again with the failing downloader; it should not be called.
        FakeProgressIndicator secondInstallProgress = new FakeProgressIndicator(true);
        result = basicInstaller.prepare(secondInstallProgress);
        assertTrue(result);
        result = basicInstaller.complete(secondInstallProgress);

        assertTrue(result);
        secondInstallProgress.assertNoErrorsOrWarnings();

        // Reload the packages.
        mgr.loadSynchronously(
                0,
                ImmutableList.of(),
                ImmutableList.of(),
                ImmutableList.of(),
                runner,
                downloader,
                new FakeSettingsController(false));
        runner.getProgressIndicator().assertNoErrorsOrWarnings();
        Path[] contents = Files.list(root.resolve("mypackage")).toArray(Path[]::new);

        // Ensure it was installed on the filesystem
        assertEquals(root.resolve("mypackage/bar"), contents[0]);

        // Ensure it was recognized as a package.
        Map<String, ? extends LocalPackage> locals = mgr.getPackages().getLocalPackages();
        assertEquals(1, locals.size());
        assertTrue(locals.containsKey("mypackage;bar"));
        LocalPackage newPkg = locals.get("mypackage;bar");
        assertEquals("Test package 2", newPkg.getDisplayName());
        assertEquals(new Revision(4, 5, 6), newPkg.getVersion());
    }
}
