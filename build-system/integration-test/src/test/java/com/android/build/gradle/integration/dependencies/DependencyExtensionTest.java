/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.utils.Pair;
import com.google.common.base.Charsets;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.io.Files;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Validates that we have the proper connections between Configuration (using extendsFrom)
 *
 * <p>This checks by injecting a task in the project to print out all the extensions and then check
 * the result (or a subset of it)
 */
public class DependencyExtensionTest {

    public static final String CONFIG_EXTENDS_FROM = "CONFIG-EXTENDS-FROM: ";
    public static final String SEPARATOR = " -> ";

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("projectWithModules").create();

    // basic relationship
    private static final ListMultimap<String, String> appBasics =
            mapOf(
                    "implementation", "api",
                    "api", "compile",
                    "runtimeOnly", "apk",
                    "compileOnly", "provided");
    private static final ListMultimap<String, String> libBasics =
            mapOf(
                    "implementation", "api",
                    "api", "compile",
                    "runtimeOnly", "publish",
                    "compileOnly", "provided");

    // variant to basic relationship
    private static final ListMultimap<String, String> compileToRaw =
            mapWithSingleKey(
                    "lollipopDemoDebugCompileClasspath",
                    "api",
                    "compile",
                    "implementation",
                    "compileOnly",
                    "lollipopImplementation",
                    "debugImplementation",
                    "demoImplementation",
                    "lollipopDemoImplementation",
                    "lollipopDemoDebugImplementation");
    private static final ListMultimap<String, String> runtimeToRaw =
            mapWithSingleKey(
                    "lollipopDemoDebugRuntimeClasspath",
                    "api",
                    "compile",
                    "implementation",
                    "runtimeOnly",
                    "lollipopImplementation",
                    "debugImplementation",
                    "demoImplementation",
                    "lollipopDemoImplementation",
                    "lollipopDemoDebugImplementation");

    // forbidden relationship
    private static final ListMultimap<String, String> forbiddenVariantToRaw =
            mapOf(
                    "lollipopDemoDebugCompileClasspath", "runtimeOnly",
                    "lollipopDemoDebugCompileClasspath", "apk",
                    "lollipopDemoDebugCompileClasspath", "publish",
                    "lollipopDemoDebugRuntimeClasspath", "provided",
                    "lollipopDemoDebugRuntimeClasspath", "compileOnly");

    // test to prod relationship
    private static final ListMultimap<String, String> testToProd =
            mapOf(
                    // basic raw configs
                    "testImplementation", "implementation",
                    "testRuntimeOnly", "runtimeOnly",
                    // flavor and build type configs
                    "testDemoImplementation", "demoImplementation",
                    "testDebugImplementation", "debugImplementation",
                    "androidTestDemoImplementation", "demoImplementation",
                    // multi flavors and variant configs
                    "testLollipopDemoImplementation", "lollipopDemoImplementation",
                    "androidTestLollipopDemoImplementation", "lollipopDemoImplementation",
                    "testLollipopDemoDebugImplementation", "lollipopDemoDebugImplementation",
                    "androidTestLollipopDemoDebugImplementation",
                            "lollipopDemoDebugImplementation");

    // forbidden relationship
    private static final ListMultimap<String, String> forbiddenTestToProd =
            mapOf(
                    "testCompileOnly", "compileOnly",
                    "testRuntimeOnly", "provided");

    @BeforeClass
    public static void setUp() throws IOException {
        Files.write("include 'app', 'library'", project.getSettingsFile(), Charsets.UTF_8);

        appendToFile(project.getSubproject("app").getBuildFile(), getBuildFileContent());
        appendToFile(project.getSubproject("library").getBuildFile(), getBuildFileContent());
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkAppConfigExtension() throws IOException, InterruptedException {
        GradleBuildResult result = project.executor().run("app:printConfig");
        ListMultimap<String, String> actual = getAllExtendsFrom(result.getStdoutAsLines());

        checkValidExtensions(appBasics, actual);
        checkValidExtensions(compileToRaw, actual);
        checkValidExtensions(runtimeToRaw, actual);
        checkValidExtensions(testToProd, actual);

        checkInvalidExtensions(forbiddenVariantToRaw, actual);
        checkInvalidExtensions(forbiddenTestToProd, actual);
    }

    @Test
    public void checkLibConfigExtension() throws IOException, InterruptedException {
        GradleBuildResult result = project.executor().run("lib:printConfig");
        ListMultimap<String, String> actual = getAllExtendsFrom(result.getStdoutAsLines());

        checkValidExtensions(libBasics, actual);
        checkValidExtensions(compileToRaw, actual);
        checkValidExtensions(runtimeToRaw, actual);
        checkValidExtensions(testToProd, actual);

        checkInvalidExtensions(forbiddenVariantToRaw, actual);
        checkInvalidExtensions(forbiddenTestToProd, actual);
    }

    private static void checkValidExtensions(
            @NonNull ListMultimap<String, String> expected,
            @NonNull ListMultimap<String, String> actual) {

        for (Map.Entry<String, String> entry : expected.entries()) {
            Truth.assertThat(actual).containsEntry(entry.getKey(), entry.getValue());
        }
    }

    private static void checkInvalidExtensions(
            @NonNull ListMultimap<String, String> expected,
            @NonNull ListMultimap<String, String> actual) {

        for (Map.Entry<String, String> entry : expected.entries()) {
            Truth.assertThat(actual).doesNotContainEntry(entry.getKey(), entry.getValue());
        }
    }

    @NonNull
    private static ListMultimap<String, String> getAllExtendsFrom(
            @NonNull List<String> stdoutLines) {
        Pattern p = Pattern.compile("(.+)" + SEPARATOR + "(.+)");

        List<Pair<String, String>> pairs =
                stdoutLines
                        .stream()
                        .filter(s -> s.startsWith(CONFIG_EXTENDS_FROM))
                        .map(
                                s -> {
                                    s = s.substring(CONFIG_EXTENDS_FROM.length()).trim();
                                    Matcher m = p.matcher(s);
                                    Truth.assertThat(m.matches()).isTrue();
                                    return Pair.of(m.group(1), m.group(2));
                                })
                        .collect(Collectors.toList());

        ListMultimap<String, String> map = ArrayListMultimap.create();
        for (Pair<String, String> pair : pairs) {
            map.put(pair.getFirst(), pair.getSecond());
        }

        return map;
    }

    @NonNull
    private static ListMultimap<String, String> mapOf(@NonNull String... values) {
        ListMultimap<String, String> map = ArrayListMultimap.create();
        for (int i = 0; i < values.length; i += 2) {
            map.put(values[i], values[i + 1]);
        }

        return map;
    }

    @NonNull
    private static ListMultimap<String, String> mapWithSingleKey(
            @NonNull String key, @NonNull String... values) {
        ListMultimap<String, String> map = ArrayListMultimap.create();
        for (String value : values) {
            map.put(key, value);
        }
        return map;
    }

    private static String getBuildFileContent() {
        return "android {\n"
                + "    flavorDimensions \"api\", \"mode\"\n"
                + "    productFlavors {\n"
                + "        demo {\n"
                + "            dimension \"mode\"\n"
                + "        }\n"
                + "\n"
                + "        full {\n"
                + "            dimension \"mode\"\n"
                + "        }\n"
                + "\n"
                + "        mnc {\n"
                + "             dimension \"api\"\n"
                + "             minSdkVersion '24'\n"
                + "        }\n"
                + "\n"
                + "        lollipop {\n"
                + "             dimension \"api\"\n"
                + "             minSdkVersion '23'\n"
                + "         }\n"
                + "    }\n"
                + "}\n"
                + "\n"
                + "task(printConfig) {\n"
                + "    doLast {\n"
                + "        project.configurations.each { config ->\n"
                + "            displayExtendsFrom(config.name, config.getExtendsFrom())\n"
                + "        }\n"
                + "    }\n"
                + "}\n"
                + "\n"
                + "def displayExtendsFrom(String rootConfig, Set<Configuration> configs) {\n"
                + "\tconfigs.each { config ->\n"
                + "\t\tprintln \""
                + CONFIG_EXTENDS_FROM
                + "$rootConfig"
                + SEPARATOR
                + "$config.name\"\n"
                + "\t\tdisplayExtendsFrom(rootConfig, config.getExtendsFrom())\n"
                + "\t}\n"
                + "}\n";
    }
}
