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

package com.android.repository.impl.installer;

import static com.android.repository.testframework.FakePackage.FakeRemotePackage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.Downloader;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.manager.RepoManagerImpl;
import com.android.repository.testframework.FakeDownloader;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.testutils.file.InMemoryFileSystems;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Tests for {@link AbstractInstaller}
 */
public class AbstractInstallerTest {
    @Test
    public void cantInstallInChild() throws Exception {
        Path sdkRoot = InMemoryFileSystems.createInMemoryFileSystemAndFolder("sdk/foo").getParent();
        Files.write(
                sdkRoot.resolve("foo/package.xml"),
                ("<repo:repository\n"
                                + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n"
                                + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                                + "    <localPackage path=\"foo\">\n"
                                + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
                                + "        <revision>\n"
                                + "            <major>3</major>\n"
                                + "        </revision>\n"
                                + "        <display-name>The first Android platform ever</display-name>\n"
                                + "    </localPackage>\n"
                                + "</repo:repository>")
                        .getBytes(StandardCharsets.UTF_8));
        RepoManager mgr = new RepoManagerImpl();
        mgr.setLocalPath(sdkRoot);
        FakeProgressIndicator progress = new FakeProgressIndicator();
        mgr.loadSynchronously(0, progress, null, null);

        FakeRemotePackage remote = new FakeRemotePackage("foo;bar");
        remote.setCompleteUrl("http://www.example.com/package.zip");
        FakeDownloader downloader = new FakeDownloader(sdkRoot.getRoot().resolve("tmp"));

        assertFalse(new TestInstaller(remote, mgr, downloader).prepare(progress));
        assertTrue(progress.getWarnings().stream().anyMatch(warning -> warning.contains("child")));
    }

    @Test
    public void cantInstallInParent() throws Exception {
        Path sdkRoot = InMemoryFileSystems.createInMemoryFileSystemAndFolder("sdk");
        Files.createDirectories(sdkRoot.resolve("foo/bar"));
        Files.write(
                sdkRoot.resolve("foo/bar/package.xml"),
                ("<repo:repository\n"
                                + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n"
                                + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                                + "    <localPackage path=\"foo;bar\">\n"
                                + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
                                + "        <revision>\n"
                                + "            <major>3</major>\n"
                                + "        </revision>\n"
                                + "        <display-name>The first Android platform ever</display-name>\n"
                                + "    </localPackage>\n"
                                + "</repo:repository>")
                        .getBytes(StandardCharsets.UTF_8));
        RepoManager mgr = new RepoManagerImpl();
        mgr.setLocalPath(sdkRoot);
        FakeProgressIndicator progress = new FakeProgressIndicator();
        mgr.loadSynchronously(0, progress, null, null);

        FakeRemotePackage remote = new FakeRemotePackage("foo");
        remote.setCompleteUrl("http://www.example.com/package.zip");
        FakeDownloader downloader = new FakeDownloader(sdkRoot.getRoot().resolve("tmp"));

        TestInstaller installer = new TestInstaller(remote, mgr, downloader);
        // Install will still work, but in a different directory
        assertTrue(installer.prepare(progress));
        assertEquals(sdkRoot.resolve("foo-2"), installer.getLocation(progress));
    }

    @Test
    public void dontOverwriteExisting() throws Exception {
        Path sdkRoot = InMemoryFileSystems.createInMemoryFileSystemAndFolder("sdk");
        Files.createDirectories(sdkRoot.resolve("foo/bar"));
        Files.write(
                sdkRoot.resolve("foo/bar/package.xml"),
                ("<repo:repository\n"
                                + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n"
                                + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                                + "    <localPackage path=\"foo;notbar\">\n"
                                + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
                                + "        <revision>\n"
                                + "            <major>3</major>\n"
                                + "        </revision>\n"
                                + "        <display-name>The first Android platform ever</display-name>\n"
                                + "    </localPackage>\n"
                                + "</repo:repository>")
                        .getBytes(StandardCharsets.UTF_8));
        RepoManager mgr = new RepoManagerImpl();
        mgr.setLocalPath(sdkRoot);
        FakeProgressIndicator progress = new FakeProgressIndicator();
        mgr.loadSynchronously(0, progress, null, null);

        FakeRemotePackage remote = new FakeRemotePackage("foo;bar");
        remote.setCompleteUrl("http://www.example.com/package.zip");
        FakeDownloader downloader = new FakeDownloader(sdkRoot.getRoot().resolve("tmp"));

        TestInstaller installer = new TestInstaller(remote, mgr, downloader);
        assertTrue(installer.prepare(progress));
        assertTrue(
                progress.getWarnings().stream()
                        .anyMatch(warning -> warning.contains("\"fake package\" (foo;bar)")));
        assertTrue(
                progress.getWarnings().stream()
                        .anyMatch(
                                warning ->
                                        warning.contains(
                                                "\"The first Android platform ever\" (foo;notbar)")));
        assertEquals(sdkRoot.resolve("foo/bar-2"), installer.getLocation(progress));
    }

    @Test
    public void useExistingPath() throws Exception {
        Path sdkRoot = InMemoryFileSystems.createInMemoryFileSystemAndFolder("sdk");
        Files.createDirectories(sdkRoot.resolve("foo/notbar"));
        Files.write(
                sdkRoot.resolve("foo/notbar/package.xml"),
                ("<repo:repository\n"
                                + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n"
                                + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                                + "    <localPackage path=\"foo;bar\">\n"
                                + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
                                + "        <revision>\n"
                                + "            <major>3</major>\n"
                                + "        </revision>\n"
                                + "        <display-name>The first Android platform ever</display-name>\n"
                                + "    </localPackage>\n"
                                + "</repo:repository>")
                        .getBytes(StandardCharsets.UTF_8));
        RepoManager mgr = new RepoManagerImpl();
        mgr.setLocalPath(sdkRoot);
        FakeProgressIndicator progress = new FakeProgressIndicator();
        mgr.loadSynchronously(0, progress, null, null);

        FakeRemotePackage remote = new FakeRemotePackage("foo;bar");
        remote.setCompleteUrl("http://www.example.com/package.zip");
        FakeDownloader downloader = new FakeDownloader(sdkRoot.getRoot().resolve("tmp"));

        TestInstaller installer = new TestInstaller(remote, mgr, downloader);
        assertTrue(installer.prepare(progress));
        assertEquals(
                InMemoryFileSystems.getPlatformSpecificPath("/sdk/foo/notbar"),
                installer.getLocation(progress).toString());
    }

    @Test
    public void deleteUnusedDirs() {
        Path sdkRoot = InMemoryFileSystems.createInMemoryFileSystemAndFolder("sdk");
        RepoManager mgr = new RepoManagerImpl();
        mgr.setLocalPath(sdkRoot);
        FakeRemotePackage remote = new FakeRemotePackage("foo;bar");
        remote.setCompleteUrl("http://www.example.com/package.zip");
        FakeDownloader downloader = new FakeDownloader(sdkRoot.getRoot().resolve("tmp"));
        // Consume temp dir 1
        AbstractPackageOperation.getNewPackageOperationTempDir(
                mgr, AbstractPackageOperation.TEMP_DIR_PREFIX);
        // prepare() will create an keep a reference to temp dir 2
        new TestInstaller(remote, mgr, downloader).prepare(new FakeProgressIndicator(true));
        Path tempDir;
        // Create the remaining temp dirs
        do {
            tempDir =
                    AbstractPackageOperation.getNewPackageOperationTempDir(
                            mgr, AbstractPackageOperation.TEMP_DIR_PREFIX);
        } while (tempDir != null);
        FakeRemotePackage remote2 = new FakeRemotePackage("foo;baz");
        TestInstaller installer = new TestInstaller(remote2, mgr, downloader);
        // This will cause the unreferenced temp dirs to be GCd (and a new one created)
        installer.prepare(new FakeProgressIndicator(true));
        // This will cause the newly created temp dir to be deleted.
        installer.complete(new FakeProgressIndicator(true));
        for (int i = 1; i < AbstractPackageOperation.MAX_PACKAGE_OPERATION_TEMP_DIRS; i++) {
            Path dir =
                    AbstractPackageOperation.getPackageOperationTempDir(
                            mgr, AbstractPackageOperation.TEMP_DIR_PREFIX, i);
            // Only temp dir 2 should remain, since it's still referenced by the incomplete install.
            assertEquals(i == 2, Files.exists(dir));
        }
    }

    @Test
    public void installerProperties() {
        Path sdkRoot = InMemoryFileSystems.createInMemoryFileSystemAndFolder("sdk");
        RepoManager mgr = new RepoManagerImpl();
        mgr.setLocalPath(sdkRoot);
        RemotePackage remote = new FakeRemotePackage("foo;bar");
        FakeDownloader downloader = new FakeDownloader(sdkRoot.getRoot().resolve("tmp"));
        AbstractInstaller installer = new TestInstaller(remote, mgr, downloader);
        assertSame(installer.getPackage(), remote);
        assertEquals(
                installer.getName(),
                String.format(
                        "Install %1$s (revision: %2$s)",
                        remote.getDisplayName(), remote.getVersion()));
    }

    private static class TestInstaller extends AbstractInstaller {

        public TestInstaller(
                @NonNull RemotePackage p,
                @NonNull RepoManager manager,
                @NonNull Downloader downloader) {
            super(p, manager, downloader);
        }

        @Override
        protected boolean doComplete(
                @Nullable Path installTemp, @NonNull ProgressIndicator progress) {
            return true;
        }

        @Override
        protected boolean doPrepare(
                @NonNull Path installTempPath, @NonNull ProgressIndicator progress) {
            return true;
        }
    }
}
