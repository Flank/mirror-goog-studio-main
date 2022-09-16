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
package com.android.repository.impl.manager;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.repository.api.LocalPackage;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.installer.AbstractPackageOperation;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.testutils.file.InMemoryFileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import org.junit.Test;

/**
 * Tests for {@link LocalRepoLoaderImpl}
 */

public class LocalRepoLoaderImplTest {

    static final String LOCAL_PACKAGE =
            "<repo:repository\n"
                    + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n"
                    + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                    + "    <localPackage path=\"foo\" obsolete=\"true\">\n"
                    + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
                    + "        <revision>\n"
                    + "            <major>1</major>\n"
                    + "        </revision>\n"
                    + "        <display-name>Test package</display-name>\n"
                    + "    </localPackage>\n"
                    + "</repo:repository>";

    static final String LOCAL_PACKAGE_2 = "<repo:repository\n"
            + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n"
            + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
            + "    <localPackage path=\"bar\" obsolete=\"true\">\n"
            + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
            + "        <revision>\n"
            + "            <major>1</major>\n"
            + "        </revision>\n"
            + "        <display-name>Test package 2</display-name>\n"
            + "    </localPackage>\n"
            + "</repo:repository>";


    // check that we update and read the hash file correctly
    @Test
    public void testHashFile() throws Exception {
        FakeProgressIndicator progress = new FakeProgressIndicator();
        Path repoRoot = InMemoryFileSystems.createInMemoryFileSystemAndFolder("repo");
        Path knownPackagesFile = repoRoot.resolve(LocalRepoLoaderImpl.KNOWN_PACKAGES_HASH_FN);
        Files.createDirectories(repoRoot);
        RepoManager mgr = new RepoManagerImpl();
        LocalRepoLoaderImpl loader = new LocalRepoLoaderImpl(repoRoot, mgr, null);
        // If there's no file we should think that an update is needed.
        assertTrue(loader.needsUpdate(0, false));
        assertTrue(loader.needsUpdate(Long.MAX_VALUE, false));

        // After the load, if there are no packages found, we shouldn't create a file
        loader.getPackages(progress);
        assertFalse(Files.exists(knownPackagesFile));

        // check that the file is created when loading when there are packages
        Path package1 = repoRoot.resolve("foo/package.xml");
        InMemoryFileSystems.recordExistingFile(package1, LOCAL_PACKAGE);
        // loader caches the packages it found, so we need to recreate it
        loader = new LocalRepoLoaderImpl(repoRoot, mgr, null);
        loader.getPackages(progress);
        assertTrue(Files.exists(knownPackagesFile));

        // check that iff the file exists and is newer than the last update,
        // shallow check returns true
        Files.setLastModifiedTime(knownPackagesFile, FileTime.fromMillis(1000L));
        assertTrue(loader.needsUpdate(1, false));
        assertFalse(loader.needsUpdate(2000, false));

        // check that deep check returns false if knownpackages is updated more recently than
        // package.xml
        Files.setLastModifiedTime(package1, FileTime.fromMillis(1L));
        assertFalse(loader.needsUpdate(2000, true));

        // check that deep check returns true if package.xml is updated more recently than
        // knownpackages
        Files.setLastModifiedTime(package1, FileTime.fromMillis(2000L));
        assertTrue(loader.needsUpdate(2000, true));

        // check that deep check returns true if there's an unknown package
        InMemoryFileSystems.recordExistingFile(
                repoRoot.resolve("bar/package.xml"), LOCAL_PACKAGE_2);
        loader = new LocalRepoLoaderImpl(repoRoot, mgr, null);
        assertTrue(loader.needsUpdate(2000, true));

        // now reload and check that update is no longer needed
        loader.getPackages(progress);
        long currentTime = System.currentTimeMillis();
        assertFalse(loader.needsUpdate(currentTime + 1000, true));
        assertFalse(loader.needsUpdate(currentTime + 1000, false));

        // remove package and ensure shallow check doesn't catch it
        loader = new LocalRepoLoaderImpl(repoRoot, mgr, null);
        Files.delete(repoRoot.resolve("bar/package.xml"));
        assertFalse(loader.needsUpdate(currentTime + 1000, false));

        // but deep check does
        assertTrue(loader.needsUpdate(currentTime + 1000, true));
    }

    @Test
    public void testNoScanningForMetadataFolders() {
        FakeProgressIndicator progress = new FakeProgressIndicator();
        // Allow the repo root name to start with metadata prefix. Although it wouldn't normally
        // be the case, there is no reason to disallow that.
        Path repoRoot =
                InMemoryFileSystems.createInMemoryFileSystemAndFolder(
                        AbstractPackageOperation.METADATA_FILENAME_PREFIX + "repo");
        RepoManager mgr = new RepoManagerImpl();

        // Check that the metadata folders are not scanned for packages.
        Path package1 = repoRoot.resolve("foo/package.xml");
        InMemoryFileSystems.recordExistingFile(package1, LOCAL_PACKAGE);
        Path package2 =
                repoRoot.resolve(
                        AbstractPackageOperation.METADATA_FILENAME_PREFIX + "bar/package.xml");
        InMemoryFileSystems.recordExistingFile(package2, LOCAL_PACKAGE_2);
        // loader caches the packages it found, so we need to recreate it
        LocalRepoLoaderImpl loader = new LocalRepoLoaderImpl(repoRoot, mgr, null);
        Map<String, LocalPackage> localPackages = loader.getPackages(progress);
        assertEquals(1, localPackages.size());
        assertEquals(package1.getParent(), localPackages.values().iterator().next().getLocation());
    }

    @Test
    public void testNoScanningResourceCacheFolders() {
        FakeProgressIndicator progress = new FakeProgressIndicator();
        Path repoRoot = InMemoryFileSystems.createInMemoryFileSystemAndFolder("repo");
        RepoManager mgr = new RepoManagerImpl();

        // Check that the resource cache folders are not scanned for packages.
        Path package1 = repoRoot.resolve("icons/package.xml");
        InMemoryFileSystems.recordExistingFile(package1, LOCAL_PACKAGE);
        Path package2 = repoRoot.resolve("foo/icons/package.xml");
        InMemoryFileSystems.recordExistingFile(package2, LOCAL_PACKAGE_2);

        LocalRepoLoaderImpl loader = new LocalRepoLoaderImpl(repoRoot, mgr, null);
        Map<String, LocalPackage> localPackages = loader.getPackages(progress);
        assertEquals(1, localPackages.size());
        assertEquals(package2.getParent(), localPackages.values().iterator().next().getLocation());
    }
}
