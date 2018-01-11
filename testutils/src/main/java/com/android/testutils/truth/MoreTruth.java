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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.testutils.apk.Dex;
import com.android.testutils.apk.Zip;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Additional entry point to {@link Truth} framework for custom {@link Subject}.
 */
public class MoreTruth {
    @NonNull
    public static FileSubject assertThat(@Nullable File file) {
        return FileSubject.assertThat(file);
    }

    @NonNull
    public static PathSubject assertThat(@Nullable Path path) {
        return PathSubject.assertThat(path);
    }

    @NonNull
    public static ZipFileSubject assertThat(@NonNull Zip zip) throws IOException {
        return ZipFileSubject.assertThat(zip);
    }

    @NonNull
    public static ZipFileSubject assertThatZip(@NonNull File file) throws IOException {
        return ZipFileSubject.assertThatZip(file);
    }

    @NonNull
    public static ZipFileSubject assertThatZip(@NonNull Zip zip) throws IOException {
        return assertThat(zip);
    }

    @NonNull
    public static DexSubject assertThatDex(@Nullable File dex) {
        return DexSubject.assertThatDex(dex);
    }

    @NonNull
    public static DexSubject assertThat(@Nullable Dex dex) {
        return DexSubject.assertThat(dex);
    }

    @NonNull
    public static <T> Java8OptionalSubject<T> assertThat(
            @SuppressWarnings("OptionalUsedAsFieldOrParameterType") @NonNull
                    java.util.Optional<T> optional) {
        return Java8OptionalSubject.assertThat(optional);
    }
}
