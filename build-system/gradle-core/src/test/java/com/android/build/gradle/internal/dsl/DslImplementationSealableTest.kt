/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl

import com.android.SdkConstants
import com.android.build.api.dsl.extension.AppExtension
import com.android.build.api.dsl.model.TypedValue
import com.android.build.gradle.internal.api.dsl.model.BuildTypeImpl
import com.android.build.gradle.internal.api.dsl.model.BuildTypeOrProductFlavorImpl
import com.android.build.gradle.internal.api.dsl.model.BuildTypeOrVariantImpl
import com.android.build.gradle.internal.api.dsl.model.FallbackStrategyImpl
import com.android.build.gradle.internal.api.dsl.model.TypedValueImpl
import com.android.build.gradle.internal.api.dsl.model.VariantPropertiesImpl
import com.android.build.gradle.internal.api.dsl.options.ExternalNativeBuildOptionsImpl
import com.android.build.gradle.internal.api.dsl.options.JavaCompileOptionsImpl
import com.android.build.gradle.internal.api.dsl.options.NdkOptionsImpl
import com.android.build.gradle.internal.api.dsl.options.PostprocessingFilesOptionsImpl
import com.android.build.gradle.internal.api.dsl.options.PostprocessingOptionsImpl
import com.android.build.gradle.internal.api.dsl.options.ShaderOptionsImpl
import com.android.build.gradle.internal.api.dsl.options.SigningConfigImpl
import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.builder.errors.EvalIssueReporter
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ListMultimap
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import jdk.internal.org.objectweb.asm.Type
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.io.File
import java.net.URL
import java.util.jar.JarFile
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType

class DslImplementationSealableTest {

    val DOT_CLASS_LENTGTH: Int = SdkConstants.DOT_CLASS.length

    @Mock lateinit var issueReporter: EvalIssueReporter
    @Mock lateinit var depecationReporter : DeprecationReporter

    val dslTypes : List<KClass<out Any>> = listOf(
            com.android.build.api.dsl.model.BuildType::class,
            com.android.build.api.dsl.options.SigningConfig::class,
            com.android.build.api.dsl.options.ExternalNativeBuildOptions::class,
            com.android.build.api.dsl.options.JavaCompileOptions::class,
            com.android.build.api.dsl.options.NdkOptions::class,
            com.android.build.api.dsl.options.ShaderOptions::class,
            com.android.build.api.dsl.options.PostprocessingOptions::class)

    val testedTypes: MutableList<KClass<*>> = mutableListOf()

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
    }

    /**
     * Starts at {@link AppExtension} class and use class loader tricks to find all the classes
     * in the parent package. This has to work using Gradle/Bazel or unit testing in the IDE
     * which respectively use jar files or directories to store compiled classes.
     *
     * For each class, it will determine if the dsl API implementation is a correct sealable
     * object.
     */
    @Test
    fun findAllDslInterfaces() {
        val appExtensionResource = Type.getInternalName(AppExtension::class.java) + SdkConstants.DOT_CLASS
        // let's find the resource associated with that class.
        val url = AppExtension::class.java.classLoader.getResource(appExtensionResource)
        assertThat(url).isNotNull()

        val base = url.toString().substring(0, url.toString().length - appExtensionResource.length)
        findAllAPIs(base,
                URL(url.toString().substring(0, url.toString().length -
                        (AppExtension::class.simpleName + SdkConstants.DOT_CLASS).length)))
    }

    /**
     * Find all DSL API classes from a URL representing a directory or a jar file.
     */
    private fun findAllAPIs(base: String, url : URL) {
        when (url.protocol) {
            "file" -> parseDirectory(base.substring("file:".length), File(url.toURI()).parentFile)
            "jar" -> parseJar(url)
            else -> fail(url.protocol + " protocol not handled")
        }
    }

    /**
     * Recursively parse a directory and find all .class files. Invoke {@link #testAPI} on each
     * found file.
     */
    private fun parseDirectory(base: String, file: File) {

        file.listFiles().forEach { it ->
            if (it.isDirectory) {
                parseDirectory(base, it)
            } else {
                if (it.name.endsWith(SdkConstants.DOT_CLASS)) {
                    testAPI(it.absolutePath
                            .substring(base.length, it.absolutePath.length - DOT_CLASS_LENTGTH)
                            .replace(File.separatorChar, '.'))
                }
            }
        }
    }

    /**
     * Parse a jar file and find all .class files located in the DSL public package. Invoke
     * {@link #testAPI} on each found file.
     */
    private fun parseJar(url: URL) {
        val jarFile = JarFile(url.path.substring("file:".length, url.path.indexOf('!')))
        for (entry in jarFile.entries()) {
            if (!entry.isDirectory
                    && entry.name.contains("com/android/build/api/dsl")
                    && entry.name.endsWith(SdkConstants.DOT_CLASS)) {
                testAPI(entry.name
                        .replace('/', '.')
                        .substring(0, entry.name.length - DOT_CLASS_LENTGTH))
            }
        }
    }

    /**
     * Test a DSL API implementation.
     *
     * so far, only loads the class and ensure it is loaded successfully.
     */
    private fun testAPI(className : String) {
        val apiClass = javaClass.classLoader.loadClass(className)
        assertThat(apiClass).isNotNull()
    }

    @Test
    fun realTest() {

        val tester = SealableImplementationTester(
                issueReporter, this::instantiate, this::propertyChecker)

        dslTypes.forEach({it ->
            System.out.println("Testing type ${it.simpleName}")
            testedTypes.add(it)
            tester.checkSealableType(it)
        })

        // check that all visited types are tested.
        tester.visitedTypes.removeAll(testedTypes)
        Truth.assertWithMessage(
                "All visited types should be tested")
                .that(tester.visitedTypes).isEmpty()
    }

    private fun instantiate(type: KType) : Any {
        when(type.classifier) {
            // Basic Types
            Boolean::class -> return java.lang.Boolean.TRUE
            Int::class -> return 12
            String::class -> return "abc"
            Any::class -> return Object()

            // Kotlin Types
            MutableList::class ->
                return MutableList(4) { _ -> instantiate(type.arguments[0].type!!)}
            MutableMap::class ->
                return mutableMapOf(
                        Pair(instantiate(type.arguments[0].type!!),
                                instantiate(type.arguments[1].type!!)))
            MutableSet::class ->
                return mutableSetOf(instantiate(type.arguments[0].type!!))

            // Java Types
            File::class -> return File("/not/real/file")

            // Guava Types
            ListMultimap::class ->
                return ArrayListMultimap.create<Any, Any>(2, 2)

            // DSL types
            TypedValue::class ->
                 return TypedValueImpl("type", "type_name", "type_value")
            com.android.build.api.dsl.options.SigningConfig::class ->
                return SigningConfigImpl("signing", depecationReporter, issueReporter)
            com.android.build.api.dsl.model.BuildType::class ->
                return BuildTypeImpl(
                        "foo",
                        VariantPropertiesImpl(issueReporter),
                        BuildTypeOrProductFlavorImpl(depecationReporter, issueReporter) {
                            PostprocessingFilesOptionsImpl(issueReporter)
                        },
                        BuildTypeOrVariantImpl("buildType",
                                depecationReporter, issueReporter),
                        FallbackStrategyImpl(depecationReporter, issueReporter),
                        depecationReporter,
                        issueReporter)

            com.android.build.api.dsl.options.ExternalNativeBuildOptions::class ->
                    return ExternalNativeBuildOptionsImpl(issueReporter)
            com.android.build.api.dsl.options.JavaCompileOptions::class ->
                    return JavaCompileOptionsImpl(issueReporter)
            com.android.build.api.dsl.options.NdkOptions::class ->
                    return NdkOptionsImpl(issueReporter)
            com.android.build.api.dsl.options.ShaderOptions::class ->
                    return ShaderOptionsImpl(issueReporter)
            com.android.build.api.dsl.options.PostprocessingOptions::class ->
                    return PostprocessingOptionsImpl(issueReporter)
        }
        throw IllegalArgumentException("I don't know how to instantiate $type")
    }

    private fun propertyChecker(property: KProperty<*>) {
        Logger.getAnonymousLogger().log(Level.FINE, "propertyCheck : ${property.name}")
//        Mockito.verify(issueReporter).reportError(Mockito.eq(SyncIssue.TYPE_GENERIC),
//                Mockito.anyString(),
//                Mockito.anyString())
    }
}
