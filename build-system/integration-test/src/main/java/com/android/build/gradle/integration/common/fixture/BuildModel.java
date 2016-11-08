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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.SyncIssue;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildActionExecuter;
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


    private boolean mAssertNoSyncIssues = true;

    private int modelLevel = AndroidProject.MODEL_LEVEL_LATEST;
    private final boolean isImproveDependencyEnabled;

    BuildModel(@NonNull GradleTestProject project, @NonNull ProjectConnection projectConnection) {
        super(
                projectConnection,
                project.getBuildFile().toPath(),
                project.getBenchmarkRecorder(),
                project.getProfileDirectory(),
                project.getHeapSize());
        isImproveDependencyEnabled = project.isImprovedDependencyEnabled();
    }

    /** Do not fail if there are sync issues */
    public BuildModel ignoreSyncIssues() {
        Preconditions.checkState(modelLevel != AndroidProject.MODEL_LEVEL_0_ORIGNAL,
                "Studio 1 was not aware of sync issues.");
        mAssertNoSyncIssues = false;
        return this;
    }

    /** Fetch the model as studio 1.0 would. */
    public BuildModel asStudio1() {
        Preconditions.checkState(mAssertNoSyncIssues, "Studio 1 was not aware of sync issues.");
        return level(AndroidProject.MODEL_LEVEL_0_ORIGNAL);
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

    /**
     * Returns the project model.
     *
     * This will fail if the project is a multi-project setup.
     */
    public AndroidProject getSingle() {
        AndroidProject androidProject = getSingle(AndroidProject.class);
        if (mAssertNoSyncIssues) {
            assertNoSyncIssues(androidProject.getName(), androidProject);
        }
        return androidProject;
    }

    /**
     * Returns the project model.
     *
     * This will fail if the project is a multi-project setup.
     */
    public <T> T getSingle(@NonNull Class<T> modelClass) {
        Map<String, T> modelMap = buildModel(
                projectConnection,
                new GetAndroidModelAction<>(modelClass),
                modelLevel);

        // ensure there was only one project
        assertEquals("Quering GradleTestProject.getModel() with multi-project settings",
                1, modelMap.size());

        return modelMap.get(":");
    }

    /** Returns a project model for each sub-project. */
    public Map<String, AndroidProject> getMulti() {
        Map<String, AndroidProject> models = getMulti(AndroidProject.class);
        if (mAssertNoSyncIssues) {
            models.forEach(BuildModel::assertNoSyncIssues);
        }
        return models;
    }

    /** Returns a project model for each sub-project. */
    public <T> Map<String, T> getMulti(@NonNull Class<T> modelClass) {

        // TODO: Make buildModel multithreaded all the time.
        // Getting multiple NativeAndroidProject results in duplicated class implemented error
        // in a multithreaded environment.  This is due to issues in Gradle relating to the
        // automatic generation of the implementation class of NativeSourceSet.  Make this
        // multithreaded when the issue is resolved.
        boolean isMultithreaded = !NativeAndroidProject.class.equals(modelClass);

        return buildModel(
                projectConnection,
                new GetAndroidModelAction<>(modelClass, isMultithreaded),
                modelLevel);
    }

    /** Return a list of all task names of the project. */
    @NonNull
    public List<String> getTaskList() {
        GradleProject project = projectConnection.getModel(GradleProject.class);
        return project.getTasks().stream()
                .map(GradleTask::getName)
                .collect(Collectors.toList());

    }

    /**
     * Returns a project model for each sub-project;
     *
     * @param connection the opened ProjectConnection
     * @param action     the build action to gather the model
     * @param modelLevel whether to emulate an older IDE (studio 1.0) querying the model.
     */
    @NonNull
    private <K, V> Map<K, V> buildModel(
            @NonNull ProjectConnection connection,
            @NonNull BuildAction<Map<K, V>> action,
            int modelLevel) {
        BuildActionExecuter<Map<K, V>> executor = connection.action(action);

        List<String> arguments = Lists.newArrayListWithCapacity(5);
        arguments.add("-P" + AndroidProject.PROPERTY_BUILD_MODEL_ONLY + "=true");
        arguments.add("-P" + AndroidProject.PROPERTY_INVOKED_FROM_IDE + "=true");

        switch (modelLevel) {
            case AndroidProject.MODEL_LEVEL_0_ORIGNAL:
                // nothing.
                break;
            case AndroidProject.MODEL_LEVEL_2_DEP_GRAPH:
                arguments.add("-P" + AndroidProject.PROPERTY_BUILD_MODEL_ONLY_VERSIONED + "="
                        + modelLevel);
                // intended fall-through
            case AndroidProject.MODEL_LEVEL_1_SYNC_ISSUE:
                arguments.add("-P" + AndroidProject.PROPERTY_BUILD_MODEL_ONLY_ADVANCED + "=true");
                break;
            default:
                throw new RuntimeException("Unsupported ModelLevel");
        }

        if (isImproveDependencyEnabled) {
            arguments.add("-P"
                    + AndroidGradleOptions.PROPERTY_ENABLE_IMPROVED_DEPENDENCY_RESOLUTION
                    + "=true");
        }

        setJvmArguments(executor);

        executor.setStandardOutput(System.out);
        executor.setStandardError(System.err);

        // See ProfileCapturer javadoc for explanation.
        try (Closeable ignored =
                new ProfileCapturer(benchmarkRecorder, benchmarkMode, profilesDirectory)) {
            executor.withArguments(Iterables.toArray(arguments, String.class));
            return executor.run();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void assertNoSyncIssues(@NonNull String name, @NonNull AndroidProject project) {
        if (!project.getSyncIssues().isEmpty()) {
            StringBuilder msg = new StringBuilder();
            msg.append("Project ").append(name).append(" had sync issues :\n");
            for (SyncIssue syncIssue : project.getSyncIssues()) {
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
