package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
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
            // http://b/149978740
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
            .create()

    @Test
    fun `test OptimizeResourcesTask works with resource shrinker` () {
        project.buildFile.appendText("""android {
                buildTypes {
                    release {
                        shrinkResources true
                        minifyEnabled true
                    }
                }
            }""")
        project.execute("assembleRelease")
        val shrinkedOptimizedApkSize =
                project.getApk(GradleTestProject.ApkType.RELEASE).contentsSize

        assertThat(shrinkedOptimizedApkSize).isGreaterThan(0)
    }
}
