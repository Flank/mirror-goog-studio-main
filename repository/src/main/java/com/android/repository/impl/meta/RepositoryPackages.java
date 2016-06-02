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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.UpdatablePackage;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.annotation.XmlTransient;


/**
 * Store of currently-known local and remote packages, in convenient forms.
 *
 * TODO: some of the functionality of this class may no longer be needed. Reevaluate once adoption
 * has progressed further.
 */
@XmlTransient
public final class RepositoryPackages {

    /**
     * All the packages that are locally-installed and have a remotely-available update.
     */
    private Set<UpdatablePackage> mUpdatedPkgs = Sets.newTreeSet();

    /**
     * All the packages that are available remotely and don't have an installed version.
     */
    private Set<RemotePackage> mNewPkgs = Sets.newTreeSet();

    /**
     * When this object was created.
     */
    private final long myTimestampMs;

    /**
     * Multimap from all prefixes of {@code path}s (the unique IDs of packages) to
     * {@link LocalPackage}s with that path prefix. All packages that are  installed or
     * available will be included.
     *
     * For example, if there are packages
     * {@code foo;bar;baz},
     * {@code foo;bar;qux}, and
     * {@code foo;xyzzy},
     * this map will contain
     * {@code foo->[Baz package, Qux package, Xyzzy package]},
     * {@code foo;bar->[Baz package, Qux package]},
     * {@code foo;bar;baz->[Baz package]},
     * {@code foo;bar;qux->[Qux package]},
     * {@code foo;xyzzy->[Xyzzy package]}
     */
    private Multimap<String, ? extends LocalPackage> mLocalPackagesByPrefix = TreeMultimap.create();

    /**
     * Map from {@code path} (the unique ID of a package) to {@link UpdatablePackage}, including all
     * packages installed or available.
     */
    private Map<String, UpdatablePackage> mConsolidatedPkgs = Maps.newTreeMap();

    /**
     * Map from {@code path} (the unique ID of a package) to {@link LocalPackage}, including all
     * installed packages.
     */
    private Map<String, ? extends LocalPackage> mLocalPackages = Maps.newHashMap();

    /**
     * Map from {@code path} (the unique ID of a package) to {@link RemotePackage}. There may be
     * more than one version of the same {@link RemotePackage} available, for example if there is a
     * stable and a preview version available.
     */
    private Map<String, RemotePackage> mRemotePackages = Maps.newTreeMap();

    private final Object mLock = new Object();

    public RepositoryPackages() {
        myTimestampMs = System.currentTimeMillis();
    }

    public RepositoryPackages(@NonNull Map<String, LocalPackage> localPkgs,
            @NonNull Map<String, RemotePackage> remotePkgs) {
        this();
        setLocalPkgInfos(localPkgs);
        setRemotePkgInfos(remotePkgs);
    }

    /**
     * Returns the timestamp (in {@link System#currentTimeMillis()} time) when this object was
     * created.
     */
    public long getTimestampMs() {
        return myTimestampMs;
    }

    /**
     * Returns the set of packages that have local updates available.
     *
     * @return A non-null, possibly empty Set of update candidates.
     */
    @NonNull
    public Set<UpdatablePackage> getUpdatedPkgs() {
        Set<UpdatablePackage> result = mUpdatedPkgs;
        if (result == null) {
            synchronized (mLock) {
                computeUpdates();
                result = mUpdatedPkgs;
            }
        }
        return result;
    }

    /**
     * Returns the set of new remote packages that are not locally present and that the user could
     * install.
     *
     * @return A non-null, possibly empty Set of new install candidates.
     */
    @NonNull
    public Set<RemotePackage> getNewPkgs() {
        Set<RemotePackage> result = mNewPkgs;
        if (result == null) {
            synchronized (mLock) {
                computeUpdates();
                result = mNewPkgs;
            }
        }
        return result;
    }

    /**
     * Returns a map of package install ids to {@link UpdatablePackage}s representing all known
     * local and remote packages. Remote packages corresponding to local packages will be
     * represented by a single item containing both the local and remote info. {@see
     * IPkgDesc#getInstallId()}
     */
    @NonNull
    public Map<String, UpdatablePackage> getConsolidatedPkgs() {
        Map<String, UpdatablePackage> result = mConsolidatedPkgs;
        if (result == null) {
            synchronized (mLock) {
                computeUpdates();
                result = mConsolidatedPkgs;
            }
        }
        return result;
    }

    /**
     * Returns a map of {@code path} (the unique ID of a package) to {@link LocalPackage}, for all
     * packages currently installed.
     */
    @NonNull
    public Map<String, ? extends LocalPackage> getLocalPackages() {
        return mLocalPackages;
    }

    /**
     * Returns a {@link Map} from {@code path} (the unique ID of a package) to
     * {@link RemotePackage}.
     */
    @NonNull
    public Map<String, RemotePackage> getRemotePackages() {
        return mRemotePackages;
    }

    @NonNull
    public Collection<? extends LocalPackage> getLocalPackagesForPrefix(
            @Nullable String pathPrefix) {
        return mLocalPackagesByPrefix.get(pathPrefix);
    }

    /**
     * Sets the collection of known {@link LocalPackage}s, and recomputes the list of updates and
     * new packages, if {@link RemotePackage}s have been set.
     */
    public void setLocalPkgInfos(@NonNull Map<String, ? extends LocalPackage> packages) {
        synchronized (mLock) {
            mLocalPackages = packages;
            invalidate();
            computeLocalPackagePrefixes();
        }
    }

    /**
     * Sets the collection of known {@link RemotePackage}s, and recomputes the list of updates and
     * new packages, if {@link LocalPackage}s have been set.
     */
    public void setRemotePkgInfos(@NonNull Map<String, RemotePackage> packages) {
        synchronized (mLock) {
            mRemotePackages = packages;
            invalidate();
        }
    }

    private void invalidate() {
        mConsolidatedPkgs = null;
        mNewPkgs = null;
        mUpdatedPkgs = null;
    }

    private void computeUpdates() {
        Map<String, UpdatablePackage> newConsolidatedPkgs = Maps.newTreeMap();
        Set<UpdatablePackage> updates = Sets.newHashSet();
        for (String path : mLocalPackages.keySet()) {
            LocalPackage local = mLocalPackages.get(path);
            UpdatablePackage updatable = new UpdatablePackage(local);
            newConsolidatedPkgs.put(path, updatable);
            if (mRemotePackages.containsKey(path)) {
                updatable.setRemote(mRemotePackages.get(path));
                if (updatable.isUpdate()) {
                    updates.add(updatable);
                }
            }
        }
        Set<RemotePackage> news = Sets.newHashSet();
        for (String path : mRemotePackages.keySet()) {
            if (!newConsolidatedPkgs.containsKey(path)) {
                RemotePackage remote = mRemotePackages.get(path);
                news.add(remote);
                UpdatablePackage updatable = new UpdatablePackage(remote);
                newConsolidatedPkgs.put(path, updatable);
            }
        }
        mNewPkgs = news;
        mUpdatedPkgs = updates;
        mConsolidatedPkgs = newConsolidatedPkgs;
    }

    private void computeLocalPackagePrefixes() {
        Multimap<String, LocalPackage> res = TreeMultimap.create();
        for (Map.Entry<String, ? extends LocalPackage> entry : mLocalPackages.entrySet()) {
            String key = entry.getKey();
            while(true) {
                res.put(key, entry.getValue());
                int endIndex = key.lastIndexOf(RepoPackage.PATH_SEPARATOR);
                if (endIndex < 0) {
                    break;
                }
                key = key.substring(0, endIndex);
            }
        }
        mLocalPackagesByPrefix = res;
    }

}
