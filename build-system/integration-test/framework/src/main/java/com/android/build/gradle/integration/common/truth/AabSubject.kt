/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.build.gradle.integration.common.truth

import com.android.testutils.apk.Aab
import com.android.testutils.apk.AndroidArchive
import com.android.testutils.apk.Dex
import com.android.testutils.truth.AbstractZipSubject
import com.google.common.truth.Fact
import com.google.common.truth.FailureMetadata
import com.google.common.truth.Subject.Factory
import com.google.common.truth.Truth
import java.io.IOException

/** Truth support for aab files.  */
class AabSubject(failureMetadata: FailureMetadata, subject: Aab) : AbstractZipSubject<AabSubject, Aab>(
    failureMetadata,
    subject) {

    companion object {
        fun aabs(): Factory<AabSubject, Aab> {
            return Factory<AabSubject, Aab> { failureMetadata, actualT ->
                AabSubject(failureMetadata, actualT)
            }
        }

        fun assertThat(aab: Aab): AabSubject {
            return Truth.assertAbout<AabSubject, Aab>(aabs()).that(aab)
        }
    }

    @Throws(IOException::class)
    override fun contains(path: String) {
        exists()
        actual().getEntry(path)
            ?: failWithoutActual(Fact.simpleFact("${actual().file} does not contain $path"))
    }

    @Throws(IOException::class)
    override fun doesNotContain(path: String) {
        exists()
        actual().getEntry(path)?.let {
            failWithoutActual(Fact.simpleFact("${actual().file} contains $path"))
        }
    }

    fun containsMainClass(featureNameOrBase: String, className: String) {
        exists()
        AndroidArchive.checkValidClassName(className)
        val dexPath = "$featureNameOrBase/dex/classes.dex"
        contains(dexPath)

        val foundClass = findClasses(featureNameOrBase, className, maxDexIndex = 1)
        if (!foundClass) {
            failWithoutActual(Fact.simpleFact("Class $className not found in primary dex for $featureNameOrBase"))
        }
    }

    fun containsSecondaryClass(featureNameOrBase: String, className: String) {
        exists()
        AndroidArchive.checkValidClassName(className)
        val foundClass = findClasses(featureNameOrBase, className, minDexIndex = 2)
        if (!foundClass) {
            failWithoutActual(Fact.simpleFact("Class $className not found in secondary dex for $featureNameOrBase"))
        }
    }

    fun containsClass(featureNameOrBase: String, className: String) {
        exists()
        AndroidArchive.checkValidClassName(className)

        val foundClass = findClasses(featureNameOrBase, className)
        if (!foundClass) {
            failWithoutActual(Fact.simpleFact("$className not found in $featureNameOrBase"))
        }
    }

    fun doesNotContainClass(featureNameOrBase: String, className: String) {
        exists()
        AndroidArchive.checkValidClassName(className)

        val foundClass = findClasses(featureNameOrBase, className)
        if (foundClass) {
            failWithoutActual(Fact.simpleFact("$className exists in $featureNameOrBase"))
        }
    }

    private fun findClasses(featureNameOrBase: String, className: String, minDexIndex: Int = 1, maxDexIndex: Int = Int.MAX_VALUE): Boolean {
        var index = minDexIndex
        var foundClass = false
        while (index <= maxDexIndex) {
            val suffix = if (index == 1) "" else index.toString()
            val dex =
                actual().getEntry("$featureNameOrBase/dex/classes$suffix.dex")?.let { Dex(it) }
                    ?: break
            if (className in dex.classes) {
                foundClass = true
                break
            }
            index++
        }
        return foundClass
    }
}
