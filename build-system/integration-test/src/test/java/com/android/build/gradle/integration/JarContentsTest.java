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

import com.android.apkzlib.utils.IOExceptionConsumer;
import com.android.build.gradle.integration.common.category.FailsUnderBazel;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/** Checks what we distribute in our jars. */
@Category(FailsUnderBazel.class)
public class JarContentsTest {

    private static final Set<String> GLOBAL_WHITELIST =
            ImmutableSet.of("NOTICE", "META-INF/MANIFEST.MF");

    private static final Multimap<String, String> EXPECTED;

    static {
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
                "instant-run/instant-run-server.jar",
                "atom_metadata.proto",
                "atom_dependency.proto",
                "apk_manifest.proto",
                "iapk_metadata.proto");
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
        EXPECTED = expected.build();
    }

    @Test
    public void checkAllJars() throws Exception {
        for (Path repo : GradleTestProject.getRepos()) {
            Path androidTools = repo.resolve("com/android/tools");
            if (!Files.isDirectory(androidTools)) {
                continue;
            }

            Files.walk(androidTools)
                    .filter(path -> path.toString().endsWith(".jar"))
                    .filter(path -> !path.toString().endsWith("-sources.jar"))
                    .filter(path -> path.toString().contains("/swt/"))
                    .forEach(IOExceptionConsumer.asConsumer(jar -> checkJar(jar, repo)));
        }
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

                if (actualName.startsWith("META-INF/services/")) {
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
