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

package com.android.build.gradle

import com.android.annotations.NonNull
import com.android.builder.core.AndroidBuilder
import com.android.testutils.TestUtils
import com.google.common.base.Joiner
import junit.framework.TestCase

import java.security.CodeSource
/**
 * Base class for tests.
 */
abstract class BaseDslTest extends TestCase {

    protected final static int COMPILE_SDK_VERSION = 24
    protected static final String BUILD_TOOL_VERSION = AndroidBuilder.MIN_BUILD_TOOLS_REV

    public static final String FOLDER_TEST_PROJECTS = "test-projects"

    protected static final def

    /**
     * Variants created by default.
     */
    DEFAULT_VARIANTS = [
           "release", "debug", "debugAndroidTest", "releaseUnitTest", "debugUnitTest"
    ]

    protected File sdkDir

    static int countVariants(Map variants) {
        variants.values().sum()
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp()
        sdkDir = TestUtils.getSdk()
    }

    /**
     * Returns the root dir for the gradle plugin project
     */
    protected File getRootDir() {
        CodeSource source = getClass().getProtectionDomain().getCodeSource()
        if (source != null) {
            URL location = source.getLocation();
            try {
                File dir = new File(location.toURI())
                assertTrue(dir.getPath(), dir.exists())

                File f =
                        dir.getParentFile().getParentFile().getParentFile().getParentFile()
                                .getParentFile().getParentFile().getParentFile()

                return  new File(
                        f,
                        Joiner.on(File.separator).join(
                                "tools",
                                "base",
                                "build-system",
                                "integration-test"))
            } catch (URISyntaxException e) {
                fail(e.getLocalizedMessage())
            }
        }

        fail("Fail to get the tools/build folder")
    }

    /**
     * Returns the root folder for the tests projects.
     */
    protected File getTestDir() {
        return getRootDir()
    }

    /**
     * Returns the name item from the collection of items. The items *must* have a "name" property.
     * @param items the item collection to search for a match
     * @param name the name of the item to return
     * @return the found item or null
     */
    protected static <T> T findNamedItemMaybe(@NonNull Collection<T> items, @NonNull String name) {
        return items.find {it.name == name}
    }

    /**
     * Returns the name item from the collection of items. The items *must* have a "name" property.
     * @param items the item collection to search for a match
     * @param name the name of the item to return
     * @return the found item or null
     */
    protected static <T> T findNamedItem(
            @NonNull Collection<T> items, @NonNull String name, @NonNull String typeName) {
        T foundItem = findNamedItemMaybe(items, name);
        assertNotNull("$name $typeName null-check", foundItem)
        return foundItem
    }
}
