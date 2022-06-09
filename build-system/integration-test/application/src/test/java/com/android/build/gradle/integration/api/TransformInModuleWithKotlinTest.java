/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.integration.api;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ProjectBuildOutputUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.VariantBuildInformation;
import com.android.ide.common.process.ProcessException;
import com.android.testutils.apk.Apk;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipFile;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.google.common.truth.Truth.assertThat;

public class TransformInModuleWithKotlinTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("transformInModuleWithKotlin")
                    .create();

    @Rule public TemporaryFolder temporaryDirectory = new TemporaryFolder();

    /* Test to verify that AARs do not include Jacoco dependencies when published. */
    @Test
    public void canPublishLibraryAarWithCoverageEnabled()
            throws IOException, InterruptedException, ClassNotFoundException {
        GradleTestProject librarySubproject = project.getSubproject(":lib");
        Files.append(
                "\nandroid.buildTypes.debug.testCoverageEnabled true\n",
                project.getSubproject("lib").getBuildFile(),
                Charsets.UTF_8);
        project.executor().run("lib:assembleDebug");
        File libraryPublishedAar =
                FileUtils.join(librarySubproject.getOutputDir(), "aar", "lib-debug.aar");
        File tempTestData = temporaryDirectory.newFolder("testData");
        File extractedJar = new File(tempTestData, "classes.jar");
        // Extracts the zipped BuildConfig.class in library-debug.aar/classes.jar to
        // the extractedBuildConfigClass temporary file, so it can be later loaded
        // into a classloader.
        ZipFile zippedLibPublishedAar = new ZipFile(libraryPublishedAar);
        InputStream classesJar =
                zippedLibPublishedAar.getInputStream(zippedLibPublishedAar.getEntry("classes.jar"));
        try (FileOutputStream fos = new FileOutputStream(extractedJar)) {
            IOUtils.copy(classesJar, fos);
        }

        try (URLClassLoader it =
                URLClassLoader.newInstance(new URL[] {(extractedJar.toURI().toURL())})) {
            Class<?> buildConfig = it.loadClass("com.android.tests.libstest.lib.BuildConfig");
            try {
                // Invoking the constructor will throw a ClassNotFoundException for
                // Jacoco agent classes if the classes contain a call to Jacoco.
                // If there is no issues with invoking the constructor,
                // then there are no Jacoco dependencies in the AAR classes.
                buildConfig.getConstructor().newInstance();
            } catch (Exception e) {
                throw new AssertionError(
                        "This AAR is not publishable as it contains a dependency on Jacoco.", e);
            }
        }
    }

    @Test
    public void testTransform() throws IOException, InterruptedException, ProcessException {
        // enable coverage in library as regression test for b/65345148
        Files.append(
                "\nandroid.buildTypes.debug.testCoverageEnabled true\n",
                project.getSubproject("lib").getBuildFile(),
                Charsets.UTF_8);

        project.execute("clean", "assembleDebug");
        Map<String, AndroidProject> multi =
                project.model().fetchAndroidProjects().getOnlyModelMap();

        Collection<VariantBuildInformation> variantsBuildInformation =
                multi.get(":app").getVariantsBuildInformation();
        assertThat(variantsBuildInformation).hasSize(2);
        Optional<VariantBuildInformation> debug =
                variantsBuildInformation.stream()
                        .filter(
                                variantBuildOutput ->
                                        variantBuildOutput.getVariantName().equals("debug"))
                        .findFirst();
        assertThat(debug.isPresent()).isTrue();
        List<String> debugOutputs = ProjectBuildOutputUtils.getOutputFiles(debug.get());
        assertThat(debugOutputs).hasSize(1);
        assertThatApk(new Apk(new File(Iterables.getOnlyElement(debugOutputs))))
                .hasClass("LHello;");
    }
}
