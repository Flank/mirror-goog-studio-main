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

package com.android.build.gradle.integration.common.fixture;

import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction.ModelContainer;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.IntegerOption;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.SyncIssue;
import com.android.utils.Pair;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.GradleTask;

/**
 * Builder for actions that get the gradle model from a {@link GradleTestProject}.
 *
 * <p>Example: <code>project.model().asStudio1().getMulti()</code> fetches the model for all
 * subprojects as Studio 1.0 does.
 */
public class ModelBuilder extends BaseGradleExecutor<ModelBuilder> {
    private int maxSyncIssueSeverityLevel = 0;
    private int modelLevel = AndroidProject.MODEL_LEVEL_LATEST;

    ModelBuilder(@NonNull GradleTestProject project, @NonNull ProjectConnection projectConnection) {
        super(
                projectConnection,
                project::setLastBuildResult,
                project.getTestDir().toPath(),
                project.getBuildFile().toPath(),
                project.getProfileDirectory(),
                project.getHeapSize());
    }

    public ModelBuilder(
            @NonNull ProjectConnection projectConnection,
            @NonNull Consumer<GradleBuildResult> lastBuildResultConsumer,
            @NonNull Path projectDirectory,
            @Nullable Path buildDotGradleFile,
            @Nullable String heapSize) {
        super(
                projectConnection,
                lastBuildResultConsumer,
                projectDirectory,
                buildDotGradleFile,
                null /*profileDirectory*/,
                heapSize);
    }

    /**
     * Do not fail if there are sync issues.
     *
     * <p>Equivalent to {@code ignoreSyncIssues(SyncIssue.SEVERITY_ERROR)}.
     */
    @NonNull
    public ModelBuilder ignoreSyncIssues() {
        return ignoreSyncIssues(SyncIssue.SEVERITY_ERROR);
    }

    /**
     * Do not fail if there are sync issues.
     *
     * <p>The severity argument is one of {@code SyncIssue.SEVERITY_ERROR} or {@code
     * SyncIssue.SEVERITY_WARNING}.
     */
    @NonNull
    public ModelBuilder ignoreSyncIssues(int severity) {
        Preconditions.checkState(
                modelLevel != AndroidProject.MODEL_LEVEL_0_ORIGINAL,
                "version 1 of Android Studio was not aware of sync issues, so it's invalid to ignore them when using MODEL_LEVEL_0_ORIGINAL");
        Preconditions.checkArgument(
                severity == SyncIssue.SEVERITY_WARNING || severity == SyncIssue.SEVERITY_ERROR,
                "incorrect severity, must be one of SyncIssue.SEVERITY_WARNING or SyncIssue.SEVERITY_ERROR");

        maxSyncIssueSeverityLevel = severity;
        return this;
    }

    /**
     * Fetch the model as Studio would, with the specified model level.
     *
     * <p>See {@code AndroidProject.MODEL_LEVEL_*}.
     */
    @NonNull
    public ModelBuilder level(int modelLevel) {
        this.modelLevel = modelLevel;
        return this;
    }

    @NonNull
    public ModelBuilder withFullDependencies() {
        with(BooleanOption.IDE_BUILD_MODEL_FEATURE_FULL_DEPENDENCIES, true);
        return this;
    }

    /**
     * Returns the project model.
     *
     * <p>This will fail if the project is a multi-project setup.
     */
    @NonNull
    public ModelContainer<AndroidProject> getSingle() throws IOException {
        return assertNoSyncIssues(getSingleModel(AndroidProject.class));
    }

    /**
     * Returns the project model.
     *
     * <p>This will fail if the project is a multi-project setup.
     */
    @NonNull
    public <T> T getSingle(@NonNull Class<T> modelClass) throws IOException {
        Preconditions.checkArgument(
                modelClass != AndroidProject.class, "please use getSingle() instead");
        return getSingleModel(modelClass).getOnlyModel();
    }

    /**
     * Returns the project model.
     *
     * <p>This will fail if the project is a multi-project setup.
     */
    @NonNull
    private <T> ModelContainer<T> getSingleModel(@NonNull Class<T> modelClass) throws IOException {
        ModelContainer<T> container =
                buildModel(new GetAndroidModelAction<>(modelClass), modelLevel);

        // ensure there was only one project
        Preconditions.checkState(
                container.getModelMaps().size() == 1,
                "attempted to getSingleModel() with multi-project settings");

        return container;
    }

    /** Returns a project model for each sub-project. */
    @NonNull
    public ModelContainer<AndroidProject> getMulti() throws IOException {
        return assertNoSyncIssues(getMultiContainer(AndroidProject.class));
    }

    /** Returns a project model for each sub-project. */
    @NonNull
    public <T> Map<String, T> getMulti(@NonNull Class<T> modelClass) throws IOException {
        Preconditions.checkArgument(
                modelClass != AndroidProject.class, "please use getMulti() instead");

        final ModelContainer<T> modelContainer = getMultiContainer(modelClass);

        if (modelContainer.getModelMaps().size() > 1) {
            throw new RuntimeException("Can't call getMulti(Class) with included builds");
        }

        return modelContainer.getRootBuildModelMap();
    }

    @NonNull
    private <T> ModelContainer<T> getMultiContainer(@NonNull Class<T> modelClass)
            throws IOException {
        // TODO: Make buildModel multithreaded all the time.
        // Getting multigetMultiContainerple NativeAndroidProject results in duplicated class implemented error
        // in a multithreaded environment.  This is due to issues in Gradle relating to the
        // automatic generation of the implementation class of NativeSourceSet.  Make this
        // multithreaded when the issue is resolved.
        boolean isMultithreaded = modelClass != NativeAndroidProject.class;
        return buildModel(new GetAndroidModelAction<>(modelClass, isMultithreaded), modelLevel);
    }

    /** Return a list of all task names of the project. */
    @NonNull
    public List<String> getTaskList() throws IOException {
        return getProject()
                .getTasks()
                .stream()
                .map(GradleTask::getName)
                .collect(Collectors.toList());
    }

    private GradleProject getProject() throws IOException {
        return projectConnection.model(GradleProject.class).withArguments(getArguments()).get();
    }

    /**
     * Returns a project model for each sub-project;
     *
     * @param action the build action to gather the model
     * @param modelLevel whether to emulate an older IDE (studio 1.0) querying the model.
     */
    @NonNull
    private <T> ModelContainer<T> buildModel(
            @NonNull BuildAction<ModelContainer<T>> action, int modelLevel) throws IOException {

        with(BooleanOption.IDE_BUILD_MODEL_ONLY, true);
        with(BooleanOption.IDE_INVOKED_FROM_IDE, true);

        switch (modelLevel) {
            case AndroidProject.MODEL_LEVEL_0_ORIGINAL:
                // nothing.
                break;
            case AndroidProject.MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD:
            case AndroidProject.MODEL_LEVEL_4_NEW_DEP_MODEL:
                with(IntegerOption.IDE_BUILD_MODEL_ONLY_VERSION, modelLevel);
                // intended fall-through
            case AndroidProject.MODEL_LEVEL_1_SYNC_ISSUE:
                with(BooleanOption.IDE_BUILD_MODEL_ONLY_ADVANCED, true);
                break;
            default:
                throw new RuntimeException("Unsupported ModelLevel: " + modelLevel);
        }

        while (true) {
            BuildActionExecuter<ModelContainer<T>> executor = projectConnection.action(action);
            setJvmArguments(executor);

            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            setStandardOut(executor, stdout);
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            setStandardError(executor, stderr);

            try {
                ModelContainer<T> result = executor.withArguments(getArguments()).run();

                lastBuildResultConsumer.accept(
                        new GradleBuildResult(stdout, stderr, ImmutableList.of(), null));

                return result;
            } catch (GradleConnectionException e) {
                RetryAction retryAction = chooseRetryAction(e);
                if (retryAction != RetryAction.RETRY) {
                    lastBuildResultConsumer.accept(
                            new GradleBuildResult(stdout, stderr, ImmutableList.of(), e));
                    if (retryAction == RetryAction.FAILED_TOO_MANY_TIMES) {
                        throw new TooFlakyException(e);
                    } else if (retryAction == RetryAction.THROW) {
                        throw e;
                    }
                }
            }
        }
    }

    private ModelContainer<AndroidProject> assertNoSyncIssues(
            @NonNull ModelContainer<AndroidProject> container) {
        container
                .getModelMaps()
                .entrySet()
                .stream()
                .flatMap(
                        entry ->
                                entry.getValue()
                                        .entrySet()
                                        .stream()
                                        .map(
                                                entry2 ->
                                                        Pair.of(
                                                                entry.getKey().getRootDir()
                                                                        + "@@"
                                                                        + entry2.getKey(),
                                                                entry2.getValue())))
                .forEach(this::assertNoSyncIssues);
        return container;
    }

    private void assertNoSyncIssues(@NonNull Pair<String, AndroidProject> projectPair) {
        List<SyncIssue> issues =
                projectPair
                        .getSecond()
                        .getSyncIssues()
                        .stream()
                        .filter(syncIssue -> syncIssue.getSeverity() > maxSyncIssueSeverityLevel)
                        .filter(syncIssue -> syncIssue.getType() != SyncIssue.TYPE_DEPRECATED_DSL)
                        .collect(Collectors.toList());

        if (!issues.isEmpty()) {
            fail(
                    "project "
                            + projectPair.getFirst()
                            + " had sync issues: "
                            + Joiner.on("\n").join(issues));
        }
    }
}
