package com.android.build.gradle.integration.manageddevice.application

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.integration.manageddevice.utils.getStandardExecutor
import com.android.build.gradle.integration.manageddevice.utils.setupLicenses
import com.android.build.gradle.integration.manageddevice.utils.setupSdkDir
import com.android.build.gradle.integration.manageddevice.utils.setupSdkRepo
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import java.io.File
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SimpleManagedDeviceTest {

    @get:Rule
    val project = GradleTestProjectBuilder().fromTestProject("utp").apply {
        // TODO(b/215756076): Make Managed Device Tasks Configuration Cachable
        withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
    }.create()

    private lateinit var sdkImageSource: File
    private lateinit var appProject: GradleTestProject
    private lateinit var userHomeDirectory: File
    private lateinit var localPrefDirectory: File
    private lateinit var sdkLocation: File

    private val executor: GradleTaskExecutor
    get() = getStandardExecutor(
        project,
        userHomeDirectory,
        localPrefDirectory,
        sdkImageSource)

    @Before
    fun setUp() {
        appProject = project.getSubproject("app")

        sdkLocation = project.file("projectSDK")

        setupSdkDir(project, sdkLocation)

        // Set up prefs folder
        userHomeDirectory = project.file("local")
        localPrefDirectory = project.file("local/.android")
        FileUtils.mkdirs(localPrefDirectory)

        sdkImageSource = project.file("sysImgSource/dl.google.com/android/repository")
        setupSdkRepo(sdkImageSource)

        appProject.buildFile.appendText("""
        android {
            testOptions {
                devices {
                    device1 (com.android.build.api.dsl.ManagedVirtualDevice) {
                        device = "Pixel 2"
                        apiLevel = 29
                        systemImageSource = "aosp"
                    }
                }
            }
        }
    """)
    }

    @Test
    fun runBasicManagedDevice() {
        executor.run(":app:device1DebugAndroidTest")

        val reportDir = FileUtils.join(
            project.getSubproject("app").buildDir,
            "reports",
            "androidTests",
            "managedDevice",
            "device1")
        assertThat(File(reportDir, "index.html")).exists()
        assertThat(File(reportDir, "com.example.android.kotlin.html")).exists()
        assertThat(
            File(reportDir, "com.example.android.kotlin.ExampleInstrumentedTest.html")).exists()
    }
}
