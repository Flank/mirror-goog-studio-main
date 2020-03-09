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

import org.junit.Test
import java.io.File

/**
 * Basic tests for [LazyManifestParser]
 */
open class LazyManifestParserFileTest : LazyManifestParserBaseTest() {

    @Test
    fun `empty manifest`() {
        given {
            manifest = """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
""".trimIndent()
        }

        expect {
            // all null values
        }
    }

    @Test
    fun `missing manifest but required`() {
        val missingFile = File(temporaryFolder.newFolder("test"),"AndroidManifest.xml")

        given {
            manifestFile = missingFile
        }

        exceptionRule.expect(RuntimeException::class.java)
        exceptionRule.expectMessage("Manifest file does not exist: " + missingFile.absolutePath)

        expect {
            // values aren't relevant here since it's going to throw
        }
    }

    @Test
    fun `missing manifest but not required`() {
        given {
            manifestFileIsRequired = false
        }

        expect {
            // all null values
        }
    }
}
