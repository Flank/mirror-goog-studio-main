/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.integration.common.truth;


import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.Format;
import com.android.build.gradle.internal.pipeline.SubStream;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;

/** Truth Subject for {@link com.android.build.gradle.internal.pipeline.SubStream} */
public class SubStreamSubject extends Subject<SubStreamSubject, SubStream> {

    public static Subject.Factory<SubStreamSubject, SubStream> subStreams() {
        return SubStreamSubject::new;
    }

    @NonNull
    public static SubStreamSubject assertThat(@Nullable SubStream stream) {
        return Truth.assertAbout(subStreams()).that(stream);
    }

    SubStreamSubject(@NonNull FailureMetadata failureMetadata, @NonNull SubStream subject) {
        super(failureMetadata, subject);
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void hasName(@NonNull String name) {
        final String actualName = actual().getName();
        if (!name.equals(actualName)) {
            failWithBadResults("has name", name, "is", actualName);
        }
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void hasFormat(@NonNull Format format) {
        final Format actualFormat = actual().getFormat();

        if (format != actualFormat) {
            failWithBadResults("has format", format, "is", actualFormat);
        }
    }
}
