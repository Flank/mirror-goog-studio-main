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

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.PLAY_SERVICES_VERSION;
import static com.android.build.gradle.integration.common.fixture.GradleTestProject.SUPPORT_LIB_VERSION;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Property.COORDINATES;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.ANDROID;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.JAVA;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction.ModelContainer;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.ProductFlavorHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.core.BuilderConstants;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.ClassField;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.MavenCoordinates;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.Library;
import com.android.builder.model.level2.LibraryGraph;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Assemble tests for basic that loads the model but doesn't build.
 */
public class BasicTest2 {
    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("basic")
            .create();

    @Rule
    public Adb adb = new Adb();

    public static ModelContainer<AndroidProject> modelContainer;

    @BeforeClass
    public static void getModel() throws IOException {
        modelContainer = project.executeAndReturnModel("clean");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        modelContainer = null;
    }

    @Test
    public void checkVariantDetails() throws Exception {
        LibraryGraphHelper helper = new LibraryGraphHelper(modelContainer);
        AndroidProject model = modelContainer.getOnlyModel();

        Collection<Variant> variants = model.getVariants();
        assertThat(variants).named("variant list").hasSize(2);

        // debug variant
        Variant debugVariant = ModelHelper.getVariant(variants, BuilderConstants.DEBUG);
        new ProductFlavorHelper(debugVariant.getMergedFlavor(), "Debug Merged Flavor")
                .setVersionCode(12)
                .setVersionName("2.0")
                .setMinSdkVersion(16)
                .setTargetSdkVersion(16)
                .setTestInstrumentationRunner("android.test.InstrumentationTestRunner")
                .setTestHandleProfiling(Boolean.FALSE)
                .setTestFunctionalTest(null)
                .test();

        // debug variant, tested.
        AndroidArtifact debugMainInfo = debugVariant.getMainArtifact();
        assertThat(debugMainInfo)
                .named("debug main artifact")
                .isNotNull();
        assertThat(debugMainInfo.getApplicationId())
                .named("debug package name")
                .isEqualTo("com.android.tests.basic.debug");
        assertThat(debugMainInfo.isSigned())
                .named("debug isSigned")
                .isTrue();
        assertThat(debugMainInfo.getSourceGenTaskName())
                .named("debug source gen task name")
                .isEqualTo("generateDebugSources");
        assertThat(debugMainInfo.getCompileTaskName())
                .named("debug compile task name")
                .isEqualTo("compileDebugSources");

        Collection<AndroidArtifactOutput> debugMainOutputs = debugMainInfo.getOutputs();
        assertThat(debugMainOutputs).named("debug outputs").isNotNull();
        assertThat(debugMainOutputs).named("debug outputs").hasSize(1);

        AndroidArtifactOutput debugMainOutput = Iterables.getOnlyElement(debugMainOutputs);
        assertThat(debugMainOutput)
                .named("debug output")
                .isNotNull();
        assertThat(debugMainOutput.getMainOutputFile())
                .named("debug output file")
                .isNotNull();
        assertThat(debugMainOutput.getMainOutputFile())
                .named("debug output assemble task name")
                .isNotNull();
        assertThat(debugMainOutput.getMainOutputFile())
                .named("debug output generate manifest task name")
                .isNotNull();
        assertThat(debugMainOutput.getVersionCode())
                .named("debug output versionCode")
                .isEqualTo(12);

        // check debug dependencies
        LibraryGraph compileGraph = debugMainInfo.getCompileGraph();
        assertThat(compileGraph).named("debug compile graph").isNotNull();

        assertThat(helper.on(compileGraph).withType(JAVA).asList())
                .named("debug compile java libs")
                .isEmpty();

        LibraryGraphHelper.Items androidItems = helper.on(compileGraph).withType(ANDROID);

        Map<String, Integer> coordinates = ImmutableMap.of(
                "com.google.android.gms:play-services-base:aar:" + PLAY_SERVICES_VERSION, 0,
                "com.android.support:support-v13:aar:" + SUPPORT_LIB_VERSION, 1,
                "com.android.support:support-v4:aar:" + SUPPORT_LIB_VERSION, 0);

        Set<String> coordinateCopies = Sets.newHashSet(coordinates.keySet());

        assertThat(androidItems.mapTo(COORDINATES))
                .named("debug compile android libs")
                .containsExactlyElementsIn(coordinateCopies);

        for (Library androidLibrary : androidItems.asLibraries()) {
            assertThat(androidLibrary).isNotNull();
            assertThat(androidLibrary.getArtifact())
                    .named("Artifact for " + androidLibrary.getArtifactAddress())
                    .isNotNull();
            assertThat(androidLibrary.getFolder())
                    .named("Folder for " + androidLibrary.getArtifactAddress())
                    .isNotNull();
            assertThat(androidLibrary.getArtifactAddress())
                    .named("coordinates for " + androidLibrary.getArtifactAddress())
                    .isIn(coordinateCopies);
            coordinateCopies.remove(androidLibrary.getArtifactAddress());
        }

        // this variant is tested.
        Collection<AndroidArtifact> debugExtraAndroidArtifacts = debugVariant
                .getExtraAndroidArtifacts();
        AndroidArtifact debugTestInfo = ModelHelper.getAndroidArtifact(
                debugExtraAndroidArtifacts,
                AndroidProject.ARTIFACT_ANDROID_TEST);

        assertThat(debugTestInfo).named("test artifact").isNotNull();
        assertThat(debugTestInfo.getApplicationId())
                .named("test package")
                .isEqualTo("com.android.tests.basic.debug.test");
        assertThat(debugTestInfo.isSigned())
                .named("test isSigned")
                .isTrue();
        assertThat(debugTestInfo.getSourceGenTaskName())
                .named("test source gen task name")
                .isEqualTo("generateDebugAndroidTestSources");
        assertThat(debugTestInfo.getCompileTaskName())
                .named("test compile task name")
                .isEqualTo("compileDebugAndroidTestSources");

        Collection<File> generatedResFolders = debugTestInfo.getGeneratedResourceFolders();
        assertThat(generatedResFolders).named("test generated res folders").isNotNull();
        // size 2 = rs output + resValue output
        assertThat(generatedResFolders).named("test generated res folders").hasSize(2);

        Collection<AndroidArtifactOutput> debugTestOutputs = debugTestInfo.getOutputs();
        assertThat(debugTestOutputs).named("test outputs").isNotNull();
        assertThat(debugTestOutputs).named("test outputs").hasSize(1);

        AndroidArtifactOutput debugTestOutput = Iterables.getOnlyElement(debugTestOutputs);
        assertThat(debugTestOutput)
                .named("test output")
                .isNotNull();
        assertThat(debugTestOutput.getMainOutputFile())
                .named("test output file")
                .isNotNull();
        assertThat(debugTestOutput.getMainOutputFile())
                .named("test output assemble task name")
                .isNotNull();
        assertThat(debugTestOutput.getMainOutputFile())
                .named("test output generate manifest task name")
                .isNotNull();

        // test the resValues and buildConfigFields.
        ProductFlavor defaultConfig = model.getDefaultConfig().getProductFlavor();
        Map<String, ClassField> buildConfigFields = defaultConfig.getBuildConfigFields();
        testMap(buildConfigFields, "defaultConfig buildconfig fields", ClassField::getValue,
                "DEFAULT", "true", "FOO", "\"foo2\"");

        Map<String, ClassField> resValues = defaultConfig.getResValues();
        testMap(resValues, "defaultConfig resValues", ClassField::getValue, "foo", "foo");

        // test on the debug build type.
        Collection<BuildTypeContainer> buildTypes = model.getBuildTypes();
        for (BuildTypeContainer buildTypeContainer : buildTypes) {
            if (buildTypeContainer.getBuildType().getName().equals(BuilderConstants.DEBUG)) {
                buildConfigFields = buildTypeContainer.getBuildType().getBuildConfigFields();

                testMap(buildConfigFields, "debug buildconfig fields", ClassField::getValue,
                        "FOO", "\"bar\"");

                resValues = buildTypeContainer.getBuildType().getResValues();
                testMap(resValues, "debug resValues", ClassField::getValue, "foo", "foo2");
            }
        }

        // now test the merged flavor
        ProductFlavor mergedFlavor = debugVariant.getMergedFlavor();

        buildConfigFields = mergedFlavor.getBuildConfigFields();
        testMap(buildConfigFields, "mergedFlavor buildconfig fields", ClassField::getValue,
                "DEFAULT", "true", "FOO", "\"foo2\"");

        resValues = mergedFlavor.getResValues();
        testMap(resValues, "mergedFlavor resValues", ClassField::getValue, "foo", "foo");

        // release variant, not tested.
        Variant releaseVariant = ModelHelper.getVariant(variants, "release");

        AndroidArtifact relMainInfo = releaseVariant.getMainArtifact();
        assertThat(relMainInfo).named("release artifact").isNotNull();
        assertThat(relMainInfo.getApplicationId())
                .named("release package")
                .isEqualTo("com.android.tests.basic");
        assertThat(relMainInfo.isSigned())
                .named("release isSigned")
                .isFalse();
        assertThat(relMainInfo.getSourceGenTaskName())
                .named("release source gen task name")
                .isEqualTo("generateReleaseSources");
        assertThat(relMainInfo.getCompileTaskName())
                .named("release compile task name")
                .isEqualTo("compileReleaseSources");

        Collection<AndroidArtifactOutput> relMainOutputs = relMainInfo.getOutputs();
        assertThat(relMainOutputs).named("release outputs").isNotNull();
        assertThat(relMainOutputs).named("release outputs").hasSize(1);

        AndroidArtifactOutput relMainOutput = Iterables.getOnlyElement(relMainOutputs);
        assertThat(relMainOutput)
                .named("release output")
                .isNotNull();
        assertThat(relMainOutput.getMainOutputFile())
                .named("release output file")
                .isNotNull();
        assertThat(relMainOutput.getMainOutputFile())
                .named("release output assemble task name")
                .isNotNull();
        assertThat(relMainOutput.getMainOutputFile())
                .named("release output generate manifest task name")
                .isNotNull();
        assertThat(relMainOutput.getVersionCode())
                .named("release output versionCode")
                .isEqualTo(13);

        Collection<AndroidArtifact> releaseExtraAndroidArtifacts =
            releaseVariant.getExtraAndroidArtifacts();
        AndroidArtifact relTestInfo = ModelHelper.getAndroidArtifact(
                releaseExtraAndroidArtifacts, AndroidProject.ARTIFACT_ANDROID_TEST);
        assertThat(relTestInfo).named("release test artifact").isNull();

        // check release dependencies
        LibraryGraph releaseGraph = relMainInfo.getCompileGraph();
        assertThat(releaseGraph).named("release compile graph").isNotNull();

        assertThat(helper.on(releaseGraph).withType(JAVA).asList())
                .named("release compile java libs")
                .isEmpty();

        androidItems = helper.on(releaseGraph).withType(ANDROID);
        coordinateCopies = Sets.newHashSet(coordinates.keySet());

        assertThat(androidItems.mapTo(COORDINATES))
                .named("release compile android libs")
                .containsExactlyElementsIn(coordinateCopies);

        for (Library androidLibrary : androidItems.asLibraries()) {
            assertThat(androidLibrary).isNotNull();
            assertThat(androidLibrary.getArtifact())
                    .named("Artifact for " + androidLibrary.getArtifactAddress())
                    .isNotNull();
            assertThat(androidLibrary.getFolder())
                    .named("Folder for " + androidLibrary.getArtifactAddress())
                    .isNotNull();
            assertThat(androidLibrary.getArtifactAddress())
                    .named("coordinates for " + androidLibrary.getArtifactAddress())
                    .isIn(coordinateCopies);
            coordinateCopies.remove(androidLibrary.getArtifactAddress());
            assertThat(androidLibrary.getLocalJars().size())
                    .named("local jar count for " + androidLibrary.getArtifactAddress())
                    .isEqualTo(coordinates.get(androidLibrary.getArtifactAddress()));
        }
    }

    private static <K, V, W> void testMap(
            @NonNull Map<K, V> map,
            @NonNull String name,
            @NonNull Function<V, W> function,
            @NonNull Object... keyValues) {
        assertThat(keyValues.length % 2).named("key/value length").isEqualTo(0);

        assertThat(map).named(name).isNotNull();
        assertThat(map).named(name).hasSize(keyValues.length / 2);

        for (int i = 0; i < keyValues.length; i+=2) {
            //noinspection unchecked
            K key = (K) keyValues[i];
            //noinspection unchecked
            W value = (W) keyValues[i+1];
            // check the map contains the key
            assertThat(map).named(name).containsKey(key);
            // check the map value for key, transformed with the function is equal to the value.
            assertThat(function.apply(map.get(key)))
                    .named(name + "[" + key + "]")
                    .isEqualTo(value);
        }
    }

    @Test
    @Category(DeviceTests.class)
    public void install() throws IOException {
        project.execute("assembleDebug");
        adb.exclusiveAccess();
        project.execute("installDebug", "uninstallAll");
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedCheck() {
        project.executeConnectedCheck();
    }
}
