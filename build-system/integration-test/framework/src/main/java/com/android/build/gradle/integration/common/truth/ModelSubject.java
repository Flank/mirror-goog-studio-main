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

import static com.android.build.gradle.integration.common.truth.IssueSubject.issues;
import static com.google.common.truth.Truth.assertAbout;

import com.android.annotations.NonNull;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import com.android.testutils.truth.IndirectSubject;
import com.google.common.collect.Iterables;
import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Truth support for AndroidProject.
 */
public class ModelSubject extends Subject<ModelSubject, AndroidProject> {

    public static Subject.Factory<ModelSubject, AndroidProject> models() {
        return ModelSubject::new;
    }

    public static ModelSubject assertThat(AndroidProject androidProject) {
        return assertAbout(models()).that(androidProject);
    }

    public ModelSubject(@NonNull FailureMetadata failureMetadata, @NonNull AndroidProject subject) {
        super(failureMetadata, subject);
    }

    public void hasIssueSize(int size) {
        Collection<SyncIssue> issues = actual().getSyncIssues();

        check().that(issues).named("Issue count for project " + actual().getName()).hasSize(size);
    }

    /**
     * Asserts that the issue collection has only a single element with the given properties.
     * Not specified properties are not tested and could have any value.
     *
     * @param severity the expected severity
     * @param type the expected type
     * @return the found issue for further testing.
     */
    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public SyncIssue hasSingleIssue(int severity, int type) {
        Collection<SyncIssue> subject = actual().getSyncIssues();

        check().that(subject).hasSize(1);

        SyncIssue issue = subject.iterator().next();
        IssueSubject.assertThat(issue).isNotNull();
        IssueSubject.assertThat(issue).hasSeverity(severity);
        IssueSubject.assertThat(issue).hasType(type);

        return issue;
    }

    /**
     * Asserts that the issue collection has only a single error with the given type.
     *
     * <p>Warnings are ignored.
     *
     * @param type the expected type
     * @return the indirect issue subject for further testing
     */
    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public IndirectSubject<IssueSubject> hasSingleError(int type) {
        Collection<SyncIssue> syncIssues =
                actual().getSyncIssues()
                        .stream()
                        .filter(issue -> issue.getSeverity() == SyncIssue.SEVERITY_ERROR)
                        .collect(Collectors.toList());
        check().that(syncIssues).hasSize(1);

        SyncIssue issue = Iterables.getOnlyElement(syncIssues);
        IssueSubject.assertThat(issue).isNotNull();
        IssueSubject.assertThat(issue).hasType(type);

        return () -> IssueSubject.assertThat(issue);
    }

    /**
     * Asserts that the issue collection has only a single element with the given properties.
     * Not specified properties are not tested and could have any value.
     *
     * @param severity the expected severity
     * @param type the expected type
     * @param data the expected data
     * @return the found issue for further testing.
     */
    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public SyncIssue hasSingleIssue(int severity, int type, String data) {
        Collection<SyncIssue> subject = actual().getSyncIssues();

        check().that(subject).hasSize(1);

        SyncIssue issue = subject.iterator().next();
        IssueSubject.assertThat(issue).isNotNull();
        IssueSubject.assertThat(issue).hasSeverity(severity);
        IssueSubject.assertThat(issue).hasType(type);
        IssueSubject.assertThat(issue).hasData(data);

        return issue;
    }

    /**
     * Asserts that the issue collection has only a single element with the given properties.
     *
     * @param severity the expected severity
     * @param type the expected type
     * @param data the expected data
     * @param message the expected message
     */
    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void hasSingleIssue(int severity, int type, String data, String message) {
        Collection<SyncIssue> subject = actual().getSyncIssues();

        check().that(subject).hasSize(1);

        SyncIssue issue = subject.iterator().next();
        IssueSubject.assertThat(issue).isNotNull();
        IssueSubject.assertThat(issue).hasSeverity(severity);
        IssueSubject.assertThat(issue).hasType(type);
        IssueSubject.assertThat(issue).hasData(data);
        IssueSubject.assertThat(issue).hasMessage(message);
    }

    /**
     * Asserts that the issue collection has only an element with the given properties.
     * Not specified properties are not tested and could have any value.
     *
     * @param severity the expected severity
     * @param type the expected type
     * @return the found issue for further testing.
     */
    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public SyncIssue hasIssue(int severity, int type) {
        Collection<SyncIssue> subject = actual().getSyncIssues();

        for (SyncIssue issue : subject) {
            if (severity == issue.getSeverity() &&
                    type == issue.getType()) {
                return issue;
            }
        }

        failWithoutActual(
                Fact.simpleFact(
                        String.format(
                                "'%s' does not contain <%s / %s>",
                                actualAsString(), severity, type)));
        // won't reach
        return null;
    }

    /**
     * Asserts that the issue collection has only an element with the given properties.
     * Not specified properties are not tested and could have any value.
     *
     * @param severity the expected severity
     * @param type the expected type
     * @param data the expected data
     * @return the found issue for further testing.
     */
    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public SyncIssue hasIssue(int severity, int type, String data) {
        Collection<SyncIssue> subject = actual().getSyncIssues();

        for (SyncIssue issue : subject) {
            if (severity == issue.getSeverity() &&
                    type == issue.getType() &&
                    data.equals(issue.getData())) {
                return issue;
            }
        }

        failWithoutActual(
                Fact.simpleFact(
                        String.format(
                                "'%s' does not contain <%s / %s / %s>",
                                actualAsString(), severity, type, data)));
        // won't reach
        return null;
    }
}
