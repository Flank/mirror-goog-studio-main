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
import com.android.builder.errors.DeprecationReporter
import com.android.builder.errors.EvalIssueReporter
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ListMultimap
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType

class DslImplementationSealableTest {

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
                return BuildTypeImpl("foo",
                        depecationReporter,
                        VariantPropertiesImpl(issueReporter),
                        BuildTypeOrProductFlavorImpl(depecationReporter, issueReporter) {
                            PostprocessingFilesOptionsImpl(issueReporter)
                        },
                        BuildTypeOrVariantImpl("buildType",
                                true,
                                false,
                                false,
                                depecationReporter, issueReporter),
                        FallbackStrategyImpl(depecationReporter, issueReporter),
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
