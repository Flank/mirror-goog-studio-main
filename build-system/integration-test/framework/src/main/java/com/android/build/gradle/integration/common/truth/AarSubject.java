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

package com.android.build.gradle.integration.common.truth;

import static com.google.common.truth.Truth.assertAbout;

import com.android.annotations.NonNull;
import com.android.testutils.apk.Aar;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Truth support for aar files. */
public class AarSubject extends AbstractAndroidSubject<AarSubject, Aar> {

    public static Subject.Factory<AarSubject, Aar> aars() {
        return AarSubject::new;
    }

    public AarSubject(@NonNull FailureMetadata failureMetadata, @NonNull Aar subject) {
        super(failureMetadata, subject);
        validateAar();
    }

    @NonNull
    public static AarSubject assertThat(@NonNull Aar aar) {
        return assertAbout(aars()).that(aar);
    }

    private void validateAar() {
        if (actual().getEntry("AndroidManifest.xml") == null) {
            failWithRawMessage("Invalid aar, should contain " + "AndroidManifest.xml");
        }
    }

    @NonNull
    public StringSubject textSymbolFile() throws IOException {
        Path entry = actual().getEntry("R.txt");
        Preconditions.checkNotNull(entry);
        return Truth.assertThat(new String(Files.readAllBytes(entry), Charsets.UTF_8));
    }
}
