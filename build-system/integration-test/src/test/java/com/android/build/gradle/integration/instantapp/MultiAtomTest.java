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
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.ANDROID;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.JAVA;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.MODULE;
import static com.android.testutils.truth.MoreTruth.assertThatZip;

import com.android.build.gradle.integration.common.category.SmokeTests;
import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction.ModelContainer;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.ApkSubject;
import com.android.build.gradle.integration.common.truth.AtomBundleSubject;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Property;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.testutils.truth.ZipFileSubject;
import com.android.utils.FileUtils;
import java.io.File;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/** Basic instantApp test with a multi-atom project. */
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
        // TODO: add support for feature-splits for AAPT2
        sProject.executor().withEnabledAapt2(false).run("clean", ":instantApp:assembleRelease");

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

        // Tests that the BuildConfig and R class are generated in the proper package.
        AtomBundleSubject baseAtomBundle =
                assertThat(sProject.getSubproject("base").getAtomBundle("release"));
        baseAtomBundle.containsClass("Lcom/android/tests/multiatom/BuildConfig;");
        baseAtomBundle.containsClass("Lcom/android/tests/multiatom/R;");
        baseAtomBundle.doesNotContainClass("Lcom/android/tests/multiatom/base/BuildConfig;");
        baseAtomBundle.doesNotContainClass("Lcom/android/tests/multiatom/base/R;");

        // Tests that the BuildConfig and R class are not packaged twice.
        AtomBundleSubject atomABundle =
                assertThat(sProject.getSubproject("atoma").getAtomBundle("release"));
        atomABundle.containsClass("Lcom/android/tests/multiatom/atoma/BuildConfig;");
        atomABundle.containsClass("Lcom/android/tests/multiatom/atoma/R;");
        atomABundle.doesNotContainClass("Lcom/android/tests/multiatom/BuildConfig;");
        atomABundle.doesNotContainClass("Lcom/android/tests/multiatom/R;");
        atomABundle.doesNotContainClass("Lcom/android/tests/multiatom/base/BuildConfig;");
        atomABundle.doesNotContainClass("Lcom/android/tests/multiatom/base/R;");

        AtomBundleSubject atomEBundle =
                assertThat(sProject.getSubproject("atome").getAtomBundle("release"));
        atomEBundle.containsClass("Lcom/android/tests/multiatom/atome/BuildConfig;");
        atomEBundle.containsClass("Lcom/android/tests/multiatom/atome/R;");
        atomEBundle.doesNotContainClass("Lcom/android/tests/multiatom/BuildConfig;");
        atomEBundle.doesNotContainClass("Lcom/android/tests/multiatom/R;");
        atomEBundle.doesNotContainClass("Lcom/android/tests/multiatom/base/BuildConfig;");
        atomEBundle.doesNotContainClass("Lcom/android/tests/multiatom/base/R;");
        atomEBundle.doesNotContainClass("Lcom/android/tests/multiatom/atomb/BuildConfig;");
        atomEBundle.doesNotContainClass("Lcom/android/tests/multiatom/atomb/R;");
        atomEBundle.doesNotContainClass("Lcom/android/tests/multiatom/atomc/BuildConfig;");
        atomEBundle.doesNotContainClass("Lcom/android/tests/multiatom/atomc/R;");

        // Check that atomC contains LibC classes.
        ApkSubject atomC =
                assertThatApk(sProject.getSubproject("instantApp").getAtom("atomc", "release"));
        atomC.containsClass("Lcom/android/tests/multiatom/libc/LibC;");
        atomC.containsClass("Lcom/android/tests/multiatom/libc/LibCActivity;");

        // Check that libC manifest is included in atomC.
        File atomCManifest =
                sProject.getSubproject("atomc")
                        .getIntermediateFile(
                                FileUtils.join(
                                        "atombundles",
                                        "release",
                                        "manifests",
                                        "AndroidManifest.xml"));
        assertThat(atomCManifest)
                .named("atomC manifest")
                .containsAllOf(
                        "        <activity android:name=\"com.android.tests.multiatom.libc.LibCActivity\" >",
                        "            <meta-data",
                        "                android:name=\"test\"",
                        "                android:value=\"42\" />",
                        "        </activity>");

        // Check that the output bundle file contains all the atoms.
        ZipFileSubject outputPackage =
                assertThatZip(sProject.getSubproject("instantApp").getInstantAppBundle("release"));
        outputPackage.contains("base.apk");
        outputPackage.contains("atoma.apk");
        outputPackage.contains("atomb.apk");
        outputPackage.contains("atomc.apk");
        outputPackage.contains("atomd.apk");
        outputPackage.contains("atome.apk");
        outputPackage.contains("atomf.apk");
    }

    @Ignore
    @Test
    public void testModelLevel1() throws Exception {
        ModelContainer<AndroidProject> modelContainer;
        modelContainer = sProject.model().level(AndroidProject.MODEL_LEVEL_1_SYNC_ISSUE).getMulti();

        AndroidProject instantAppModel = modelContainer.getModelMap().get(":instantApp");
        assertThat(instantAppModel).named("Instant app model").isNotNull();
        assertThat(instantAppModel.getProjectType())
                .named("Instant app project type")
                .isEqualTo(AndroidProject.PROJECT_TYPE_INSTANTAPP);

        Variant variant = ModelHelper.getVariant(instantAppModel.getVariants(), "release");
        Dependencies dependencies = variant.getMainArtifact().getDependencies();
        assertThat(dependencies.getJavaLibraries()).named("Javalibs dependencies").isEmpty();
        assertThat(dependencies.getLibraries()).named("Android dependencies").isEmpty();
        assertThat(dependencies.getAtoms()).named("Atoms dependencies").hasSize(7);
        assertThat(dependencies.getBaseAtom()).named("Base atom").isNotNull();
    }

    @Ignore
    @Test
    public void testModelFull() throws Exception {
        ModelContainer<AndroidProject> modelContainer = sProject.model().getMulti();
        Map<String, AndroidProject> models = modelContainer.getModelMap();

        LibraryGraphHelper helper = new LibraryGraphHelper(modelContainer);

        AndroidProject instantAppModel = models.get(":instantApp");
        assertThat(instantAppModel).named("InstantApp model").isNotNull();
        assertThat(instantAppModel.getProjectType())
                .named("InstantApp project type")
                .isEqualTo(AndroidProject.PROJECT_TYPE_INSTANTAPP);

        Variant variant = ModelHelper.getVariant(instantAppModel.getVariants(), "release");
        DependencyGraphs instantAppDeps = variant.getMainArtifact().getDependencyGraphs();
        assertThat(helper.on(instantAppDeps).withType(JAVA).asList())
                .named("InstantApp Javalibs dependencies")
                .isEmpty();
        assertThat(helper.on(instantAppDeps).withType(ANDROID).asList())
                .named("InstantApp Android dependencies")
                .isEmpty();
        assertThat(helper.on(instantAppDeps).withType(MODULE).asList())
                .named("InstantApp Atoms dependencies")
                .hasSize(3);

        AndroidProject atomCModel = models.get(":atomc");
        assertThat(atomCModel).named("AtomC model").isNotNull();

        Variant atomCVariant = ModelHelper.getVariant(atomCModel.getVariants(), "release");
        assertThat(atomCVariant).named("AtomC release variant").isNotNull();
        DependencyGraphs atomCDeps = atomCVariant.getMainArtifact().getDependencyGraphs();
        assertThat(helper.on(atomCDeps).withType(JAVA).asList())
                .named("atomC javalibs dependencies")
                .isEmpty();

        assertThat(helper.on(atomCDeps).withType(ANDROID).asList())
                .named("AtomC lib dependencies")
                .isEmpty();

        assertThat(helper.on(atomCDeps).withType(MODULE).mapTo(Property.GRADLE_PATH))
                .named("AtomC module dependencies")
                .containsAllOf(":libc", ":base");
    }
}
