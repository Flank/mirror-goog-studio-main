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
package com.android.repository.testframework;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.Dependency;
import com.android.repository.impl.meta.RevisionType;

/**
 * A {@link Dependency} for use in {@link FakePackage}.
 */
public class FakeDependency extends Dependency {

    private final String mPath;

    private final RevisionType mRevision;

    private final Boolean mSoft;

    public FakeDependency(String path) {
        this(path, null, null, null, false);
    }

    public FakeDependency(String path, final Integer major, final Integer minor,
            final Integer micro) {
        this(path, major, minor, micro, false);
    }

    public FakeDependency(
            @NonNull String path,
            @Nullable final Integer major,
            @Nullable final Integer minor,
            @Nullable final Integer micro,
            @Nullable Boolean soft) {
        mPath = path;
        mRevision = major == null ? null : new RevisionType() {
            @Override
            public int getMajor() {
                return major;
            }

            @Nullable
            @Override
            public Integer getMicro() {
                return minor;
            }

            @Nullable
            @Override
            public Integer getMinor() {
                return micro;
            }
        };
        mSoft = soft;
    }

    @NonNull
    @Override
    public String getPath() {
        return mPath;
    }

    @Nullable
    @Override
    public RevisionType getMinRevision() {
        return mRevision;
    }

    @Override
    @Nullable
    public Boolean isSoft() {
        return mSoft;
    }
}
