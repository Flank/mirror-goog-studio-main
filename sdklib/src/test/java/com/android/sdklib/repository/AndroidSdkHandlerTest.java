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
package com.android.sdklib.repository;

import static com.android.repository.testframework.FakePackage.FakeLocalPackage;

import com.android.prefs.AndroidLocationsSingleton;
import com.android.repository.Revision;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RepositorySource;
import com.android.repository.api.RepositorySourceProvider;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.io.FileOp;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.MockFileOp;
import com.android.testutils.TestUtils;
import com.google.common.collect.ImmutableList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import junit.framework.TestCase;

/**
 * Tests for {@link AndroidSdkHandler}
 */
public class AndroidSdkHandlerTest extends TestCase {

    public void testGetLatestPackage() {
        FileOp fop = new MockFileOp();
        FakeLocalPackage p1_1 = new FakeLocalPackage("p;1.1", fop);
        p1_1.setRevision(Revision.parseRevision("1.1"));
        FakeLocalPackage p1_20 = new FakeLocalPackage("p;1.20", fop);
        p1_20.setRevision(Revision.parseRevision("1.20"));
        FakeLocalPackage p2_1 = new FakeLocalPackage("p;2.1", fop);
        p2_1.setRevision(Revision.parseRevision("2.1"));
        FakeLocalPackage p2_2_rc3 = new FakeLocalPackage("p;2.2-rc3", fop);
        p2_2_rc3.setRevision(Revision.parseRevision("2.2-rc3"));

        FakeLocalPackage qr2_0 = new FakeLocalPackage("q;r;2.0", fop);
        qr2_0.setRevision(Revision.parseRevision("2.0"));
        FakeLocalPackage qr2_1 = new FakeLocalPackage("q;r;2.1", fop);
        qr2_1.setRevision(Revision.parseRevision("2.1"));

        RepositoryPackages packages = new RepositoryPackages();
        packages.setLocalPkgInfos(ImmutableList.of(p1_1, p1_20, p2_1, p2_2_rc3, qr2_0, qr2_1));

        LocalPackage latest = AndroidSdkHandler.getLatestPackageFromPrefixCollection(
                packages.getLocalPackagesForPrefix("p"),
                null,
                false, // allowPreview
                Revision::parseRevision,
                Comparator.<Revision>naturalOrder());
        assertNotNull(latest);
        assertEquals("p;2.1", latest.getPath());

        LocalPackage earliest = AndroidSdkHandler.getLatestPackageFromPrefixCollection(
                packages.getLocalPackagesForPrefix("p"),
                null,
                false, // allowPreview
                Revision::parseRevision,
                Comparator.<Revision>reverseOrder());
        assertNotNull(earliest);
        assertEquals("p;1.1", earliest.getPath());

        LocalPackage longest = AndroidSdkHandler.getLatestPackageFromPrefixCollection(
                packages.getLocalPackagesForPrefix("p"),
                null,
                false, // allowPreview
                String::length,
                Comparator.naturalOrder());
        assertNotNull(longest);
        assertEquals("p;1.20", longest.getPath());

        longest = AndroidSdkHandler.getLatestPackageFromPrefixCollection(
                packages.getLocalPackagesForPrefix("p"),
                null,
                true, // allowPreview
                String::length,
                Comparator.naturalOrder());
        assertNotNull(longest);
        assertEquals("p;2.2-rc3", longest.getPath());

        latest = AndroidSdkHandler.getLatestPackageFromPrefixCollection(
                packages.getLocalPackagesForPrefix("p"),
                null,
                true, // allowPreview
                Revision::parseRevision,
                Comparator.<Revision>naturalOrder());
        assertNotNull(latest);
        assertEquals("p;2.2-rc3", latest.getPath());

        latest = AndroidSdkHandler.getLatestPackageFromPrefixCollection(
                packages.getLocalPackagesForPrefix("q;r"),
                null,
                true, // allowPreview
                Revision::parseRevision,
                Comparator.<Revision>naturalOrder());
        assertNotNull(latest);
        assertEquals("q;r;2.1", latest.getPath());

        latest = AndroidSdkHandler.getLatestPackageFromPrefixCollection(
                packages.getLocalPackagesForPrefix("o"),
                null,
                true, // allowPreview
                Revision::parseRevision,
                Comparator.<Revision>naturalOrder());
        assertNull(latest);

        latest = AndroidSdkHandler.getLatestPackageFromPrefixCollection(
          packages.getLocalPackagesForPrefix("p"),
          (revision) -> revision.getMinor() != 1,
          false, // allowPreview
          Revision::parseRevision,
          Comparator.<Revision>naturalOrder());
        assertNotNull(latest);
        assertEquals("p;1.20", latest.getPath());

        earliest = AndroidSdkHandler.getLatestPackageFromPrefixCollection(
          packages.getLocalPackagesForPrefix("p"),
          (revision) -> revision.getMinor() != 1,
          false, // allowPreview
          Revision::parseRevision,
          Comparator.<Revision>reverseOrder());
        assertNotNull(earliest);
        assertEquals("p;1.20", earliest.getPath());

        longest = AndroidSdkHandler.getLatestPackageFromPrefixCollection(
          packages.getLocalPackagesForPrefix("p"),
          (revision) -> revision.getMajor() != 1,
          false, // allowPreview
          String::length,
          Comparator.naturalOrder());
        assertNotNull(longest);
        assertEquals("p;2.1", longest.getPath());

        longest = AndroidSdkHandler.getLatestPackageFromPrefixCollection(
          packages.getLocalPackagesForPrefix("p"),
          (revision) -> revision.getMajor() != 2,
          true, // allowPreview
          String::length,
          Comparator.naturalOrder());
        assertNotNull(longest);
        assertEquals("p;1.20", longest.getPath());

        latest = AndroidSdkHandler.getLatestPackageFromPrefixCollection(
          packages.getLocalPackagesForPrefix("p"),
          (revision) -> !revision.isPreview(),
          true, // allowPreview
          Revision::parseRevision,
          Comparator.<Revision>naturalOrder());
        assertNotNull(latest);
        assertEquals("p;2.1", latest.getPath());

        latest = AndroidSdkHandler.getLatestPackageFromPrefixCollection(
          packages.getLocalPackagesForPrefix("q;r"),
          (revision) -> revision.getMinor() != 1,
          true, // allowPreview
          Revision::parseRevision,
          Comparator.<Revision>naturalOrder());
        assertNotNull(latest);
        assertEquals("q;r;2.0", latest.getPath());

        latest = AndroidSdkHandler.getLatestPackageFromPrefixCollection(
          packages.getLocalPackagesForPrefix("o"),
          (revision) -> revision.getMajor() == 3,
          true, // allowPreview
          Revision::parseRevision,
          Comparator.<Revision>naturalOrder());
        assertNull(latest);
    }

    public void testGetLatestPackageException() {
        FakeLocalPackage p1_1 = new FakeLocalPackage("p;1.1");
        p1_1.setRevision(Revision.parseRevision("1.1"));
        FakeLocalPackage pgarbage = new FakeLocalPackage("p;garbage");
        pgarbage.setRevision(Revision.parseRevision("1.2.3"));

        RepositoryPackages packages = new RepositoryPackages();

        packages.setLocalPkgInfos(ImmutableList.of(p1_1, pgarbage));
        try {
            AndroidSdkHandler.getLatestPackageFromPrefixCollection(
                    packages.getLocalPackagesForPrefix("p"),
                    null,
                    false, // allowPreview
                    Revision::parseRevision,
                    Comparator.<Revision>naturalOrder());
            fail();
        } catch (NumberFormatException ignored) {
        }
    }

    public void testLocale() {
        Locale origDefault = Locale.getDefault();
        Locale.setDefault(new Locale("hi", "IN"));
        try {
            List<RepositorySourceProvider> providers =
                    AndroidSdkHandler.getInstance(
                                    AndroidLocationsSingleton.INSTANCE, TestUtils.getSdk())
                            .getSdkManager(new FakeProgressIndicator())
                            .getSourceProviders();
            boolean found = false;
            StringBuilder urls = new StringBuilder();
            for (RepositorySourceProvider provider : providers) {
                try {
                    for (RepositorySource source :
                            provider.getSources(null, new FakeProgressIndicator(), false)) {
                        String url = source.getUrl();
                        if (url.equals(
                                "https://dl.google.com/android/repository/repository2-1.xml")) {
                            found = true;
                            break;
                        }
                        urls.append(url + "\n");
                    }
                } catch (Exception e) {
                    // ignore, remote providers will throw.
                }
            }
            assertTrue(urls.toString(), found);
        } finally {
            Locale.setDefault(origDefault);
        }
    }
}
