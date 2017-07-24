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

import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.JAVA;
import static org.junit.Assert.assertTrue;

import com.android.SdkConstants;
import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction.ModelContainer;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.Library;
import com.google.common.truth.Truth;
import groovy.transform.CompileStatic;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Assemble tests for rsSupportMode. */
@CompileStatic
public class RsSupportModeTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("rsSupportMode")
                    .addGradleProperties("android.useDeprecatedNdk=true")
                    .create();

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
    public void testRsSupportMode() throws Exception {
        LibraryGraphHelper helper = new LibraryGraphHelper(model);

        Variant debugVariant =
                ModelHelper.getVariant(model.getOnlyModel().getVariants(), "x86Debug");

        AndroidArtifact mainArtifact = debugVariant.getMainArtifact();

        DependencyGraphs graph = mainArtifact.getDependencyGraphs();

        List<Library> libraries = helper.on(graph).withType(JAVA).asLibraries();
        Truth.assertThat(libraries).isNotEmpty();

        boolean foundSupportJar = false;
        for (Library lib : libraries) {
            File file = lib.getArtifact();
            if (SdkConstants.FN_RENDERSCRIPT_V8_JAR.equals(file.getName())) {
                foundSupportJar = true;
                break;
            }
        }

        assertTrue("Found suppport jar check", foundSupportJar);
    }
}
