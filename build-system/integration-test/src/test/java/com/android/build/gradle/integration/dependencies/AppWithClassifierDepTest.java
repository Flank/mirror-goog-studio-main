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
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.JAVA;
import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST;

import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction.ModelContainer;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.Library;
import java.io.File;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * test for same dependency with and without classifier.
 */
public class AppWithClassifierDepTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithClassifierDep")
            .create();
    public static ModelContainer<AndroidProject> model;
    private static LibraryGraphHelper helper;

    @BeforeClass
    public static void setUp() {
        model = project.model().getSingle();
        helper = new LibraryGraphHelper(model);
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        model = null;
        helper = null;
    }

    @Test
    public void checkDebugDepInModel() {
        Variant variant = ModelHelper.getVariant(model.getOnlyModel().getVariants(), "debug");

        DependencyGraphs graph = variant.getMainArtifact().getDependencyGraphs();

        LibraryGraphHelper.Items javaItems = helper.on(graph).withType(JAVA);
        assertThat(javaItems.mapTo(COORDINATES)).containsExactly("com.foo:sample:1.0@jar");

        Library library = javaItems.asSingleLibrary();
        assertThat(library.getArtifact())
                .named("jar location")
                .isEqualTo(new File(
                        project.getTestDir(), "repo/com/foo/sample/1.0/sample-1.0.jar"));
    }

    @Test
    public void checkAndroidTestDepInModel() {
        Variant debugVariant = ModelHelper.getVariant(model.getOnlyModel().getVariants(), "debug");

        AndroidArtifact androidTestArtifact = ModelHelper.getAndroidArtifact(
                debugVariant.getExtraAndroidArtifacts(), ARTIFACT_ANDROID_TEST);

        DependencyGraphs graph = androidTestArtifact.getDependencyGraphs();

        LibraryGraphHelper.Items javaItems = helper.on(graph).withType(JAVA);
        assertThat(javaItems.mapTo(COORDINATES)).containsExactly("com.foo:sample:1.0:testlib@jar");

        Library library = javaItems.asSingleLibrary();
        assertThat(library.getArtifact())
                .named("jar location")
                .isEqualTo(new File(
                        project.getTestDir(),
                        "repo/com/foo/sample/1.0/sample-1.0-testlib.jar"));
    }
}
