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

package com.android.build.gradle.integration.library;

import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.JAVA;
import static com.android.builder.core.BuilderConstants.DEBUG;
import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction.ModelContainer;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.Library;
import groovy.transform.CompileStatic;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/** Assemble tests for libTestDep. */
@CompileStatic
public class LibTestDepTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("libTestDep").create();

    private static ModelContainer<AndroidProject> model;

    @BeforeClass
    public static void setUp() throws IOException, InterruptedException {
        model = project.executeAndReturnModel("clean", "assembleDebug");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        model = null;
    }

    @Test
    public void lint() throws IOException, InterruptedException {
        project.execute("lint");
    }

    @Test
    public void checkTestVariantInheritsDepsFromMainVariant() {
        LibraryGraphHelper helper = new LibraryGraphHelper(model);

        Collection<Variant> variants = model.getOnlyModel().getVariants();
        Variant debugVariant = ModelHelper.getVariant(variants, DEBUG);

        Collection<AndroidArtifact> extraAndroidArtifact = debugVariant.getExtraAndroidArtifacts();
        AndroidArtifact testArtifact =
                ModelHelper.getAndroidArtifact(extraAndroidArtifact, ARTIFACT_ANDROID_TEST);

        DependencyGraphs testGraph = testArtifact.getDependencyGraphs();
        List<Library> javaLibraries = helper.on(testGraph).withType(JAVA).asLibraries();
        assertEquals(2, javaLibraries.size());
        for (Library lib : javaLibraries) {
            File f = lib.getArtifact();
            assertTrue(
                    f.getName().equals("guava-19.0.jar") || f.getName().equals("jsr305-1.3.9.jar"));
        }
    }

    @Test
    public void checkDebugAndReleaseOutputHaveDifferentNames() {
        ModelHelper.compareDebugAndReleaseOutput(model.getOnlyModel());
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedCheck() throws IOException, InterruptedException {
        project.executeConnectedCheck();
    }
}
