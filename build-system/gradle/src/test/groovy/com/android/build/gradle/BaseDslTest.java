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

import com.android.annotations.NonNull;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.builder.core.AndroidBuilder;
import com.android.testutils.TestUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import junit.framework.TestCase;

/** Base class for tests. */
public abstract class BaseDslTest extends TestCase {
    public static final int COMPILE_SDK_VERSION = 24;
    public static final String BUILD_TOOL_VERSION = AndroidBuilder.MIN_BUILD_TOOLS_REV.toString();
    public static final String FOLDER_TEST_PROJECTS = "test-projects";
    public static final List<String> DEFAULT_VARIANTS =
            ImmutableList.of(
                    "release", "debug", "debugAndroidTest", "releaseUnitTest", "debugUnitTest");

    protected File sdkDir;

    protected static int countVariants(Map<String, Integer> variants) {
        return variants.values().stream().mapToInt(Integer::intValue).sum();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        sdkDir = TestUtils.getSdk();
    }

    /** Returns the root dir for the gradle plugin project */
    protected File getRootDir() {
        CodeSource source = getClass().getProtectionDomain().getCodeSource();
        if (source != null) {
            URL location = source.getLocation();
            try {
                File dir = new File(location.toURI());
                TestCase.assertTrue(dir.getPath(), dir.exists());

                File f =
                        dir.getParentFile()
                                .getParentFile()
                                .getParentFile()
                                .getParentFile()
                                .getParentFile()
                                .getParentFile()
                                .getParentFile();

                return new File(
                        f,
                        Joiner.on(File.separator)
                                .join("tools", "base", "build-system", "integration-test"));
            } catch (URISyntaxException e) {
                TestCase.fail(e.getLocalizedMessage());
            }
        }

        TestCase.fail("Fail to get the tools/build folder");
        return null;
    }

    /** Returns the root folder for the tests projects. */
    protected File getTestDir() {
        return getRootDir();
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
        assertNotNull("Variant with name " + name + " not found.", foundItem);
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
            @NonNull Collection<T> variants, @NonNull String name) {
        Optional<T> result = variants.stream().filter(t -> t.getName().equals(name)).findAny();
        return result.orElseThrow(
                () -> new AssertionError("Variant data for " + name + " not found."));
    }
}
