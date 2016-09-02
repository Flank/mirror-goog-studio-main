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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.repository.api.RepoManager;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.MockFileOp;
import java.io.File;
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
        MockFileOp fop = new MockFileOp();
        File repoRoot = new File("/repo");
        File knownPackagesFile = new File(repoRoot, LocalRepoLoaderImpl.KNOWN_PACKAGES_HASH_FN);
        fop.mkdirs(repoRoot);
        RepoManager mgr = new RepoManagerImpl(fop);
        LocalRepoLoaderImpl loader = new LocalRepoLoaderImpl(repoRoot, mgr, null, fop);
        // If there's no file we should think that an update is needed.
        assertTrue(loader.needsUpdate(0, false));
        assertTrue(loader.needsUpdate(Long.MAX_VALUE, false));

        // After the load, if there are no packages found, we shouldn't create a file
        loader.getPackages(progress);
        assertFalse(fop.exists(knownPackagesFile));

        // check that the file is created when loading when there are packages
        File package1 = new File(repoRoot, "foo/package.xml");
        fop.recordExistingFile(package1.getPath(),
                LOCAL_PACKAGE.getBytes());
        // loader caches the packages it found, so we need to recreate it
        loader = new LocalRepoLoaderImpl(repoRoot, mgr, null, fop);
        loader.getPackages(progress);
        assertTrue(fop.exists(knownPackagesFile));

        // check that iff the file exists and is newer than the last update,
        // shallow check returns true
        fop.setLastModified(knownPackagesFile, 1000);
        assertTrue(loader.needsUpdate(1, false));
        assertFalse(loader.needsUpdate(2000, false));

        // check that deep check returns false if knownpackages is updated more recently than
        // package.xml
        fop.setLastModified(package1, 1);
        assertFalse(loader.needsUpdate(2000, true));

        // check that deep check returns true if package.xml is updated more recently than
        // knownpackages
        fop.setLastModified(package1, 2000);
        assertTrue(loader.needsUpdate(2000, true));

        // check that deep check returns true if there's an unknown package
        fop.recordExistingFile(new File(repoRoot, "bar/package.xml").getPath(),
                LOCAL_PACKAGE_2.getBytes());
        loader = new LocalRepoLoaderImpl(repoRoot, mgr, null, fop);
        assertTrue(loader.needsUpdate(2000, true));

        // now reload and check that update is no longer needed
        loader.getPackages(progress);
        long currentTime = System.currentTimeMillis();
        assertFalse(loader.needsUpdate(currentTime + 1000, true));
        assertFalse(loader.needsUpdate(currentTime + 1000, false));

        // remove package and ensure shallow check doesn't catch it
        loader = new LocalRepoLoaderImpl(repoRoot, mgr, null, fop);
        fop.delete(new File(repoRoot, "bar/package.xml"));
        assertFalse(loader.needsUpdate(currentTime + 1000, false));

        // but deep check does
        assertTrue(loader.needsUpdate(currentTime + 1000, true));

    }


}
