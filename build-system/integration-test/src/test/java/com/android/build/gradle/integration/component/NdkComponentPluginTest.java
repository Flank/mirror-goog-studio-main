package com.android.build.gradle.integration.component;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Basic integration test for ndk component plugin. */
public class NdkComponentPluginTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(new HelloWorldJniApp())
                    .useExperimentalGradleVersion(true)
                    .create();

    @Before
    public void setUp() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "import com.android.build.gradle.model.NdkComponentModelPlugin\n"
                        + "apply plugin: NdkComponentModelPlugin\n"
                        + "\n"
                        + "model {\n"
                        + "    android {\n"
                        + "        compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "        ndk {\n"
                        + "            moduleName \"hello-jni\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
    }

    @Test
    public void assemble() throws IOException, InterruptedException {
        project.execute("assemble");
        assertThat(project.file("build/intermediates/binaries/debug/obj/x86/libhello-jni.so"))
                .exists();
        assertThat(project.file("build/intermediates/binaries/debug/lib/x86/libhello-jni.so"))
                .exists();
        assertThat(project.file("build/intermediates/binaries/release/lib/x86/libhello-jni.so"))
                .exists();
    }
}
