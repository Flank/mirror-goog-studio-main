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

package com.android.build.gradle.internal.profile

import com.android.ide.common.workers.GradlePluginMBeans
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class ProfileAgentTest {

    @Mock lateinit var listener: RecordingBuildListener

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
    }

    @Test
    fun testBuildMBean() {
        try {
            ProfileAgent.register("foo", listener)
            val buildMBean = GradlePluginMBeans.getBuildMBean()
            Truth.assertThat(buildMBean).isNotNull()
            val sessionId = buildMBean?.sessionId
            Truth.assertThat(sessionId).isNotNull()
            Truth.assertThat(buildMBean?.sessionId).isEqualTo(sessionId)
        } finally {
            ProfileAgent.unregister()
        }
    }
}