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

package com.android.build.gradle.integration.instant;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.Logcat;
import com.android.build.gradle.integration.common.fixture.Packaging;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.truth.AbstractAndroidSubject;
import com.android.build.gradle.integration.common.truth.ApkSubject;
import com.android.build.gradle.integration.common.truth.DexFileSubject;
import com.android.build.gradle.integration.common.utils.AndroidVersionMatcher;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.incremental.ColdswapMode;
import com.android.builder.model.InstantRun;
import com.android.ddmlib.IDevice;
import com.android.tools.fd.client.InstantRunArtifact;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.truth.Expect;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Smoke test for hot swap builds.
 */
@RunWith(FilterableParameterized.class)
public class HotSwapTest {

    private static final ColdswapMode COLDSWAP_MODE = ColdswapMode.MULTIDEX;
    private static final String LOG_TAG = "hotswapTest";
    private static final String ORIGINAL_MESSAGE = "Original";
    private static final int CHANGES_COUNT = 3;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Packaging.getParameters();
    }

    @Parameterized.Parameter
    public Packaging packaging;

    @Rule
    public final Adb adb = new Adb();

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Rule
    public Logcat logcat = Logcat.create();

    @Rule
    public Expect expect = Expect.createAndEnableStackTrace();

    @Before
    public void activityClass() throws IOException {
        Assume.assumeFalse("Disabled until instant run supports Jack", GradleTestProject.USE_JACK);
        createActivityClass(ORIGINAL_MESSAGE);
    }

    @Test
    public void buildIncrementallyWithInstantRun() throws Exception {
        InstantRun instantRunModel =
                InstantRunTestUtils.getInstantRunModel(project.model().getSingle());

        InstantRunTestUtils.doInitialBuild(project, packaging, 19, COLDSWAP_MODE);

        // As no injected API level, will default to no splits.
        ApkSubject apkFile = expect.about(ApkSubject.FACTORY)
                .that(project.getApk("debug"));
        apkFile.hasClass("Lcom/example/helloworld/HelloWorld;",
                AbstractAndroidSubject.ClassFileScope.MAIN)
                .that().hasMethod("onCreate");
        apkFile.hasClass("Lcom/android/tools/fd/runtime/BootstrapApplication;",
                AbstractAndroidSubject.ClassFileScope.MAIN);

        createActivityClass("CHANGE");

        project.executor()
                .withInstantRun(19, COLDSWAP_MODE)
                .withPackaging(packaging)
                .run("assembleDebug");

        InstantRunArtifact artifact =
                InstantRunTestUtils.getReloadDexArtifact(instantRunModel);

        expect.about(DexFileSubject.FACTORY)
                .that(artifact.file)
                .hasClass("Lcom/example/helloworld/HelloWorld$1$override;")
                .that().hasMethod("call");
    }

    @Test
    public void testModel() throws Exception {
        InstantRun instantRunModel = InstantRunTestUtils.getInstantRunModel(
                project.model().getSingle());

        assertTrue(instantRunModel.isSupportedByArtifact());

        TestFileUtils.appendToFile(
                project.getBuildFile(), "\nandroid.buildTypes.debug.useJack = true");

        instantRunModel = InstantRunTestUtils.getInstantRunModel(
                project.model().getSingle());

        assertFalse(instantRunModel.isSupportedByArtifact());
    }

    @Test
    @Category(DeviceTests.class)
    public void artHotSwapChangeTest() throws Exception {
        doHotSwapChangeTest(adb.getDevice(AndroidVersionMatcher.thatUsesArt()));
    }

    @Test
    @Category(DeviceTests.class)
    public void dalvikHotSwapChangeTest() throws Exception {
        doHotSwapChangeTest(adb.getDevice(AndroidVersionMatcher.thatUsesDalvik()));
    }

    private void doHotSwapChangeTest(@NonNull IDevice device) throws Exception {
        HotSwapTester tester =
                new HotSwapTester(
                        project,
                        packaging,
                        "com.example.helloworld",
                        "HelloWorld",
                        LOG_TAG,
                        device,
                        logcat);

        List<HotSwapTester.Change> changes = new ArrayList<>();

        for (int i = 0; i < CHANGES_COUNT; i++) {
            changes.add(new HotSwapTester.LogcatChange(i, ORIGINAL_MESSAGE) {
                @Override
                public void makeChange() throws Exception {
                    createActivityClass(CHANGE_PREFIX + changeId);
                }
            });
        }

        tester.run(
                () -> assertThat(logcat).containsMessageWithText("Original"),
                changes);
    }

    private void createActivityClass(String message)
            throws IOException {
        String javaCompile = "package com.example.helloworld;\n"
                + "import android.app.Activity;\n"
                + "import android.os.Bundle;\n"
                + "import java.util.logging.Logger;\n"
                + "\n"
                + "import java.util.concurrent.Callable;\n"
                + "\n"
                + "public class HelloWorld extends Activity {\n"
                + "    /** Called when the activity is first created. */\n"
                + "    @Override\n"
                + "    public void onCreate(Bundle savedInstanceState) {\n"
                + "        super.onCreate(savedInstanceState);\n"
                + "        setContentView(R.layout.main);\n"
                + "        Callable<Void> callable = new Callable<Void>() {\n"
                + "            @Override\n"
                + "            public Void call() throws Exception {\n"
                + "                Logger.getLogger(\"" + LOG_TAG + "\")\n"
                + "                        .warning(\"" + message + "\");"
                + "                return null;\n"
                + "            }\n"
                + "        };\n"
                + "        try {\n"
                + "            callable.call();\n"
                + "        } catch (Exception e) {\n"
                + "            throw new RuntimeException(e);\n"
                + "        }\n"
                + "    }\n"
                + "}\n";
        Files.write(javaCompile,
                project.file("src/main/java/com/example/helloworld/HelloWorld.java"),
                Charsets.UTF_8);
    }

}
