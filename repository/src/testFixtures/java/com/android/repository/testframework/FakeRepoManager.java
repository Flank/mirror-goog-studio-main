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
package com.android.repository.testframework;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.Downloader;
import com.android.repository.api.FallbackLocalRepoLoader;
import com.android.repository.api.FallbackRemoteRepoLoader;
import com.android.repository.api.PackageOperation;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.ProgressRunner;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.RepositorySource;
import com.android.repository.api.RepositorySourceProvider;
import com.android.repository.api.SchemaModule;
import com.android.repository.api.SettingsController;
import com.android.repository.impl.meta.RepositoryPackages;
import com.google.common.collect.Lists;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.w3c.dom.ls.LSResourceResolver;

/**
 * A fake {@link RepoManager}, for use in unit tests.
 */
public class FakeRepoManager extends RepoManager {
    private final RepositoryPackages mPackages;
    private Path mLocalPath;
    private final List<SchemaModule<?>> mModules =
            Lists.newArrayList(RepoManager.getCommonModule(), RepoManager.getGenericModule());

    public FakeRepoManager(@Nullable Path localPath, @NonNull RepositoryPackages packages) {
        mLocalPath = localPath;
        mPackages = packages;
    }

    public FakeRepoManager(@NonNull RepositoryPackages packages) {
        mPackages = packages;
    }

    @Override
    public void registerSchemaModule(@NonNull SchemaModule<?> module) {
        mModules.add(module);
    }

    @NonNull
    @Override
    public List<SchemaModule<?>> getSchemaModules() {
        return mModules;
    }

    @Override
    public void setLocalPath(@Nullable Path path) {
        mLocalPath = path;
    }

    @Nullable
    @Override
    public Path getLocalPath() {
        return mLocalPath;
    }

    @Override
    public void setFallbackLocalRepoLoader(@Nullable FallbackLocalRepoLoader local) {
    }

    @Override
    public void registerSourceProvider(@NonNull RepositorySourceProvider provider) {
    }

    @NonNull
    @Override
    public List<RepositorySourceProvider> getSourceProviders() {
        return Collections.emptyList();
    }

    @Override
    public List<RepositorySource> getSources(
            @Nullable Downloader downloader,
            @NonNull ProgressIndicator progress,
            boolean forceRefresh) {
        return Collections.emptyList();
    }

    @Override
    public void setFallbackRemoteRepoLoader(@Nullable FallbackRemoteRepoLoader remote) {
    }

    @Override
    public void load(
            long cacheExpirationMs,
            @Nullable List<RepoLoadedListener> onLocalComplete,
            @Nullable List<RepoLoadedListener> onSuccess,
            @Nullable List<Runnable> onError,
            @NonNull ProgressRunner runner,
            @Nullable Downloader downloader,
            @Nullable SettingsController settings) {}

    @Override
    public void loadSynchronously(
            long cacheExpirationMs,
            @Nullable List<RepoLoadedListener> onLocalComplete,
            @Nullable List<RepoLoadedListener> onSuccess,
            @Nullable List<Runnable> onError,
            @NonNull ProgressRunner runner,
            @Nullable Downloader downloader,
            @Nullable SettingsController settings) {}

    @Override
    public void markInvalid() {
    }

    @Override
    public void markLocalCacheInvalid() {
    }

    @Override
    public boolean reloadLocalIfNeeded(@NonNull ProgressIndicator progress) {
        return false;
    }

    @NonNull
    @Override
    public RepositoryPackages getPackages() {
        return mPackages;
    }

    @Nullable
    @Override
    public LSResourceResolver getResourceResolver(@NonNull ProgressIndicator progress) {
        return null;
    }

    @Override
    public void addLocalChangeListener(@NonNull RepoLoadedListener listener) {
    }

    @Override
    public void removeLocalChangeListener(@NonNull RepoLoadedListener listener) {
    }

    @Override
    public void addRemoteChangeListener(@NonNull RepoLoadedListener listener) {
    }

    @Override
    public void removeRemoteChangeListener(@NonNull RepoLoadedListener listener) {
    }

    @Override
    public void installBeginning(@NonNull RepoPackage repoPackage,
            @NonNull PackageOperation installer) {
    }

    @Override
    public void installEnded(@NonNull RepoPackage repoPackage) {
    }

    @Nullable
    @Override
    public PackageOperation getInProgressInstallOperation(@NonNull RepoPackage remotePackage) {
        return null;
    }
}
