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

package com.android.build.gradle.internal.manifest

import com.android.build.gradle.internal.utils.IssueSubject.assertThat
import com.android.builder.model.SyncIssue
import org.junit.Test
import java.io.File

/**
 * Basic tests for [LazyManifestParser]
 */
internal class LazyManifestParserFileTest : LazyManifestParserBaseTest() {

    @Test
    fun `empty manifest`() {
        given {
            manifest = """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
""".trimIndent()
        }

        expect {
            data {
                // all null values
            }
        }
    }

    @Test
    fun `missing manifest but required`() {
        val missingFile = File(temporaryFolder.newFolder("test"),"AndroidManifest.xml")

        given {
            manifestFile = missingFile
        }

        expect {
            issue {
                severity = SyncIssue.SEVERITY_ERROR
                type = SyncIssue.TYPE_MISSING_ANDROID_MANIFEST
                message = "Manifest file does not exist: ${missingFile.absolutePath}"
            }
        }
    }

    @Test
    fun `missing manifest but not required`() {
        val missingFile = File(temporaryFolder.newFolder("test"),"AndroidManifest.xml")

        given {
            manifestFile = missingFile
            manifestFileIsRequired = false
        }

        expect {
            data {
                // all null values
            }
        }
    }

    @Test
    fun `early manifest parsing check`() {

        given {
            manifest = """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
""".trimIndent()
            earlyManifestParsingCheck = true
        }

        withIssueChecker {
            val onlyIssue = it.single()
            assertThat(onlyIssue).hasMessageThatContains("The manifest is being parsed during configuration. Please either remove android.disableConfigurationManifestParsing from build.gradle or remove any build configuration rules that read the android manifest file.\n")
            assertThat(onlyIssue).hasSeverity(SyncIssue.SEVERITY_WARNING)
            assertThat(onlyIssue).hasType(SyncIssue.TYPE_MANIFEST_PARSED_DURING_CONFIGURATION)
        }
        expect {
            // already checked in withIssueChecker block
        }
    }

}
