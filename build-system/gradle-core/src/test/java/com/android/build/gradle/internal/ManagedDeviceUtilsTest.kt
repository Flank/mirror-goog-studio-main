package com.android.build.gradle.internal

import com.android.testutils.SystemPropertyOverrides
import com.android.utils.Environment
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ManagedDeviceUtilsTest {

    var isRosettaValue: Boolean = false

    @Before
    fun setup() {
        isRosettaValue = false
        val environment: Environment = object : Environment() {
            override fun getVariable(name: EnvironmentVariable): String? = null

            override val isRosetta
                get() = isRosettaValue
        }
        Environment.initialize(environment)
    }

    @Test
    fun computeAvdName_worksWithParenthesis() {
        val computedName = computeAvdName(
            29,
            "google_apis",
            "x86",
            "Pixel 2 (something)"
        )

        assertThat(computedName).isEqualTo("dev29_google_apis_x86_Pixel_2__something_")
    }

    @Test
    fun computeAbiFromArchitecture_useX86Over64IfAvailable() {
        SystemPropertyOverrides().use { systemPropertyOverrides ->
            systemPropertyOverrides.setProperty("os.arch", "x86_64")

            // Any aosp api 29 and below (except 26) has an x86 image.
            assertThat(computeAbiFromArchitecture(false, 25, "aosp")).isEqualTo("x86")
            assertThat(computeAbiFromArchitecture(false, 29, "aosp")).isEqualTo("x86")
            // Any aosp api 30 and above doesn't have an x86 image.
            assertThat(computeAbiFromArchitecture(false, 30, "aosp")).isEqualTo("x86_64")
            assertThat(computeAbiFromArchitecture(false, 31, "aosp")).isEqualTo("x86_64")
            // aosp api 26 doesn't have an x86 image
            assertThat(computeAbiFromArchitecture(false, 26, "aosp")).isEqualTo("x86_64")

            // Google api 30 and below has an x86 image.
            assertThat(computeAbiFromArchitecture(false, 25, "google")).isEqualTo("x86")
            assertThat(computeAbiFromArchitecture(false, 29, "google")).isEqualTo("x86")
            assertThat(computeAbiFromArchitecture(false, 30, "google")).isEqualTo("x86")
            // Google api 31 and above doesn't have an x86 image.
            assertThat(computeAbiFromArchitecture(false, 31, "google")).isEqualTo("x86_64")

            // aosp-atd has an x86 image at api 30
            assertThat(computeAbiFromArchitecture(false, 30, "aosp-atd")).isEqualTo("x86")
            assertThat(computeAbiFromArchitecture(false, 31, "aosp-atd")).isEqualTo("x86_64")

            // same for google-atd
            assertThat(computeAbiFromArchitecture(false, 30, "google-atd")).isEqualTo("x86")
            assertThat(computeAbiFromArchitecture(false, 31, "google-atd")).isEqualTo("x86_64")
        }
    }

    @Test
    fun computeAbiFromArchitecture_use64IfOverrideIsUsed() {
        SystemPropertyOverrides().use { systemPropertyOverrides ->
            systemPropertyOverrides.setProperty("os.arch", "x86_64")

            // If require64Bit is set, we should always use x86_64 over the x86 image.
            assertThat(computeAbiFromArchitecture(true, 25, "aosp")).isEqualTo("x86_64")
            assertThat(computeAbiFromArchitecture(true, 29, "aosp")).isEqualTo("x86_64")
            assertThat(computeAbiFromArchitecture(true, 30, "aosp")).isEqualTo("x86_64")
            assertThat(computeAbiFromArchitecture(true, 31, "aosp")).isEqualTo("x86_64")

            assertThat(computeAbiFromArchitecture(true, 25, "google")).isEqualTo("x86_64")
            assertThat(computeAbiFromArchitecture(true, 29, "google")).isEqualTo("x86_64")
            assertThat(computeAbiFromArchitecture(true, 30, "google")).isEqualTo("x86_64")
            assertThat(computeAbiFromArchitecture(true, 31, "google")).isEqualTo("x86_64")

            assertThat(computeAbiFromArchitecture(true, 30, "aosp-atd")).isEqualTo("x86_64")
            assertThat(computeAbiFromArchitecture(true, 31, "aosp-atd")).isEqualTo("x86_64")

            assertThat(computeAbiFromArchitecture(true, 30, "google-atd")).isEqualTo("x86_64")
            assertThat(computeAbiFromArchitecture(true, 31, "google-atd")).isEqualTo("x86_64")
        }
    }

    @Test
    fun computeAbiFromArchitecture_useArmIfArm() {
        SystemPropertyOverrides().use { systemPropertyOverrides ->
            systemPropertyOverrides.setProperty("os.arch", "aarch64")

            // Regardless of api, require64Bit, and source, we should use "arm64-v8a" images.
            assertThat(computeAbiFromArchitecture(false, 29, "aosp")).isEqualTo("arm64-v8a")
            assertThat(computeAbiFromArchitecture(true, 29, "aosp")).isEqualTo("arm64-v8a")
            assertThat(computeAbiFromArchitecture(false, 29, "google")).isEqualTo("arm64-v8a")
            assertThat(computeAbiFromArchitecture(true, 29, "google")).isEqualTo("arm64-v8a")
            assertThat(computeAbiFromArchitecture(false, 29, "aosp-atd")).isEqualTo("arm64-v8a")
            assertThat(computeAbiFromArchitecture(true, 29, "aosp-atd")).isEqualTo("arm64-v8a")
            assertThat(computeAbiFromArchitecture(false, 29, "google-atd")).isEqualTo("arm64-v8a")
            assertThat(computeAbiFromArchitecture(true, 29, "google-atd")).isEqualTo("arm64-v8a")

            assertThat(computeAbiFromArchitecture(false, 30, "aosp")).isEqualTo("arm64-v8a")
            assertThat(computeAbiFromArchitecture(true, 30, "aosp")).isEqualTo("arm64-v8a")
            assertThat(computeAbiFromArchitecture(false, 30, "google")).isEqualTo("arm64-v8a")
            assertThat(computeAbiFromArchitecture(true, 30, "google")).isEqualTo("arm64-v8a")
            assertThat(computeAbiFromArchitecture(false, 30, "aosp-atd")).isEqualTo("arm64-v8a")
            assertThat(computeAbiFromArchitecture(true, 30, "aosp-atd")).isEqualTo("arm64-v8a")
            assertThat(computeAbiFromArchitecture(false, 30, "google-atd")).isEqualTo("arm64-v8a")
            assertThat(computeAbiFromArchitecture(true, 30, "google-atd")).isEqualTo("arm64-v8a")
        }
    }

    @Test
    fun computeAbiFromArchitecture_useArmIfX86RosettaOnArm() {
        isRosettaValue = true
        SystemPropertyOverrides().use { systemPropertyOverrides ->
            systemPropertyOverrides.setProperty("os.arch", "x86_64")
            systemPropertyOverrides.setProperty("os.name", "mac")

            // Regardless of api, require64Bit, and source, we should use "arm64-v8a" images.
            assertThat(computeAbiFromArchitecture(false, 29, "aosp")).isEqualTo("arm64-v8a")
            assertThat(computeAbiFromArchitecture(true, 29, "aosp")).isEqualTo("arm64-v8a")
            assertThat(computeAbiFromArchitecture(false, 29, "google")).isEqualTo("arm64-v8a")
            assertThat(computeAbiFromArchitecture(true, 29, "google")).isEqualTo("arm64-v8a")
            assertThat(computeAbiFromArchitecture(false, 29, "aosp-atd")).isEqualTo("arm64-v8a")
            assertThat(computeAbiFromArchitecture(true, 29, "aosp-atd")).isEqualTo("arm64-v8a")
            assertThat(computeAbiFromArchitecture(false, 29, "google-atd")).isEqualTo("arm64-v8a")
            assertThat(computeAbiFromArchitecture(true, 29, "google-atd")).isEqualTo("arm64-v8a")

            assertThat(computeAbiFromArchitecture(false, 30, "aosp")).isEqualTo("arm64-v8a")
            assertThat(computeAbiFromArchitecture(true, 30, "aosp")).isEqualTo("arm64-v8a")
            assertThat(computeAbiFromArchitecture(false, 30, "google")).isEqualTo("arm64-v8a")
            assertThat(computeAbiFromArchitecture(true, 30, "google")).isEqualTo("arm64-v8a")
            assertThat(computeAbiFromArchitecture(false, 30, "aosp-atd")).isEqualTo("arm64-v8a")
            assertThat(computeAbiFromArchitecture(true, 30, "aosp-atd")).isEqualTo("arm64-v8a")
            assertThat(computeAbiFromArchitecture(false, 30, "google-atd")).isEqualTo("arm64-v8a")
            assertThat(computeAbiFromArchitecture(true, 30, "google-atd")).isEqualTo("arm64-v8a")
        }
    }
}
