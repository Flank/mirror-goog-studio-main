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

package com.android.build.gradle.integration.dependencies;

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.DEFAULT_BUILD_TOOL_VERSION;
import static com.android.build.gradle.integration.common.fixture.GradleTestProject.DEFAULT_COMPILE_SDK_VERSION;
import static com.android.build.gradle.integration.common.fixture.GradleTestProject.SUPPORT_LIB_VERSION;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatZip;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.ANDROID;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction;
import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction.ModelContainer;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.core.ApkInfoParser;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.MavenCoordinates;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.GraphItem;
import com.android.builder.model.level2.LibraryGraph;
import com.android.ide.common.process.DefaultProcessExecutor;
import com.android.ide.common.process.ProcessExecutor;
import com.android.repository.Revision;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.utils.StdLogger;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

public class VariantDependencyTest {
    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.noBuildFile())
            .create();

    private static ModelContainer<AndroidProject> model;
    private static ApkInfoParser apkInfoParser;
    private static LibraryGraphHelper helper;

    @BeforeClass
    public static void setUp() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: \"com.android.application\"\n"
                        + "\n"
                        + "configurations {\n"
                        + "    freeLollipopDebugCompile\n"
                        + "    paidIcsCompile\n"
                        + "}\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion " + DEFAULT_COMPILE_SDK_VERSION + "\n"
                        + "    buildToolsVersion \"" + DEFAULT_BUILD_TOOL_VERSION + "\"\n"
                        + "\n"
                        + "    flavorDimensions \"model\", \"api\"\n"
                        + "    productFlavors {\n"
                        + "        Lollipop {\n"
                        + "            dimension \"api\"\n"
                        + "            minSdkVersion 21\n"
                        + "        }\n"
                        + "        ics {\n"
                        + "            dimension \"api\"\n"
                        + "            minSdkVersion 15\n"
                        + "        }\n"
                        + "        free {\n"
                        + "            dimension \"model\"\n"
                        + "        }\n"
                        + "        paid {\n"
                        + "            dimension \"model\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    freeLollipopDebugCompile \"com.android.support:leanback-v17:"
                        + SUPPORT_LIB_VERSION
                        + "\"\n"
                        + "    paidIcsCompile \"com.android.support:appcompat-v7:"
                        + SUPPORT_LIB_VERSION
                        + "\"\n"
                        + "}\n");

        project.execute("clean", "assemble");
        model = project.model().getSingle();
        helper = new LibraryGraphHelper(model);

        FakeProgressIndicator progress = new FakeProgressIndicator();
        BuildToolInfo buildToolInfo =
                AndroidSdkHandler.getInstance(project.getSdkDir()).getBuildToolInfo(
                        Revision.parseRevision(DEFAULT_BUILD_TOOL_VERSION), progress);

        File aapt = null;
        if (buildToolInfo != null) {
             aapt = new File(buildToolInfo.getPath(BuildToolInfo.PathId.AAPT));
        }
        assertTrue("Test requires build-tools " + DEFAULT_BUILD_TOOL_VERSION,
                aapt != null && aapt.isFile());
        ProcessExecutor processExecutor = new DefaultProcessExecutor(
                new StdLogger(StdLogger.Level.ERROR));
        apkInfoParser = new ApkInfoParser(aapt, processExecutor);
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        model = null;
        helper = null;
        apkInfoParser = null;
    }

    @Test
    public void buildVariantSpecificDependency() throws IOException {
        // check that the dependency was added by looking for a res file coming from the
        // dependency.
        checkApkForContent("freeLollipopDebug", "res/drawable/lb_background.xml");
    }

    @Test
    public void buildMultiFlavorDependency() throws IOException {
        // check that the dependency was added by looking for a res file coming from the
        // dependency.
        checkApkForContent("paidIcsDebug", "res/anim/abc_fade_in.xml");
        checkApkForContent("paidIcsRelease", "res/anim/abc_fade_in.xml");
    }

    @Test
    public void buildDefaultDependency() throws IOException {
        // make sure that the other variants do not include any file from the variant-specific
        // and multi-flavor dependencies.
        Set<String> paths = Sets.newHashSet(
                "res/anim/abc_fade_in.xml",
                "res/drawable/lb_background.xml");

        checkApkForMissingContent("paidLollipopDebug", paths);
        checkApkForMissingContent("paidLollipopRelease", paths);
        checkApkForMissingContent("freeLollipopRelease", paths);
        checkApkForMissingContent("freeIcsDebug", paths);
        checkApkForMissingContent("freeIcsRelease", paths);
    }

    @Test
    public void modelVariantCount() {
        Collection<Variant> variants = model.getOnlyModel().getVariants();
        assertThat(variants).named("variants").hasSize(8);
    }

    @Test
    public void modelVariantSpecificDependency() {
        Collection<Variant> variants = model.getOnlyModel().getVariants();
        String variantName = "freeLollipopDebug";
        checkVariant(
                variants,
                variantName,
                "com.android.support:leanback-v17:aar:" + SUPPORT_LIB_VERSION);
    }

    @Test
    public void modelMultiFlavorDependency() {
        Collection<Variant> variants = model.getOnlyModel().getVariants();

        checkVariant(
                variants,
                "paidIcsDebug",
                "com.android.support:appcompat-v7:aar:" + SUPPORT_LIB_VERSION);
        checkVariant(
                variants,
                "paidIcsRelease",
                "com.android.support:appcompat-v7:aar:" + SUPPORT_LIB_VERSION);
    }

    @Test
    public void modelDefaultDependency() {
        Collection<Variant> variants = model.getOnlyModel().getVariants();

        checkVariant(variants, "paidLollipopDebug", null);
        checkVariant(variants, "paidLollipopRelease", null);
        checkVariant(variants, "freeLollipopRelease", null);
        checkVariant(variants, "freeIcsDebug", null);
        checkVariant(variants, "freeIcsRelease", null);
    }

    private static void checkVariant(
            @NonNull Collection<Variant> variants,
            @NonNull String variantName,
            @Nullable String dependencyName) {
        Variant variant = ModelHelper.findVariantByName(variants, variantName);
        assertThat(variant).named(variantName).isNotNull();

        AndroidArtifact artifact = variant.getMainArtifact();
        assertThat(artifact)
                .named("main artifact for " + variantName)
                .isNotNull();

        LibraryGraph graph = artifact.getCompileGraph();
        assertThat(graph)
                .named("dependencies for main artifact of " + variantName)
                .isNotNull();

        List<GraphItem> androidDependencyItems = helper.on(graph).withType(ANDROID).asList();
        if (dependencyName != null) {
            assertThat(androidDependencyItems)
                    .named("aar deps for " + variantName)
                    .isNotEmpty();

            GraphItem library = Iterables.getFirst(androidDependencyItems, null);
            assertThat(library).named("first graph item for " + variantName).isNotNull();
            assertThat(library.getArtifactAddress()).isEqualTo(dependencyName);
        } else {
            assertThat(androidDependencyItems).named("android deps for " + variantName).isEmpty();
        }
    }

    private static void checkApkForContent(
            @NonNull String variantName,
            @NonNull String checkFilePath) throws IOException {
        // use the model to get the output APK!
        File apk = ModelHelper.findOutputFileByVariantName(
                model.getOnlyModel().getVariants(), variantName);
        assertThat(apk).isFile();
        assertThatZip(apk).contains(checkFilePath);
    }

    private static void checkApkForMissingContent(
            @NonNull String variantName,
            @NonNull Set<String> checkFilePath) throws IOException {
        // use the model to get the output APK!
        File apk = ModelHelper.findOutputFileByVariantName(
                model.getOnlyModel().getVariants(), variantName);
        assertThat(apk).isFile();
        assertThatZip(apk).entries(".*").containsNoneIn(checkFilePath);
    }
}
