package com.android.build.gradle.integration.component;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/** Test various options can be set without necessarily using it. */
public class ComponentDslTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(new HelloWorldJniApp())
                    .useExperimentalGradleVersion(true)
                    .create();

    @Before
    public void setUp() throws IOException {
        project.file("proguard.txt").createNewFile();
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: \"com.android.model.application\"\n"
                        + "\n"
                        + "model {\n"
                        + "    android {\n"
                        + "        compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "        defaultConfig {\n"
                        + "            minSdkVersion.apiLevel "
                        + GradleTestProject.SUPPORT_LIB_MIN_SDK
                        + "\n"
                        + "        }\n"
                        + "        ndk {\n"
                        + "            moduleName \"hello-jni\"\n"
                        + "        }\n"
                        + "        buildTypes {\n"
                        + "            release {\n"
                        + "                minifyEnabled false\n"
                        + "                proguardFiles.add(file(\"proguard-rules.pro\"))\n"
                        + "                externalNativeBuild {\n"
                        + "                    ndkBuild {\n"
                        + "                        cFlags.addAll(\"-DCOLOR=RED\")\n"
                        + "                        abiFilters.addAll(\"x86\", \"x86_64\")\n"
                        + "                    }\n"
                        + "                }\n"
                        + "            }\n"
                        + "        }\n"
                        + "        productFlavors {\n"
                        + "            create(\"f1\") {\n"
                        + "                dimension \"foo\"\n"
                        + "                proguardFiles.add(file(\"proguard.txt\"))\n"
                        + "                buildConfigFields.create {\n"
                        + "                    type \"String\"\n"
                        + "                    name \"foo\"\n"
                        + "                    value \"\\\"bar\\\"\"\n"
                        + "                }\n"
                        + "            }\n"
                        + "            create(\"f2\") {\n"
                        + "                dimension \"foo\"\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compile \'com.android.support:appcompat-v7:"
                        + GradleTestProject.SUPPORT_LIB_VERSION
                        + "\'\n"
                        + "}\n");
    }

    @Test
    public void assemble() throws IOException, InterruptedException {
        AndroidProject model = project.executeAndReturnModel("assemble").getOnlyModel();
        assertThat(model).isNotNull();
        assertThat(model.getName()).isEqualTo(project.getName());
        assertThat(model.getBuildTypes()).hasSize(2);
        assertThat(model.getProductFlavors()).hasSize(2);
        assertThat(model.getVariants()).hasSize(4);
        assertThat(project.getApk("f1", "debug")).exists();
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedAndroidTest() throws IOException, InterruptedException {
        project.executeConnectedCheck();
    }
}
