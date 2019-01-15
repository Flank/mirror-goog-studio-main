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

import com.android.annotations.NonNull;
import com.android.builder.model.AndroidArtifact;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;

public class ArtifactSubject extends Subject<ArtifactSubject, AndroidArtifact> {

    public static Subject.Factory<ArtifactSubject, AndroidArtifact> artifacts() {
        return ArtifactSubject::new;
    }

    public ArtifactSubject(
            @NonNull FailureMetadata failureMetadata, @NonNull AndroidArtifact subject) {
        super(failureMetadata, subject);
    }
}
