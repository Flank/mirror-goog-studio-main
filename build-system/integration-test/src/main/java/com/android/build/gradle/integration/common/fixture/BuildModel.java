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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction.ModelContainer;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.IntegerOption;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.SyncIssue;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
public class BuildModel extends BaseGradleExecutor<BuildModel> {

    public enum Feature {
        /** full dependencies, including package graph, and provided and skipped properties. */
        FULL_DEPENDENCIES(BooleanOption.IDE_BUILD_MODEL_FEATURE_FULL_DEPENDENCIES),
        ;

        private BooleanOption option;

        Feature(BooleanOption option) {
            this.option = option;
        }
    }

    private int mMaxSyncIssueSeverityLevel = 0;

    private int modelLevel = AndroidProject.MODEL_LEVEL_LATEST;

    BuildModel(@NonNull GradleTestProject project, @NonNull ProjectConnection projectConnection) {
        super(
                projectConnection,
                project::setLastBuildResult,
                project.getTestDir().toPath(),
                project.getBuildFile().toPath(),
                project.getBenchmarkRecorder(),
                project.getProfileDirectory(),
                project.isImprovedDependencyEnabled(),
                project.getHeapSize());
        with(
                BooleanOption.ENABLE_IMPROVED_DEPENDENCY_RESOLUTION,
                project.isImprovedDependencyEnabled());
    }

    /** Do not fail if there are sync issues */
    public BuildModel ignoreSyncIssues() {
        Preconditions.checkState(modelLevel != AndroidProject.MODEL_LEVEL_0_ORIGINAL,
                "Studio 1 was not aware of sync issues.");
        mMaxSyncIssueSeverityLevel = SyncIssue.SEVERITY_ERROR;
        return this;
    }

    public BuildModel ignoreSyncIssueWarnings() {
        Preconditions.checkState(
                modelLevel != AndroidProject.MODEL_LEVEL_0_ORIGINAL,
                "Studio 1 was not aware of sync issues.");
        mMaxSyncIssueSeverityLevel = SyncIssue.SEVERITY_WARNING;
        return this;
    }

    /** Fetch the model as studio 1.0 would. */
    public BuildModel asStudio1() {
        Preconditions.checkState(
                mMaxSyncIssueSeverityLevel == 0, "Studio 1 was not aware of sync issues.");
        return level(AndroidProject.MODEL_LEVEL_0_ORIGINAL);
    }

    /**
     * Fetch the model as studio would, with the specified model level.
     *
     * <p>See AndroidProject.MODEL_LEVEL_...
     */
    public BuildModel level(int modelLevel) {
        this.modelLevel = modelLevel;
        return this;
    }

    public BuildModel withFeature(Feature feature) {
        with(feature.option, true);
        return this;
    }

    /**
     * Returns the project model.
     *
     * <p>This will fail if the project is a multi-project setup.
     */
    public ModelContainer<AndroidProject> getSingle() throws IOException {
        ModelContainer<AndroidProject> container = getSingleModel(AndroidProject.class);
        if (mMaxSyncIssueSeverityLevel > 0) {
            AndroidProject project = Iterables.getOnlyElement(container.getModelMap().values());
            assertNoSyncIssues(project.getName(), project);
        }
        return container;
    }

    /**
     * Returns the project model.
     *
     * <p>This will fail if the project is a multi-project setup.
     */
    public <T> T getSingle(@NonNull Class<T> modelClass) throws IOException {
        // if passing AndroidStudio.class, use getSingle() instead
        assertThat(modelClass)
                .named("Class name in getSingle(Class<T>)")
                .isNotEqualTo(AndroidProject.class);

        ModelContainer<T> container = getSingleModel(modelClass);

        return Iterables.getOnlyElement(container.getModelMap().values());
    }

    /**
     * Returns the project model.
     *
     * <p>This will fail if the project is a multi-project setup.
     */
    private <T> ModelContainer<T> getSingleModel(@NonNull Class<T> modelClass) throws IOException {
        ModelContainer<T> container =
                buildModel(new GetAndroidModelAction<>(modelClass), modelLevel);

        // ensure there was only one project
        assertThat(container.getModelMap())
                .named("Quering GradleTestProject.getModel() with multi-project settings")
                .hasSize(1);

        return container;
    }


    /** Returns a project model for each sub-project. */
    public ModelContainer<AndroidProject> getMulti() throws IOException {
        ModelContainer<AndroidProject> container = getMultiContainer(AndroidProject.class);
        if (mMaxSyncIssueSeverityLevel > 0) {
            container.getModelMap().forEach(this::assertNoSyncIssues);
        }
        return container;
    }

    /** Returns a project model for each sub-project. */
    public <T> Map<String, T> getMulti(@NonNull Class<T> modelClass) throws IOException {
        assertThat(modelClass)
                .named("class name in getMulti(Class<T>)")
                .isNotEqualTo(AndroidProject.class);

        return getMultiContainer(modelClass).getModelMap();
    }

    private <T> ModelContainer<T> getMultiContainer(@NonNull Class<T> modelClass)
            throws IOException {
        // TODO: Make buildModel multithreaded all the time.
        // Getting multiple NativeAndroidProject results in duplicated class implemented error
        // in a multithreaded environment.  This is due to issues in Gradle relating to the
        // automatic generation of the implementation class of NativeSourceSet.  Make this
        // multithreaded when the issue is resolved.
        boolean isMultithreaded = !NativeAndroidProject.class.equals(modelClass);

        return buildModel(
                new GetAndroidModelAction<>(modelClass, isMultithreaded),
                modelLevel);
    }

    /** Return a list of all task names of the project. */
    @NonNull
    public List<String> getTaskList() throws IOException {
        GradleProject project =
                projectConnection.model(GradleProject.class).withArguments(getArguments()).get();

        return project.getTasks().stream().map(GradleTask::getName).collect(Collectors.toList());
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
        BuildActionExecuter<ModelContainer<T>> executor = this.projectConnection.action(action);

        with(BooleanOption.IDE_BUILD_MODEL_ONLY, true);
        with(BooleanOption.IDE_INVOKED_FROM_IDE, true);


        switch (modelLevel) {
            case AndroidProject.MODEL_LEVEL_0_ORIGINAL:
                // nothing.
                break;
            case AndroidProject.MODEL_LEVEL_2_DONT_USE:
                with(IntegerOption.IDE_BUILD_MODEL_ONLY_VERSION, modelLevel);
                // intended fall-through
            case AndroidProject.MODEL_LEVEL_1_SYNC_ISSUE:
                with(BooleanOption.IDE_BUILD_MODEL_ONLY_ADVANCED, true);
                break;
            default:
                throw new RuntimeException("Unsupported ModelLevel");
        }

        setJvmArguments(executor);

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        setStandardOut(executor, stdout);
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        setStandardError(executor, stderr);

        GradleConnectionException exception = null;
        // See ProfileCapturer javadoc for explanation.
        try (Closeable ignored =
                new ProfileCapturer(benchmarkRecorder, benchmarkMode, profilesDirectory)) {
            return executor.withArguments(getArguments()).run();
        } catch (GradleConnectionException e) {
            exception = e;
            throw e;
        } finally {
            lastBuildResultConsumer.accept(
                    new GradleBuildResult(stdout, stderr, ImmutableList.of(), exception));
        }
    }

    private void assertNoSyncIssues(@NonNull String name, @NonNull AndroidProject project) {
        List<SyncIssue> filteredIssues =
                project.getSyncIssues()
                        .stream()
                        .filter(syncIssue -> syncIssue.getSeverity() > mMaxSyncIssueSeverityLevel)
                        .collect(Collectors.toList());

        if (!filteredIssues.isEmpty()) {
            StringBuilder msg = new StringBuilder();
            msg.append("Project ").append(name).append(" had sync issues :\n");
            for (SyncIssue syncIssue : filteredIssues) {
                msg.append(
                        MoreObjects.toStringHelper(SyncIssue.class)
                                .add(
                                        "type",
                                        getIntConstantName(
                                                SyncIssue.class, "TYPE", syncIssue.getType()))
                                .add(
                                        "severity",
                                        getIntConstantName(
                                                SyncIssue.class,
                                                "SEVERITY",
                                                syncIssue.getSeverity()))
                                .add("data", syncIssue.getData())
                                .add("message", syncIssue.getMessage())
                                .toString());
                msg.append("\n");
            }
            fail(msg.toString());
        }
    }

    @NonNull
    private static String getIntConstantName(
            @NonNull Class<?> clazz, @NonNull String prefix, int value) {
        return Arrays.stream(clazz.getFields())
                .filter(field -> field.getName().startsWith(prefix))
                .filter(
                        field -> {
                            try {
                                return field.getInt(null) == value;
                            } catch (IllegalAccessException e) {
                                return false;
                            }
                        })
                .map(Field::getName)
                .findAny()
                .orElseGet(() -> Integer.toString(value));
    }
}
