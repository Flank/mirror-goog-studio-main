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
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.SubjectFactory;
import java.io.IOException;

/** Truth support for zip files. */
public class ZipFileSubject extends AbstractZipSubject<ZipFileSubject, Zip> {

    public static final SubjectFactory<ZipFileSubject, Zip> FACTORY =
            new SubjectFactory<ZipFileSubject, Zip>() {
                @Override
                public ZipFileSubject getSubject(@NonNull FailureStrategy fs, @NonNull Zip that) {
                    return new ZipFileSubject(fs, that);
                }
            };

    public ZipFileSubject(@NonNull FailureStrategy failureStrategy, @NonNull Zip subject) {
        super(failureStrategy, subject);
    }

    @Override
    public void contains(@NonNull String path) throws IOException {
        exists();
        if (getSubject().getEntry(path) == null) {
            failWithRawMessage("'%s' does not contain '%s'", getSubject(), path);
        }
    }

    @Override
    public void doesNotContain(@NonNull String path) throws IOException {
        exists();
        if (getSubject().getEntry(path) != null) {
            failWithRawMessage("'%s' unexpectedly contains '%s'", getSubject(), path);
        }
    }

}
