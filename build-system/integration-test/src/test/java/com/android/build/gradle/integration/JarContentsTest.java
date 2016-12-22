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

package com.android.build.gradle.integration;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.model.Version;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.BeforeClass;
import org.junit.Test;

/** Checks what we distribute in our jars. */
public class JarContentsTest {

    private static final Set<String> IGNORED_ARTIFACTS =
            ImmutableSet.of(
                    "swt",
                    "ddmuilib",
                    "asset-studio",
                    "monkeyrunner",
                    "uiautomatorviewer",
                    "hierarchyviewer2lib",
                    "traceview");

    // TODO: Handle NOTICE files in Bazel.
    private static final Set<String> GLOBAL_WHITELIST =
            ImmutableSet.of("NOTICE", "NOTICE.txt", "META-INF/MANIFEST.MF");

    private static final Multimap<String, String> EXPECTED;

    static {
        // Useful command for getting these lists:
        // unzip -l path/to.jar | grep -v class | grep -v "/$" | tail -n +2 | awk '{print "\"" $4 "\","}'

        // Feel free to change these to be a regexp pattern if it becomes too tedious to maintain.

        ImmutableSetMultimap.Builder<String, String> expected = ImmutableSetMultimap.builder();
        expected.putAll(
                "com/android/tools/build/builder/",
                "com/android/builder/version.properties",
                "com/android/builder/internal/AndroidManifest.template");
        expected.putAll(
                "com/android/tools/build/builder-model/",
                "com/android/builder/model/version.properties");
        expected.putAll(
                "com/android/tools/build/gradle/",
                "com/android/build/gradle/internal/version.properties",
                "META-INF/gradle-plugins/android.properties",
                "META-INF/gradle-plugins/android-library.properties",
                "META-INF/gradle-plugins/android-reporting.properties",
                "META-INF/gradle-plugins/com.android.application.properties",
                "META-INF/gradle-plugins/com.android.library.properties",
                "META-INF/gradle-plugins/com.android.test.properties",
                "META-INF/gradle-plugins/com.android.external.build.properties",
                "META-INF/gradle-plugins/com.android.instantapp.properties",
                "META-INF/gradle-plugins/com.android.atom.properties");
        expected.putAll(
                "com/android/tools/build/gradle-core/",
                "com/android/build/gradle/internal/test/report/base-style.css",
                "com/android/build/gradle/internal/test/report/style.css",
                "com/android/build/gradle/internal/test/report/report.js",
                "com/android/build/gradle/proguard-android.txt",
                "com/android/build/gradle/proguard-android-optimize.txt",
                "instant-run/instant-run-server.jar");
        expected.putAll(
                "com/android/tools/build/gradle-experimental/",
                "com/android/build/gradle/model/version.properties",
                "META-INF/gradle-plugins/com.android.model.application.properties",
                "META-INF/gradle-plugins/com.android.model.library.properties",
                "META-INF/gradle-plugins/com.android.model.native.properties",
                "META-INF/gradle-plugins/com.android.model.external.properties");
        expected.putAll(
                "com/android/tools/internal/build/test/devicepool/",
                "META-INF/gradle-plugins/devicepool.properties");
        expected.putAll(
                "com/android/tools/dvlib/",
                "com/android/dvlib/devices-1.xsd",
                "com/android/dvlib/devices-2.xsd");
        expected.putAll(
                "com/android/tools/sdklib/",
                "com/android/sdklib/internal/build/BuildConfig.template",
                "com/android/sdklib/devices/wear.xml",
                "com/android/sdklib/devices/tv.xml",
                "com/android/sdklib/devices/devices.xml",
                "com/android/sdklib/devices/nexus.xml",
                "com/android/sdklib/repository/sdk-common-custom.xjb",
                "com/android/sdklib/repository/sdk-common.xjb",
                "com/android/sdklib/repository/sdk-repository-01.xsd",
                "com/android/sdklib/repository/sdk-sys-img-01.xsd",
                "com/android/sdklib/repository/sdk-addon-01.xsd",
                "com/android/sdklib/repository/sdk-common-01.xsd",
                "com/android/sdklib/repository/legacy/sdk-addon-02.xsd",
                "com/android/sdklib/repository/legacy/sdk-repository-11.xsd",
                "com/android/sdklib/repository/legacy/sdk-addon-03.xsd",
                "com/android/sdklib/repository/legacy/sdk-addon-07.xsd",
                "com/android/sdklib/repository/legacy/sdk-sys-img-03.xsd",
                "com/android/sdklib/repository/legacy/sdk-repository-08.xsd",
                "com/android/sdklib/repository/legacy/sdk-addon-05.xsd",
                "com/android/sdklib/repository/legacy/sdk-repository-01.xsd",
                "com/android/sdklib/repository/legacy/sdk-repository-04.xsd",
                "com/android/sdklib/repository/legacy/sdk-repository-10.xsd",
                "com/android/sdklib/repository/legacy/sdk-repository-12.xsd",
                "com/android/sdklib/repository/legacy/sdk-sys-img-01.xsd",
                "com/android/sdklib/repository/legacy/sdk-repository-07.xsd",
                "com/android/sdklib/repository/legacy/sdk-addon-01.xsd",
                "com/android/sdklib/repository/legacy/sdk-addon-04.xsd",
                "com/android/sdklib/repository/legacy/sdk-sys-img-02.xsd",
                "com/android/sdklib/repository/legacy/sdk-stats-1.xsd",
                "com/android/sdklib/repository/legacy/sdk-repository-02.xsd",
                "com/android/sdklib/repository/legacy/sdk-addons-list-2.xsd",
                "com/android/sdklib/repository/legacy/sdk-addon-06.xsd",
                "com/android/sdklib/repository/legacy/sdk-repository-03.xsd",
                "com/android/sdklib/repository/legacy/sdk-addons-list-1.xsd",
                "com/android/sdklib/repository/legacy/sdk-repository-06.xsd",
                "com/android/sdklib/repository/legacy/sdk-repository-05.xsd",
                "com/android/sdklib/repository/legacy/sdk-repository-09.xsd",
                "com/android/sdklib/repository/sources/sdk-sites-list-1.xsd",
                "com/android/sdklib/repository/sources/sdk-sites-list-3.xsd",
                "com/android/sdklib/repository/sources/sdk-sites-list-2.xsd",
                "com/android/sdklib/repository/README.txt");
        expected.putAll(
                "com/android/tools/lint/lint/",
                "com/android/tools/lint/lint-warning.png",
                "com/android/tools/lint/lint-run.png",
                "com/android/tools/lint/default.css",
                "com/android/tools/lint/lint-error.png",
                "com/android/tools/lint/hololike.css");
        expected.putAll(
                "com/android/tools/lint/lint-checks/",
                "com/android/tools/lint/checks/api-versions-support-library.xml");
        expected.putAll(
                "com/android/tools/repository/",
                "com/android/repository/api/common.xjb",
                "com/android/repository/api/generic.xjb",
                "com/android/repository/api/global.xjb",
                "com/android/repository/api/generic-01.xsd",
                "com/android/repository/api/catalog.xml",
                "com/android/repository/api/list-common.xjb",
                "com/android/repository/api/repo-common-01.xsd",
                "com/android/repository/api/repo-sites-common-1.xsd",
                "com/android/repository/impl/meta/common-custom.xjb",
                "com/android/repository/impl/meta/generic-custom.xjb",
                "com/android/repository/impl/sources/repo-sites-common-custom.xjb");
        expected.putAll(
                "com/android/databinding/compilerCommon/",
                "data_binding_version_info.properties");
        expected.putAll(
                "com/android/databinding/compiler/",
                "api-versions.xml",
                "META-INF/services/javax.annotation.processing.Processor");

        EXPECTED = expected.build();
    }

    @BeforeClass
    public static void checkExpectedMap() {
        for (String key : EXPECTED.keySet()) {
            if (!key.endsWith("/")) {
                throw new AssertionError(key + "needs to end with a '/' in the EXPECTED map.");
            }
        }
    }

    @Test
    public void checkTools() throws Exception {
        checkGroup("com/android/tools");
    }

    @Test
    public void checkDataBinding() throws Exception {
        checkGroup("com/android/databinding");
    }

    private static void checkGroup(String groupPrefix) throws IOException {
        boolean foundAndroidRepo = false;

        for (Path repo : GradleTestProject.getRepos()) {
            Path androidTools = repo.resolve(groupPrefix);
            if (!Files.isDirectory(androidTools)) {
                continue;
            } else {
                foundAndroidRepo = true;
            }

            List<Path> ourJars =
                    Files.walk(androidTools)
                            .filter(path -> path.toString().endsWith(".jar"))
                            .filter(path -> !path.toString().endsWith("-sources.jar"))
                            .filter(path -> !isIgnored(path))
                            .filter(JarContentsTest::isCurrentVersion)
                            .collect(Collectors.toList());

            assertThat(ourJars).named("jars to check").isNotEmpty();

            for (Path jar : ourJars) {
                checkJar(jar, repo);
            }
        }

        assertTrue("Failed to find android repo.", foundAndroidRepo);
    }

    private static boolean isIgnored(Path path) {
        return IGNORED_ARTIFACTS
                .stream()
                .anyMatch(name -> path.toString().contains("/" + name + "/"));
    }

    private static boolean isCurrentVersion(Path path) {
        return path.toString().contains(Version.ANDROID_GRADLE_PLUGIN_VERSION)
                || path.toString().contains(Version.ANDROID_GRADLE_COMPONENT_PLUGIN_VERSION)
                || path.toString().contains(Version.ANDROID_TOOLS_BASE_VERSION);
    }

    private static void checkJar(Path jar, Path repo) throws IOException {
        String relativePath = repo.relativize(jar).toString();

        Collection<String> expected =
                EXPECTED.get(relativePath.substring(0, CharMatcher.DIGIT.indexIn(relativePath)));
        if (expected == null) {
            expected = Collections.emptySet();
        }

        try (ZipFile zipFile = new ZipFile(jar.toFile())) {
            for (ZipEntry entry : Collections.list(zipFile.entries())) {
                String actualName = entry.getName();
                if (entry.isDirectory() || actualName.endsWith(".class")) {
                    continue;
                }

                if (GLOBAL_WHITELIST.contains(actualName)) {
                    continue;
                }

                if (actualName.endsWith(".kotlin_module")) {
                    // TODO: Handle kotlin modules in Bazel.
                    continue;
                }

                if (actualName.endsWith(".proto")) {
                    // Gradle packages the proto files in jars.
                    // TODO: Can we remove these from the jars?
                    continue;
                }

                if (expected.contains(actualName)) {
                    continue;
                }

                throw new AssertionError(relativePath + " contains " + actualName);
            }

            for (String expectedName : expected) {
                if (zipFile.getEntry(expectedName) == null) {
                    throw new AssertionError(relativePath + " does not contain " + expectedName);
                }
            }
        }
    }
}
