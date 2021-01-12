package com.android.build.gradle.integration.application

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.ZipFileSubject
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * Integration tests for [GenerateManifestJarTask]
 */
@RunWith(Parameterized::class)
class GenerateManifestJarTaskTest(private val enableManifestClass : Boolean) {

    @Rule
    @JvmField
    val project = GradleTestProject.builder()
            .addGradleProperties(
                    "${BooleanOption.GENERATE_MANIFEST_CLASS.propertyName}=$enableManifestClass")
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            // http://b/149978740
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
            .create()

    @Before
    fun setup() {
        project.buildFile.appendText("dependencies { testImplementation 'junit:junit:4.12'\n }")
    }

    @Test
    fun `test Manifest Class is generated if there is at least one permission`() {
        val androidManifest = FileUtils.join(
                project.projectDir, "src", "main", SdkConstants.ANDROID_MANIFEST_XML)
        FileUtils.writeToFile(androidManifest,
                //language=XML
                """<manifest xmlns:android="http://schemas.android.com/apk/res/android"
                  package="com.example.helloworld"
                  android:versionCode="1"
                  android:versionName="1.0">

                <permission
                    android:name="com.example.helloworld.permission.PERMISSION_ONE"
                    android:label="permissionOne"
                    android:description="@string/app_name"
                    android:permissionGroup="android.permission-group.TEST_PERMISSION"
                    android:protectionLevel="dangerous" />

                <application android:label="@string/app_name">
                    <activity android:name=".ManifestClassTest"
                              android:label="@string/app_name">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>
            </manifest>"""
        )

        FileUtils.writeToFile(FileUtils.join(
                project.projectDir, "src", "main", "java", "com", "example", "helloworld",
                "ManifestClassTest.java"),
                //language=java
                """package com.example.helloworld;

                    import android.app.Activity;
                    import android.os.Bundle;
                    import java.lang.AssertionError;

                    public class ManifestClassTest extends Activity {

                        @Override
                        protected void onCreate(Bundle savedInstanceState) {
                            testManifestClassContainsPermission();
                        }

                        public static void testManifestClassContainsPermission() {
                            String permissionOne = Manifest.permission.PERMISSION_ONE;
                            if (!permissionOne.equals(
                                    "com.example.helloworld.permission.PERMISSION_ONE")) {
                                throw new AssertionError(
                                    "permissionOne should equal: " + permissionOne);
                            }
                        }
                    }""".trimIndent()
        )

        FileUtils.writeToFile(FileUtils.join(
                project.projectDir, "src", "test", "java", "com", "example", "helloworld",
                "ManifestClassUnitTest.java"),
                //language=java
                """package com.example.helloworld;

                    import com.example.helloworld.ManifestClassTest;
                    import org.junit.Test;import org.junit.runner.RunWith;
                    import org.junit.runners.JUnit4;

                    @RunWith(JUnit4.class)
                    public class ManifestClassUnitTest {

                        @Test
                        public void testPermissionOneValue() {
                            ManifestClassTest.testManifestClassContainsPermission();
                        }
                    }""".trimIndent()
        )
        if (enableManifestClass) {
            project.execute(
                    "assembleDebug",
                    "assembleRelease",
                    "testDebugUnitTest",
                    "testReleaseUnitTest"
            )
        } else {
            project.executeExpectingFailure("assembleDebug", "assembleRelease")
        }

        val manifestJarExists = getManifestJarArtifact()
            .walkBottomUp().filter(File::isFile).map(File::getName).toList()
        if (enableManifestClass) {
            // Contains two manifest jars (for debug and release)
            assertThat(manifestJarExists).containsExactly("Manifest.jar", "Manifest.jar")
        } else {
            assertThat(manifestJarExists).isEmpty()
        }
    }

    @Test
    fun `test builds with no manifest custom permissions`() {
        project.executor()
            .with(BooleanOption.GENERATE_MANIFEST_CLASS, enableManifestClass)
            .run("clean", "assembleDebug")

        // Check Manifest JAR has been created, but contains no entries
        val intermediateCompiledManifest = getManifestJarArtifact()
            .walkBottomUp().filter(File::isFile).toList()
        assertThat(intermediateCompiledManifest).hasSize(if (enableManifestClass) 1 else 0)
        intermediateCompiledManifest.forEach { manifestJar ->
            ZipFileSubject.assertThat(manifestJar) {
                it.entries(".*").isEmpty()
            }
        }
    }

    private fun getManifestJarArtifact() = project.getIntermediateFile(
        InternalArtifactType.COMPILE_MANIFEST_JAR.getFolderName()
    )

    companion object {
        @Parameterized.Parameters(name = "enableManifestClass_{0}")
        @JvmStatic fun params() = listOf(true, false)
    }
}
