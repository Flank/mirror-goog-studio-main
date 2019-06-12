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
import com.android.repository.testframework.*;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
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
        fop.recordExistingFile("/repo/dummy/foo/package.xml", ByteStreams
                .toByteArray(getClass().getResourceAsStream("/testPackage.xml")));
        fop.recordExistingFile("/repo/dummy/bar/package.xml", ByteStreams
                .toByteArray(getClass().getResourceAsStream("/testPackage2.xml")));

        // Set up a RepoManager.
        RepoManager mgr = new RepoManagerImpl(fop);
        File root = new File("/repo");
        mgr.setLocalPath(root);

        FakeProgressRunner runner = new FakeProgressRunner();
        // Load the local packages.
        mgr.load(0, ImmutableList.<RepoManager.RepoLoadedCallback>of(),
                ImmutableList.<RepoManager.RepoLoadedCallback>of(), ImmutableList.<Runnable>of(),
                runner, new FakeDownloader(fop), new FakeSettingsController(false), true);
        runner.getProgressIndicator().assertNoErrorsOrWarnings();
        RepositoryPackages pkgs = mgr.getPackages();

        // Get one of the packages to uninstall.
        LocalPackage p = pkgs.getLocalPackages().get("dummy;foo");
        // Uninstall it
        InstallerFactory factory = new BasicInstallerFactory();
        Uninstaller uninstaller = factory.createUninstaller(p, mgr, fop);
        uninstaller.prepare(new FakeProgressIndicator(true));
        uninstaller.complete(new FakeProgressIndicator(true));
        // Verify that the deleted dir is gone.
        assertFalse(fop.exists(new File("/repo/dummy/foo")));
        assertTrue(fop.exists(new File("/repo/dummy/bar/package.xml")));
    }

    public void testDeleteNonstandardLocation() throws Exception {
        File toDeleteDir = new File("/sdk/foo with space");
        File otherDir1 = new File("/sdk/foo");
        File otherDir2 = new File("/sdk/bar");
        MockFileOp fop = new MockFileOp();
        fop.mkdirs(otherDir1);
        fop.mkdirs(otherDir2);
        fop.mkdirs(toDeleteDir);
        File toDeleteFile = new File(toDeleteDir, "a");
        fop.recordExistingFile(toDeleteFile);
        File otherFile1 = new File(otherDir1, "b");
        fop.recordExistingFile(otherFile1);
        File otherFile2 = new File(otherDir2, "c");
        fop.recordExistingFile(otherFile2);

        LocalPackage localPackage = new FakePackage.FakeLocalPackage("foo");
        localPackage.setInstalledPath(toDeleteDir);
        RepositoryPackages packages =
                new RepositoryPackages(
                        ImmutableList.of(new FakePackage.FakeLocalPackage("bar"), localPackage),
                        ImmutableList.of());
        RepoManager mgr = new FakeRepoManager(new File("/sdk"), packages);
        InstallerFactory factory = new BasicInstallerFactory();
        Uninstaller uninstaller = factory.createUninstaller(localPackage, mgr, fop);
        FakeProgressIndicator progress = new FakeProgressIndicator();
        uninstaller.prepare(progress);
        uninstaller.complete(progress);
        assertFalse(fop.exists(toDeleteFile));
        assertFalse(fop.exists(toDeleteDir));
        assertTrue(fop.exists(otherFile1));
        assertTrue(fop.exists(otherFile2));
        assertTrue(fop.exists(otherDir1));
        assertTrue(fop.exists(otherDir2));
    }

    // Test installing a new package
    public void testInstallFresh() throws Exception {
        MockFileOp fop = new MockFileOp();
        // We have a different package installed already.
        fop.recordExistingFile("/repo/dummy/foo/package.xml", ByteStreams
                .toByteArray(getClass().getResourceAsStream("/testPackage.xml")));
        RepoManager mgr = new RepoManagerImpl(fop);
        File root = new File("/repo");
        mgr.setLocalPath(root);
        FakeDownloader downloader = new FakeDownloader(fop);
        URL repoUrl = new URL("http://example.com/dummy.xml");

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
        mgr.registerSourceProvider(new ConstantSourceProvider(repoUrl.toString(), "dummy",
                ImmutableList.of(RepoManager.getGenericModule())));
        FakeProgressRunner runner = new FakeProgressRunner();

        // Load
        mgr.load(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS,
                ImmutableList.<RepoManager.RepoLoadedCallback>of(),
                ImmutableList.<RepoManager.RepoLoadedCallback>of(), ImmutableList.<Runnable>of(),
                runner, downloader, new FakeSettingsController(false), true);

        // Ensure we picked up the local package.
        RepositoryPackages pkgs = mgr.getPackages();
        runner.getProgressIndicator().assertNoErrorsOrWarnings();
        assertEquals(1, pkgs.getLocalPackages().size());
        assertEquals(2, pkgs.getRemotePackages().size());

        FakeProgressIndicator progress = new FakeProgressIndicator(true);
        // Install one of the packages.
        RemotePackage p = pkgs.getRemotePackages().get("dummy;bar");
        Installer basicInstaller =
                new BasicInstallerFactory().createInstaller(p, mgr, downloader, fop);
        File repoTempDir = new File(mgr.getLocalPath(), AbstractPackageOperation.REPO_TEMP_DIR_FN);
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
        mgr.load(0, ImmutableList.<RepoManager.RepoLoadedCallback>of(),
                ImmutableList.<RepoManager.RepoLoadedCallback>of(), ImmutableList.<Runnable>of(),
                runner, downloader, new FakeSettingsController(false), true);
        runner.getProgressIndicator().assertNoErrorsOrWarnings();
        File[] contents = fop.listFiles(new File(root, "dummy"));

        // Ensure it was installed on the filesystem
        assertEquals(2, contents.length);
        assertEquals(new File(root, "dummy/bar"), contents[0]);
        assertEquals(new File(root, "dummy/foo"), contents[1]);

        // Ensure it was recognized as a package.
        Map<String, ? extends LocalPackage> locals = mgr.getPackages().getLocalPackages();
        assertEquals(2, locals.size());
        assertTrue(locals.containsKey("dummy;bar"));
        LocalPackage newPkg = locals.get("dummy;bar");
        assertEquals("Test package 2", newPkg.getDisplayName());
        assertEquals("license text 2", newPkg.getLicense().getValue().trim());
        assertEquals(new Revision(4, 5, 6), newPkg.getVersion());
    }

    // Test cancellation along the way - the partial installation should be cleaned up.
    public void testCleanupWhenCancelled() throws Exception {
        MockFileOp fop = new MockFileOp();
        // We have a different package installed already.
        fop.recordExistingFile("/repo/dummy/foo/package.xml", ByteStreams
          .toByteArray(getClass().getResourceAsStream("/testPackage.xml")));
        RepoManager mgr = new RepoManagerImpl(fop);
        File root = new File("/repo");
        mgr.setLocalPath(root);
        FakeDownloader downloader = new FakeDownloader(fop);
        URL repoUrl = new URL("http://example.com/dummy.xml");

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
        mgr.registerSourceProvider(new ConstantSourceProvider(repoUrl.toString(), "dummy",
                                                              ImmutableList.of(RepoManager.getGenericModule())));
        FakeProgressRunner runner = new FakeProgressRunner();

        // Load
        mgr.load(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS,
                 ImmutableList.<RepoManager.RepoLoadedCallback>of(),
                 ImmutableList.<RepoManager.RepoLoadedCallback>of(), ImmutableList.<Runnable>of(),
                 runner, downloader, new FakeSettingsController(false), true);

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
        doTestCleanupWithProgress(pkgs, mgr, downloader, cancellingProgress1, fop);

        FakeProgressIndicator cancellingProgress2 = new FakeProgressIndicator(true);
        // Cancel right away - this will lead to installer exit when it first checks for the cancellation marker.
        cancellingProgress2.cancel();
        doTestCleanupWithProgress(pkgs, mgr, downloader, cancellingProgress1, fop);

        runner = new FakeProgressRunner();
        // Reload the packages.
        mgr.load(0, ImmutableList.<RepoManager.RepoLoadedCallback>of(),
                 ImmutableList.<RepoManager.RepoLoadedCallback>of(), ImmutableList.<Runnable>of(),
                 runner, downloader, new FakeSettingsController(false), true);
        runner.getProgressIndicator().assertNoErrorsOrWarnings();
        File[] contents = fop.listFiles(new File(root, "dummy"));

        // Ensure the package we cancelled the installation for is not on the filesystem
        assertEquals(1, contents.length);
        assertEquals(new File(root, "dummy/foo"), contents[0]);

        // Ensure it was not recognized as a package.
        Map<String, ? extends LocalPackage> locals = mgr.getPackages().getLocalPackages();
        assertEquals(1, locals.size());
        assertTrue(!locals.containsKey("dummy;bar"));
    }

    private void doTestCleanupWithProgress(
            RepositoryPackages pkgs,
            RepoManager mgr,
            Downloader downloader,
            FakeProgressIndicator progress,
            MockFileOp fop) {
        RemotePackage p = pkgs.getRemotePackages().get("dummy;bar");
        Installer basicInstaller =
                spy(new BasicInstallerFactory().createInstaller(p, mgr, downloader, fop));
        File repoTempDir = new File(mgr.getLocalPath(), AbstractPackageOperation.REPO_TEMP_DIR_FN);
        File packageOperationDir =
                new File(
                        repoTempDir,
                        String.format("%1$s01", AbstractPackageOperation.TEMP_DIR_PREFIX));
        File unzipDir = new File(packageOperationDir, BasicInstaller.FN_UNZIP_DIR);
        assertFalse(fop.exists(unzipDir));
        basicInstaller.prepare(progress.createSubProgress(0.5));
        assertFalse(fop.exists(unzipDir));
        basicInstaller.complete(progress.createSubProgress(1));
        assertFalse(fop.exists(unzipDir));
        progress.assertNoErrorsOrWarnings();
        verify((BasicInstaller) basicInstaller, times(1)).cleanup(any());
    }


    // Test installing an upgrade to an existing package.
    public void testInstallUpgrade() throws Exception {
        MockFileOp fop = new MockFileOp();
        // Record a couple existing packages.
        fop.recordExistingFile("/repo/dummy/foo/package.xml", ByteStreams
                .toByteArray(getClass().getResourceAsStream("/testPackage.xml")));
        fop.recordExistingFile("/repo/dummy/bar/package.xml", ByteStreams.toByteArray(
                getClass().getResourceAsStream("/testPackage2-lowerVersion.xml")));
        RepoManager mgr = new RepoManagerImpl(fop);
        File root = new File("/repo");
        mgr.setLocalPath(root);

        // Create the archive and register the repo to be downloaded.
        FakeDownloader downloader = new FakeDownloader(fop);
        URL repoUrl = new URL("http://example.com/dummy.xml");
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
        mgr.registerSourceProvider(new ConstantSourceProvider(repoUrl.toString(), "dummy",
                ImmutableList.of(RepoManager.getGenericModule())));
        FakeProgressRunner runner = new FakeProgressRunner();

        // Load
        mgr.load(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS,
                ImmutableList.<RepoManager.RepoLoadedCallback>of(),
                ImmutableList.<RepoManager.RepoLoadedCallback>of(), ImmutableList.<Runnable>of(),
                runner, downloader, new FakeSettingsController(false), true);
        runner.getProgressIndicator().assertNoErrorsOrWarnings();
        RepositoryPackages pkgs = mgr.getPackages();

        // Ensure the old local package was found with the right version
        Map<String, ? extends LocalPackage> locals = mgr.getPackages().getLocalPackages();
        LocalPackage oldPkg = locals.get("dummy;bar");
        assertEquals(new Revision(3), oldPkg.getVersion());

        // Ensure the new package is found with the right version
        RemotePackage update = pkgs.getRemotePackages().get("dummy;bar");
        assertEquals(new Revision(4, 5, 6), update.getVersion());

        // Install the update
        FakeProgressIndicator progress = new FakeProgressIndicator();
        Installer basicInstaller =
                new BasicInstallerFactory().createInstaller(update, mgr, downloader, fop);
        basicInstaller.prepare(progress);
        basicInstaller.complete(progress);

        // Reload the repo
        mgr.load(0, ImmutableList.<RepoManager.RepoLoadedCallback>of(),
                ImmutableList.<RepoManager.RepoLoadedCallback>of(), ImmutableList.<Runnable>of(),
                runner, downloader, new FakeSettingsController(false), true);
        runner.getProgressIndicator().assertNoErrorsOrWarnings();

        // Ensure the files are still there
        File[] contents = fop.listFiles(new File(root, "dummy"));
        assertEquals(2, contents.length);
        assertEquals(new File(root, "dummy/bar"), contents[0]);
        assertEquals(new File(root, "dummy/foo"), contents[1]);

        // Ensure the packages are still there
        locals = mgr.getPackages().getLocalPackages();
        assertEquals(2, locals.size());
        assertTrue(locals.containsKey("dummy;bar"));
        LocalPackage newPkg = locals.get("dummy;bar");
        assertEquals("Test package 2", newPkg.getDisplayName());
        assertEquals("license text 2", newPkg.getLicense().getValue().trim());

        // Ensure the update was installed
        assertEquals(new Revision(4, 5, 6), newPkg.getVersion());

        // TODO: verify the actual contents of the update?
    }

    public void testExistingDownload() throws Exception {
        MockFileOp fop = new MockFileOp();
        RepoManager mgr = new RepoManagerImpl(fop);
        File root = new File("/repo");
        mgr.setLocalPath(root);
        FakeDownloader downloader = new FakeDownloader(fop) {
            @Override
            public void downloadFully(@NonNull URL url, @NonNull File target,
                    @Nullable String checksum, @NonNull ProgressIndicator indicator)
                    throws IOException {
                super.downloadFully(url, target, checksum, indicator);
                throw new IOException("expected");
            }
        };
        URL repoUrl = new URL("http://example.com/dummy.xml");

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

        String repo = "<repo:repository\n" +
                "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n"
                +
                "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "    <remotePackage path=\"dummy;bar\">\n" +
                "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n" +
                "        <revision>\n" +
                "            <major>4</major>\n" +
                "            <minor>5</minor>\n" +
                "            <micro>6</micro>\n" +
                "        </revision>\n" +
                "        <display-name>Test package 2</display-name>\n" +
                "        <archives>\n" +
                "            <archive>\n" +
                "                <complete>\n" +
                "                    <size>2345</size>\n" +
                "                    <checksum>" +
                Downloader.hash(
                        new ByteArrayInputStream(zipBytes), zipBytes.length,
                        new FakeProgressIndicator()) +
                "</checksum>\n" +
                "                    <url>http://example.com/2/arch1</url>\n" +
                "                </complete>\n" +
                "            </archive>\n" +
                "        </archives>\n" +
                "    </remotePackage>\n" +
                "</repo:repository>";

        // The repo we're going to download
        downloader.registerUrl(repoUrl, repo.getBytes());

        // Register a source provider to get the repo
        mgr.registerSourceProvider(new ConstantSourceProvider(repoUrl.toString(), "dummy",
                ImmutableList.of(RepoManager.getGenericModule())));
        FakeProgressRunner runner = new FakeProgressRunner();

        // Load
        mgr.load(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS,
                ImmutableList.<RepoManager.RepoLoadedCallback>of(),
                ImmutableList.<RepoManager.RepoLoadedCallback>of(), ImmutableList.<Runnable>of(),
                runner, downloader, new FakeSettingsController(false), true);

        // Install one of the packages.
        RemotePackage p = mgr.getPackages().getRemotePackages().get("dummy;bar");
        Installer basicInstaller =
                new BasicInstallerFactory().createInstaller(p, mgr, downloader, fop);
        FakeProgressIndicator firstInstallProgress = new FakeProgressIndicator(true);
        boolean result = basicInstaller.prepare(firstInstallProgress);

        // be sure it was actually cancelled
        assertFalse(result);
        assertTrue(!firstInstallProgress.getWarnings().isEmpty());
        Downloader failingDownloader = new Downloader() {
            @Nullable
            @Override
            public InputStream downloadAndStream(@NonNull URL url,
                    @NonNull ProgressIndicator indicator) throws IOException {
                fail();
                return null;
            }

            @Nullable
            @Override
            public Path downloadFully(@NonNull URL url, @NonNull ProgressIndicator indicator)
                    throws IOException {
                fail();
                return null;
            }

            @Override
            public void downloadFully(@NonNull URL url, @NonNull File target,
                    @Nullable String checksum, @NonNull ProgressIndicator indicator)
                    throws IOException {
                assertEquals(checksum,
                        Downloader.hash(fop.newFileInputStream(target),
                                fop.length(target), indicator));
            }
        };
        basicInstaller =
                new BasicInstallerFactory().createInstaller(p, mgr, failingDownloader, fop);
        // Try again with the failing downloader; it should not be called.
        FakeProgressIndicator secondInstallProgress = new FakeProgressIndicator(true);
        result = basicInstaller.prepare(secondInstallProgress);
        assertTrue(result);
        result = basicInstaller.complete(secondInstallProgress);

        assertTrue(result);
        secondInstallProgress.assertNoErrorsOrWarnings();

        // Reload the packages.
        mgr.load(0, ImmutableList.<RepoManager.RepoLoadedCallback>of(),
                ImmutableList.<RepoManager.RepoLoadedCallback>of(), ImmutableList.<Runnable>of(),
                runner, downloader, new FakeSettingsController(false), true);
        runner.getProgressIndicator().assertNoErrorsOrWarnings();
        File[] contents = fop.listFiles(new File(root, "dummy"));

        // Ensure it was installed on the filesystem
        assertEquals(new File(root, "dummy/bar"), contents[0]);

        // Ensure it was recognized as a package.
        Map<String, ? extends LocalPackage> locals = mgr.getPackages().getLocalPackages();
        assertEquals(1, locals.size());
        assertTrue(locals.containsKey("dummy;bar"));
        LocalPackage newPkg = locals.get("dummy;bar");
        assertEquals("Test package 2", newPkg.getDisplayName());
        assertEquals(new Revision(4, 5, 6), newPkg.getVersion());
    }
}
