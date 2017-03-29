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

package com.android.build.gradle;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.api.ApkVariant;
import com.android.build.gradle.api.ApkVariantOutput;
import com.android.build.gradle.api.ApplicationVariant;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.api.TestVariant;
import com.android.build.gradle.tasks.MergeResources;
import groovy.util.Eval;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.gradle.api.DomainObjectSet;
import org.junit.Assert;

/** Tests for the public DSL of the App plugin ("android") */
public class AppPluginDslTest
        extends AbstractAppPluginDslTest<AppPlugin, AppExtension, ApplicationVariant> {

    private static void checkTasks(@NonNull ApkVariant variant) {
        boolean isTestVariant = variant instanceof TestVariant;

        assertNotNull(variant.getAidlCompile());
        assertNotNull(variant.getMergeResources());
        assertNotNull(variant.getMergeAssets());
        assertNotNull(variant.getGenerateBuildConfig());
        assertNotNull(variant.getJavaCompiler());
        assertNotNull(variant.getProcessJavaResources());
        assertNotNull(variant.getAssemble());
        assertNotNull(variant.getUninstall());

        for (BaseVariantOutput baseVariantOutput : variant.getOutputs()) {
            Assert.assertTrue(baseVariantOutput instanceof ApkVariantOutput);
            ApkVariantOutput apkVariantOutput = (ApkVariantOutput) baseVariantOutput;
            assertNotNull(apkVariantOutput.getProcessManifest());
            assertNotNull(apkVariantOutput.getProcessResources());
            assertNotNull(apkVariantOutput.getPackageApplication());
        }

        if (variant.isSigningReady()) {
            assertNotNull(variant.getInstall());

            for (BaseVariantOutput baseVariantOutput : variant.getOutputs()) {
                ApkVariantOutput apkVariantOutput = (ApkVariantOutput) baseVariantOutput;

                // Check if we did the right thing, depending on the default value of the flag.
                Assert.assertNotNull(apkVariantOutput.getZipAlign());
            }

        } else {
            Assert.assertNull(variant.getInstall());
        }

        if (isTestVariant) {
            TestVariant testVariant = DefaultGroovyMethods.asType(variant, TestVariant.class);
            assertNotNull(testVariant.getConnectedInstrumentTest());
            assertNotNull(testVariant.getTestedVariant());
        }
    }

    @Override
    @NonNull
    protected DomainObjectSet<TestVariant> getTestVariants() {
        return android.getTestVariants();
    }

    @NonNull
    @Override
    protected DomainObjectSet<ApplicationVariant> getVariants() {
        return android.getApplicationVariants();
    }

    @Override
    @NonNull
    protected Class<AppPlugin> getPluginClass() {
        return AppPlugin.class;
    }

    @Override
    @NonNull
    protected Class<AppExtension> getExtensionClass() {
        return AppExtension.class;
    }

    @Override
    protected void checkTestedVariant(
            @NonNull String variantName,
            @NonNull String testedVariantName,
            @NonNull Collection<ApplicationVariant> variants,
            @NonNull Set<TestVariant> testVariants) {
        ApplicationVariant variant = findVariant(variants, variantName);
        assertNotNull(variant.getTestVariant());
        assertEquals(testedVariantName, variant.getTestVariant().getName());
        assertEquals(variant.getTestVariant(), findVariantMaybe(testVariants, testedVariantName));
        checkTasks(variant);
        checkTasks(variant.getTestVariant());
    }

    @Override
    protected void checkNonTestedVariant(
            @NonNull String variantName, @NonNull Set<ApplicationVariant> variants) {
        ApplicationVariant variant = findVariant(variants, variantName);
        Assert.assertNull(variant.getTestVariant());
        checkTasks(variant);
    }

    @Override
    @NonNull
    protected String getPluginName() {
        return "com.android.application";
    }

    @Override
    @NonNull
    protected String getReleaseJavacTaskName() {
        return "compileReleaseJavaWithJavac";
    }

    public void testGeneratedDensities() throws Exception {
        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "    flavorDimensions 'foo'\n"
                        + "    productFlavors {\n"
                        + "        f1 {\n"
                        + "        }\n"
                        + "\n"
                        + "        f2  {\n"
                        + "            vectorDrawables {\n"
                        + "                generatedDensities 'ldpi'\n"
                        + "                generatedDensities += ['mdpi']\n"
                        + "            }\n"
                        + "        }\n"
                        + "\n"
                        + "        f3 {\n"
                        + "            vectorDrawables {\n"
                        + "                generatedDensities = defaultConfig.generatedDensities - ['ldpi', 'mdpi']\n"
                        + "            }\n"
                        + "        }\n"
                        + "\n"
                        + "        f4.vectorDrawables.generatedDensities = []\n"
                        + "\n"
                        + "        oldSyntax {\n"
                        + "            generatedDensities = ['ldpi']\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        plugin.createAndroidTasks(false);

        checkGeneratedDensities(
                "mergeF1DebugResources", "ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi");
        checkGeneratedDensities("mergeF2DebugResources", "ldpi", "mdpi");
        checkGeneratedDensities("mergeF3DebugResources", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi");
        checkGeneratedDensities("mergeF4DebugResources");
        checkGeneratedDensities("mergeOldSyntaxDebugResources", "ldpi");
    }

    public void testUseSupportLibrary_default() throws Exception {
        plugin.createAndroidTasks(false);

        assertThat(getTask("mergeDebugResources", MergeResources.class).isDisableVectorDrawables())
                .isFalse();
    }

    public void testUseSupportLibrary_flavors() throws Exception {

        Eval.me(
                "project",
                project,
                "\n"
                        + "project.android {\n"
                        + "\n"
                        + "    flavorDimensions 'foo'\n"
                        + "    productFlavors {\n"
                        + "        f1 {\n"
                        + "        }\n"
                        + "\n"
                        + "        f2  {\n"
                        + "            vectorDrawables {\n"
                        + "                useSupportLibrary true\n"
                        + "            }\n"
                        + "        }\n"
                        + "\n"
                        + "        f3 {\n"
                        + "            vectorDrawables {\n"
                        + "                useSupportLibrary = false\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        plugin.createAndroidTasks(false);

        assertThat(
                        getTask("mergeF1DebugResources", MergeResources.class)
                                .isDisableVectorDrawables())
                .isFalse();
        assertThat(
                        getTask("mergeF2DebugResources", MergeResources.class)
                                .isDisableVectorDrawables())
                .isTrue();
        assertThat(
                        getTask("mergeF3DebugResources", MergeResources.class)
                                .isDisableVectorDrawables())
                .isFalse();
    }

    private void checkGeneratedDensities(String taskName, String... densities) {
        MergeResources mergeResources = getTask(taskName, MergeResources.class);
        assertThat(mergeResources.getGeneratedDensities())
                .containsExactlyElementsIn(Arrays.asList(densities));
    }
}
