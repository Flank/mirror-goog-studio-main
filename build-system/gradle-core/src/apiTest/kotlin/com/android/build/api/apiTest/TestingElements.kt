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

package com.android.build.api.apiTest

import com.android.build.api.apiTest.VariantApiBaseTest.ScriptingLanguage

/**
 * repository of Gradle tasks and Android related artifacts like build files or manifest files that
 * can be used to assemble tests.
 */
class TestingElements(val language: ScriptingLanguage) {

    fun addGitVersionTask(builder: VariantApiBaseTest.GivenBuilder) {
        builder.addSource(
            constructFilePath("GitVersion"),
            getGitVersionTask()
        )
    }

    fun addManifestProducerTask(builder: VariantApiBaseTest.GivenBuilder) {
        builder.addSource(
            constructFilePath("ManifestProducerTask"),
            getManifestProducerTask()
        )
    }

    fun addManifestVerifierTask(builder: VariantApiBaseTest.GivenBuilder) {
        builder.addSource(
            constructFilePath("VerifyManifestTask"),
            getManifestVerifierTask()
        )
    }

    fun addManifest(builder: VariantApiBaseTest.GivenBuilder) {
        builder.manifest =
                // language=xml
            """<?xml version="1.0" encoding="utf-8"?>
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="com.android.build.example.minimal">
                        <application android:label="Minimal">
                            <activity android:name="MainActivity">
                                <intent-filter>
                                    <action android:name="android.intent.action.MAIN" />
                                    <category android:name="android.intent.category.LAUNCHER" />
                                </intent-filter>
                            </activity>
                        </application>
                    </manifest>
                    """.trimIndent()
    }

    fun addMainActivity(builder: VariantApiBaseTest.GivenBuilder) =
        when(language) {
            ScriptingLanguage.Kotlin ->
                builder.addSource(
                    constructFilePath("com/android/build/example/minimal/MainActivity.kt"),
                    //language = kotlin
                    """
                        package com.android.build.example.minimal

                        import android.app.Activity
                        import android.os.Bundle
                        import android.widget.TextView

                        class MainActivity : Activity() {
                            override fun onCreate(savedInstanceState: Bundle?) {
                                super.onCreate(savedInstanceState)
                                val label = TextView(this)
                                label.setText("Hello world!")
                                setContentView(label)
                            }
                        }
                        """.trimIndent()
                )
            ScriptingLanguage.Groovy ->
                builder.addSource(
                    constructFilePath("com/android/build/example/minimal/MainActivity"),
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
                    """.trimIndent())
    }

    private fun constructFilePath(relativePath: String) =
        when(language) {
            ScriptingLanguage.Kotlin -> "src/main/kotlin/$relativePath.kt"
            ScriptingLanguage.Groovy -> "src/main/java/$relativePath.groovy"
        }



    fun getGitVersionTask() =
        when(language) {
            ScriptingLanguage.Kotlin ->
                // language=kotlin
                """
                import org.gradle.api.DefaultTask
                import org.gradle.api.file.RegularFileProperty
                import org.gradle.api.tasks.OutputFile
                import org.gradle.api.tasks.TaskAction

                abstract class GitVersionTask: DefaultTask() {

                    @get:OutputFile
                    abstract val gitVersionOutputFile: RegularFileProperty

                    @ExperimentalStdlibApi
                    @TaskAction
                    fun taskAction() {

                        val firstProcess = ProcessBuilder("git","rev-parse --short HEAD").start()
                        val error = firstProcess.errorStream.readBytes().decodeToString()
                        if (error != null) {
                            System.err.println("Git error : ${'$'}error")
                        }
                        var gitVersion = firstProcess.inputStream.readBytes().decodeToString()
                        if (gitVersion.isEmpty()) {
                            gitVersion="12"
                        }
                        gitVersionOutputFile.get().asFile.writeText(gitVersion)
                    }
                }
                """.trimIndent()
            ScriptingLanguage.Groovy ->
                // language=groovy
                """
                import org.gradle.api.DefaultTask
                import org.gradle.api.file.RegularFileProperty
                import org.gradle.api.tasks.OutputFile
                import org.gradle.api.tasks.TaskAction

                abstract class GitVersionTask extends DefaultTask {

                    @OutputFile
                    abstract RegularFileProperty getGitVersionOutputFile()

                    @TaskAction
                    void taskAction() {
                        String gitVersion = "git rev-parse --short HEAD".execute().text.trim()
                        if (gitVersion.isEmpty()) {
                            gitVersion="12"
                        }
                        getGitVersionOutputFile().get().asFile.write(gitVersion)
                    }
                }
                """.trimIndent()
        }

    fun getManifestProducerTask() =
        when(language) {
            ScriptingLanguage.Kotlin ->
                // language=kotlin
                """ 
                import org.gradle.api.DefaultTask
                import org.gradle.api.file.RegularFileProperty
                import org.gradle.api.tasks.InputFile
                import org.gradle.api.tasks.OutputFile
                import org.gradle.api.tasks.TaskAction

                abstract class ManifestProducerTask: DefaultTask() {
                    @get:InputFile
                    abstract val gitInfoFile: RegularFileProperty

                    @get:OutputFile
                    abstract val updatedManifest: RegularFileProperty

                    @TaskAction
                    fun taskAction() {

                        val gitVersion = gitInfoFile.get().asFile.readText()
                        val manifest = ""${'"'}<?xml version="1.0" encoding="utf-8"?>
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="com.android.build.example.minimal"
                        android:versionName="${'$'}{gitVersion}"
                        android:versionCode="1" >
                        <application android:label="Minimal">
                            <activity android:name="MainActivity">
                                <intent-filter>
                                    <action android:name="android.intent.action.MAIN" />
                                    <category android:name="android.intent.category.LAUNCHER" />
                                </intent-filter>
                            </activity>
                        </application>
                    </manifest>
                        ""${'"'}
                        println("Writes to " + updatedManifest.get().asFile.absolutePath)
                        updatedManifest.get().asFile.writeText(manifest)
                    }
                }
                """.trimIndent()
            ScriptingLanguage.Groovy ->
                // language=kotlin
                """
                import org.gradle.api.DefaultTask
                import org.gradle.api.file.RegularFileProperty
                import org.gradle.api.tasks.InputFile
                import org.gradle.api.tasks.OutputFile
                import org.gradle.api.tasks.TaskAction
                
                abstract class ManifestProducerTask extends DefaultTask {
                    @InputFile
                    abstract RegularFileProperty getGitInfoFile()
                
                    @OutputFile
                    abstract RegularFileProperty getOutputManifest()
                
                    @TaskAction
                    void taskAction() {
                
                        String gitVersion = new String(getGitInfoFile().get().asFile.readBytes())
                        String manifest = ""${'"'}<?xml version=\"1.0\" encoding=\"utf-8\"?>
                        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                            package="com.android.build.example.minimal"
                            android:versionName="${'$'}{gitVersion}"
                            android:versionCode="1" >
                        
                            <application android:label="Minimal">
                                <activity android:name="MainActivity">
                                    <intent-filter>
                                        <action android:name="android.intent.action.MAIN" />
                                        <category android:name="android.intent.category.LAUNCHER" />
                                    </intent-filter>
                                </activity>
                            </application>
                        </manifest>
                            ""${'"'}
                        println("Writes to " + getOutputManifest().get().getAsFile().getAbsolutePath())
                        getOutputManifest().get().getAsFile().write(manifest)
                    }
                }                    
                """.trimIndent()
        }

    fun getManifestTransformerTask() =
        when(language) {
            ScriptingLanguage.Kotlin ->
                """
                abstract class ManifestTransformerTask: DefaultTask() {

                    @get:InputFile
                    abstract val gitInfoFile: RegularFileProperty

                    @get:InputFile
                    abstract val mergedManifest: RegularFileProperty

                    @get:OutputFile
                    abstract val updatedManifest: RegularFileProperty

                    @TaskAction
                    fun taskAction() {

                        val gitVersion = gitInfoFile.get().asFile.readText()
                        var manifest = mergedManifest.asFile.get().readText()
                        manifest = manifest.replace("android:versionCode=\"1\"", "android:versionCode=\""+ gitVersion +"\"")
                        System.out.println("Writes to " + updatedManifest.get().asFile.getAbsolutePath())
                        updatedManifest.get().asFile.writeText(manifest)
                    }
                }
                """.trimIndent()
            ScriptingLanguage.Groovy ->
                """
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
                }
                """.trimIndent()
        }

    private fun getManifestVerifierTask() =
        when(language) {
            ScriptingLanguage.Kotlin ->
                // language=kotlin
                """ 
                import org.gradle.api.DefaultTask
                import org.gradle.api.file.DirectoryProperty
                import org.gradle.api.tasks.InputFiles
                import org.gradle.api.tasks.TaskAction
                import java.io.ByteArrayOutputStream
                import java.io.PrintStream

                import com.android.tools.apk.analyzer.ApkAnalyzerImpl
                import com.android.build.api.variant.BuiltArtifactsLoader
                import org.gradle.api.provider.Property
                import org.gradle.api.tasks.Internal
                import java.io.File

                abstract class VerifyManifestTask: DefaultTask() {

                    @get:InputFiles
                    abstract val apkFolder: DirectoryProperty

                    @get:Internal
                    abstract val builtArtifactsLoader: Property<BuiltArtifactsLoader>

                    @TaskAction
                    fun taskAction() {
                        val byteArrayOutputStream = object : ByteArrayOutputStream() {
                            @Synchronized
                            override fun toString(): String =
                                super.toString().replace(System.getProperty("line.separator"), "\n")
                        }
                        val ps = PrintStream(byteArrayOutputStream)
                        val apkAnalyzer = ApkAnalyzerImpl(ps, null)
                        val builtArtifacts = builtArtifactsLoader.get().load(apkFolder.get())
                            ?: throw RuntimeException("Cannot load APKs")
                        if (builtArtifacts.elements.size != 1) 
                            throw RuntimeException("Expected one APK !")
                        val apk = File(builtArtifacts.elements.single().outputFile).toPath()
                        apkAnalyzer.resXml(apk, "/AndroidManifest.xml")
                        val manifest = byteArrayOutputStream.toString()
                        println(if (manifest.contains("android:versionCode=\"12\"")) "SUCCESS" else "FAILED")
                    }
                }
                """.trimIndent()
            ScriptingLanguage.Groovy ->
                """

                """.trimIndent()
        }
}