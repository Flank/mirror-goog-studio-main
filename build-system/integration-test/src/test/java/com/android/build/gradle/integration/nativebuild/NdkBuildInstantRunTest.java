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

package com.android.build.gradle.integration.nativebuild;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;

import com.android.build.gradle.integration.common.fixture.BuildScriptGenerator;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.TemporaryProjectModification;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.common.utils.ZipHelper;
import com.android.build.gradle.integration.instant.InstantRunTestUtils;
import com.android.build.gradle.internal.incremental.ColdswapMode;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.InstantRun;
import com.android.testutils.incremental.FileRecord;
import com.android.tools.fd.client.InstantRunBuildInfo;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Test to ensure ndk-build project works with instant run.
 */
public class NdkBuildInstantRunTest {

    @ClassRule
    public static GradleTestProject sProject = GradleTestProject.builder()
            .fromTestApp(HelloWorldJniApp.builder().build())
            .addFile(HelloWorldJniApp.androidMkC("src/main/jni"))
            .create();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @BeforeClass
    public static void setUp() throws IOException {
        TestFileUtils.appendToFile(
                sProject.getBuildFile(),
                new BuildScriptGenerator(
                        "apply plugin: '${application_plugin}'\n"
                                + "${model_start}"
                                + "    android {\n"
                                + "        compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n"
                                + "        buildToolsVersion \"" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "\"\n"
                                + "        externalNativeBuild {\n"
                                + "            ndkBuild {\n"
                                + "               path 'src/main/jni/Android.mk'\n"
                                + "            }\n"
                                + "        }\n"
                                + "    }\n"
                                + "${model_end}\n").build(false));
    }

    @AfterClass
    public static void tearDown() {
        sProject = null;
    }

    @Test
    public void checkHotSwapBuild() throws Exception {
        sProject.executor()
                .withInstantRun(23, ColdswapMode.DEFAULT)
                .withProperty(AndroidProject.PROPERTY_BUILD_ABI, "x86")
                .run("clean", "assembleDebug");

        AndroidProject model = sProject.model().getSingle().getOnlyModel();
        InstantRun instantRunModel = InstantRunTestUtils.getInstantRunModel(model);
        File apk = InstantRunTestUtils.getOnlyArtifact(instantRunModel).file;
        assertThat(apk).exists();
        assertThatApk(apk).contains("lib/x86/libhello-jni.so");

        TemporaryProjectModification.doTest(
                sProject,
                modifiedProject -> {
                    modifiedProject.replaceInFile(
                            "src/main/java/com/example/hellojni/HelloJni.java",
                            "tv.setText\\(.*\\)",
                            "tv.setText(\"Hello from Java\")");

                    sProject.executor()
                            .withInstantRun(23, ColdswapMode.DEFAULT)
                            .withProperty(AndroidProject.PROPERTY_BUILD_ABI, "x86")
                            .run("assembleDebug");
                    InstantRunBuildInfo context = InstantRunTestUtils.loadContext(instantRunModel);
                    assertThat(context.getVerifierStatus()).isEqualTo(
                            InstantRunVerifierStatus.COMPATIBLE.toString());
                });
    }

    @Test
    public void checkFullBuildIsTriggered() throws Exception {
        sProject.executor()
                .withInstantRun(23, ColdswapMode.DEFAULT)
                .withProperty(AndroidProject.PROPERTY_BUILD_ABI, "x86")
                .run("clean", "assembleDebug");

        AndroidProject model = sProject.model().getSingle().getOnlyModel();
        InstantRun instantRunModel = InstantRunTestUtils.getInstantRunModel(model);
        File apk = InstantRunTestUtils.getOnlyArtifact(instantRunModel).file;
        assertThat(apk).exists();
        assertThatApk(apk).contains("lib/x86/libhello-jni.so");
        File outputLib = temporaryFolder.newFile();
        ZipHelper.extractFile(apk, "lib/x86/libhello-jni.so", outputLib);

        FileRecord lib = FileRecord.of(outputLib);

        File src = sProject.file("src/main/jni/hello-jni.c");
        Files.append("\nvoid foo() {}\n", src, Charsets.UTF_8);

        sProject.executor()
                .withInstantRun(23, ColdswapMode.DEFAULT)
                .withProperty(AndroidProject.PROPERTY_BUILD_ABI, "x86")
                .run("assembleDebug");
        InstantRunBuildInfo context = InstantRunTestUtils.loadContext(instantRunModel);
        assertThat(context.getVerifierStatus()).isEqualTo(
                InstantRunVerifierStatus.JAVA_RESOURCES_CHANGED.toString());
        assertThat(context.getArtifacts()).hasSize(1);

        ZipHelper.extractFile(apk, "lib/x86/libhello-jni.so", outputLib);
        assertThat(lib).hasChanged();
    }
}
