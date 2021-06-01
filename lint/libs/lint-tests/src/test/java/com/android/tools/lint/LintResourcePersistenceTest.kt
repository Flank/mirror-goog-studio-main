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

package com.android.tools.lint

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import com.android.testutils.TestUtils
import com.android.tools.lint.LintResourceRepository.Companion.get
import com.android.tools.lint.checks.infrastructure.TestFiles.manifest
import com.android.tools.lint.checks.infrastructure.TestLintClient
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.client.api.ResourceRepositoryScope
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.model.PathVariables
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Persistence is also tested a bit in [LintResourcePersistenceTest]
 */
class LintResourcePersistenceTest {
    @get:Rule
    var temporaryFolder = TemporaryFolder()

    private fun getPathVariables(): PathVariables {
        val pathVariables = PathVariables()
        pathVariables.add("ROOT", temporaryFolder.root)
        return pathVariables
    }

    @Test
    fun testDeserialization() {
        val expected =
            "" +
                "http://schemas.android.com/apk/res-auto;;app/res/values-b\\+sr\\+Latn/values.xml," +
                "+styleable:ContentFrame,0,V-content:reference:-contentId:reference:" +
                "-windowSoftInputMode:flags:stateUnspecified:0,stateUnchanged:1," +
                "-fastScrollOverlayPosition:enum:floating:0,atThumb:1,aboveThumb:2,;"

        val pathVariables = getPathVariables()
        val deserialized = LintResourcePersistence.deserialize(
            expected.trim(), pathVariables, null, null,
        )
        val serialized = deserialized.serialize(pathVariables, null, sort = true)
        assertEquals(expected, serialized.trim())
    }

    @Test
    fun testFrameworkResources() {
        // This test uses a massive amount of memory and fails when run from Gradle with
        //     java.lang.OutOfMemoryError at LintResourcePersistenceTest.kt:73
        // Just run this when running lint locally (and leave trace to remember to do this)
        if (System.getenv("INCLUDE_EXPENSIVE_LINT_TESTS") == null) {
            println("Skipping ${this.javaClass.simpleName}.testFrameworkResources: Resource intensive")
            return
        }

        // Serialize and deserialize the entire framework resource folder (which is massive
        // and uses pretty much all the resource capabilities) and diff the two

        val androidHome = TestUtils.getSdk().toFile().path ?: return

        val task = lint().files(manifest().minSdk(19)).sdkHome(File(androidHome))
        val client = TestLintClient()
        val dir = File(temporaryFolder.root, "framework-project")
        val projectDir = task.createProjects(dir)[0]
        client.setLintTask(task)

        val project = Project.create(client, projectDir, projectDir)
        client.registerProject(projectDir, project)
        project.directLibraries = emptyList()
        val folderRepository = get(client, project, ResourceRepositoryScope.ANDROID)

        // Test serialization too -- serialize and deserialize the repositories and
        // make sure they work the same
        val serialized =
            LintResourcePersistence.serialize(folderRepository as LintResourceRepository, client.pathVariables)
        val deserialized = LintResourcePersistence.deserialize(serialized, getPathVariables())

        // If both methods returned empty string the above would equal, so also perform
        // some spot checks on the resource repositories.
        // Also wanted to compare this against the AGP resource repository, but
        // that repository is unable to handle framework resources (e.g. complains
        // about duplicate resource items which happens in the framework (with for example
        // alternate strings provided for phone vs tablet, attempting to read
        // <public> elements etc.

        for (resources in listOf(folderRepository, deserialized)) {
            assertFalse(
                resources.hasResources(
                    ResourceNamespace.ANDROID,
                    ResourceType.STRING,
                    "ok123"
                )
            )
            val okItems =
                resources.getResources(ResourceNamespace.ANDROID, ResourceType.STRING, "ok")
            assertTrue(okItems.size > 50)
            val defaultOk = okItems.first { it.configuration.isDefault }
            assertEquals("OK", defaultOk.resourceValue?.value)

            val smsItems = resources.getResources(
                ResourceNamespace.ANDROID,
                ResourceType.STRING,
                "sms_short_code_details"
            )
            val smsEn = smsItems.first() { it.configuration.isDefault }
            // Note -- this string can change in the platform; if it does, update the test
            // to match it.
            assertEquals(
                "This may cause charges on your mobile account.",
                smsEn.resourceValue!!.value
            )
            assertEquals(
                "This <b>may cause charges</b> on your mobile account.",
                smsEn.resourceValue!!.rawXmlValue
            )
            val smsNo = smsItems.first() { it.configuration.localeQualifier?.value == "nb" }
            assertEquals(
                "Dette kan føre til kostnader på mobilabonnementet ditt.",
                smsNo.resourceValue!!.value
            )
            assertEquals(
                "\"Dette \"<b>\"kan føre til kostnader\"</b>\" på mobilabonnementet ditt.\"",
                smsNo.resourceValue!!.rawXmlValue
            )

            val mimeItems = resources.getResources(
                ResourceNamespace.ANDROID,
                ResourceType.STRING,
                "mime_type_document_ext"
            )
            val mimeEn = mimeItems.first() { it.configuration.isDefault }
            assertEquals("(PDF) document", mimeEn.resourceValue!!.value)
            assertEquals(
                "<xliff:g example=\"PDF\" id=\"extension\">%1\$s</xliff:g> document",
                mimeEn.resourceValue!!.rawXmlValue
            )
        }

        // Make sure all the locales are present too; there's something like 86 translations of this one:
        assertEquals(
            folderRepository.getResources(
                ResourceNamespace.ANDROID,
                ResourceType.STRING,
                "ok"
            ).size,
            deserialized.getResources(ResourceNamespace.ANDROID, ResourceType.STRING, "ok").size,
        )

        val deserializedPrint = deserialized.prettyPrint()
        val folderPrint = folderRepository.prettyPrint()
        assertEquals(folderPrint, deserializedPrint)
    }
}
