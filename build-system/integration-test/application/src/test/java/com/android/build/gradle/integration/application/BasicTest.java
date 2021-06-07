/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.integration.application;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.android.AndroidProjectTypes;
import com.android.build.api.variant.VariantOutputConfiguration;
import com.android.build.api.variant.impl.BuiltArtifactImpl;
import com.android.build.api.variant.impl.BuiltArtifactsImpl;
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl;
import com.android.build.gradle.integration.common.category.SmokeTests;
import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.ProjectBuildOutputUtils;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.core.BuilderConstants;
import com.android.builder.model.AaptOptions;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.JavaCompileOptions;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.Variant;
import com.android.builder.model.VariantBuildInformation;
import com.google.common.truth.Truth;
import java.io.File;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.gradle.api.JavaVersion;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Assemble tests for basic.
 */
@Category(SmokeTests.class)
public class BasicTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("basic")
                    // http://b/149978740
                    .addGradleProperties(
                            BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS.getPropertyName()
                                    + "=false")
                    .create();

    public static AndroidProject model;

    @BeforeClass
    public static void getModel() throws Exception {
        // basic project overwrites buildConfigField which emits a sync warning
        project.execute("clean", "assembleDebug", "assembleRelease");
        ModelContainer<AndroidProject> container =
                project.model().ignoreSyncIssues().fetchAndroidProjects();
        model = container.getOnlyModel();
        container
                .getOnlyModelSyncIssues()
                .forEach(
                        issue -> {
                            assertThat(issue.getSeverity()).isEqualTo(SyncIssue.SEVERITY_WARNING);
                            assertThat(issue.getMessage())
                                    .containsMatch(Pattern.compile(".*value is being replaced.*"));
                        });
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        model = null;
    }

    @Test
    public void report() throws Exception {
        project.executor()
                // https://github.com/gradle/gradle/issues/12871
                .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
                .run("androidDependencies");
    }

    @Test
    public void basicModel() {
        assertNotEquals(
                "Library Project",
                model.getProjectType(),
                AndroidProjectTypes.PROJECT_TYPE_LIBRARY);
        assertEquals("Project Type", AndroidProjectTypes.PROJECT_TYPE_APP, model.getProjectType());

        Truth.assertWithMessage("Namespace")
                .that(model.getNamespace())
                .isEqualTo("com.android.tests.basic");
        Truth.assertWithMessage("AndroidTestNamespace")
                .that(model.getAndroidTestNamespace())
                .isEqualTo("com.android.tests.basic.test");

        assertEquals(
                "Compile Target", GradleTestProject.getCompileSdkHash(), model.getCompileTarget());
        assertFalse("Non empty bootclasspath", model.getBootClasspath().isEmpty());

        assertNotNull("aaptOptions not null", model.getAaptOptions());
        assertEquals(
                "aaptOptions namespacing",
                AaptOptions.Namespacing.DISABLED,
                model.getAaptOptions().getNamespacing());

        // Since source and target compatibility are not explicitly set in the build.gradle,
        // the default value depends on the JDK used.
        JavaVersion expected = JavaVersion.VERSION_1_8;

        JavaCompileOptions javaCompileOptions = model.getJavaCompileOptions();
        assertEquals(
                expected.toString(),
                javaCompileOptions.getSourceCompatibility());
        assertEquals(
                expected.toString(),
                javaCompileOptions.getTargetCompatibility());
        assertEquals("UTF-8", javaCompileOptions.getEncoding());
    }

    @Test
    public void sourceProvidersModel() {
        AndroidProjectUtils.testDefaultSourceSets(model, project.getProjectDir());

        // test the source provider for the artifacts
        for (Variant variant : model.getVariants()) {
            AndroidArtifact artifact = variant.getMainArtifact();
            assertNull(artifact.getVariantSourceProvider());
            assertNull(artifact.getMultiFlavorSourceProvider());
        }
    }

    @Test
    public void checkDebugAndReleaseOutputHaveDifferentNames() {
        Truth.assertThat(model.getVariantsBuildInformation()).named("variant count").hasSize(2);

        // debug variant
        VariantBuildInformation debugVariant =
                AndroidProjectUtils.getVariantBuildInformationByName(model, BuilderConstants.DEBUG);

        // release variant
        VariantBuildInformation releaseVariant =
                AndroidProjectUtils.getVariantBuildInformationByName(
                        model, BuilderConstants.RELEASE);

        String debugFile = ProjectBuildOutputUtils.getSingleOutputFile(debugVariant);
        String releaseFile = ProjectBuildOutputUtils.getSingleOutputFile(releaseVariant);

        Assert.assertFalse("debug: $debugFile / release: $releaseFile", debugFile == releaseFile);
    }

    @Test
    public void weDontFailOnLicenceDotTxtWhenPackagingDependencies() throws Exception {
        project.execute("assembleAndroidTest");
    }

    @Test
    public void testBuildOutputModel() throws Exception {
        project.execute("assemble", "assembleDebugAndroidTest", "testDebugUnitTest");
        Map<String, AndroidProject> multi =
                project.model().ignoreSyncIssues().fetchAndroidProjects().getOnlyModelMap();

        AndroidProject mainModule = multi.get(":");
        assertThat(mainModule.getVariantsBuildInformation()).hasSize(2);
        assertThat(
                        mainModule.getVariantsBuildInformation().stream()
                                .map(VariantBuildInformation::getVariantName)
                                .collect(Collectors.toList()))
                .containsExactly("debug", "release");

        for (VariantBuildInformation variantBuildOutput :
                mainModule.getVariantsBuildInformation()) {
            assertThat(variantBuildOutput.getAssembleTaskName()).contains("assemble");
            assertThat(variantBuildOutput.getAssembleTaskOutputListingFile()).isNotNull();
            File listingFile = new File(variantBuildOutput.getAssembleTaskOutputListingFile());
            BuiltArtifactsImpl builtArtifacts =
                    BuiltArtifactsLoaderImpl.loadFromFile(
                            listingFile, listingFile.getParentFile().toPath());

            assertThat(builtArtifacts).isNotNull();
            assertThat(builtArtifacts.getElements()).hasSize(1);
            BuiltArtifactImpl built = builtArtifacts.getElements().iterator().next();
            assertThat(new File(built.getOutputFile()).exists()).isTrue();
            assertThat(built.getVariantOutputConfiguration().getFilters()).isEmpty();
            assertThat(built.getVariantOutputConfiguration().getOutputType())
                    .isEqualTo(VariantOutputConfiguration.OutputType.SINGLE);
        }
    }

    @Test
    public void testRenderscriptDidNotRun() throws Exception {
        // Execute renderscript task and check if it was skipped
        project.execute("compileDebugRenderscript");
        assertThat(
                        project.getBuildResult()
                                .getTask(":compileDebugRenderscript")
                                .getExecutionState()
                                .toString())
                .isEqualTo("SKIPPED");
    }

    @Test
    public void testFlatDirWarning() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(), "repositories { flatDir { dirs \"libs\" } }");
        project.executor().run("clean", "assembleDebug");
        ModelContainer<AndroidProject> onlyModel =
                project.model().ignoreSyncIssues(SyncIssue.SEVERITY_WARNING).fetchAndroidProjects();
        assertThat(
                        onlyModel.getOnlyModelSyncIssues().stream()
                                .map(syncIssue -> syncIssue.getMessage())
                                .filter(syncIssues -> syncIssues.contains("flatDir"))
                                .collect(Collectors.toList())
                                .size())
                .isEqualTo(1);
    }
}
