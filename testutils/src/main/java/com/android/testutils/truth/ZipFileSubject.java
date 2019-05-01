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
import com.android.testutils.apk.Zip;
import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import java.io.File;
import java.io.IOException;

/** Truth support for zip files. */
public class ZipFileSubject extends AbstractZipSubject<ZipFileSubject, Zip> {

    public static Subject.Factory<ZipFileSubject, Zip> zips() {
        return ZipFileSubject::new;
    }

    public ZipFileSubject(@NonNull FailureMetadata failureMetadata, @NonNull Zip subject) {
        super(failureMetadata, subject);
    }

    public static ZipFileSubject assertThat(@NonNull Zip zip) {
        return Truth.assertAbout(zips()).that(zip);
    }

    public static ZipFileSubject assertThatZip(@NonNull File file) throws IOException {
        Zip zip = new Zip(file.toPath());
        return Truth.assertAbout(zips()).that(zip);
    }

    @Override
    public void contains(@NonNull String path) {
        exists();
        if (actual().getEntry(path) == null) {
            failWithoutActual(
                    Fact.simpleFact(String.format("'%s' does not contain '%s'", actual(), path)));
        }
    }

    @Override
    public void doesNotContain(@NonNull String path) {
        exists();
        if (actual().getEntry(path) != null) {
            failWithoutActual(
                    Fact.simpleFact(
                            String.format("'%s' unexpectedly contains '%s'", actual(), path)));
        }
    }

}
