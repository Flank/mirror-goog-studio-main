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

package com.android.build.gradle.tasks.factory;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;
import static com.android.build.gradle.internal.scope.TaskOutputHolder.AnchorOutputType.CLASSES_FOR_UNIT_TESTS;
import static com.android.builder.core.VariantType.UNIT_TEST;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.google.common.base.Preconditions;
import java.io.File;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.reporting.ConfigurableReport;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestTaskReports;

/**
 * Patched version of {@link Test} that we need to use for local unit tests support.
 */
public class AndroidUnitTest extends Test {

    private FileCollection resCollection;
    private FileCollection assetsCollection;

    /** All test classes, from both Java and Kotlin. */
    private FileCollection testClasses;

    /**
     * Returns the test class files.
     *
     * <p>Because test classes may come from additional bytecode sources (e.g. Kotlin), we need to
     * override this.
     */
    @Override
    public FileTree getCandidateClassFiles() {
        return testClasses.getAsFileTree();
    }

    @InputFiles
    @Optional
    public FileCollection getResCollection() {
        return resCollection;
    }

    @InputFiles
    @Optional
    public FileCollection getAssetsCollection() {
        return assetsCollection;
    }

    /**
     * Configuration Action for a JavaCompile task.
     */
    public static class ConfigAction implements TaskConfigAction<AndroidUnitTest> {

        private final VariantScope scope;

        public ConfigAction(@NonNull VariantScope scope) {
            this.scope = Preconditions.checkNotNull(scope);
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName(UNIT_TEST.getPrefix());
        }

        @NonNull
        @Override
        public Class<AndroidUnitTest> getType() {
            return AndroidUnitTest.class;
        }

        @Override
        public void execute(@NonNull AndroidUnitTest runTestsTask) {
            final TestVariantData variantData = (TestVariantData) scope.getVariantData();
            final BaseVariantData testedVariantData =
                    (BaseVariantData) variantData.getTestedVariantData();

            // we run by default in headless mode, so the forked JVM doesn't steal focus.
            runTestsTask.systemProperty("java.awt.headless", "true");

            runTestsTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
            runTestsTask.setDescription(
                    "Run unit tests for the "
                            + testedVariantData.getVariantConfiguration().getFullName()
                            + " build.");

            runTestsTask.testClasses = scope.getOutput(CLASSES_FOR_UNIT_TESTS);

            runTestsTask.setClasspath(computeClasspath());

            // if android resources are meant to be accessible, then we need to make sure
            // changes to them trigger a new run of the tasks
            if (scope.getGlobalScope()
                    .getExtension()
                    .getTestOptions()
                    .getUnitTests()
                    .isIncludeAndroidResources()) {
                VariantScope testedScope = testedVariantData.getScope();
                runTestsTask.assetsCollection = testedScope.getOutput(TaskOutputType.MERGED_ASSETS);
                runTestsTask.resCollection = testedScope.getOutput(TaskOutputType.MERGED_RES);
            }

            // Put the variant name in the report path, so that different testing tasks don't
            // overwrite each other's reports. For component model plugin, the report tasks are not
            // yet configured.  We get a hardcoded value matching Gradle's default. This will
            // eventually be replaced with the new Java plugin.
            TestTaskReports testTaskReports = runTestsTask.getReports();
            ConfigurableReport xmlReport = testTaskReports.getJunitXml();
            xmlReport.setDestination(
                    new File(
                            scope.getGlobalScope().getTestResultsFolder(),
                            runTestsTask.getName()));

            ConfigurableReport htmlReport = testTaskReports.getHtml();
            htmlReport.setDestination(
                    new File(
                            scope.getGlobalScope().getTestReportFolder(),
                            runTestsTask.getName()));

            scope.getGlobalScope()
                    .getExtension()
                    .getTestOptions()
                    .getUnitTests()
                    .applyConfiguration(runTestsTask);
        }

        @NonNull
        private ConfigurableFileCollection computeClasspath() {
            ConfigurableFileCollection collection = scope.getGlobalScope().getProject().files();

            // the test classpath is made up of:
            // - the test component classes and java_res
            collection.from(scope.getOutput(CLASSES_FOR_UNIT_TESTS));
            // TODO is this the right thing? this doesn't include the res merging via transform AFAIK
            collection.from(scope.getOutput(TaskOutputType.JAVA_RES));

            // - the runtime dependencies for both CLASSES and JAVA_RES type
            collection.from(
                    scope.getArtifactFileCollection(RUNTIME_CLASSPATH, ALL, CLASSES));
            collection.from(
                    scope.getArtifactFileCollection(
                            RUNTIME_CLASSPATH,
                            ALL,
                            ArtifactType.JAVA_RES));

            // Mockable JAR is last, to make sure you can shadow the classes with
            // dependencies.
            collection.from(scope.getGlobalScope().getOutput(TaskOutputType.MOCKABLE_JAR));
            return collection;
        }
    }
}
