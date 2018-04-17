/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.testutils

import org.mockito.ArgumentMatchers

/**
 * Make some of the ArgumentMatches methods more friendly to Kotlin.
 *
 * ArgumentMatchers.any or .eq are returning null which cannot be passed to non null parameters
 * being tested.
 *
 * These function will return a value instead.
 */
object MockitoKotlinUtils {
    fun <T : Any> safeAny(type: Class<T>, value: T): T = ArgumentMatchers.any(type) ?: value
    fun <T : Any> safeEq(value: T): T = ArgumentMatchers.eq(value) ?: value
}