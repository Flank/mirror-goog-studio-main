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

import com.android.annotations.NonNull;
import com.android.testutils.apk.AtomBundle;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.SubjectFactory;

/** Truth support for atombundle files. */
@SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
public class AtomBundleSubject extends AbstractDexAndroidSubject<AtomBundleSubject, AtomBundle> {

    public static final SubjectFactory<AtomBundleSubject, AtomBundle> FACTORY =
            new SubjectFactory<AtomBundleSubject, AtomBundle>() {
                @Override
                public AtomBundleSubject getSubject(
                        @NonNull FailureStrategy failureStrategy, @NonNull AtomBundle subject) {
                    return new AtomBundleSubject(failureStrategy, subject);
                }
            };

    public AtomBundleSubject(
            @NonNull FailureStrategy failureStrategy, @NonNull AtomBundle subject) {
        super(failureStrategy, subject);
    }

}
