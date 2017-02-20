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

package com.android.build.gradle.integration.application

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction.ModelContainer
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper
import com.android.build.gradle.integration.common.utils.ModelHelper
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidProject
import com.android.builder.model.Variant
import com.android.builder.model.level2.Library
import com.android.builder.model.level2.DependencyGraphs
import com.google.common.truth.Truth
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.JAVA
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertThat
import static org.junit.Assert.assertTrue

/**
 * Assemble tests for rsSupportMode.
 */
@CompileStatic
class RsSupportModeTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("rsSupportMode")
            .addGradleProperties("android.useDeprecatedNdk=true")
            .create()
    static ModelContainer<AndroidProject> model

    @BeforeClass
    static void setUp() {
        model =project.executeAndReturnModel("clean", "assembleDebug")
    }

    @AfterClass
    static void cleanUp() {
        project = null
        model = null
    }

    @Test
    void testRsSupportMode() throws Exception {
        LibraryGraphHelper helper = new LibraryGraphHelper(model);

        Variant debugVariant = ModelHelper.getVariant(
                model.getOnlyModel().getVariants(), "x86Debug")

        AndroidArtifact mainArtifact = debugVariant.getMainArtifact()

        DependencyGraphs graph = mainArtifact.getDependencyGraphs()

        List<Library> libraries = helper.on(graph).withType(JAVA).asLibraries();
        Truth.assertThat(libraries).isNotEmpty();

        boolean foundSupportJar = false
        for (Library lib : libraries) {
            File file = lib.getArtifact()
            if (SdkConstants.FN_RENDERSCRIPT_V8_JAR.equals(file.getName())) {
                foundSupportJar = true
                break
            }
        }

        assertTrue("Found suppport jar check", foundSupportJar)
    }
}
