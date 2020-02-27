package com.android.build.gradle.internal.dependency

import com.android.SdkConstants
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.fixtures.FakeGradleRegularFile
import com.android.build.gradle.internal.fixtures.FakeTransformOutputs
import com.android.testutils.TestInputsGenerator.jarWithEmptyClasses
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files


class LibraryDependencySourcesTransformTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var explodedAar: File

    @Before
    fun setup() {
        explodedAar = temporaryFolder.newFolder("aar")
        addResourceTestDirectoryTo(explodedAar)

        val jarDir = File(explodedAar, SdkConstants.FD_JARS)
        val libDir = File(jarDir, SdkConstants.LIBS_FOLDER)
        jarDir.mkdir()
        libDir.mkdir()
        val jar1 = File(jarDir, SdkConstants.FN_CLASSES_JAR).toPath()
        val jar2 = File(libDir, "lib1.jar").toPath()

        jarWithEmptyClasses(jar1,
                listOf(
                        "com/android/build/gradle/internal/transforms/testdata/CarbonForm",
                        "com/android/build/gradle/internal/transforms/testdata/Animal"
                )
        )

        jarWithEmptyClasses(jar2,
                listOf(
                        "com/android/build/gradle/internal/transforms/testdata/Cat",
                        "com/android/build/gradle/internal/transforms/testdata/ClassWithInnerClass",
                        "com/android/build/gradle/internal/transforms/testdata/ClassWithInnerClass\$TheInnerClass"
                )
        )

        val manifest = File(explodedAar, "AndroidManifest.xml")
        Files.write(
                manifest.toPath(),
                listOf(
                        "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"",
                        "    package=\"com.example.mylibrary\" >",
                        "</manifest>"))
    }

    @Test
    fun `Parse JAR for class files`() {
        val jar1 = FileUtils.join(explodedAar, SdkConstants.FD_JARS, SdkConstants.FN_CLASSES_JAR)
        val jar2 = FileUtils.join(explodedAar, SdkConstants.FD_JARS, SdkConstants.LIBS_FOLDER, "lib1.jar")

        val classesFromJar1 = getClassesInJar(jar1.toPath())
        val classesFromJar2 = getClassesInJar(jar2.toPath())

        assertThat(classesFromJar1).containsExactlyElementsIn(
                listOf(
                        "com/android/build/gradle/internal/transforms/testdata/CarbonForm.class",
                        "com/android/build/gradle/internal/transforms/testdata/Animal.class"
                )
        )

        assertThat(classesFromJar2).containsExactlyElementsIn(
                listOf(
                        "com/android/build/gradle/internal/transforms/testdata/Cat.class",
                        "com/android/build/gradle/internal/transforms/testdata/ClassWithInnerClass.class",
                        "com/android/build/gradle/internal/transforms/testdata/ClassWithInnerClass\$TheInnerClass.class"
                )
        )
    }

    @Test
    fun `Transform produces expected resource symbols, given an exploded AAR with two JAR files`() {
        val transform = getTestTransform(explodedAar)

        val transformOutputs = FakeTransformOutputs(temporaryFolder)

        transform.transform(transformOutputs)

        val outputClassesFile =
                FileUtils.join(transformOutputs.outputDirectory, "classes${SdkConstants.DOT_TXT}")

        assertThat(outputClassesFile.readLines()).containsExactlyElementsIn(
                listOf(
                        "com/android/build/gradle/internal/transforms/testdata/CarbonForm.class",
                        "com/android/build/gradle/internal/transforms/testdata/Animal.class",
                        "com/android/build/gradle/internal/transforms/testdata/Cat.class",
                        "com/android/build/gradle/internal/transforms/testdata/ClassWithInnerClass.class",
                        "com/android/build/gradle/internal/transforms/testdata/ClassWithInnerClass\$TheInnerClass.class"
                )
        )
    }

    @Test
    fun `Transform produces expected classes, given an exploded AAR with resources`() {
        val transform = getTestTransform(explodedAar)

        val transformOutputs = FakeTransformOutputs(temporaryFolder)

        transform.transform(transformOutputs)

        val outputClassesFile = FileUtils.join(transformOutputs.outputDirectory,
                "resources_symbols${SdkConstants.DOT_TXT}")

        assertThat(outputClassesFile.readLines()).containsExactlyElementsIn(
                listOf(
                        "R_DEF: Internal format may change without notice",
                        "",
                        "attr? maybeAttr",
                        "attr myAttr",
                        "attr myAttr2",
                        "layout activity_main",
                        "string app_name",
                        "string desc",
                        "styleable ds android:name android:color myAttr",
                        "styleable ds2 myAttr2 maybeAttr"
                )
        )
    }

    private fun addResourceTestDirectoryTo(parentDirectory: File): File {
        val resDir = File(parentDirectory, SdkConstants.FD_RES)
        val layoutDir = File(resDir, SdkConstants.FD_RES_LAYOUT)
        val valuesDir = File(resDir, SdkConstants.FD_RES_VALUES)
        FileUtils.mkdirs(resDir)
        FileUtils.mkdirs(layoutDir)
        FileUtils.mkdirs(valuesDir)
        // Write test values file to resource directory.
        mapOf(
                "${SdkConstants.FD_RES_LAYOUT}/activity_main.xml" to """
                    <RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
                                    xmlns:app="http://schemas.android.com/apk/res-auto">
                        <ImageView android:src="@drawable/foo"/>
                    </RelativeLayout>""",
                // values files
                "${SdkConstants.FD_RES_VALUES}/values.xml" to """
                    <resources>
                        <string name="app_name">My app</string>
                        <string name="desc">It does something</string>
                    </resources>""",
                "${SdkConstants.FD_RES_VALUES}/style.xml" to """
                    <resources>
                        <attr name="myAttr" format="color" />
                        <declare-styleable name="ds">
                            <attr name="android:name" />
                            <attr name="android:color" />
                            <attr name="myAttr" />
                        </declare-styleable>
                    </resources>""",
                "${SdkConstants.FD_RES_VALUES}/stylesReversed.xml" to """
                    <resources>
                            <declare-styleable name="ds2">
                                <attr name="myAttr2" />
                                <attr name="maybeAttr" />
                            </declare-styleable>
                        <attr name="myAttr2" format="color" />
                        </resources>"""
        ).forEach { (fileName, content) ->
            val resFile = File(resDir, fileName)
            Files.write(resFile.toPath(), content.toByteArray(Charset.defaultCharset()))
        }
        return resDir
    }

    private fun getTestTransform(explodedAarFile: File): LibraryDependencySourcesTransform {
        return object : LibraryDependencySourcesTransform() {
            override val inputArtifact: Provider<FileSystemLocation>
                get() = FakeGradleProvider(FakeGradleRegularFile(explodedAarFile))

            override fun getParameters(): GenericTransformParameters = object :
                    GenericTransformParameters {
                override val projectName: Property<String> = FakeGradleProperty("")
            }
        }
    }
}
