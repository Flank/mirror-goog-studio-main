/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.android.utils.HelpfulEnumConverter
import junit.framework.TestCase.fail
import org.junit.Test

class TaskCategoryTest {

    @Test
    fun `All sdk-common TaskCategory enum elements is in gradle-core TaskCategory enum`() {
        val gradleCoreTaskCategoryConverter = HelpfulEnumConverter(com.android.build.gradle.internal.tasks.TaskCategory::class.java)
        try {
            com.android.ide.common.attribution.TaskCategory.values().forEach {
                gradleCoreTaskCategoryConverter.convert(it.toString()) }
        } catch (e: Exception) {
            fail("Elements in sdk-common TaskCategory is not in gradle-core TaskCategory")
        }
    }

    @Test
    fun `All gradle-core TaskCategory enum elements is in sdk-common TaskCategory enum`() {
        val sdkCommonTaskCategoryConverter = HelpfulEnumConverter(com.android.ide.common.attribution.TaskCategory::class.java)
        try {
            com.android.build.gradle.internal.tasks.TaskCategory.values().forEach {
                sdkCommonTaskCategoryConverter.convert(it.toString()) }
        } catch (e: Exception) {
            fail("Elements in gradle-core TaskCategory is not in sdk-common TaskCategory")
        }
    }

}
