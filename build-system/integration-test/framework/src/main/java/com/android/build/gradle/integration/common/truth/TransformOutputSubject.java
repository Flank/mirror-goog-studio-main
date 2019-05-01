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
import com.android.build.gradle.integration.common.fixture.app.TransformOutputContent;
import com.android.build.gradle.internal.pipeline.SubStream;
import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;

public class TransformOutputSubject
        extends Subject<TransformOutputSubject, TransformOutputContent> {

    public static Subject.Factory<TransformOutputSubject, TransformOutputContent>
            transformOutputs() {
        return TransformOutputSubject::new;
    }

    TransformOutputSubject(
            @NonNull FailureMetadata failureMetadata, @NonNull TransformOutputContent subject) {
        super(failureMetadata, subject);
    }

    /** Attests (with a side-effect failure) that the subject contains the supplied item. */
    public final void containsByName(@Nullable String name) {
        for (SubStream subStream : actual()) {
            if (subStream.getName().equals(name)) {
                return;
            }
        }

        failWithoutActual(
                Fact.simpleFact(
                        String.format(
                                "%s should have contained stream named <%s>",
                                actualAsString(), name)));
    }
}
