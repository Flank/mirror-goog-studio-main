package com.android.build.gradle.integration.component;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.category.SmokeTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/** Unit tests for component plugin. */
@Category(SmokeTests.class)
public class UnitTestingComponentTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.noBuildFile())
                    .useExperimentalGradleVersion(true)
                    .withoutNdk()
                    .create();

    @Before
    public void setUp() throws IOException {
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
                        + "        buildToolsVersion \""
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "\n"
                        + "        testOptions.unitTests.returnDefaultValues = true\n"
                        + "    }\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    testCompile 'junit:junit:4.12'\n"
                        + "}\n");

        Path unitTest =
                project.getTestDir()
                        .toPath()
                        .resolve("src/test/java/com/android/tests/UnitTest.java");
        Files.createDirectories(unitTest.getParent());
        String unitTestContent =
                "\n"
                        + "package com.android.tests;\n"
                        + "\n"
                        + "import static org.junit.Assert.*;\n"
                        + "\n"
                        + "import android.util.ArrayMap;\n"
                        + "import android.os.Debug;\n"
                        + "import org.junit.Test;\n"
                        + "\n"
                        + "public class UnitTest {\n"
                        + "  @Test\n"
                        + "  public void defaultValues() {\n"
                        + "    ArrayMap map = new ArrayMap();\n"
                        + "    // Check different return types.\n"
                        + "    map.clear();\n"
                        + "    assertEquals(0, map.size());\n"
                        + "    assertEquals(false, map.isEmpty());\n"
                        + "    assertNull(map.keySet());\n"
                        + "    // Check a static method as well.\n"
                        + "    assertEquals(0, Debug.getGlobalAllocCount());\n"
                        + "  }\n"
                        + "}\n";
        Files.write(unitTest, unitTestContent.getBytes());
    }

    @Test
    public void testDebug() throws IOException, InterruptedException {
        project.execute("clean", "testDebug");
        assertThat(
                        project.file(
                                "build/test-results/testDebugUnitTest/TEST-com.android.tests.UnitTest.xml"))
                .exists();
    }
}
