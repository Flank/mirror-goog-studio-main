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

package com.android.tools.firebase.testlab.gradle.services

import com.android.testutils.MockitoKt.mock
import org.gradle.api.Action
import org.gradle.api.services.BuildServiceRegistry
import org.gradle.api.services.BuildServiceSpec
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.Mockito.argThat
import org.mockito.Mockito.eq
import org.mockito.Mockito.startsWith
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.Mockito.withSettings
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

/**
 * Unit tests for [TestLabBuildService].
 */
class TestLabBuildServiceTest {

    @get:Rule
    val mockitoJUnitRule: MockitoRule = MockitoJUnit.rule()

    @get:Rule
    val temporaryFolderRule = TemporaryFolder()

    @Mock(answer = Answers.RETURNS_MOCKS)
    lateinit var mockBuildServiceRegistry: BuildServiceRegistry

    @Test
    fun registerIfAbsent() {
        val credentialFile = temporaryFolderRule.newFile("testCredentialFile").apply {
            writeText("""
                    {
                      "client_id": "test_client_id",
                      "client_secret": "test_client_secret",
                      "quota_project_id": "test_quota_project_id",
                      "refresh_token": "test_refresh_token",
                      "type": "authorized_user"
                    }
                """.trimIndent())
        }
        TestLabBuildService.RegistrationAction{
            credentialFile
        }.registerIfAbsent(mockBuildServiceRegistry)

        lateinit var configAction: Action<in BuildServiceSpec<TestLabBuildService.Parameters>>
        verify(mockBuildServiceRegistry).registerIfAbsent(
            startsWith("com.android.tools.firebase.testlab.gradle.services.TestLabBuildService_"),
            eq(TestLabBuildService::class.java),
            argThat {
                configAction = it
                true
            }
        )

        val mockSpec = mock<BuildServiceSpec<TestLabBuildService.Parameters>>()
        val mockParams = mock<TestLabBuildService.Parameters>(
            withSettings().defaultAnswer(Answers.RETURNS_DEEP_STUBS))
        `when`(mockSpec.parameters).thenReturn(mockParams)

        configAction.execute(mockSpec)

        verify(mockParams.credentialFile).set(eq(credentialFile))
        verify(mockParams.quotaProjectName).set(eq("test_quota_project_id"))
    }
}
