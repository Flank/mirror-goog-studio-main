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

package com.android.build.gradle.integration.common.truth;

import static com.google.common.truth.Truth.assertAbout;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.testutils.apk.SplitApks;
import com.android.testutils.truth.DexClassSubject;
import com.android.testutils.truth.IndirectSubject;
import com.google.common.base.Preconditions;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import java.io.IOException;
import java.util.stream.Collectors;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;

/** Truth support for multiple apk files. */
@SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
public final class SplitApksSubject extends Subject<SplitApksSubject, SplitApks> {

    public static Subject.Factory<SplitApksSubject, SplitApks> splitApks() {
        return SplitApksSubject::new;
    }

    private SplitApksSubject(@NonNull FailureMetadata failureMetadata, SplitApks subject) {
        super(failureMetadata, subject);
    }

    public IndirectSubject<DexClassSubject> hasClass(@NonNull String name) throws IOException {
        Preconditions.checkNotNull(actual());
        @Nullable DexBackedClassDef foundClass = actual().getAllClasses().get(name);
        if (foundClass == null) {
            failWithRawMessage(
                    "%s does not contain class %s.\n Classes: \n    %s",
                    actual(),
                    name,
                    actual().getAllClasses()
                            .keySet()
                            .stream()
                            .collect(Collectors.joining("\n    ")));
        }
        return () -> assertAbout(DexClassSubject.dexClasses()).that(foundClass);
    }

    public void hasSize(int expectedSize) {
        if (actual().size() != expectedSize) {
            failWithBadResults("has a size of", expectedSize, "is", actual().size());
        }
    }
}
