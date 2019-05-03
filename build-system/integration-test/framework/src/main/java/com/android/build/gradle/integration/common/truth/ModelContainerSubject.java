/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import com.android.testutils.truth.IndirectSubject;
import com.google.common.collect.Iterables;
import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.tooling.model.BuildIdentifier;

public final class ModelContainerSubject
        extends Subject<ModelContainerSubject, ModelContainer<AndroidProject>> {

    public static Subject.Factory<ModelContainerSubject, ModelContainer<AndroidProject>>
            containers() {
        return ModelContainerSubject::new;
    }

    protected ModelContainerSubject(
            FailureMetadata metadata, ModelContainer<AndroidProject> actual) {
        super(metadata, actual);
    }

    @NonNull
    public static ModelContainerSubject assertThat(
            @Nullable ModelContainer<AndroidProject> modelContainer) {
        return assertAbout(ModelContainerSubject.containers()).that(modelContainer);
    }

    /** Asserts about the root build of this container. */
    public ModelContainerBuildSubject rootBuild() {
        BuildIdentifier rootBuildId = actual().getRootBuildId();
        return check("rootBuild()")
                .about(ModelContainerBuildSubject.build(rootBuildId))
                .that(actual());
    }

    public static final class ModelContainerBuildSubject
            extends Subject<ModelContainerBuildSubject, ModelContainer<AndroidProject>> {

        private final BuildIdentifier buildId;

        public static Subject.Factory<ModelContainerBuildSubject, ModelContainer<AndroidProject>>
                build(BuildIdentifier buildId) {
            return (metadata, actual) -> new ModelContainerBuildSubject(metadata, actual, buildId);
        }

        protected ModelContainerBuildSubject(
                FailureMetadata metadata,
                ModelContainer<AndroidProject> actual,
                BuildIdentifier buildId) {
            super(metadata, actual);
            this.buildId = buildId;
        }

        private Map<String, AndroidProject> getProjectsModelMap() {
            return actual().getModelMaps().get(buildId);
        }

        /** Asserts that this build contains project {@code projectName}. */
        @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
        public void hasProject(String projectName) {
            check("hasProject(%s)", projectName)
                    .that(getProjectsModelMap())
                    .containsKey(projectName);
        }

        /** Asserts about the single project contained in this build. */
        public ModelContainerProjectSubject onlyProject() {
            Set<String> projectsSet = getProjectsModelMap().keySet();
            check("onlyProject()").that(projectsSet).hasSize(1);
            String projectName = Iterables.getOnlyElement(projectsSet);
            return project(projectName);
        }

        /** Asserts about the {@code projectName} project contained in this build. */
        public ModelContainerProjectSubject project(String projectName) {
            return check("project()")
                    .about(ModelContainerProjectSubject.project(buildId, projectName))
                    .that(actual());
        }
    }

    public static final class ModelContainerProjectSubject
            extends Subject<ModelContainerProjectSubject, ModelContainer<AndroidProject>> {

        private final BuildIdentifier buildId;
        private final String project;

        public static Subject.Factory<ModelContainerProjectSubject, ModelContainer<AndroidProject>>
                project(BuildIdentifier buildId, String project) {
            return (metadata, actual) ->
                    new ModelContainerProjectSubject(metadata, actual, buildId, project);
        }

        protected ModelContainerProjectSubject(
                FailureMetadata metadata,
                ModelContainer<AndroidProject> actual,
                BuildIdentifier buildId,
                String project) {
            super(metadata, actual);
            this.buildId = buildId;
            this.project = project;
        }

        private AndroidProject getModel() {
            return actual().getModelMaps().get(buildId).get(project);
        }

        private Collection<SyncIssue> getSyncIssues() {
            return actual().getSyncIssuesMap().get(buildId).get(project);
        }

        /** Asserts about the {@link AndroidProject} model of this project. */
        public ModelSubject model() {
            return check("model()").about(ModelSubject.models()).that(getModel());
        }

        /** Asserts this project has {@code size} sync issues. */
        @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
        public void hasIssueSize(int size) {
            check().that(getSyncIssues())
                    .named("Issue count for the single project.")
                    .hasSize(size);
        }

        /** Asserts this project has no sync issues. */
        @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
        public void hasNoIssues() {
            hasIssueSize(0);
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
            Collection<SyncIssue> subject = getSyncIssues();

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
                    getSyncIssues()
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
            Collection<SyncIssue> subject = getSyncIssues();

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
            Collection<SyncIssue> subject = getSyncIssues();

            check().that(subject).hasSize(1);

            SyncIssue issue = subject.iterator().next();
            IssueSubject.assertThat(issue).isNotNull();
            IssueSubject.assertThat(issue).hasSeverity(severity);
            IssueSubject.assertThat(issue).hasType(type);
            IssueSubject.assertThat(issue).hasData(data);
            IssueSubject.assertThat(issue).hasMessage(message);
        }

        /**
         * Asserts that the issue collection has only an element with the given properties. Not
         * specified properties are not tested and could have any value.
         *
         * @param severity the expected severity
         * @param type the expected type
         * @return the found issue for further testing.
         */
        @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
        public SyncIssue hasIssue(int severity, int type) {
            Collection<SyncIssue> subject = getSyncIssues();

            for (SyncIssue issue : subject) {
                if (severity == issue.getSeverity() && type == issue.getType()) {
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
         * Asserts that the issue collection has only an element with the given properties. Not
         * specified properties are not tested and could have any value.
         *
         * @param severity the expected severity
         * @param type the expected type
         * @param data the expected data
         * @return the found issue for further testing.
         */
        @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
        public SyncIssue hasIssue(int severity, int type, String data) {
            Collection<SyncIssue> subject = getSyncIssues();

            for (SyncIssue issue : subject) {
                if (severity == issue.getSeverity()
                        && type == issue.getType()
                        && data.equals(issue.getData())) {
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
}
