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

import com.android.annotations.NonNull;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import java.util.Optional;

/**
 * Truth Subject for Java 8 Optional.
 */
@SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
public class Java8OptionalSubject<T> extends Subject<Java8OptionalSubject<T>, Optional<T>> {

    private static final SubjectFactory<Java8OptionalSubject<Object>, Optional<Object>> FACTORY =
            new SubjectFactory<Java8OptionalSubject<Object>, Optional<Object>>() {
                @Override
                public Java8OptionalSubject<Object> getSubject(
                        FailureStrategy fs, Optional<Object> that) {
                    return new Java8OptionalSubject<>(fs, that);
                }
            };

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public Java8OptionalSubject(
            FailureStrategy failureStrategy, Optional<T> subject) {
        super(failureStrategy, subject);
    }
    @NonNull
    public static <T> Java8OptionalSubject<T> assertThat(
            @SuppressWarnings("OptionalUsedAsFieldOrParameterType") @NonNull
                    java.util.Optional<T> optional) {
        //noinspection unchecked Ignoring generics to avoid having to instantiate a new factory.
        return assert_()
                .about(
                        (SubjectFactory<Java8OptionalSubject<T>, Optional<T>>)
                                (SubjectFactory) FACTORY)
                .that(optional);
    }

    public void isPresent() {
        if (!getSubject().isPresent()) {
            fail("is present");
        }
    }

    public void hasValueEqualTo(T value) {
        if (!getSubject().isPresent()) {
            fail("is present");
        }

        //noinspection OptionalGetWithoutIsPresent
        T actual = getSubject().get();
        if (!actual.equals(value)) {
            failWithBadResults("is equals to", value.toString(), "is", actual);
        }
    }

    public void isAbsent() {
        if (getSubject().isPresent()) {
            fail("is not present");
        }
    }
}
