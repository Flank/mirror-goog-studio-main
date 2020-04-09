/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.api.apiTest.groovy

import com.android.build.api.apiTest.VariantApiBaseTest
import com.google.common.truth.Truth
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Test
import kotlin.test.assertNotNull

class TransformArtifactTest: VariantApiBaseTest(ScriptingLanguage.Groovy) {

    @Test
    fun manifestTransformerTest() {
        given {
            val localBuildFolder = testBuildDir.newFolder()
            val groovyExtensions =
                // language=groovy
                """
                import com.android.build.api.artifact.ArtifactTypes
                import org.gradle.api.file.RegularFileProperty

                abstract class GitVersion extends DefaultTask {

                    @OutputFile
                    abstract RegularFileProperty getGitInfoFile()

                    @TaskAction
                    void taskAction() {
                        String gitVersion = "git rev-parse --short HEAD".execute().text.trim()
                        if (gitVersion.isEmpty()) {
                            gitVersion="12"
                        }
                        getGitInfoFile().get().asFile.write(gitVersion)
                    }
                }

                abstract class ManifestTransformerTask extends DefaultTask {

                    @InputFile
                    abstract RegularFileProperty getGitInfoFile()

                    @InputFile
                    abstract RegularFileProperty getMergedManifest()

                    @OutputFile
                    abstract RegularFileProperty getUpdatedManifest()

                    @TaskAction
                    void taskAction() {
                        String gitVersion = new String(getGitInfoFile().get().asFile.readBytes())
                        String manifest = new String(getMergedManifest().get().asFile.readBytes())
                        manifest = manifest.replace("android:versionCode=\"1\"", 
                            "android:versionCode=\""+ gitVersion +"\"")
                        getUpdatedManifest().get().asFile.write(manifest)
                    }
                }"""

            buildFile =
                """
                $groovyExtensions

                android {
                    compileSdkVersion 29
                    buildToolsVersion "29.0.3"
                    defaultConfig {
                        minSdkVersion 21
                        targetSdkVersion 29
                    }

                    onVariantProperties {
                        TaskProvider gitVersionProvider = tasks.register(it.getName() + 'GitVersionProvider', GitVersion) {
                            task ->
                                task.getGitInfoFile().set(new File("${localBuildFolder.absolutePath}/gitVersion"))
                                task.getOutputs().upToDateWhen { false }
                        }

                        TaskProvider manifestUpdater = tasks.register(it.getName() + 'ManifestUpdater', ManifestTransformerTask) {
                            task ->
                                task.getGitInfoFile().set(gitVersionProvider.flatMap { it.getGitInfoFile() })
                        }
                        it.operations.transform(manifestUpdater,
                                { it.getMergedManifest() },
                                { it.getUpdatedManifest() })
                        .on(ArtifactTypes.MERGED_MANIFEST.INSTANCE)
                    }
                }
                """.trimIndent()
            manifest =
                    // language=xml
                    """<?xml version="1.0" encoding="utf-8"?>
                        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                            package="com.android.build.example.minimal" >
                            android:versionCode="1">
                            <application android:label="Minimal">
                                <activity android:name="MainActivity">
                                    <intent-filter>
                                        <action android:name="android.intent.action.MAIN" />
                                        <category android:name="android.intent.category.LAUNCHER" />
                                    </intent-filter>
                                </activity>
                            </application>
                        </manifest>""".trimIndent()

            @Suppress("ClassNameDiffersFromFileName")
            addSource(
                "src/main/java/com/android/example/MainActivity.java",
                // language=java
                """
            package com.android.build.example.minimal;

            import android.app.Activity;
            import android.os.Bundle;
            import android.widget.TextView;

            public class MainActivity extends Activity
            {
                @Override
                public void onCreate(Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);

                    TextView label = new TextView(this);
                    label.setText("Hello world!");

                    setContentView(label);
                }
            }
            """.trimIndent()
            )
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
            arrayOf(
                ":debugGitVersionProvider",
                ":processDebugMainManifest",
                ":debugManifestUpdater"
            ).forEach {
                val task = task(it)
                assertNotNull(task)
                Truth.assertThat(task.outcome).isEqualTo(TaskOutcome.SUCCESS)

            }
        }
    }
}