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

import com.android.build.gradle.internal.services.createProjectServices
import com.android.testutils.AbstractBuildGivenBuildExpectTest
import org.junit.Rule
import org.junit.rules.ExpectedException
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Basic tests for [LazyManifestParser]
 */
abstract class LazyManifestParserBaseTest :
    AbstractBuildGivenBuildExpectTest<LazyManifestParserBaseTest.GivenBuilder, ManifestData>() {

    // no tests here

    // ---------------------------------------------------------------------------------------------

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @get:Rule
    val exceptionRule : ExpectedException = ExpectedException.none()

    private val projectServices = createProjectServices()

    override fun instantiateGiven() = GivenBuilder()
    override fun instantiateResult() = ManifestData()

    override fun defaultWhen(given: GivenBuilder): ManifestData? {
        val manifestValue = given.manifest

        val manifestFile = given.manifestFile
            ?: if (manifestValue != null) {
                temporaryFolder.newFile("AndroidManifest.xml").also {
                    it.writeText(manifestValue)
                }
            } else {
                File("/path/to/no/manifest")
            }

        val manifestFileProperty = projectServices.objectFactory.fileProperty().fileValue(manifestFile)

        val manifestParser = LazyManifestParser(
            manifestFile = manifestFileProperty,
            manifestFileRequired = given.manifestFileIsRequired,
            projectServices = projectServices
        )

        return manifestParser.manifestData.get()
    }

    class GivenBuilder {
        var manifest: String? = null
        var manifestFile: File? = null
        var manifestFileIsRequired = true
    }
}
