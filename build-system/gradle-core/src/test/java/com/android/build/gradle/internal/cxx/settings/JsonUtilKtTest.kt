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

package com.android.build.gradle.internal.cxx.settings

import com.android.build.gradle.internal.cxx.RandomInstanceGenerator
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class JsonUtilKtTest {
    private val realWorldTestExample = """
        {
          "environments": [
            {
              "namespace": "my-namespace",
              "environment": "VS_14_x86",
              "inheritEnvironments": [],
              "VC14INSTALLDIR": "C:\\Program Files (x86)\\Microsoft Visual Studio 14.0\\VC",
              "WINDOWSKITS": "C:\\Program Files (x86)\\Windows Kits",
              "WINDOWSKITS_VERSION": "10.0.17134.0",
              "PATH": "${'$'}{env.PATH};${'$'}{env.VC14INSTALLDIR}bin;${'$'}{env.WINDOWSKITS}\\10\\bin\\x86",
              "INCLUDE": "${'$'}{env.VC14INSTALLDIR}\\INCLUDE;${'$'}{env.VC14INSTALLDIR}\\ATLMFC\\INCLUDE;${'$'}{env.WINDOWSKITS}\\10\\include\\${'$'}{env.WINDOWSKITS_VERSION}\\ucrt;${'$'}{env.WINDOWSKITS}\\NETFXSDK\\4.6.1\\include\\um;${'$'}{env.WINDOWSKITS}\\10\\include\\${'$'}{env.WINDOWSKITS_VERSION}\\shared;${'$'}{env.WINDOWSKITS}\\10\\include\\${'$'}{env.WINDOWSKITS_VERSION}\\um;${'$'}{env.WINDOWSKITS}\\10\\include\\${'$'}{env.WINDOWSKITS_VERSION}\\winrt;",
              "LIB": "${'$'}{env.VC14INSTALLDIR}\\LIB;${'$'}{env.VC14INSTALLDIR}\\ATLMFC\\LIB;${'$'}{env.WINDOWSKITS}\\10\\lib\\${'$'}{env.WINDOWSKITS_VERSION}\\ucrt\\x86;${'$'}{env.WINDOWSKITS}\\NETFXSDK\\4.6.1\\lib\\um\\x86;${'$'}{env.WINDOWSKITS}\\10\\lib\\${'$'}{env.WINDOWSKITS_VERSION}\\um\\x86;",
              "LIBPATH": "C:\\windows\\Microsoft.NET\\Framework\\v4.0.30319;${'$'}{env.VC14INSTALLDIR}\\LIB;${'$'}{env.VC14INSTALLDIR}\\ATLMFC\\LIB;${'$'}{env.WINDOWSKITS}\\10\\UnionMetadata;${'$'}{env.WINDOWSKITS}\\10\\References;C:\\Program Files (x86)\\Microsoft SDKs\\Windows Kits\\10\\ExtensionSDKs\\Microsoft.VCLibs\\14.0\\References\\CommonConfiguration\\neutral;"
            }
          ],
          "configurations": [
            {
              "name": "x86-Debug",
              "description": "",
              "generator": "Ninja",
              "configurationType": "Debug",
              "inheritEnvironments": [
                "VS_14_x86"
              ],
              "buildRoot": "${'$'}{env.USERPROFILE}\\CMakeBuilds\\${'$'}{workspaceHash}\\build\\${'$'}{name}",
              "installRoot": "${'$'}{env.USERPROFILE}\\CMakeBuilds\\${'$'}{workspaceHash}\\install\\${'$'}{name}",
              "cmakeCommandArgs": "",
              "cmakeToolchain": "",
              "cmakeExecutable": "",
              "buildCommandArgs": "-v",
              "ctestCommandArgs": "",
              "variables": [
                {
                  "name": "CMAKE_C_COMPILER",
                  "value": "${'$'}{env.BIN_ROOT}\\gcc.exe"
                },
                {
                  "name": "CMAKE_CXX_COMPILER",
                  "value": "${'$'}{env.BIN_ROOT}\\g++.exe"
                }
              ]
            }
          ]
        }""".trimIndent()

    @Test
    fun `real world test`() {
        val settings = createCmakeSettingsJsonFromString(realWorldTestExample)
        val returnToString = settings.toJsonString()
        val roundTrip = createCmakeSettingsJsonFromString(returnToString)
        assertThat(settings).isEqualTo(roundTrip)
        assertThat(realWorldTestExample).isEqualTo(returnToString)
    }

    @Test
    fun `comments allowed`() {
        val settings = createCmakeSettingsJsonFromString("""{
            // A comment
        }""".trimIndent())
        val returnToString = settings.toJsonString()
        val roundTrip = createCmakeSettingsJsonFromString(returnToString)
        assertThat(settings).isEqualTo(roundTrip)
    }
    
    @Test
    fun `round trip synthesize random with string property`() {
        val sb = RandomInstanceGenerator()
            .addFactory(PropertyValue::class.java) { sb ->
                PropertyValue.StringPropertyValue(sb.synthetic(String::class.java))
            }

        val settings = sb.synthetic(CMakeSettings::class.java)
        val returnToString = settings.toJsonString()
        val roundTrip = createCmakeSettingsJsonFromString(returnToString)
        assertThat(settings).isEqualTo(roundTrip)
    }

    @Test
    fun `round trip synthesize random with lookup property`() {
        val sb = RandomInstanceGenerator()
            .addFactory(PropertyValue::class.java) { sb ->
                val result = sb.synthetic(String::class.java)
                PropertyValue.LookupPropertyValue { result }
            }

        val settings = sb.synthetic(CMakeSettings::class.java)
        val returnToString = settings.toJsonString()
        val roundTrip = createCmakeSettingsJsonFromString(returnToString)
        assertThat(settings.toJsonString()).isEqualTo(roundTrip.toJsonString())
    }
}