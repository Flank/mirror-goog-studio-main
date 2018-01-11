package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ProjectBuildOutputUtils;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.VariantBuildOutput;
import com.android.testutils.apk.Apk;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** MultiAPK test where densities are obtained automatically. */
public class AutoDensitySplitTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("densitySplit").create();

    private static ProjectBuildOutput outputModel;

    @BeforeClass
    public static void setUp() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "  splits {\n"
                        + "    density {\n"
                        + "      enable true\n"
                        + "      auto true\n"
                        + "      compatibleScreens 'small', 'normal', 'large', 'xlarge'\n"
                        + "    }\n"
                        + "  }\n"
                        + "}\n");
        outputModel =
                project.executeAndReturnModel(ProjectBuildOutput.class, "clean", "assembleDebug");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        outputModel = null;
    }

    @Test
    public void testPackaging() throws IOException {
        VariantBuildOutput debugVariantOutput =
                ProjectBuildOutputUtils.getDebugVariantBuildOutput(outputModel);

        assertThat(debugVariantOutput.getOutputs()).hasSize(5);

        Apk mdpiApk = project.getApk("mdpi", GradleTestProject.ApkType.DEBUG);
        assertThat(mdpiApk).contains("res/drawable-mdpi-v4/other.png");
    }

    @Test
    public void checkVersionCodeInApk() throws IOException {
        Apk universalApk = project.getApk("universal", GradleTestProject.ApkType.DEBUG);
        assertThat(universalApk).hasVersionCode(112);
        assertThat(universalApk).hasVersionName("version 112");

        Apk mdpiApk = project.getApk("mdpi", GradleTestProject.ApkType.DEBUG);
        assertThat(mdpiApk).hasVersionCode(212);
        assertThat(mdpiApk).hasVersionName("version 212");

        Apk hdpiApk = project.getApk("hdpi", GradleTestProject.ApkType.DEBUG);
        assertThat(hdpiApk).hasVersionCode(312);
        assertThat(hdpiApk).hasVersionName("version 312");

        Apk xhdpiApk = project.getApk("xhdpi", GradleTestProject.ApkType.DEBUG);
        assertThat(xhdpiApk).hasVersionCode(412);
        assertThat(xhdpiApk).hasVersionName("version 412");

        Apk xxhdiApk = project.getApk("xxhdpi", GradleTestProject.ApkType.DEBUG);
        assertThat(xxhdiApk).hasVersionCode(512);
        assertThat(xxhdiApk).hasVersionName("version 512");
    }
}
