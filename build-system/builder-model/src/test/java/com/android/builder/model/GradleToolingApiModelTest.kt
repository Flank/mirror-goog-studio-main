/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.builder.model

import com.android.testutils.ApiTester
import com.google.common.collect.ImmutableSet
import com.google.common.io.Resources
import com.google.common.reflect.ClassPath
import org.junit.Test
import java.io.IOException

class GradleToolingApiModelTest {
    @Test
    @Throws(Exception::class)
    fun stableApiElements() {
        getApiTester().checkApiElements()
    }

    companion object {
        private val EXCLUDED_CLASSES = ImmutableSet.of<Class<*>>(
            GradleToolingApiModelTest::class.java,
            GradleToolingApiModelUpdater::class.java,
            TestOptionsTest::class.java
        )
        private val STABLE_API_URL = Resources.getResource(
            GradleToolingApiModelTest::class.java, "tooling-api-model-api.txt"
        )

        fun getApiTester(): ApiTester {
            val classes =
                ClassPath
                    .from(GradleToolingApiModelTest::class.java.classLoader)
                    .getTopLevelClassesRecursive("com.android.builder.model")
                    .filter { !EXCLUDED_CLASSES.contains(it.load()) }
            return ApiTester(
                "Android Gradle Plugin Tooling Model API.",
                classes,
                ApiTester.Filter.ALL,
                "The Android Gradle Plugin Tooling Model API."
                        + " API has changed, either revert "
                        + "the api change or re-run GradleToolingApiModelUpdater.main[] from the "
                        + "IDE to update the API file.\n"
                        + "GradleToolingApiModelUpdater will apply the following changes if run:\n",
                STABLE_API_URL
            )
        }
    }
}