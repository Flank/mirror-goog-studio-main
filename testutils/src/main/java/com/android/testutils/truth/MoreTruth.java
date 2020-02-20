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
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import java.io.File;
import java.io.IOException;

/**
 * Additional entry point to {@link Truth} framework for custom {@link Subject}.
 *
 * @deprecated Import the helpers from the subject classes themselves.
 */
@Deprecated
public class MoreTruth {

    /**
     * DO NOT USE this method as zip files will not be closed on Windows.
     *
     * <p>Use this instead: Zip(zipFile).use { [ZipFileSubject.]assertThat(it)... }
     */
    @Deprecated
    @NonNull
    public static ZipFileSubject assertThatZip(@NonNull File file) throws IOException {
        return ZipFileSubject.assertThatZip(file);
    }
}
