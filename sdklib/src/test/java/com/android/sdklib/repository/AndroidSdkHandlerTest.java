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

import com.android.repository.Revision;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RepositorySource;
import com.android.repository.api.RepositorySourceProvider;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.testutils.TestUtils;
import com.google.common.collect.ImmutableList;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import junit.framework.TestCase;

/**
 * Tests for {@link AndroidSdkHandler}
 */
public class AndroidSdkHandlerTest extends TestCase {

    public void testGetLatestPackage() {
        FakeLocalPackage p1_1 = new FakeLocalPackage("p;1.1");
        p1_1.setRevision(Revision.parseRevision("1.1"));
        FakeLocalPackage p1_20 = new FakeLocalPackage("p;1.20");
        p1_20.setRevision(Revision.parseRevision("1.20"));
        FakeLocalPackage p2_1 = new FakeLocalPackage("p;2.1");
        p2_1.setRevision(Revision.parseRevision("2.1"));
        FakeLocalPackage p2_2_rc3 = new FakeLocalPackage("p;2.2-rc3");
        p2_2_rc3.setRevision(Revision.parseRevision("2.2-rc3"));

        FakeLocalPackage qr2_0 = new FakeLocalPackage("q;r;2.0");
        qr2_0.setRevision(Revision.parseRevision("2.0"));
        FakeLocalPackage qr2_1 = new FakeLocalPackage("q;r;2.1");
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
        assertEquals(latest.getPath(), "p;2.1");

        LocalPackage earliest = AndroidSdkHandler.getLatestPackageFromPrefixCollection(
                packages.getLocalPackagesForPrefix("p"),
                null,
                false, // allowPreview
                Revision::parseRevision,
                Comparator.<Revision>reverseOrder());
        assertNotNull(earliest);
        assertEquals(earliest.getPath(), "p;1.1");

        LocalPackage longest = AndroidSdkHandler.getLatestPackageFromPrefixCollection(
                packages.getLocalPackagesForPrefix("p"),
                null,
                false, // allowPreview
                String::length,
                Comparator.naturalOrder());
        assertNotNull(longest);
        assertEquals(longest.getPath(), "p;1.20");

        longest = AndroidSdkHandler.getLatestPackageFromPrefixCollection(
                packages.getLocalPackagesForPrefix("p"),
                null,
                true, // allowPreview
                String::length,
                Comparator.naturalOrder());
        assertNotNull(longest);
        assertEquals(longest.getPath(), "p;2.2-rc3");

        latest = AndroidSdkHandler.getLatestPackageFromPrefixCollection(
                packages.getLocalPackagesForPrefix("p"),
                null,
                true, // allowPreview
                Revision::parseRevision,
                Comparator.<Revision>naturalOrder());
        assertNotNull(latest);
        assertEquals(latest.getPath(), "p;2.2-rc3");

        latest = AndroidSdkHandler.getLatestPackageFromPrefixCollection(
                packages.getLocalPackagesForPrefix("q;r"),
                null,
                true, // allowPreview
                Revision::parseRevision,
                Comparator.<Revision>naturalOrder());
        assertNotNull(latest);
        assertEquals(latest.getPath(), "q;r;2.1");

        latest = AndroidSdkHandler.getLatestPackageFromPrefixCollection(
                packages.getLocalPackagesForPrefix("o"),
                null,
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
            Set<RepositorySourceProvider> providers =
                    AndroidSdkHandler.getInstance(TestUtils.getSdk())
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
