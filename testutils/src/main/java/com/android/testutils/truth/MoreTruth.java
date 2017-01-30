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

package com.android.testutils.truth;

import static com.google.common.truth.Truth.assert_;
import static com.google.common.truth.TruthJUnit.assume;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.testutils.apk.Dex;
import com.android.testutils.apk.Zip;
import com.android.testutils.incremental.FileRecord;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.common.truth.Truth;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Additional entry point to {@link Truth} framework for custom {@link Subject}.
 */
public class MoreTruth {
    @NonNull
    public static FileSubject assertThat(@Nullable File file) {
        return assert_().about(FileSubjectFactory.factory()).that(file);
    }

    @NonNull
    public static PathSubject assertThat(@Nullable Path path) {
        return assert_().about(PathSubject.FACTORY).that(path);
    }

    @NonNull
    public static PathSubject assumeThat(@NonNull Path path) {
        return assume().about(PathSubject.FACTORY).that(path);
    }

    public static FileRecordSubject assertThat(@NonNull FileRecord fileRecord) {
        return assert_().about(FileRecordSubject.FACTORY).that(fileRecord);
    }

    @NonNull
    public static ZipFileSubject assertThat(@Nullable Zip zip) throws IOException {
        return  assert_().about(ZipFileSubject.FACTORY).that(zip);
    }

    @NonNull
    public static ZipFileSubject assertThatZip(@Nullable File file) throws IOException {
        Zip zip = new Zip(file.toPath());
        return assert_().about(ZipFileSubject.FACTORY).that(zip);
    }

    @NonNull
    public static ZipFileSubject assertThatZip(@Nullable Zip zip) throws IOException {
        return assert_().about(ZipFileSubject.FACTORY).that(zip);
    }

    @NonNull
    public static DexSubject assertThatDex(@Nullable File dex) {
        return assertThat(dex != null ? new Dex(dex.toPath()) : null);
    }

    @NonNull
    public static DexSubject assertThat(@Nullable Dex dex) {
        return assert_().about(DexSubject.FACTORY).that(dex);
    }

    @NonNull
    public static <T> Java8OptionalSubject<T> assertThat(
            @SuppressWarnings("OptionalUsedAsFieldOrParameterType") @NonNull
                    java.util.Optional<T> optional) {
        // need to create a new factory here so that it's generic
        return assert_().about(new SubjectFactory<Java8OptionalSubject<T>, Optional<T>>() {
            @Override
            public Java8OptionalSubject<T> getSubject(FailureStrategy fs, java.util.Optional<T> that) {
                return new Java8OptionalSubject<>(fs, that);
            }
        }).that(optional);
    }
}
