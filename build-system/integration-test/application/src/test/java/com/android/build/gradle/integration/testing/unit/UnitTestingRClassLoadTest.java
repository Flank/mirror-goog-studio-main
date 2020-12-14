/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.integration.testing.unit;

import static com.android.build.gradle.integration.common.truth.ScannerSubject.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject;
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.VariantUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.JavaArtifact;
import com.android.builder.model.Variant;
import com.android.testutils.apk.Zip;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verify that the unit test runtime R class has the transitive resources.
 *
 * <p>Regression test for https://issuetracker.google.com/143762955
 */
public class UnitTestingRClassLoadTest {
    private MinimalSubProject a =
            MinimalSubProject.Companion.lib("com.example.a")
                    .withFile(
                            "src/main/res/values/strings.xml",
                            "<resources><string name=\"my_string\">my string</string></resources>");

    private MinimalSubProject b = MinimalSubProject.Companion.lib("com.example.b");

    private MinimalSubProject c =
            MinimalSubProject.Companion.lib("com.example.c")
                    .appendToBuild(
                            "android.testOptions.unitTests.includeAndroidResources false\n"
                                    + "dependencies {\n"
                                    + "    testImplementation 'junit:junit:4.12'\n"
                                    + "}\n")
                    .withFile(
                            "src/test/java/com/example/c/test/MyTest.java",
                            "package com.example.c.test;\n"
                                    + "\n"
                                    + "import static org.junit.Assert.assertNotEquals;\n"
                                    + "\n"
                                    + "// Use the R class from the direct dependency that references"
                                    + " a// resource in the indirect dependency.\n"
                                    + "import com.example.b.R;\n"
                                    + "import org.junit.Test;\n"
                                    + "\n"
                                    + "public class MyTest {\n"
                                    + "    @Test\n"
                                    + "    public void readRClass() {\n"
                                    + "        assertNotEquals(0, R.string.my_string);\n"
                                    + "    }\n"
                                    + "}\n");

    private MultiModuleTestProject build =
            MultiModuleTestProject.builder()
                    .subproject(":a", a)
                    .subproject(":b", b)
                    .subproject(":c", c)
                    .dependency(c, b)
                    .dependency(b, a)
                    .build();

    @Rule
    public GradleTestProject project = GradleTestProject.builder().fromTestApp(build).create();

    /**
     * Check that the test passes when run (regression test for
     * https://issuetracker.google.com/143762955)
     */
    @Test
    public void checkLoadRClassInTest() throws Exception {
        project.executor().run(":c:test");
        File xmlResults =
                project.file(
                        "c/build/test-results/testDebugUnitTest/"
                                + "TEST-com.example.c.test.MyTest.xml");
        assertThat(xmlResults).isFile();
    }

    /**
     * Check that the R class in the model used when running unit tests is present and contains the
     * expected classes.
     */
    @Test
    public void checkRClassInModel() throws Exception {
        AndroidProject model = project.model().fetchAndroidProjects().getOnlyModelMap().get(":c");
        Variant debug = AndroidProjectUtils.getVariantByName(model, "debug");
        JavaArtifact debugUnitTest = VariantUtils.getUnitTestArtifact(debug);
        // Check that the IDE commands build that jar.
        ImmutableList.Builder<String> commands = ImmutableList.builder();
        commands.add(":c:" + debug.getMainArtifact().getSourceGenTaskName());
        for (String taskName : debugUnitTest.getIdeSetupTaskNames()) {
            commands.add(":c:" + taskName);
        }
        commands.add(debugUnitTest.getCompileTaskName());
        project.executor().run(commands.build());
        // Check that the R jar has the expected classes
        assertThat(debugUnitTest.getAdditionalClassesFolders()).hasSize(1);
        File rJar = Iterables.getOnlyElement(debugUnitTest.getAdditionalClassesFolders());
        try (Zip zip = new Zip(rJar)) {
            Set<String> entryNames =
                    zip.getEntries().stream().map(Path::toString).collect(Collectors.toSet());
            assertThat(entryNames)
                    .named("Zip entries of " + rJar)
                    .containsExactly(
                            "/com/example/a/R.class",
                            "/com/example/a/R$string.class",
                            "/com/example/b/R.class",
                            "/com/example/b/R$string.class",
                            "/com/example/c/R.class",
                            "/com/example/c/R$string.class",
                            "/com/example/c/test/R.class",
                            "/com/example/c/test/R$string.class");
        }
        // And a field has the expected modifiers.
        try (URLClassLoader urlClassLoader = new URLClassLoader(new URL[] {rJar.toURI().toURL()})) {
            Class<?> aClass = urlClassLoader.loadClass("com.example.a.R$string");
            Field myStringField = aClass.getField("my_string");
            assertThat(Modifier.toString(myStringField.getModifiers())).isEqualTo("public static");
            assertThat(myStringField.getInt(null)).isNotEqualTo(0);
        }
    }
}
