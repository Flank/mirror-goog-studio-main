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
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.fixture.Logcat;
import com.android.ddmlib.logcat.LogCatMessage;
import com.google.common.base.MoreObjects;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import java.util.function.Predicate;

@SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
public class LogCatMessagesSubject extends Subject<LogCatMessagesSubject, Logcat> {

    public static Subject.Factory<LogCatMessagesSubject, Logcat> logCatMessages() {
        return LogCatMessagesSubject::new;
    }

    public LogCatMessagesSubject(
            @NonNull FailureMetadata failureMetadata, @Nullable Logcat subject) {
        super(failureMetadata, subject);
    }


    public void containsMessageWithText(@NonNull String text) {
        if (!containsMessageThatMatches(messageTextOf(text))) {
            fail("contains message with text ", text);
        }
    }

    public void doesNotContainMessageWithText(@NonNull String text) {
        if (containsMessageThatMatches(messageTextOf(text))) {
            fail("does not contain message with text ", text);
        }
    }

    @Override
    protected String actualCustomStringRepresentation() {
        if (actual() == null) {
            return super.actualCustomStringRepresentation();
        }
        return MoreObjects.toStringHelper(actual())
                .addValue(actual().getFilteredLogCatMessages())
                .toString();
    }

    private boolean containsMessageThatMatches(@NonNull Predicate<LogCatMessage> predicate) {
        for (LogCatMessage message : actual().getFilteredLogCatMessages()) {
            if (predicate.test(message)) {
                return true;
            }
        }
        return false;
    }


    @NonNull
    private static Predicate<LogCatMessage> messageTextOf(@NonNull final String text) {
        return input -> text.equals(input.getMessage());
    }
}
