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
@Suppress("ClassNameDiffersFromFileName")
class TestingElements(val language: ScriptingLanguage) {

    fun addGitVersionTask(builder: VariantApiBaseTest.GivenBuilder) {
        builder.addSource(
            constructFilePath("GitVersion"),
            """
            import org.gradle.api.DefaultTask
            import org.gradle.api.file.RegularFileProperty
            import org.gradle.api.tasks.OutputFile
            import org.gradle.api.tasks.TaskAction
            ${getGitVersionTask()}
            """
        )
    }

    fun addManifestProducerTask(builder: VariantApiBaseTest.GivenBuilder) {
        builder.addSource(
            constructFilePath("ManifestProducerTask"),
            """
            import org.gradle.api.DefaultTask
            import org.gradle.api.file.RegularFileProperty
            import org.gradle.api.tasks.InputFile
            import org.gradle.api.tasks.OutputFile
            import org.gradle.api.tasks.TaskAction
            ${getManifestProducerTask()}
            """

        )
    }

    fun addManifestTransformerTask(builder: VariantApiBaseTest.GivenBuilder) {
        builder.addSource(
            constructFilePath("ManifestTransformerTask"),
            """
            import org.gradle.api.DefaultTask
            import org.gradle.api.file.RegularFileProperty
            import org.gradle.api.tasks.InputFile
            import org.gradle.api.tasks.OutputFile
            import org.gradle.api.tasks.TaskAction
            ${getGitVersionManifestTransformerTask()}
            """
        )
    }

    fun addManifestVerifierTask(builder: VariantApiBaseTest.GivenBuilder) {
        builder.addSource(
            constructFilePath("VerifyManifestTask"),
            getManifestVerifierTask()
        )
    }

    fun addCopyApksTask(builder: VariantApiBaseTest.GivenBuilder) {
        builder.addSource(
            constructFilePath("CopyApksTask"),
            """
            import java.io.Serializable
            import java.io.File
            import javax.inject.Inject
            import org.gradle.api.DefaultTask
            import org.gradle.api.file.RegularFileProperty
            import org.gradle.api.file.Directory
            import org.gradle.api.file.DirectoryProperty
            import org.gradle.api.tasks.InputFiles
            import org.gradle.api.tasks.Internal
            import org.gradle.api.tasks.OutputDirectory
            import org.gradle.api.tasks.TaskAction
            import org.gradle.api.provider.Property
            import org.gradle.workers.WorkParameters
            import org.gradle.workers.WorkerExecutor
            import org.gradle.workers.WorkAction
            import com.android.build.api.artifact.SingleArtifact
            import com.android.build.api.artifact.ArtifactKind
            import com.android.build.api.artifact.Artifact
            import com.android.build.api.artifact.Artifact.Replaceable
            import com.android.build.api.artifact.Artifact.ContainsMany
            import com.android.build.api.artifact.ArtifactTransformationRequest
            import com.android.build.api.variant.BuiltArtifact

            ${getCopyApksTask()}
            """
        )
    }

    fun addCommonAndroidBuildLogic() =
                """compileSdkVersion(29)
                defaultConfig {
                    minSdkVersion(21)
                }"""

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

    fun addLibraryManifest(libName: String, builder: VariantApiBaseTest.GivenBuilder) {
        builder.manifest =
                // language=xml
            """<?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.android.build.example.$libName">
                    <application>
                    </application>
                </manifest>
            """.trimIndent()
    }

    fun addMainActivity(builder: VariantApiBaseTest.GivenBuilder) =
        when(language) {
            ScriptingLanguage.Kotlin ->
                builder.addSource(
                    constructFilePath("com/android/build/example/minimal/MainActivity.kt"),
            //language=kotlin
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
            """)
            ScriptingLanguage.Groovy ->
                builder.addSource(
                    constructFilePath("com/android/build/example/minimal/MainActivity"),
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
            """)
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
            abstract class GitVersionTask: DefaultTask() {

                @get:OutputFile
                abstract val gitVersionOutputFile: RegularFileProperty

                @ExperimentalStdlibApi
                @TaskAction
                fun taskAction() {

                    // this would be the code to get the tip of tree version,
                    // val firstProcess = ProcessBuilder("git","rev-parse --short HEAD").start()
                    // val error = firstProcess.errorStream.readBytes().decodeToString()
                    // if (error.isNotBlank()) {
                    //      System.err.println("Git error : ${'$'}error")
                    // }
                    // var gitVersion = firstProcess.inputStream.readBytes().decodeToString()

                    // but here, we are just hardcoding :
                    gitVersionOutputFile.get().asFile.writeText("1234")
                }
            }
            """
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
                    // this would be the code to get the tip of tree version,
                    // String gitVersion = "git rev-parse --short HEAD".execute().text.trim()
                    // if (gitVersion.isEmpty()) {
                    //    gitVersion="12"
                    //}
                    getGitVersionOutputFile().get().asFile.write("1234")
                }
            }
            """
        }

    fun getStringProducerTask(valueToProduce: String) =
            when(language) {
                ScriptingLanguage.Kotlin ->
                    // language=kotlin
                    """
            abstract class StringProducerTask: DefaultTask() {

                @get:OutputFile
                abstract val outputFile: RegularFileProperty

                @ExperimentalStdlibApi
                @TaskAction
                fun taskAction() {
                    outputFile.get().asFile.writeText("$valueToProduce")
                }
            }
            """
                ScriptingLanguage.Groovy ->
                    // language=groovy
                    """
            import org.gradle.api.DefaultTask
            import org.gradle.api.file.RegularFileProperty
            import org.gradle.api.tasks.OutputFile
            import org.gradle.api.tasks.TaskAction

            abstract class StringProducerTask extends DefaultTask {

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void taskAction() {
                    getOutputFile().get().asFile.write("$valueToProduce")
                }
            }
            """
            }

fun getManifestProducerTask() =
        when(language) {
            ScriptingLanguage.Kotlin ->
            // language=kotlin
            """
            abstract class ManifestProducerTask: DefaultTask() {
                @get:InputFile
                abstract val gitInfoFile: RegularFileProperty

                @get:OutputFile
                abstract val outputManifest: RegularFileProperty

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
                    println("Writes to " + outputManifest.get().asFile.absolutePath)
                    outputManifest.get().asFile.writeText(manifest)
                }
            }
            """
            ScriptingLanguage.Groovy ->
            // language=groovy
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
                    String manifest = ""${'"'}<?xml version="1.0" encoding="utf-8"?>
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="com.android.build.example.minimal"
                        android:versionName="${'$'}{new String(getGitInfoFile().get().asFile.readBytes())}"
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
            """
        }

    fun getSimpleManifestTransformerTask() =
        when(language) {
            ScriptingLanguage.Kotlin ->
                // language=kotlin
                """
            abstract class ManifestTransformerTask: DefaultTask() {

                @get:Input
                abstract val activityName: Property<String>

                @get:InputFile
                abstract val mergedManifest: RegularFileProperty

                @get:OutputFile
                abstract val updatedManifest: RegularFileProperty

                @TaskAction
                fun taskAction() {

                    var manifest = mergedManifest.asFile.get().readText()
                    manifest = manifest.replace("<application",
                    "<uses-permission android:name=\"android.permission.INTERNET\"/>\n<application")
                    println("Writes to " + updatedManifest.get().asFile.getAbsolutePath())
                    updatedManifest.get().asFile.writeText(manifest)
                }
            }
            """
            ScriptingLanguage.Groovy ->
                // language=groovy
                """
            import org.gradle.api.file.RegularFileProperty
            import org.gradle.api.provider.Property

            abstract class ManifestTransformerTask extends DefaultTask {

                @Input
                abstract Property<String> getActivityName()

                @InputFile
                abstract RegularFileProperty getMergedManifest()

                @OutputFile
                abstract RegularFileProperty getUpdatedManifest()

                @TaskAction
                void taskAction() {
                    String manifest = new String(getMergedManifest().get().asFile.readBytes())
                    manifest = manifest.replace("/<application>",
                        "\t\t<activity android:name=" + getActivityName().get() + "></activity>\n\t</application>")
                    getUpdatedManifest().get().asFile.write(manifest)
                }
            }
            """
        }

    fun getGitVersionManifestTransformerTask() =
        when(language) {
            ScriptingLanguage.Kotlin ->
            // language=kotlin
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
                    manifest = manifest.replace("android:versionCode=\"1\"", "android:versionCode=\"${'$'}{gitVersion}\"")
                    println("Writes to " + updatedManifest.get().asFile.getAbsolutePath())
                    updatedManifest.get().asFile.writeText(manifest)
                }
            }
            """
            ScriptingLanguage.Groovy ->
            // language=groovy
            """
            import org.gradle.api.file.RegularFileProperty

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
            """
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
                    val builtArtifacts = builtArtifactsLoader.get().load(apkFolder.get())
                        ?: throw RuntimeException("Cannot load APKs")
                    if (builtArtifacts.elements.size != 1)
                        throw RuntimeException("Expected one APK !")
                    val apk = File(builtArtifacts.elements.single().outputFile).toPath()
                    println("Insert code to verify manifest file in ${'$'}{apk}")
                    println("SUCCESS")
                }
            }
            """
            ScriptingLanguage.Groovy ->
                """

                """.trimIndent()
        }

        fun getDisplayApksTask()=
            when(language) {
                ScriptingLanguage.Kotlin ->
            // language=kotlin
            """
            abstract class DisplayApksTask: DefaultTask() {

                @get:InputFiles
                abstract val apkFolder: DirectoryProperty

                @get:Internal
                abstract val builtArtifactsLoader: Property<BuiltArtifactsLoader>

                @TaskAction
                fun taskAction() {

                    val builtArtifacts = builtArtifactsLoader.get().load(apkFolder.get())
                        ?: throw RuntimeException("Cannot load APKs")
                    builtArtifacts.elements.forEach {
                        println("Got an APK at ${ '$' }{it.outputFile}")
                    }
                }
            }
            """
                ScriptingLanguage.Groovy ->
            // language=groovy
                    """
            import org.gradle.api.DefaultTask
            import org.gradle.api.file.DirectoryProperty
            import org.gradle.api.tasks.InputFiles
            import org.gradle.api.tasks.TaskAction
            import com.android.build.api.artifact.SingleArtifact
            import com.android.build.api.variant.BuiltArtifactsLoader
            import com.android.build.api.variant.BuiltArtifacts
            import org.gradle.api.provider.Property
            import org.gradle.api.tasks.Internal

            abstract class DisplayApksTask extends DefaultTask {

                @InputFiles
                abstract DirectoryProperty getApkFolder()

                @Internal
                abstract Property<BuiltArtifactsLoader> getBuiltArtifactsLoader()

                @TaskAction
                void taskAction() {

                    BuiltArtifacts artifacts = getBuiltArtifactsLoader().get().load(getApkFolder().get())
                    if (artifacts == null) {
                        throw new RuntimeException("Cannot load APKs")
                    }
                    artifacts.elements.forEach {
                        println("Got an APK at ${'$'}{it.outputFile}")
                    }
                }
            }
            """
            }

    fun getAllClassesAccessTask()=
        when(language) {
            ScriptingLanguage.Kotlin ->
                // language=kotlin
                """
            import org.gradle.api.DefaultTask
            import org.gradle.api.file.Directory
            import org.gradle.api.provider.ListProperty
            import org.gradle.api.tasks.InputFiles
            import org.gradle.api.tasks.TaskAction

            abstract class GetAllClassesTask: DefaultTask() {

                @get:InputFiles
                abstract val allClasses: ListProperty<Directory>

                @get:InputFiles
                abstract val allJarsWithClasses: ListProperty<RegularFile>

                @TaskAction
                fun taskAction() {

                    allClasses.get().forEach { directory ->
                        println("Directory : ${'$'}{directory.asFile.absolutePath}")
                        directory.asFile.walk().filter(File::isFile).forEach { file ->
                            println("File : ${'$'}{file.absolutePath}")
                        }
                        allJarsWithClasses.get().forEach { file ->
                            println("JarFile : ${'$'}{file.asFile.absolutePath}")
                        }
                    }
                }
            }
            """
            ScriptingLanguage.Groovy ->
                // language=groovy
                """
            import org.gradle.api.DefaultTask
            import org.gradle.api.file.Directory
            import org.gradle.api.file.RegularFile
            import org.gradle.api.tasks.InputFiles
            import org.gradle.api.tasks.TaskAction
            import org.gradle.api.provider.ListProperty

            abstract class GetAllClassesTask extends DefaultTask {

                @InputFiles
                abstract ListProperty<Directory> getAllClasses()

                @InputFiles
                abstract ListProperty<RegularFile> getAllJarsWithClasses()

                @TaskAction
                void taskAction() {

                    allClasses.get().forEach { directory ->
                        println("Directory : ${'$'}{directory.asFile.absolutePath}")
                        directory.asFile.traverse(type: groovy.io.FileType.FILES) { file ->
                            println("File : ${'$'}{file.absolutePath}")
                        }
                        allJarsWithClasses.get().forEach { file ->
                            println("JarFile : ${'$'}{file.asFile.absolutePath}")
                        }
                    }
                }
            }
            """
        }

    fun getCopyApksTask()=
        when(language) {
            ScriptingLanguage.Groovy ->
                // language=groovy
                """
            import org.gradle.api.DefaultTask
            import org.gradle.api.file.Directory
            import org.gradle.api.file.DirectoryProperty
            import org.gradle.api.file.RegularFileProperty
            import org.gradle.api.provider.Property
            import org.gradle.api.tasks.InputFiles
            import org.gradle.api.tasks.TaskAction
            import org.gradle.workers.WorkerExecutor
            import com.android.build.api.variant.BuiltArtifact
            import com.android.build.api.artifact.ArtifactTransformationRequest
            import org.gradle.api.tasks.Internal

            import java.nio.file.Files

            interface WorkItemParameters extends WorkParameters {
                RegularFileProperty getInputApkFile()
                RegularFileProperty getOutputApkFile()
            }

            abstract class WorkItem implements WorkAction<WorkItemParameters> {

                WorkItemParameters workItemParameters

                @Inject
                WorkItem(WorkItemParameters parameters) {
                   this.workItemParameters = parameters
                }

                void execute() {
                    workItemParameters.getOutputApkFile().get().getAsFile().delete()
                    Files.copy(
                        workItemParameters.getInputApkFile().getAsFile().get().toPath(),
                        workItemParameters.getOutputApkFile().get().getAsFile().toPath())
                }
            }

            abstract class CopyApksTask extends DefaultTask {

                private WorkerExecutor workers

                @Inject
                CopyApksTask(WorkerExecutor workerExecutor) {
                    this.workers = workerExecutor
                }

                @InputFiles
                abstract DirectoryProperty getApkFolder()

                @OutputDirectory
                abstract DirectoryProperty getOutFolder()

                @Internal
                abstract Property<ArtifactTransformationRequest<CopyApksTask>> getTransformationRequest()

                @TaskAction
                void taskAction() {

                     transformationRequest.get().submit(
                         this,
                         workers.noIsolation(),
                         WorkItem, {
                             BuiltArtifact builtArtifact,
                             Directory outputLocation,
                             WorkItemParameters param ->
                                File inputFile = new File(builtArtifact.outputFile)
                                param.getInputApkFile().set(inputFile)
                                param.getOutputApkFile().set(new File(outputLocation.asFile, inputFile.name))
                                param.getOutputApkFile().get().getAsFile()
                         }
                    )
                }
            }
            """
            ScriptingLanguage.Kotlin ->
            // language=kotlin
            """
            interface WorkItemParameters: WorkParameters, Serializable {
                val inputApkFile: RegularFileProperty
                val outputApkFile: RegularFileProperty
            }

            abstract class WorkItem @Inject constructor(private val workItemParameters: WorkItemParameters)
                : WorkAction<WorkItemParameters> {
                override fun execute() {
                    workItemParameters.outputApkFile.get().asFile.delete()
                    workItemParameters.inputApkFile.asFile.get().copyTo(
                        workItemParameters.outputApkFile.get().asFile)
                }
            }
            abstract class CopyApksTask @Inject constructor(private val workers: WorkerExecutor): DefaultTask() {

                @get:InputFiles
                abstract val apkFolder: DirectoryProperty

                @get:OutputDirectory
                abstract val outFolder: DirectoryProperty

                @get:Internal
                abstract val transformationRequest: Property<ArtifactTransformationRequest<CopyApksTask>>

                @TaskAction
                fun taskAction() {

                  transformationRequest.get().submit(
                     this,
                     workers.noIsolation(),
                     WorkItem::class.java) {
                         builtArtifact: BuiltArtifact,
                         outputLocation: Directory,
                         param: WorkItemParameters ->
                            val inputFile = File(builtArtifact.outputFile)
                            param.inputApkFile.set(inputFile)
                            param.outputApkFile.set(File(outputLocation.asFile, inputFile.name))
                            param.outputApkFile.get().asFile
                     }
                }
            }
            """
        }
}
