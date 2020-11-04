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

package com.android.repository.impl.meta;

import static com.android.repository.testframework.FakePackage.FakeLocalPackage;
import static com.android.repository.testframework.FakePackage.FakeRemotePackage;

import com.android.repository.Revision;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.UpdatablePackage;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import junit.framework.TestCase;

/**
 * Tests for {@link RepositoryPackages}
 */
public class RepositoryPackagesTest extends TestCase {

    private RepositoryPackages mPackages;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // p1 has no corresponding remote
        LocalPackage l1 = new FakeLocalPackage("p1");

        // p2 has an updated remote
        LocalPackage l2 = new FakeLocalPackage("p2");
        FakeRemotePackage r2 = new FakeRemotePackage("p2");
        r2.setRevision(new Revision(2));

        // p3 has a non-updated remote
        LocalPackage l3 = new FakeLocalPackage("p3");
        RemotePackage r3 = new FakeRemotePackage("p3");

        // p4 is only remote
        RemotePackage r4 = new FakeRemotePackage("p4");

        mPackages = new RepositoryPackages(ImmutableList.of(l1, l2, l3),
                ImmutableList.of(r2, r3, r4));
    }

    public void testConsolidated() {
        Map<String, UpdatablePackage> consolidated = mPackages.getConsolidatedPkgs();
        assertEquals(4, consolidated.size());

        UpdatablePackage updatable = consolidated.get("p1");
        assertFalse(updatable.isUpdate());
        assertTrue(updatable.hasLocal());
        assertFalse(updatable.hasRemote());
        assertEquals(new Revision(1), updatable.getRepresentative().getVersion());
        assertEquals("p1", updatable.getRepresentative().getPath());

        updatable = consolidated.get("p2");
        assertTrue(updatable.isUpdate());
        assertTrue(updatable.hasLocal());
        assertTrue(updatable.hasRemote());
        assertEquals(new Revision(1), updatable.getLocal().getVersion());
        assertEquals(new Revision(2), updatable.getRemote().getVersion());
        assertEquals("p2", updatable.getRepresentative().getPath());

        updatable = consolidated.get("p3");
        assertFalse(updatable.isUpdate());
        assertTrue(updatable.hasLocal());
        assertTrue(updatable.hasRemote());
        assertEquals(new Revision(1), updatable.getRepresentative().getVersion());
        assertEquals("p3", updatable.getRepresentative().getPath());

        updatable = consolidated.get("p4");
        assertFalse(updatable.isUpdate());
        assertFalse(updatable.hasLocal());
        assertTrue(updatable.hasRemote());
        assertEquals(new Revision(1), updatable.getRemote().getVersion());
        assertEquals("p4", updatable.getRepresentative().getPath());
    }

    public void testNewPackages() {
        Set<RemotePackage> news = mPackages.getNewPkgs();

        assertEquals(1, news.size());
        assertEquals("p4", news.iterator().next().getPath());
    }

    public void testUpdates() {
        Set<UpdatablePackage> updates = mPackages.getUpdatedPkgs();

        assertEquals(1, updates.size());
        assertEquals("p2", updates.iterator().next().getRepresentative().getPath());
    }

    public void testPrefixes() {
        List<LocalPackage> locals = new ArrayList<>();
        List<RemotePackage> remotes = new ArrayList<>();

        FakeLocalPackage l1 = new FakeLocalPackage("a;b;c");
        locals.add(l1);
        FakeRemotePackage r1 = new FakeRemotePackage("a;b;c");
        remotes.add(r1);
        FakeLocalPackage l2 = new FakeLocalPackage("a;b;d");
        locals.add(l2);
        FakeRemotePackage r2 = new FakeRemotePackage("a;b;d");
        remotes.add(r2);
        FakeLocalPackage l3 = new FakeLocalPackage("a;c");
        locals.add(l3);
        FakeRemotePackage r3 = new FakeRemotePackage("a;c");
        remotes.add(r3);
        FakeLocalPackage l4 = new FakeLocalPackage("d");
        locals.add(l4);
        FakeRemotePackage r4 = new FakeRemotePackage("d");
        remotes.add(r4);
        FakeLocalPackage localOnly = new FakeLocalPackage("l");
        locals.add(localOnly);
        FakeRemotePackage remoteOnly = new FakeRemotePackage("r");
        remotes.add(remoteOnly);

        RepositoryPackages packages = new RepositoryPackages();
        packages.setLocalPkgInfos(locals);
        packages.setRemotePkgInfos(remotes);

        Collection<LocalPackage> localPackages = packages.getLocalPackagesForPrefix("a");
        assertEquals(3, localPackages.size());
        assertTrue(localPackages.containsAll(Sets.newHashSet(l1, l2, l3)));

        Collection<RemotePackage> remotePackages = packages.getRemotePackagesForPrefix("a");
        assertEquals(3, remotePackages.size());
        assertTrue(remotePackages.containsAll(Sets.newHashSet(r1, r2, r3)));

        localPackages = packages.getLocalPackagesForPrefix("a;b");
        assertEquals(2, localPackages.size());
        assertTrue(localPackages.containsAll(Sets.newHashSet(l1, l2)));

        remotePackages = packages.getRemotePackagesForPrefix("a;b");
        assertEquals(2, remotePackages.size());
        assertTrue(remotePackages.containsAll(Sets.newHashSet(r1, r2)));

        localPackages = packages.getLocalPackagesForPrefix("a;b;c");
        assertEquals(1, localPackages.size());
        assertTrue(localPackages.contains(l1));

        remotePackages = packages.getRemotePackagesForPrefix("a;b;c");
        assertEquals(1, remotePackages.size());
        assertTrue(remotePackages.contains(r1));

        localPackages = packages.getLocalPackagesForPrefix("a;b;f");
        assertEquals(0, localPackages.size());

        remotePackages = packages.getRemotePackagesForPrefix("a;b;f");
        assertEquals(0, remotePackages.size());

        localPackages = packages.getLocalPackagesForPrefix("l");
        assertEquals(1, localPackages.size());
        assertTrue(localPackages.contains(localOnly));

        remotePackages = packages.getRemotePackagesForPrefix("l");
        assertEquals(0, remotePackages.size());

        localPackages = packages.getLocalPackagesForPrefix("r");
        assertEquals(0, localPackages.size());

        remotePackages = packages.getRemotePackagesForPrefix("r");
        assertEquals(1, remotePackages.size());
        assertTrue(remotePackages.contains(remoteOnly));
    }

}
