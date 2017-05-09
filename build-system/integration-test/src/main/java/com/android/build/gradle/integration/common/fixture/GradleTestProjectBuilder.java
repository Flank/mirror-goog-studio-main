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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.fixture.app.AbstractAndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.performance.BenchmarkRecorder;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public final class GradleTestProjectBuilder {

    @Nullable private String name;
    @Nullable private TestProject testProject = null;
    @Nullable private String targetGradleVersion;
    @Nullable private String buildToolsVersion;
    private boolean withoutNdk = false;
    @NonNull private List<String> gradleProperties = Lists.newArrayList();
    @Nullable private String heapSize;
    @Nullable private BenchmarkRecorder benchmarkRecorder;
    @NonNull private Path relativeProfileDirectory = Paths.get("build", "android-profile");
    private boolean withDependencyChecker =
            false; // FIXME once all the tests are passing we can enable this back.

    /** Create a GradleTestProject. */
    public GradleTestProject create() {
        if (targetGradleVersion == null) {
            targetGradleVersion = GradleTestProject.GRADLE_TEST_VERSION;
        }
        return new GradleTestProject(
                name,
                testProject,
                targetGradleVersion,
                withoutNdk,
                withDependencyChecker,
                gradleProperties,
                heapSize,
                buildToolsVersion,
                benchmarkRecorder,
                relativeProfileDirectory);
    }

    /**
     * Set the name of the project.
     *
     * <p>Necessary if you have multiple projects in a test class.
     */
    public GradleTestProjectBuilder withName(@NonNull String name) {
        this.name = name;
        return this;
    }

    /** Use the gradle version for experimental plugin. */
    public GradleTestProjectBuilder useExperimentalGradleVersion(boolean mode) {
        if (mode) {
            targetGradleVersion = GradleTestProject.GRADLE_EXP_TEST_VERSION;
        }
        return this;
    }

    /** Create a project without setting ndk.dir in local.properties. */
    public GradleTestProjectBuilder withoutNdk() {
        this.withoutNdk = true;
        return this;
    }

    /** Create GradleTestProject from a TestProject. */
    public GradleTestProjectBuilder fromTestApp(@NonNull TestProject testProject) {
        this.testProject = testProject;
        return this;
    }

    /** Create GradleTestProject from an existing test project. */
    public GradleTestProjectBuilder fromTestProject(@NonNull String project) {
        AndroidTestApp app = new EmptyTestApp();
        if (name == null) {
            name = project;
        }
        File projectDir = new File(GradleTestProject.TEST_PROJECT_DIR, project);
        addAllFiles(app, projectDir);
        return fromTestApp(app);
    }

    /** Create GradleTestProject from an existing test project. */
    public GradleTestProjectBuilder fromExternalProject(@NonNull String project) {
        try {
            AndroidTestApp app = new EmptyTestApp();
            name = project;
            // compute the root folder of the checkout, based on test-projects.
            File parentDir =
                    GradleTestProject.TEST_PROJECT_DIR
                            .getCanonicalFile()
                            .getParentFile()
                            .getParentFile()
                            .getParentFile()
                            .getParentFile()
                            .getParentFile();
            parentDir = new File(parentDir, "external");
            File projectDir = new File(parentDir, project);
            if (!projectDir.exists()) {
                projectDir = new File(parentDir, project.replace('-', '_'));
            }
            if (!projectDir.exists()) {
                throw new RuntimeException("Project " + project + " not found in " + projectDir + ".");
            }
            addAllFiles(app, projectDir);
            return fromTestApp(app);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Add a new file to the project. */
    public GradleTestProjectBuilder addFile(@NonNull TestSourceFile file) {
        return addFiles(Lists.newArrayList(file));
    }

    /** Add a new file to the project. */
    public GradleTestProjectBuilder addFiles(@NonNull List<TestSourceFile> files) {
        if (!(this.testProject instanceof AndroidTestApp)) {
            throw new IllegalStateException("addFile is only for AndroidTestApp");
        }
        AndroidTestApp app = (AndroidTestApp) this.testProject;
        for (TestSourceFile file : files) {
            app.addFile(file);
        }
        return this;
    }

    /** Add gradle properties. */
    public GradleTestProjectBuilder addGradleProperties(@NonNull String property) {
        gradleProperties.add(property);
        return this;
    }

    /**
     * Sets the test heap size requirement. Example values : 1024m, 2048m...
     *
     * @param heapSize the heap size in a format understood by the -Xmx JVM parameter
     * @return itself.
     */
    public GradleTestProjectBuilder withHeap(String heapSize) {
        this.heapSize = heapSize;
        return this;
    }

    public GradleTestProjectBuilder withDependencyChecker(
            boolean dependencyChecker) {
        this.withDependencyChecker = dependencyChecker;
        return this;
    }

    public GradleTestProjectBuilder withBuildToolsVersion(String buildToolsVersion) {
        this.buildToolsVersion = buildToolsVersion;
        return this;
    }

    public GradleTestProjectBuilder forBenchmarkRecording(BenchmarkRecorder benchmarkRecorder) {
        this.benchmarkRecorder = benchmarkRecorder;
        return this;
    }

    /**
     * Sets the location to look for profiles. Defaults to build/android-profile
     *
     * <p>This is useful for projects where the root directory is not the root gradle project.
     */
    public GradleTestProjectBuilder withRelativeProfileDirectory(
            @NonNull Path relativeProfileDirectory) {
        this.relativeProfileDirectory = relativeProfileDirectory;
        return this;
    }

    private static class EmptyTestApp extends AbstractAndroidTestApp {
        @Override
        public boolean containsFullBuildScript() {
            return true;
        }
    }

    /** Add all files in a directory to an AndroidTestApp. */
    private static void addAllFiles(AndroidTestApp app, File projectDir) {
        for (String filePath : TestFileUtils.listFiles(projectDir)) {
            File file = new File(filePath);
            try {
                app.addFile(
                        new TestSourceFile(
                                file.getParent(),
                                file.getName(),
                                Files.toByteArray(new File(projectDir, filePath))));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
