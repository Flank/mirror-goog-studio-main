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

package com.android.build.gradle.internal.utils;

import static com.google.common.truth.Truth.assertAbout;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.SyncIssue;
import com.google.common.base.Objects;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import java.util.List;

public class IssueSubject extends Subject<IssueSubject, SyncIssue> {

    public static Subject.Factory<IssueSubject, SyncIssue> issues() {
        return IssueSubject::new;
    }

    public static IssueSubject assertThat(SyncIssue issue) {
        return assertAbout(issues()).that(issue);
    }

    public IssueSubject(@NonNull FailureMetadata failureMetadata, @NonNull SyncIssue subject) {
        super(failureMetadata, subject);
    }

    public void hasSeverity(int severity) {
        if (severity != actual().getSeverity()) {
            failWithBadResults("has severity", severity, "is", actual().getSeverity());
        }
    }

    public void hasType(int type) {
        if (type != actual().getType()) {
            failWithBadResults("has type", type, "is", actual().getType());
        }
    }

    public void hasData(@Nullable String data) {
        if (!Objects.equal(data, actual().getData())) {
            failWithBadResults("has data", data, "is", actual().getData());
        }
    }

    public void hasMessage(@Nullable String message) {
        if (!Objects.equal(message, actual().getMessage())) {
            failWithBadResults("has message", message, "is", actual().getMessage());
        }
    }

    public void hasMultiLineMessage(@NonNull List<String> lines) {
        if (!Objects.equal(lines, actual().getMultiLineMessage())) {
            failWithBadResults(
                    "has multi-line message", lines, "is", actual().getMultiLineMessage());
        }
    }

    public void hasMessageThatContains(@NonNull String messageContent) {
        if (!actual().getMessage().contains(messageContent)) {
            failWithBadResults(
                    "has message that contains", messageContent, "is", actual().getMessage());
        }
    }

    @Override
    protected String actualCustomStringRepresentation() {

        SyncIssue issue = actual();
        String fullName =
                String.format(
                        "%d|%d|%s|%s",
                        issue.getSeverity(), issue.getType(), issue.getData(), issue.getMessage());

        return (internalCustomName() == null)
                ? fullName
                : "\"" + this.internalCustomName() + "\" <" + fullName + ">";
    }
}
