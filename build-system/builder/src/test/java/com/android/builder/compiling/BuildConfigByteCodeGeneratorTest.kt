package com.android.builder.compiling

import com.android.builder.internal.ClassFieldImpl
import com.android.testutils.apk.Zip
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path

internal class BuildConfigByteCodeGeneratorTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `BuildConfig bytecode is correctly generated, given an sample build configuration`() {
        val packageFolder = temporaryFolder.newFolder("my.app.pkg")

        val generator = getSampleByteCodeGenerator(packageFolder.toPath())

        generator.generate()

        val buildConfigBytecodeFile = generator.getBuildConfigFile()

        val urls = arrayOf<URL>(buildConfigBytecodeFile.toURI().toURL())
        val urlClassLoader = URLClassLoader.newInstance(urls)
        val loadedClass = urlClassLoader.loadClass("my.app.pkg.BuildConfig")

        val listOfExpectedFields = listOf(
                ClassFieldImpl("java.lang.String", "APPLICATION_ID", "my.app.pkg"),
                ClassFieldImpl("java.lang.String", "BUILD_TYPE", "debug"),
                ClassFieldImpl("int", "VERSION_CODE", "1"),
                ClassFieldImpl("java.lang.String", "VERSION_NAME", "1.0"),
                ClassFieldImpl("boolean", "DEBUG", "false")
        )
        loadedClass.fields.forEachIndexed { index, field ->
            assertThat(field.type.typeName).isEqualTo(listOfExpectedFields[index].type)
            assertThat(field.name).isEqualTo(listOfExpectedFields[index].name)
            assertThat(field.get(field).toString()).isEqualTo(listOfExpectedFields[index].value)
        }
    }

    @Test
    fun `Check JAR contains expected classes`() {
        val packageFolder = temporaryFolder.newFolder("my.app.pkg")

        val generator = getSampleByteCodeGenerator(packageFolder.toPath())
        generator.generate()
        val bytecodeJar = generator.getBuildConfigFile()

        Zip(bytecodeJar).use {
            assertThat(it.entries).hasSize(1)

            assertThat(it.entries.map { f -> f.toString() })
                    .containsExactly("/my/app/pkg/BuildConfig.class")
        }
    }

    private fun getSampleByteCodeGenerator(packageFolder: Path): BuildConfigByteCodeGenerator =
            BuildConfigByteCodeGenerator.Builder()
                    .setOutputPath(packageFolder)
                    .setBuildConfigPackageName(packageFolder.toFile().name)
                    .setBuildConfigName("BuildConfig")
                    .addStringField("APPLICATION_ID", "my.app.pkg")
                    .addStringField("BUILD_TYPE", "debug")
                    .addIntField("VERSION_CODE", 1)
                    .addStringField("VERSION_NAME", "1.0")
                    .addDebugField("DEBUG", false)
                    .build()
}