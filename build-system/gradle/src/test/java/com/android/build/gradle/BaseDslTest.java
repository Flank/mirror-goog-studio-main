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

package com.android.build.gradle;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.builder.core.AndroidBuilder;
import com.android.testutils.OsType;
import com.android.testutils.TestUtils;
import com.android.utils.FileUtils;
import com.google.common.collect.Lists;
import java.io.File;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import junit.framework.TestCase;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;

/** Base class for tests. */
public abstract class BaseDslTest extends TestCase {
    protected static final int COMPILE_SDK_VERSION = 24;
    protected static final String BUILD_TOOL_VERSION =
            AndroidBuilder.MIN_BUILD_TOOLS_REV.toString();
    private static final String MANIFEST_TEMPLATE =
            // language=xml
            "<?xml version=\"1.0\" encoding=\"utf-8\"?><manifest package=\"%s\"></manifest>";

    protected static int countVariants(Map<String, Integer> variants) {
        return variants.values().stream().mapToInt(Integer::intValue).sum();
    }

    protected static void checkDefaultVariants(List<VariantScope> variants) {
        assertThat(Lists.transform(variants, VariantScope::getFullVariantName))
                .containsExactly(
                        "release", "debug", "debugAndroidTest", "releaseUnitTest", "debugUnitTest");
    }

    /**
     * Returns the variant with the given name, or null.
     *
     * @param variants the variants
     * @param name the name of the item to return
     * @return the found variant or null
     */
    protected static <T extends BaseVariant> T findVariantMaybe(
            @NonNull Collection<T> variants, @NonNull String name) {
        return variants.stream().filter(t -> t.getName().equals(name)).findAny().orElse(null);
    }

    /**
     * Returns the variant with the given name. Fails if there is no such variant.
     *
     * @param variants the item collection to search for a match
     * @param name the name of the item to return
     * @return the found variant
     */
    protected static <T extends BaseVariant> T findVariant(
            @NonNull Collection<T> variants, @NonNull String name) {
        T foundItem = findVariantMaybe(variants, name);
        assertNotNull("Variant with name " + name + " not found in " + variants, foundItem);
        return foundItem;
    }

    /**
     * Returns the variant data with the given name. Fails if there is no such variant.
     *
     * @param variants the item collection to search for a match
     * @param name the name of the item to return
     * @return the found variant
     */
    protected static <T extends BaseVariantData> T findVariantData(
            @NonNull Collection<VariantScope> variants, @NonNull String name) {
        Optional<?> result =
                variants.stream()
                        .filter(t -> t.getFullVariantName().equals(name))
                        .map(VariantScope::getVariantData)
                        .findAny();
        //noinspection unchecked: too much hassle with BaseVariantData generics, not worth it for test code.
        return (T)
                result.orElseThrow(
                        () -> new AssertionError("Variant data for " + name + " not found."));
    }

    protected File projectDirectory;
    protected Project project;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        SdkHandler.setTestSdkFolder(TestUtils.getSdk());
        projectDirectory = Files.createTempDirectory(getClass().getName()).toFile();
        File manifest = new File(projectDirectory, "src/main/AndroidManifest.xml");
        FileUtils.createFile(manifest, String.format(MANIFEST_TEMPLATE, getClass().getName()));

        ProjectBuilder projectBuilder = ProjectBuilder.builder().withProjectDir(projectDirectory);

        if (OsType.getHostOs() == OsType.WINDOWS) {
            // On Windows Gradle assumes the user home $PROJECT_DIR/userHome and unzips some DLLs
            // there that this JVM will load, so they cannot be deleted. Below we set things up so
            // that all tests use a single userHome directory and project dirs can be deleted.
            File tmpdir = new File(System.getProperty("java.io.tmpdir"));
            projectBuilder.withGradleUserHomeDir(new File(tmpdir, "testGradleUserHome"));
        }

        project = projectBuilder.build();
    }

    @Override
    protected void tearDown() throws Exception {
        FileUtils.deletePath(projectDirectory);
        super.tearDown();
    }
}
