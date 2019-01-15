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
import com.android.builder.model.NativeSettings;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import java.util.List;

/**
 * ATruth Subject for NativeSettings
 */
public class NativeSettingsSubject
        extends Subject<NativeSettingsSubject, NativeSettings> {

    static Subject.Factory<NativeSettingsSubject, NativeSettings> nativeSettings() {
        return NativeSettingsSubject::new;
    }

    private NativeSettingsSubject(
            @NonNull FailureMetadata failureMetadata, @NonNull NativeSettings subject) {
        super(failureMetadata, subject);

    }

    @NonNull
    public static NativeSettingsSubject assertThat(@Nullable NativeSettings settings) {
        return assertAbout(nativeSettings()).that(settings);
    }

    public void doesNotContainCompilerFlagStartingWith(@NonNull String prefix) {
        List<String> compilerFlags = actual().getCompilerFlags();
        for (String flag : compilerFlags) {
            if (flag.startsWith(prefix)) {
                fail("doesn't contain flag with prefix", prefix);
            }
        }
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void containsCompilerFlagStartingWith(@NonNull String prefix) {
        List<String> compilerFlags = actual().getCompilerFlags();
        for (String flag : compilerFlags) {
            if (flag.startsWith(prefix)) {
                return;
            }
        }
        fail("contains flag with prefix", prefix);
    }

    public void doesNotContainCompilerFlag(String expected) {
        List<String> compilerFlags = actual().getCompilerFlags();
        for (String flag : compilerFlags) {
            if (flag.equals(expected)) {
                fail("doesn't contain flag", expected);
            }
        }
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void containsCompilerFlag(String expected) {
        List<String> compilerFlags = actual().getCompilerFlags();
        for (String flag : compilerFlags) {
            if (flag.equals(expected)) {
                return;
            }
        }
        fail("contains compiler flag", expected);
    }


}