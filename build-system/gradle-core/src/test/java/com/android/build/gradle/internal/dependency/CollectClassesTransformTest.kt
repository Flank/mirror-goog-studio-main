package com.android.build.gradle.internal.dependency

import com.android.SdkConstants
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.fixtures.FakeGradleRegularFile
import com.android.build.gradle.internal.fixtures.FakeTransformOutputs
import com.android.build.gradle.internal.transforms.testdata.EnumClass
import com.android.build.gradle.internal.transforms.testdata.OuterClass
import com.android.testutils.TestInputsGenerator
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests for [CollectClassesTransform]
 */
internal class CollectClassesTransformTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `extracts all class paths from jar file`() {
        val jarDirectory = temporaryFolder.newFolder()
        val testJar = writeTestJar(jarDirectory,
                listOf(EnumClass::class.java,
                        OuterClass::class.java,
                        OuterClass.InnerClass::class.java
                )
        )

        val transform = getTestTransform(testJar)

        val transformOutputs = FakeTransformOutputs(temporaryFolder)

        transform.transform(transformOutputs)

        assertThat(transformOutputs.outputFile.readLines()).containsExactlyElementsIn(
                listOf(
                        "com/android/build/gradle/internal/transforms/testdata/EnumClass.class",
                        "com/android/build/gradle/internal/transforms/testdata/OuterClass.class",
                        "com/android/build/gradle/internal/transforms/testdata/OuterClass\$InnerClass.class"
                )
        )
    }

    private fun writeTestJar(
            outputFolder: File, classes: List<Class<*>>, name: String = "testJar"
    ) : File {
        val jarFile = File(outputFolder,
                "${name.removeSuffix(SdkConstants.DOT_JAR)}${SdkConstants.DOT_JAR}")
        val jarContents = TestInputsGenerator.jarWithClasses(classes)
        jarFile.writeBytes(jarContents)
        return jarFile
    }

    private fun getTestTransform(jarFile: File): CollectClassesTransform {
        return object : CollectClassesTransform() {
            override val inputJarArtifact: Provider<FileSystemLocation>
                get() = FakeGradleProvider(FakeGradleRegularFile(jarFile))

            override fun getParameters(): GenericTransformParameters = object :
                    GenericTransformParameters {
                override val projectName: Property<String> = FakeGradleProperty("")
            }
        }
    }
}
