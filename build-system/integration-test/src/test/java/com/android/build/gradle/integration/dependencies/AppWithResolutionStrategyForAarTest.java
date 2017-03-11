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

package com.android.build.gradle.integration.dependencies;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Property.COORDINATES;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction.ModelContainer;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.DependencyGraphs;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.util.Collection;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** test for flavored dependency on a different package. */
public class AppWithResolutionStrategyForAarTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();
    static ModelContainer<AndroidProject> modelContainer;

    @BeforeClass
    public static void setUp() throws Exception {
        Files.write("include 'app', 'library'", project.getSettingsFile(), Charsets.UTF_8);

        appendToFile(project.getBuildFile(),
                "\n" +
                "subprojects {\n" +
                "    apply from: \"$rootDir/../commonLocalRepo.gradle\"\n" +
                "}\n");
        appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    debugCompile project(\":library\")\n"
                        + "    releaseCompile project(\":library\")\n"
                        + "}\n"
                        + "\n"
                        + "android.applicationVariants.all { variant ->\n"
                        + "  if (variant.buildType.name == \"debug\") {\n"
                        + "    variant.getCompileConfiguration().resolutionStrategy {\n"
                        + "      eachDependency { DependencyResolveDetails details ->\n"
                        + "        if (details.requested.name == \"jdeferred-android-aar\") {\n"
                        + "          details.useVersion \"1.2.2\"\n"
                        + "        }\n"
                        + "      }\n"
                        + "    }\n"
                        + "    variant.getRuntimeConfiguration().resolutionStrategy {\n"
                        + "      eachDependency { DependencyResolveDetails details ->\n"
                        + "        if (details.requested.name == \"jdeferred-android-aar\") {\n"
                        + "          details.useVersion \"1.2.2\"\n"
                        + "        }\n"
                        + "      }\n"
                        + "    }\n"
                        + "  }\n"
                        + "}\n"
                        + "\n");

        TestFileUtils.appendToFile(project.getSubproject("library").getBuildFile(),
                "\n" +
                "dependencies {\n" +
                "    compile \"org.jdeferred:jdeferred-android-aar:1.2.3\"\n" +
                "}\n");

        modelContainer = project.model().getMulti();
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        modelContainer = null;
    }

    @Test
    public void checkModelContainsCorrectDependencies() throws Exception {
        LibraryGraphHelper helper = new LibraryGraphHelper(modelContainer);

        AndroidProject appProject = modelContainer.getModelMap().get(":app");
        Collection<Variant> appVariants = appProject.getVariants();

        checkJarDependency(helper, appVariants, "debug", "org.jdeferred:jdeferred-android-aar:1.2.2@aar");
        checkJarDependency(helper, appVariants, "release", "org.jdeferred:jdeferred-android-aar:1.2.3@aar");
    }

    private static void checkJarDependency(
            @NonNull LibraryGraphHelper helper,
            @NonNull Collection<Variant> appVariants,
            @NonNull String variantName,
            @NonNull String aarCoodinate) {
        Variant appVariant = ModelHelper.getVariant(appVariants, variantName);

        AndroidArtifact appArtifact = appVariant.getMainArtifact();

        DependencyGraphs artifactCompileGraph = appArtifact.getDependencyGraphs();

        assertThat(helper.on(artifactCompileGraph).mapTo(COORDINATES))
                .named("module dependencies of " + variantName)
                .containsAllOf(":library::" + variantName, aarCoodinate);
    }
}
