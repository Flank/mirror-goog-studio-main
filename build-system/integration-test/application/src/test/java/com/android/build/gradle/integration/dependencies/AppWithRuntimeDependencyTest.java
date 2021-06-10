/*
 * Copyright (C) 2018 The Android Open Source Project
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
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.Variant;
import com.android.testutils.MavenRepoGenerator;
import com.android.testutils.TestAarGenerator;
import com.android.testutils.TestInputsGenerator;
import com.android.utils.PathUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Test for runtime only dependencies. Test project structure: app -> library (implementation) ----
 * library -> library2 (implementation) ---- library -> [guava (implementation) and example aar]
 *
 * <p>The test verifies that the dependency model of app module contains library2, guava and the
 * example aar as runtime only dependencies.
 */
public class AppWithRuntimeDependencyTest {

    private static MavenRepoGenerator mavenRepo;

    static {
        try {
            byte[] aar =
                    TestAarGenerator.generateAarWithContent(
                            "com.example.aar",
                            TestInputsGenerator.jarWithEmptyClasses(
                                    ImmutableList.of("com/example/MyClass")),
                            ImmutableMap.of());
            mavenRepo =
                    new MavenRepoGenerator(
                            ImmutableList.of(
                                    new MavenRepoGenerator.Library(
                                            "com.example:aar:1", "aar", aar)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("projectWithModules")
                    .withAdditionalMavenRepo(mavenRepo)
                    .create();

    @BeforeClass
    public static void setUp() throws Exception {
        //noinspection deprecation
        Files.write(
                "include 'app', 'library', 'library2'", project.getSettingsFile(), Charsets.UTF_8);

        appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\ndependencies {\n" + "    implementation project(':library')\n" + "}\n");

        appendToFile(
                project.getSubproject("library").getBuildFile(),
                "\ndependencies {\n"
                        + "    implementation project(':library2')\n"
                        + "    implementation 'com.google.guava:guava:19.0'\n"
                        + "    implementation 'com.example:aar:1'\n"
                        + "}\n");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkRuntimeClasspathWithLevel1Model() throws Exception {
        Map<String, AndroidProject> models =
                project.model()
                        .level(AndroidProject.MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD)
                        .fetchAndroidProjects()
                        .getOnlyModelMap();

        Variant appDebug = AndroidProjectUtils.getVariantByName(models.get(":app"), "debug");

        Dependencies deps = appDebug.getMainArtifact().getDependencies();

        // Verify that app has one AndroidLibrary dependency, :library.
        Collection<AndroidLibrary> libs = deps.getLibraries();
        assertThat(libs).named("app android library deps count").hasSize(1);
        assertThat(Iterables.getOnlyElement(libs).getProject())
                .named("app android library deps path")
                .isEqualTo(":library");

        // Verify that app doesn't have module dependency.
        assertThat(deps.getJavaModules()).named("app module dependency count").isEmpty();

        // Verify that app doesn't have JavaLibrary dependency.
        assertThat(deps.getJavaLibraries()).named("app java dependency count").isEmpty();

        // Verify that app has runtime only dependencies on guava and the aar.
        List<String> runtimeOnlyClasses =
                deps.getRuntimeOnlyClasses()
                        .stream()
                        .map(file -> PathUtils.toSystemIndependentPath(file.toPath()))
                        .collect(Collectors.toList());

        assertThat(runtimeOnlyClasses).hasSize(2);
        // Verify the order of the artifacts too.
        assertThat(runtimeOnlyClasses.get(0)).endsWith("/guava-19.0.jar");
        assertThat(runtimeOnlyClasses.get(1)).endsWith("/aar-1/jars/classes.jar");
    }
}
