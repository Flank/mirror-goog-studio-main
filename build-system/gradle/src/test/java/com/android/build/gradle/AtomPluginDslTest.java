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

import com.android.annotations.NonNull;
import com.android.build.gradle.api.AtomVariant;
import com.android.build.gradle.api.TestVariant;
import java.util.Collection;
import java.util.Set;
import org.gradle.api.DomainObjectSet;

public class AtomPluginDslTest
        extends AbstractAppPluginDslTest<AtomPlugin, AtomExtension, AtomVariant> {

    private static void checkAtomVariantTasks(@NonNull AtomVariant variant) {
        assertNotNull(variant.getAidlCompile());
        assertNotNull(variant.getMergeResources());
        assertNotNull(variant.getMergeAssets());
        assertNotNull(variant.getGenerateBuildConfig());
        assertNotNull(variant.getJavaCompiler());
        assertNotNull(variant.getProcessJavaResources());
        assertNotNull(variant.getAssemble());

        // FIX ME !
        //for (BaseVariantOutput baseVariantOutput : variant.getOutputs()) {
        //    assertTrue(baseVariantOutput instanceof AtomVariantOutputImpl);
        //    AtomVariantOutputImpl atomVariantOutput = (AtomVariantOutputImpl) baseVariantOutput;
        //
        //    assertNotNull(atomVariantOutput.getProcessManifest());
        //    assertNotNull(atomVariantOutput.getProcessResources());
        //    assertNotNull(atomVariantOutput.getBundleAtom());
        //}
    }

    @NonNull
    @Override
    protected Class<AtomPlugin> getPluginClass() {
        return AtomPlugin.class;
    }

    @NonNull
    @Override
    protected Class<AtomExtension> getExtensionClass() {
        return AtomExtension.class;
    }

    @NonNull
    @Override
    protected DomainObjectSet<TestVariant> getTestVariants() {
        return android.getTestVariants();
    }

    @NonNull
    @Override
    protected DomainObjectSet<AtomVariant> getVariants() {
        return android.getAtomVariants();
    }

    @NonNull
    @Override
    protected String getPluginName() {
        return "com.android.atom";
    }

    @NonNull
    @Override
    protected String getReleaseJavacTaskName() {
        return "compileReleaseAtomJavaWithJavac";
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        android.getDefaultConfig().setVersionName("1.0");
    }

    @Override
    protected void checkTestedVariant(
            @NonNull String variantName,
            @NonNull String testedVariantName,
            @NonNull Collection<AtomVariant> variants,
            @NonNull Set<TestVariant> testVariants) {
        AtomVariant variant = findVariant(variants, variantName);
        assertNotNull(variant);
        assertNotNull(variant.getTestVariant());
        assertEquals(testedVariantName, variant.getTestVariant().getName());
        assertEquals(variant.getTestVariant(), findVariantMaybe(testVariants, testedVariantName));
        checkAtomVariantTasks(variant);
        assertTrue(variant.getTestVariant() instanceof TestVariant);
        checkTestTasks(variant.getTestVariant());
    }

    @Override
    protected void checkNonTestedVariant(
            @NonNull String variantName, @NonNull Set<AtomVariant> variants) {
        AtomVariant variant = findVariant(variants, variantName);
        assertNotNull(variant);
        assertNull(variant.getTestVariant());
        checkAtomVariantTasks(variant);
    }
}
