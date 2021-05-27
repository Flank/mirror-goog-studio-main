/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.utp.plugins.host.coverage.com.android.tools.utp.plugins.host.coverage

import com.android.tools.utp.plugins.host.coverage.AndroidTestCoveragePlugin
import com.android.tools.utp.plugins.host.coverage.proto.AndroidTestCoverageConfigProto.AndroidTestCoverageConfig
import com.google.protobuf.Any
import com.google.testing.platform.api.config.ProtoConfig
import com.google.testing.platform.proto.api.core.ExtensionProto
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for [AndroidTestCoveragePlugin]
 */
@RunWith(JUnit4::class)
class AndroidTestCoveragePluginTest {

    private fun createAndroidTestCoveragePlugin(config: AndroidTestCoverageConfig)
        : AndroidTestCoveragePlugin {
        val packedConfig = Any.pack(config)
        val protoConfig = object: ProtoConfig {
            override val configProto: Any
                get() = packedConfig
            override val configResource: ExtensionProto.ConfigResource?
                get() = null
        }
        return AndroidTestCoveragePlugin().apply {
            configure(protoConfig)
        }
    }

    @Test
    fun configure() {
        createAndroidTestCoveragePlugin(AndroidTestCoverageConfig.getDefaultInstance())
    }
}
