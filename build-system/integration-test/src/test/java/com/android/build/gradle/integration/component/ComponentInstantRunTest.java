package com.android.build.gradle.integration.component;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.instant.InstantRunTestUtils;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.InstantRun;
import com.android.builder.model.OptionalCompilationStep;
import com.android.sdklib.AndroidVersion;
import com.android.testutils.apk.Apk;
import com.android.tools.ir.client.InstantRunBuildInfo;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

/**
 * Simple test to ensure component model plugin do not crash when instant run is enabled.
 */
public class ComponentInstantRunTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldJniApp.builder().build())
            .useExperimentalGradleVersion(true)
            .create();

    @Before
    public void setUp() throws Exception {
        TestFileUtils.appendToFile(project.getBuildFile(),
                "apply plugin: \"com.android.model.application\"\n"
                        + "model {\n"
                        + "    android {\n"
                        + "        compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n"
                        + "        buildToolsVersion \"" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "\"\n"
                        + "    }\n"
                        + "}\n");
    }

    @Test
    public void basicAssemble() throws Exception {
        project.executor().withInstantRun(new AndroidVersion(21, null)).run("assembleDebug");
        assertThat(project.getApk("debug")).exists();
    }

    @Ignore("Temporarily disabled until instant run works for component model plugin")
    @Test
    public void withNativeCode() throws Exception {
        Files.append(
                "model {\n"
                        + "    android.ndk {\n"
                        + "        moduleName \"hello-jni\"\n"
                        + "    }\n"
                        + "}\n",
                project.getBuildFile(),
                Charsets.UTF_8);

        project.executor()
                .withInstantRun(new AndroidVersion(21, null), OptionalCompilationStep.RESTART_ONLY)
                .run("assembleDebug");
        AndroidProject model = project.model().getSingle().getOnlyModel();
        Apk apk = project.getApk("debug");
        assertThat(apk).exists();
        assertThat(apk).contains("lib/x86/libhello-jni.so");

        File src = project.file("src/main/jni/hello-jni.c");
        Files.append("\nvoid foo() {}\n", src, Charsets.UTF_8);

        InstantRun instantRunModel = InstantRunTestUtils.getInstantRunModel(model);
        project.executor().withInstantRun(new AndroidVersion(21, null)).run("assembleDebug");
        InstantRunBuildInfo context = InstantRunTestUtils.loadContext(instantRunModel);
        assertThat(context.getVerifierStatus()).isEqualTo(
                InstantRunVerifierStatus.JAVA_RESOURCES_CHANGED.toString());
        assertThat(context.getArtifacts()).hasSize(0);
    }

}
