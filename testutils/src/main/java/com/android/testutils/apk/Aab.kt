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

package com.android.testutils.apk

import org.mockito.CheckReturnValue
import java.io.File

class Aab(file: File) : Zip(file) {

    /**
     * Returns whether this Aab contains the given class in the classes.dex file in the given
     * feature (or base).
     *
     * @param featureNameOrBase the name of the feature or "base" for the base module.
     * @param className the name of the class, following [Apk.CLASS_FORMAT] format.
     */
    @CheckReturnValue
    fun containsMainClass(featureNameOrBase: String, className: String): Boolean {
        AndroidArchive.checkValidClassName(className)
        val mainDex = getEntry("$featureNameOrBase/dex/classes.dex")?.let { Dex(it) }
        return mainDex?.classes?.containsKey(className) ?: false
    }

    /**
     * Returns whether this Aab contains the given class in one of the classes<i>.dex files
     * (excluding the main classes.dex file) in the given feature (or base).
     *
     * @param featureNameOrBase the name of the feature or "base" for the base module.
     * @param className the name of the class, following [Apk.CLASS_FORMAT] format.
     */
    @CheckReturnValue
    fun containsSecondaryClass(featureNameOrBase: String, className: String): Boolean {
        AndroidArchive.checkValidClassName(className)
        var index = 2
        while (true) {
            val dex = getEntry("$featureNameOrBase/dex/classes$index.dex")?.let { Dex(it) } ?: break
            if (dex.classes.containsKey(className)) {
                return true
            }
            index++
        }
        return false
    }

    /**
     * Returns whether this Aab contains the given class in the classes.dex file *or* one of the
     * classes<i>.dex files in the given feature (or base).
     *
     * @param featureNameOrBase the name of the feature or "base" for the base module.
     * @param className the name of the class, following [Apk.CLASS_FORMAT] format.
     */
    @CheckReturnValue
    fun containsClass(featureNameOrBase: String, className: String): Boolean {
        return containsMainClass(featureNameOrBase, className)
                || containsSecondaryClass(featureNameOrBase, className)
    }
}
