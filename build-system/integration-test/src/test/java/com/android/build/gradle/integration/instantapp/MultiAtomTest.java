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

package com.android.build.gradle.integration.instantapp;

import static com.android.SdkConstants.FD_RES_CLASS;
import static com.android.SdkConstants.FD_SOURCE_GEN;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.category.SmokeTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.builder.model.AndroidAtom;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.Variant;
import com.android.utils.FileUtils;
import com.google.common.collect.Iterables;
import java.io.File;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Basic instantApp test with a multi-atom project.
 */
@Category(SmokeTests.class)
public class MultiAtomTest {
    @ClassRule
    public static GradleTestProject sProject = GradleTestProject.builder()
            .fromTestProject("multiAtom")
            .withoutNdk()
            .create();

    @AfterClass
    public static void cleanUp() {
        sProject = null;
    }

    @Test
    public void build() throws Exception {
        sProject.execute("clean", ":instantApp:assembleRelease");

        // Tests that the atom-dependent library R.java file is regenerated.
        File libResFile =
                sProject.getSubproject(":instantApp")
                        .file(
                                FileUtils.join(
                                        "build",
                                        AndroidProject.FD_GENERATED,
                                        FD_SOURCE_GEN,
                                        FD_RES_CLASS,
                                        "atomc-release",
                                        "com",
                                        "android",
                                        "tests",
                                        "multiatom",
                                        "libc",
                                        "R.java"));
        assertThat(libResFile).named("LibC R.java file").isFile();

        assertThat(libResFile)
                .named("libc R.java file")
                .containsAllOf("public static final int libc_name =");
    }

    @Test
    public void testModelLevel1() {
        Map<String, AndroidProject> models;
        models = sProject.model().level(AndroidProject.MODEL_LEVEL_1_SYNC_ISSUE).getMulti();

        AndroidProject instantAppModel = models.get(":instantApp");
        assertThat(instantAppModel).named("Instant app model").isNotNull();
        assertThat(instantAppModel.getProjectType())
                .named("Instant app project type")
                .isEqualTo(AndroidProject.PROJECT_TYPE_INSTANTAPP);

        Variant variant = ModelHelper.getVariant(instantAppModel.getVariants(), "release");
        Dependencies dependencies = variant.getMainArtifact().getPackageDependencies();
        assertThat(dependencies.getJavaLibraries()).named("Javalibs dependencies").isEmpty();
        assertThat(dependencies.getLibraries()).named("Android dependencies").isEmpty();
        assertThat(dependencies.getAtoms()).named("Atoms dependencies").isEmpty();
        assertThat(dependencies.getBaseAtom()).named("Base atom").isNull();
    }

    @Test
    public void testModelFull() {
        Map<String, AndroidProject> models;
        models = sProject.model().level(AndroidProject.MODEL_LEVEL_2_DEP_GRAPH).getMulti();

        AndroidProject instantAppModel = models.get(":instantApp");
        assertThat(instantAppModel).named("InstantApp model").isNotNull();
        assertThat(instantAppModel.getProjectType())
                .named("InstantApp project type")
                .isEqualTo(AndroidProject.PROJECT_TYPE_INSTANTAPP);

        Variant variant = ModelHelper.getVariant(instantAppModel.getVariants(), "release");
        Dependencies instantAppDeps = variant.getMainArtifact().getPackageDependencies();
        assertThat(instantAppDeps.getJavaLibraries())
                .named("InstantApp Javalibs dependencies")
                .isEmpty();
        assertThat(instantAppDeps.getLibraries())
                .named("InstantApp Android dependencies")
                .isEmpty();
        assertThat(instantAppDeps.getAtoms()).named("InstantApp Atoms dependencies").hasSize(3);

        AndroidAtom baseAtom = instantAppDeps.getBaseAtom();
        assertThat(baseAtom).named("Base atom").isNotNull();
        assertThat(baseAtom.getResolvedCoordinates())
                .named("Base atom resolved coordinates")
                .isEqualTo("multiAtom", "base", "unspecified", "atombundle", null);

        AndroidProject atomCModel = models.get(":atomc");
        assertThat(atomCModel).named("AtomC model").isNotNull();

        Variant atomCVariant = ModelHelper.getVariant(atomCModel.getVariants(), "release");
        assertThat(atomCVariant).named("AtomC release variant").isNotNull();
        Dependencies atomCDeps = atomCVariant.getMainArtifact().getPackageDependencies();
        assertThat(atomCDeps.getJavaLibraries()).named("atomC javalibs dependencies").isEmpty();

        assertThat(atomCDeps.getLibraries()).named("AtomC lib dependencies").hasSize(1);
        AndroidLibrary libC = Iterables.getOnlyElement(atomCDeps.getLibraries());
        assertThat(libC.getResolvedCoordinates())
                .named("LibC resolved coordinates")
                .isEqualTo("multiAtom", "libc", "unspecified", "aar", null);

        assertThat(atomCDeps.getAtoms()).named("AtomC atom dependencies").hasSize(1);
        assertThat(Iterables.getOnlyElement(atomCDeps.getAtoms()))
                .named("AtomC atom dependency")
                .isEqualTo(atomCDeps.getBaseAtom());
    }
}
