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
import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.Variant;
import com.google.common.collect.Iterables;
import com.google.common.truth.Truth;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.File;
import java.util.Collection;

/**
 * test for same dependency with and without classifier.
 */
public class AppWithClassifierDepTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithClassifierDep")
            .create();
    public static AndroidProject model;

    @BeforeClass
    public static void setUp() {
        model = project.model().getSingle();
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        model = null;
    }

    @Test
    public void checkDebugDepInModel() {
        Variant variant = ModelHelper.getVariant(model.getVariants(), "debug");
        Dependencies dependencies = variant.getMainArtifact().getCompileDependencies();

        Collection<JavaLibrary> javaLibs = dependencies.getJavaLibraries();

        assertThat(javaLibs).named("javalibs count").hasSize(1);
        JavaLibrary javaLib = Iterables.getOnlyElement(javaLibs);

        assertThat(javaLib.getJarFile())
                .named("jar location")
                .isEqualTo(new File(project.getTestDir(), "repo/com/foo/sample/1.0/sample-1.0.jar"));
        assertThat(javaLib.getResolvedCoordinates())
                .named("resolved coordinates")
                .isEqualTo("com.foo", "sample", "1.0");
    }

    @Test
    public void checkAndroidTestDepInModel() {
        Variant debugVariant = ModelHelper.getVariant(model.getVariants(), "debug");

        AndroidArtifact androidTestArtifact = ModelHelper.getAndroidArtifact(
                debugVariant.getExtraAndroidArtifacts(), ARTIFACT_ANDROID_TEST);
        Truth.assertThat(androidTestArtifact).isNotNull();

        Dependencies dependencies = androidTestArtifact.getCompileDependencies();

        Collection<JavaLibrary> javaLibs = dependencies.getJavaLibraries();

        assertThat(javaLibs).named("javalibs count").hasSize(1);
        JavaLibrary javaLib = Iterables.getOnlyElement(javaLibs);

        assertEquals(
                new File(project.getTestDir(), "repo/com/foo/sample/1.0/sample-1.0-testlib.jar"),
                javaLib.getJarFile());
        assertThat(javaLib.getResolvedCoordinates())
                .named("resolved coordinates")
                .isEqualTo("com.foo", "sample", "1.0", null, "testlib");
    }
}
