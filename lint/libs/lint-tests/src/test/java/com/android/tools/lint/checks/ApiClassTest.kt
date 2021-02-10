/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.testutils.TestUtils
import com.android.tools.lint.checks.infrastructure.TestLintClient
import com.android.tools.lint.checks.infrastructure.checkTransitiveComparator
import org.junit.Test
import java.io.File
import java.lang.RuntimeException
import java.util.Collections
import java.util.Random

class ApiClassTest {
    @Test
    fun checkComparator() {
        val client = object : TestLintClient() {
            override fun getSdkHome(): File? {
                return TestUtils.getSdk().toFile()
            }
        }
        val target = client.getLatestSdkTarget(
            ApiLookup.SDK_DATABASE_MIN_VERSION,
            true
        ) ?: return
        val folder: File = File(target.getLocation())
        val database =
            File(folder, SdkConstants.FD_DATA + File.separator + ApiLookup.XML_FILE_PATH)
        if (database.isFile) {
            val api = Api.parseApi(database)
            val classes = api.classes.values.toMutableList()
            // The classes list is WAY too large for this (>5K items, with an n^3 algorithm).
            // So instead, pick out 100 randomly chosen items (varying over time) and
            // check those (and include the seed in the test error be able to reproduce
            // an error if it should happen
            val seed = System.currentTimeMillis()
            val generator = Random(seed)
            Collections.shuffle(classes, generator)
            val sublist = classes.subList(0, 99)
            try {
                checkTransitiveComparator(sublist)
            } catch (error: Throwable) {
                throw RuntimeException("Seed was $seed", error)
            }
        }
    }
}
