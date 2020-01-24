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


class LibraryClasspathsTransformTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var explodedAar: File

    @Before
    fun setup() {
        explodedAar = temporaryFolder.newFolder("aar")

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
    fun `Transform produces expected output, given an exploded AAR with two JAR files`() {
        val transform = object : LibraryClasspathsTransform() {
            override val inputArtifact: Provider<FileSystemLocation>
                get() = FakeGradleProvider(FakeGradleRegularFile(explodedAar))

            override fun getParameters(): GenericTransformParameters = object :
                    GenericTransformParameters {
                override val projectName: Property<String> = FakeGradleProperty("")
            }
        }

        val transformOutputs = FakeTransformOutputs(temporaryFolder)

        transform.transform(transformOutputs)

        assertThat(transformOutputs.outputFile.readLines()).containsExactlyElementsIn(
                listOf(
                        "com/android/build/gradle/internal/transforms/testdata/Cat.class",
                        "com/android/build/gradle/internal/transforms/testdata/ClassWithInnerClass.class",
                        "com/android/build/gradle/internal/transforms/testdata/ClassWithInnerClass\$TheInnerClass.class",
                        "com/android/build/gradle/internal/transforms/testdata/CarbonForm.class",
                        "com/android/build/gradle/internal/transforms/testdata/Animal.class"
                )
        )
    }
}
