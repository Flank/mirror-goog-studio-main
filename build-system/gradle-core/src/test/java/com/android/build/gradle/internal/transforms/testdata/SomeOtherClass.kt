/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.transforms.testdata

import java.io.IOException
import java.lang.Exception

class SomeOtherClass(s: SomeClass) {
    @Throws(Exception::class)
    fun someMethod(@YetAnotherClass m: Map<Cat, Dog>, t1: Toy): Animal? {
        val n = arrayOf<Array<Array<NewClass>>>()
        val enumOne = EnumClass.ONE
        if (t1 is CarbonForm) {
            return null
        }
        try { } catch (e1: IOException) { }
        return t1 as Tiger
    }
}
