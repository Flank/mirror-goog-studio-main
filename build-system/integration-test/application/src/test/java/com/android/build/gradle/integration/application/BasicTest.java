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
import com.android.build.gradle.internal.scope.ArtifactTypeUtil;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.core.BuilderConstants;
import com.android.builder.model.AaptOptions;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.JavaCompileOptions;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.Variant;
import com.android.builder.model.VariantBuildInformation;
import com.android.ide.common.build.ListingFileRedirect;
import com.android.utils.FileUtils;
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

   @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void report() throws Exception {
        project.executor()
                // https://github.com/gradle/gradle/issues/12871
                .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
                .run("androidDependencies");
    }

    @Test
    public void weDontFailOnLicenceDotTxtWhenPackagingDependencies() throws Exception {
        project.execute("assembleAndroidTest");
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
