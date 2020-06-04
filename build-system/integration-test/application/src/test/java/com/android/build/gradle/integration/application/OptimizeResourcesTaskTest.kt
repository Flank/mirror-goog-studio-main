package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.internal.tasks.OptimizeResourcesTask
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/** Integration tests for [OptimizeResourcesTask]. */
class OptimizeResourcesTaskTest {

    @Rule
    @JvmField
    val project = GradleTestProject.builder()
            .fromTestApp(MinSdkVersionTest.helloWorldApp)
            .create()

    @Test
    fun `test OptimizeResourcesTask produces smaller APK during release build` () {
        project.gradlePropertiesFile.writeText("android.enableResourceOptimizations=false")
                project.execute("assembleRelease")
        val unoptimzedApk = project.getApk(GradleTestProject.ApkType.RELEASE).contentsSize

        project.gradlePropertiesFile.writeText("android.enableResourceOptimizations=true")
        project.execute("assembleRelease")
        val optimzedApk = project.getApk(GradleTestProject.ApkType.RELEASE).contentsSize
        assertThat(optimzedApk)

        assertThat(unoptimzedApk).isGreaterThan(optimzedApk)
    }

    @Test
    fun `test OptimizeResourcesTask works with resource shrinker` () {
        project.gradlePropertiesFile.writeText("android.enableResourceOptimizations=false")
        project.buildFile.appendText("""android {
                buildTypes {
                    release {
                        shrinkResources true
                        minifyEnabled true
                    }
                }
            }""")
        project.execute("assembleRelease")
        val shrinkedApkSize =
                project.getApk(GradleTestProject.ApkType.RELEASE).contentsSize

        project.gradlePropertiesFile.writeText("android.enableResourceOptimizations=true")
        project.execute("assembleRelease")
        val shrinkedOptimizedApkSize =
                project.getApk(GradleTestProject.ApkType.RELEASE).contentsSize

        assertThat(shrinkedApkSize).isNotEqualTo(0)
        assertThat(shrinkedOptimizedApkSize).isNotEqualTo(0)
        assertThat(shrinkedApkSize).isGreaterThan(shrinkedOptimizedApkSize)
    }
}