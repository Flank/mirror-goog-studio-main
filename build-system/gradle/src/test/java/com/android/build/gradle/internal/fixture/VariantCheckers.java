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

package com.android.build.gradle.internal.fixture;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.android.annotations.NonNull;
import com.android.build.gradle.AppExtension;
import com.android.build.gradle.LibraryExtension;
import com.android.build.gradle.api.ApkVariant;
import com.android.build.gradle.api.ApkVariantOutput;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.api.LibraryVariant;
import com.android.build.gradle.api.TestVariant;
import com.android.build.gradle.internal.api.TestedVariant;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.gradle.api.DomainObjectSet;
import org.junit.Assert;

public class VariantCheckers {

    @NonNull
    public static VariantChecker createAppChecker(@NonNull AppExtension android) {
        return new AppVariantChecker(android);
    }

    @NonNull
    public static VariantChecker createLibraryChecker(@NonNull LibraryExtension android) {
        return new LibraryVariantChecker(android);
    }

    public static int countVariants(Map<String, Integer> variants) {
        return variants.values().stream().mapToInt(Integer::intValue).sum();
    }

    public static void checkDefaultVariants(List<VariantScope> variants) {
        assertThat(Lists.transform(variants, VariantScope::getFullVariantName))
                .containsExactly(
                        "release", "debug", "debugAndroidTest", "releaseUnitTest", "debugUnitTest");
    }

    /**
     * Returns the variant with the given name, or null.
     *
     * @param variants the variants
     * @param name the name of the item to return
     * @return the found variant or null
     */
    static <T extends BaseVariant & TestedVariant> T findVariantMaybe(
            @NonNull Collection<T> variants, @NonNull String name) {
        return variants.stream().filter(t -> t.getName().equals(name)).findAny().orElse(null);
    }

    /**
     * Returns the variant with the given name. Fails if there is no such variant.
     *
     * @param variants the item collection to search for a match
     * @param name the name of the item to return
     * @return the found variant
     */
    static <T extends BaseVariant & TestedVariant> T findVariant(
            @NonNull Collection<T> variants, @NonNull String name) {
        T foundItem = findVariantMaybe(variants, name);
        assertThat(foundItem).named("Variant with name " + name).isNotNull();
        return foundItem;
    }

    @NonNull
    static TestVariant findTestVariantMaybe(
            @NonNull Collection<? extends TestVariant> variants, @NonNull String name) {
        return variants.stream().filter(t -> t.getName().equals(name)).findAny().orElse(null);
    }

    @NonNull
    static TestVariant findTestVariant(
            @NonNull Collection<? extends TestVariant> variants, @NonNull String name) {
        TestVariant foundItem = findTestVariantMaybe(variants, name);
        assertThat(foundItem).named("Test variant with name " + name).isNotNull();
        return foundItem;
    }

    /**
     * Returns the variant data with the given name. Fails if there is no such variant.
     *
     * @param variants the item collection to search for a match
     * @param name the name of the item to return
     * @return the found variant
     */
    public static <T extends BaseVariantData> T findVariantData(
            @NonNull Collection<VariantScope> variants, @NonNull String name) {
        Optional<?> result =
                variants.stream()
                        .filter(t -> t.getFullVariantName().equals(name))
                        .map(VariantScope::getVariantData)
                        .findAny();
        //noinspection unchecked: too much hassle with BaseVariantData generics, not worth it for test code.
        return (T)
                result.orElseThrow(
                        () -> new AssertionError("Variant data for " + name + " not found."));
    }

    private static class AppVariantChecker implements VariantChecker {
        @NonNull private final AppExtension android;

        public AppVariantChecker(@NonNull AppExtension android) {
            this.android = android;
        }

        @Override
        @NonNull
        public DomainObjectSet<TestVariant> getTestVariants() {
            return android.getTestVariants();
        }

        @NonNull
        @Override
        public Set<BaseTestedVariant> getVariants() {
            return android.getApplicationVariants()
                    .stream()
                    .map(BaseTestedVariant::create)
                    .collect(Collectors.toSet());
        }

        @Override
        public void checkTestedVariant(
                @NonNull String variantName,
                @NonNull String testedVariantName,
                @NonNull Collection<BaseTestedVariant> variants,
                @NonNull Set<TestVariant> testVariants) {
            BaseTestedVariant variant = findVariant(variants, variantName);
            assertNotNull(variant.getTestVariant());
            assertEquals(testedVariantName, variant.getTestVariant().getName());
            if (variant.getTestVariant() != null) {
                assertEquals(
                        variant.getTestVariant(), findTestVariant(testVariants, testedVariantName));
            }
            checkTasks(variant.getOriginal());
            checkTasks(variant.getTestVariant());
        }

        @Override
        public void checkNonTestedVariant(
                @NonNull String variantName, @NonNull Set<BaseTestedVariant> variants) {
            BaseTestedVariant variant = findVariant(variants, variantName);
            Assert.assertNull(variant.getTestVariant());
            checkTasks(variant.getOriginal());
        }

        @Override
        @NonNull
        public String getReleaseJavacTaskName() {
            return "compileReleaseJavaWithJavac";
        }

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
    }

    private static class LibraryVariantChecker implements VariantChecker {

        private final LibraryExtension android;

        public LibraryVariantChecker(LibraryExtension android) {
            this.android = android;
        }

        @Override
        public void checkNonTestedVariant(
                @NonNull String variantName, @NonNull Set<BaseTestedVariant> variants) {
            BaseTestedVariant variant = findVariant(variants, variantName);
            assertNotNull(variant);
            assertNull(variant.getTestVariant());
            checkLibraryTasks(variant.getOriginal());
        }

        private static void checkTestTasks(@NonNull TestVariant variant) {
            assertNotNull(variant.getAidlCompile());
            assertNotNull(variant.getMergeResources());
            assertNotNull(variant.getMergeAssets());
            assertNotNull(variant.getMergeResources());
            assertNotNull(variant.getGenerateBuildConfig());
            assertNotNull(variant.getJavaCompile());
            assertNotNull(variant.getProcessJavaResources());

            assertNotNull(variant.getAssemble());
            assertNotNull(variant.getUninstall());

            if (variant.isSigningReady()) {
                assertNotNull(variant.getInstall());
            } else {
                assertNull(variant.getInstall());
            }

            assertNotNull(variant.getConnectedInstrumentTest());
        }

        private static void checkLibraryTasks(@NonNull LibraryVariant variant) {
            assertNotNull(variant.getCheckManifest());
            assertNotNull(variant.getAidlCompile());
            assertNotNull(variant.getMergeResources());
            assertNotNull(variant.getGenerateBuildConfig());
            assertNotNull(variant.getJavaCompile());
            assertNotNull(variant.getProcessJavaResources());
            assertNotNull(variant.getAssemble());
        }

        @NonNull
        @Override
        public DomainObjectSet<TestVariant> getTestVariants() {
            return android.getTestVariants();
        }

        @NonNull
        @Override
        public Set<BaseTestedVariant> getVariants() {
            return android.getLibraryVariants()
                    .stream()
                    .map(BaseTestedVariant::create)
                    .collect(Collectors.toSet());
        }

        @NonNull
        @Override
        public String getReleaseJavacTaskName() {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public void checkTestedVariant(
                @NonNull String variantName,
                @NonNull String testedVariantName,
                @NonNull Collection<BaseTestedVariant> variants,
                @NonNull Set<TestVariant> testVariants) {
            BaseTestedVariant variant = findVariant(variants, variantName);
            assertNotNull(variant);
            assertNotNull(variant.getTestVariant());
            assertEquals(testedVariantName, variant.getTestVariant().getName());
            assertEquals(
                    variant.getTestVariant(), findTestVariant(testVariants, testedVariantName));
            checkLibraryTasks(variant.getOriginal());
            checkTestTasks(variant.getTestVariant());
        }
    }
}
