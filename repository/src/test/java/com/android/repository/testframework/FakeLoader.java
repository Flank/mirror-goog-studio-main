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
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.SettingsController;
import com.android.repository.impl.manager.LocalRepoLoader;
import com.android.repository.impl.manager.RemoteRepoLoader;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link LocalRepoLoader} or {@link RemoteRepoLoader} that returns the given packages.
 *
 * TODO: Unless more shared functionality is needed it would probably make sense to split this up,
 * or maybe replaced with a Mockito-mocked class.
 */
public class FakeLoader<T extends RepoPackage> implements LocalRepoLoader, RemoteRepoLoader {

    private final Map<String, T> mPackages;

    public FakeLoader() {
        mPackages = new HashMap<>();
    }

    public FakeLoader(@NonNull Map<String, T> packages) {
        mPackages = packages;
    }

    @NonNull
    @Override
    public Map<String, LocalPackage> getPackages(@NonNull ProgressIndicator progress) {
        //noinspection unchecked
        return (Map<String, LocalPackage>) run();
    }

    @Override
    public boolean needsUpdate(long lastLocalRefreshMs, boolean deepCheck) {
        return true;
    }

    @NonNull
    @Override
    public Map<String, RemotePackage> fetchPackages(@NonNull ProgressIndicator progress,
            @NonNull Downloader downloader, @Nullable SettingsController settings) {
        //noinspection unchecked
        return (Map<String, RemotePackage>) run();
    }

    protected Map<String, T> run() {
        return mPackages;
    }
}
