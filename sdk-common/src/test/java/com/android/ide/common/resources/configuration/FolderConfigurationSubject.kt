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

package com.android.ide.common.resources.configuration

import com.android.resources.Density
import com.google.common.truth.Truth.assert_
import com.android.resources.ScreenRound
import com.android.resources.UiMode
import com.google.common.truth.FailureStrategy
import com.google.common.truth.Subject
import com.google.common.truth.SubjectFactory
import com.google.common.truth.Truth

/**
 * Custom [Truth] subject for testing [FolderConfiguration] with a nicer API and better failure
 * messages
 */
class FolderConfigurationSubject(failureStrategy: FailureStrategy, subject: FolderConfiguration) :
    Subject<FolderConfigurationSubject, FolderConfiguration>(failureStrategy, subject) {

    fun isMatchFor(folderConfiguration: FolderConfiguration) {
        if (!actual().isMatchFor(folderConfiguration)) {
            fail("is match for " + folderConfiguration.toDisplayString())
        }
    }

    fun isNotMatchFor(folderConfiguration: FolderConfiguration) {
        if (actual().isMatchFor(folderConfiguration)) {
            fail("is not match for " + folderConfiguration.toDisplayString())
        }
    }

    fun hasNoLocale() {
        val qualifier = actual().localeQualifier
        if (qualifier != null) {
            failWithBadResults("has no locale", "", "has", qualifier.shortDisplayValue)
        }
    }

    fun hasLanguage(expected: String) {
        val actualValue = actual().localeQualifier?.language

        if (actualValue != expected) {
            failWithBadResults("has language", expected, "has", actualValue)
        }
    }

    fun hasRegion(expected: String) {
        val actualValue = actual().localeQualifier?.region

        if (actualValue != expected) {
            failWithBadResults("has region", expected, "has", actualValue)
        }
    }

    fun hasNoScreenDimension() {
        val qualifier = actual().screenDimensionQualifier
        if (qualifier != null) {
            failWithBadResults("has no screen dimension", "", "has", qualifier.shortDisplayValue)
        }
    }

    fun hasNoLayoutDirection() {
        val qualifier = actual().layoutDirectionQualifier
        if (qualifier != null && qualifier != qualifier.nullQualifier) {
            failWithBadResults("has no layout direction", "", "has", qualifier.shortDisplayValue)
        }
    }

    fun hasNoVersion() {
        val qualifier = actual().versionQualifier
        if (qualifier != null) {
            failWithBadResults("has no version", "", "has", qualifier.shortDisplayValue)
        }
    }

    fun hasVersion(expected: Int) {
        val actualValue = actual().versionQualifier?.version
        if (actualValue != expected) {
            failWithBadResults("has version", expected, "has", actualValue)
        }
    }

    fun hasScreenRound(expected: ScreenRound) {
        val actualValue = actual().screenRoundQualifier?.value

        if (actualValue != expected) {
            failWithBadResults("has screen round", expected, "has", actualValue)
        }
    }

    fun hasNoUiMode() {
        val qualifier = actual().uiModeQualifier
        if (qualifier != null) {
            failWithBadResults("has no UI Mode", "", "has", qualifier.shortDisplayValue)
        }
    }

    fun hasUiMode(expected: UiMode) {
        val actualValue = actual().uiModeQualifier?.value

        if (actualValue != expected) {
            failWithBadResults("has UI Mode", expected, "has", actualValue)
        }
    }

    fun hasDensity(expected: Density) {
        val actualValue = actual().densityQualifier?.value

        if (actualValue != expected) {
            failWithBadResults("has Density", expected, "has", actualValue)
        }
    }

    companion object {
        private val FACTORY: SubjectFactory<FolderConfigurationSubject, FolderConfiguration> =
            object : SubjectFactory<FolderConfigurationSubject, FolderConfiguration>() {
                override fun getSubject(
                    fs: FailureStrategy,
                    that: FolderConfiguration
                ): FolderConfigurationSubject {
                    return FolderConfigurationSubject(fs, that)
                }
            }

        @JvmStatic
        fun assertThat(folderConfiguration: FolderConfiguration?): FolderConfigurationSubject {
            return assert_().about(FolderConfigurationSubject.FACTORY).that(folderConfiguration)
        }
    }
}
