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

package com.android.utils

import com.android.testutils.SystemPropertyOverrides
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ComputerArchUtilsTest {
    @Before
    fun setup() {
        Environment.initialize()
    }

    @Test
    fun cpuArchitectureFromStringTest() {
        assertEquals(CpuArchitecture.UNKNOWN, architectureFromString(null))
        assertEquals(CpuArchitecture.UNKNOWN, architectureFromString(""))
        assertEquals(CpuArchitecture.X86_64, architectureFromString("x86_64"))
        assertEquals(CpuArchitecture.X86_64, architectureFromString("ia64"))
        assertEquals(CpuArchitecture.X86_64, architectureFromString("amd64"))
        assertEquals(CpuArchitecture.X86, architectureFromString("i486"))
        assertEquals(CpuArchitecture.X86, architectureFromString("i586"))
        assertEquals(CpuArchitecture.X86, architectureFromString("i686"))
        assertEquals(CpuArchitecture.X86, architectureFromString("x86"))
        assertEquals(CpuArchitecture.ARM, architectureFromString("aarch64"))
        assertEquals(CpuArchitecture.UNKNOWN, architectureFromString("x96"))
        assertEquals(CpuArchitecture.UNKNOWN, architectureFromString("i6869"))
    }

    @Test
    fun getJvmArchitectureTest() {
        SystemPropertyOverrides().use { systemPropertyOverrides ->
            systemPropertyOverrides.setProperty("os.arch", null)
            assertEquals(CpuArchitecture.UNKNOWN, jvmArchitecture)

            systemPropertyOverrides.setProperty("os.arch", "")
            assertEquals(CpuArchitecture.UNKNOWN, jvmArchitecture)

            systemPropertyOverrides.setProperty("os.arch", "x86_64")
            assertEquals(CpuArchitecture.X86_64, jvmArchitecture)

            systemPropertyOverrides.setProperty("os.arch", "ia64")
            assertEquals(CpuArchitecture.X86_64, jvmArchitecture)

            systemPropertyOverrides.setProperty("os.arch", "amd64")
            assertEquals(CpuArchitecture.X86_64, jvmArchitecture)

            systemPropertyOverrides.setProperty("os.arch", "i486")
            assertEquals(CpuArchitecture.X86, jvmArchitecture)

            systemPropertyOverrides.setProperty("os.arch", "i586")
            assertEquals(CpuArchitecture.X86, jvmArchitecture)

            systemPropertyOverrides.setProperty("os.arch", "i686")
            assertEquals(CpuArchitecture.X86, jvmArchitecture)

            systemPropertyOverrides.setProperty("os.arch", "x86")
            assertEquals(CpuArchitecture.X86, jvmArchitecture)

            systemPropertyOverrides.setProperty("os.arch", "aarch64")
            assertEquals(CpuArchitecture.ARM, jvmArchitecture)

            systemPropertyOverrides.setProperty("os.arch", "x96")
            assertEquals(CpuArchitecture.UNKNOWN, jvmArchitecture)

            systemPropertyOverrides.setProperty("os.arch", "i6869")
            assertEquals(CpuArchitecture.UNKNOWN, jvmArchitecture)
        }
    }

    @Test
    fun getOsArchitecture_unrecognizedOsTest() {
        SystemPropertyOverrides().use { systemPropertyOverrides ->
            systemPropertyOverrides.setProperty("os.arch", null)
            assertEquals(CpuArchitecture.UNKNOWN, osArchitecture)

            systemPropertyOverrides.setProperty("os.arch", "")
            assertEquals(CpuArchitecture.UNKNOWN, osArchitecture)

            systemPropertyOverrides.setProperty("os.arch", "i6869")
            assertEquals(CpuArchitecture.UNKNOWN, osArchitecture)
        }
    }

    @Test
    fun getOsArchitecture_linuxTest() {
        try {
            SystemPropertyOverrides().use { systemPropertyOverrides ->
                systemPropertyOverrides.setProperty("os.name", "Linux")

                // Case 1: x86 jvm running on x86 Linux
                Environment.instance = object : Environment() {
                    override fun getVariable(name: EnvironmentVariable): String? =
                        if (name.key == "HOSTTYPE") "x86" else null
                }
                systemPropertyOverrides.setProperty("os.arch", "x86")
                assertEquals(CpuArchitecture.X86, osArchitecture)

                // Case 2: x86_64 jvm running on x86_64 Linux
                Environment.instance = object : Environment() {
                    override fun getVariable(name: EnvironmentVariable): String? =
                        if (name.key == "HOSTTYPE") "x86_64" else null
                }
                systemPropertyOverrides.setProperty("os.arch", "x86_64")
                assertEquals(CpuArchitecture.X86_64, osArchitecture)

                // Case 3: x86 jvm running on x86_64 Linux
                Environment.instance = object : Environment() {
                    override fun getVariable(name: EnvironmentVariable): String? =
                        if (name.key == "HOSTTYPE") "x86_64" else null
                }
                systemPropertyOverrides.setProperty("os.arch", "x86")
                assertEquals(CpuArchitecture.X86_64, osArchitecture)
            }
        } finally {
           Environment.instance = Environment.SYSTEM
        }
    }

    @Test
    fun getOsArchitecture_windowsTest() {
        try {
            SystemPropertyOverrides().use { systemPropertyOverrides ->
                systemPropertyOverrides.setProperty("os.name", "Windows 10")

                // Case 1: x86 jvm running on x86 Windows
                systemPropertyOverrides.setProperty("os.arch", "x86")
                Environment.instance = object : Environment() {
                    // In this case, PROCESSOR_ARCHITEW6432 will not be set.
                    override fun getVariable(name: EnvironmentVariable): String? = null
                }
                assertEquals(CpuArchitecture.X86, osArchitecture)

                // Case 2: x86_64 jvm running on x86_64 Windows
                systemPropertyOverrides.setProperty("os.arch", "x86_64")
                Environment.instance = object : Environment() {
                    // In this case, PROCESSOR_ARCHITEW6432 will not be set.
                    override fun getVariable(name: EnvironmentVariable): String? = null
                }
                assertEquals(CpuArchitecture.X86_64, osArchitecture)

                // Case 3: x86 jvm running on x86_64 Windows
                systemPropertyOverrides.setProperty("os.arch", "x86")
                Environment.instance = object : Environment() {
                    // WOW64 emulator will set PROCESSOR_ARCHITEW64 in this case.
                    override fun getVariable(name: EnvironmentVariable): String? =
                        if (name.key == "PROCESSOR_ARCHITEW6432") "AMD64" else null
                }
                assertEquals(CpuArchitecture.X86_64, osArchitecture)
            }
        } finally {
            Environment.instance = Environment.SYSTEM
        }
    }

    @Test
    fun getOsArchitecture_macTest() {
        try {
            SystemPropertyOverrides().use { systemPropertyOverrides ->
                systemPropertyOverrides.setProperty("os.name", "Mac")

                // Case 1: x86_64 jvm on x86_64 Mac
                systemPropertyOverrides.setProperty("os.arch", "x86_64")
                Environment.instance = object : Environment() {
                    override fun getVariable(name: EnvironmentVariable): String? = null
                    override val isRosetta: Boolean = false
                }
                assertEquals(CpuArchitecture.X86_64, osArchitecture)

                // Case 2: arm jvm on arm Mac
                systemPropertyOverrides.setProperty("os.arch", "aarch64")
                Environment.instance = object : Environment() {
                    override fun getVariable(name: EnvironmentVariable): String? = null
                    override val isRosetta: Boolean
                        get() = error("isRosetta should not be called in ARM jvm environment.")
                }
                assertEquals(CpuArchitecture.ARM, osArchitecture)

                // Case 3: x86_64 jvm emulated on arm Mac
                systemPropertyOverrides.setProperty("os.arch", "x86_64")
                Environment.instance = object : Environment() {
                    override fun getVariable(name: EnvironmentVariable): String? = null
                    override val isRosetta: Boolean = true
                }
                assertEquals(CpuArchitecture.X86_ON_ARM, osArchitecture)
            }
        } finally {
            Environment.instance = Environment.SYSTEM
        }
    }
}
