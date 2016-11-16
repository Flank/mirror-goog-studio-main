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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction.ModelContainer
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.ApkHelper
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.common.utils.ModelHelper
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidArtifactOutput
import com.android.builder.model.AndroidProject
import com.android.builder.model.Variant
import org.junit.Rule
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertTrue

/**
 * Tests for @{applicationId} placeholder presence in library manifest files.
 * Such placeholders should be left intact until the library is merged into a consuming application
 * with a known application Id.
 */
class ApplicationIdInLibsTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("applicationIdInLibsTest")
            .create()

    @Test
    public void "test library placeholder substitution in final apk"() throws Exception {
        ModelContainer<AndroidProject> models =
                project.executeAndReturnMultiModel(
                        "clean",
                        "app:assembleDebug")
        assertTrue(checkPermissionPresent(
                models,
                "'com.example.manifest_merger_example.flavor.permission.C2D_MESSAGE'"))

        TestFileUtils.searchAndReplace(
                project.file('app/build.gradle'),
                "manifest_merger_example.flavor",
                "manifest_merger_example.change")

        models = project.executeAndReturnMultiModel(
                "clean",
                "app:assembleDebug")
        assertFalse(checkPermissionPresent(
                models,
                "'com.example.manifest_merger_example.flavor.permission.C2D_MESSAGE'"))
        assertTrue(checkPermissionPresent(
                models,
                "'com.example.manifest_merger_example.change.permission.C2D_MESSAGE'"))
    }

    private static boolean checkPermissionPresent(
            ModelContainer<AndroidProject> models, String permission) {
        // Load the custom model for the project
        Collection<Variant> variants = models.getModelMap().get(":app").getVariants()
        assertEquals("Variant Count", 2, variants.size())

        // get the main artifact of the debug artifact
        Variant debugVariant = ModelHelper.getVariant(variants, "flavorDebug")
        AndroidArtifact debugMainArtifact = debugVariant.getMainArtifact()
        assertNotNull("Debug main info null-check", debugMainArtifact)

        // get the outputs.
        Collection<AndroidArtifactOutput> debugOutputs = debugMainArtifact.getOutputs()
        assertNotNull(debugOutputs)

        assertEquals(1, debugOutputs.size())
        AndroidArtifactOutput output = debugOutputs.first()
        assertEquals(1, output.getOutputs().size())

        List<String> apkBadging =
                ApkHelper.getApkBadging(output.getOutputs().first().getOutputFile());

        for (String line : apkBadging) {
            if (line.contains("uses-permission: name=" +
                    permission)) {
                return true;
            }
        }

        return false;
    }
}
