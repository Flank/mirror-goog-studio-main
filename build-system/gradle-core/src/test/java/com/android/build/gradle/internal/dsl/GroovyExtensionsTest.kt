/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.build.gradle.internal.dsl.decorator.androidPluginDslDecorator
import com.android.utils.usLocaleCapitalize
import com.android.utils.usLocaleDecapitalize
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertWithMessage
import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

/**
 * The AGP DSL decorator automatically generates an Action taking method
 * that triggers Gradle's instantiator to generate a groovy closure so
 * users get 'this' style rather than 'it'.
 *
 * However, for CommonExtension we use Kotlin delegation from
 * the legacy BaseExtension hierarchy of classes, so that
 * generated method isn't on the extension as accessible by the user.
 *
 * Adding it to InternalCommonExtension, makes the Kotlin compiler generate
 * a delegate method, and causes Gradle to generate the appropriate method,
 */
class GroovyExtensionsTest {

    @get:Rule
    val expect: Expect = Expect.create()

    @Test
    fun testCommonExtension() {
        validate(InternalCommonExtension::class.java, CommonExtensionImpl::class.java)
    }

    @Test
    fun testApplicationExtension() {
        validate(InternalApplicationExtension::class.java, ApplicationExtensionImpl::class.java)
    }

    @Test
    fun testLibraryExtension() {
        validate(InternalLibraryExtension::class.java, LibraryExtensionImpl::class.java)
    }

    @Test
    fun testDynamicFeatureExtension() {
        validate(InternalDynamicFeatureExtension::class.java, DynamicFeatureExtension::class.java)
    }

    @Test
    fun testTestExtension() {
        validate(InternalTestExtension::class.java, TestExtensionImpl::class.java)
    }

    private val Type.lowerBound
        get() = when(this) {
            is WildcardType -> lowerBounds.single()
            else -> this
        }

    private val Method.gradleBlockType: Type
        get() {
            val parameter = parameters.single()
            val parameterType = parameter.parameterizedType as ParameterizedType
            return when(parameter.type) {
                Action::class.java -> parameterType.actualTypeArguments[0].lowerBound
                Function1::class.java -> parameterType.actualTypeArguments[0].lowerBound
                else -> throw IllegalArgumentException("Unknown parameter type for method $this")
            }
        }

    // Handle known, acceptable discrepancies for blocks that are in BaseExtension.
    // New blocks should not be added to baseExtension, so the API type can be added.
    // i.e. this mapping should not be expanded
    private val Type.normalizedTypeName: String
        get() = when (typeName) {
            "com.android.build.gradle.internal.CompileOptions" -> "com.android.build.api.dsl.CompileOptions"
            "com.android.build.gradle.internal.coverage.JacocoOptions" -> "com.android.build.api.dsl.JacocoOptions"
            "com.android.build.gradle.internal.dsl.AaptOptions" -> "com.android.build.api.dsl.AaptOptions"
            "com.android.build.gradle.internal.dsl.AdbOptions" -> "com.android.build.api.dsl.AdbOptions"
            "com.android.build.gradle.internal.dsl.BundleOptions" -> "com.android.build.api.dsl.Bundle"
            "com.android.build.gradle.internal.dsl.DataBindingOptions" -> "com.android.build.api.dsl.DataBinding"
            "com.android.build.gradle.internal.dsl.DefaultConfig" -> "DefaultConfigT"
            "com.android.build.gradle.internal.dsl.ExternalNativeBuild" -> "com.android.build.api.dsl.ExternalNativeBuild"
            "com.android.build.gradle.internal.dsl.LintOptions" -> "com.android.build.api.dsl.LintOptions"
            "com.android.build.gradle.internal.dsl.PackagingOptions" -> "com.android.build.api.dsl.PackagingOptions"
            "com.android.build.gradle.internal.dsl.Splits" -> "com.android.build.api.dsl.Splits"
            "com.android.build.gradle.internal.dsl.TestOptions" -> "com.android.build.api.dsl.TestOptions"
            "org.gradle.api.NamedDomainObjectContainer<com.android.build.gradle.api.AndroidSourceSet>" ->
                "org.gradle.api.NamedDomainObjectContainer<? extends com.android.build.api.dsl.AndroidSourceSet>"
            "org.gradle.api.NamedDomainObjectContainer<com.android.build.gradle.internal.dsl.BuildType>" ->
                "org.gradle.api.NamedDomainObjectContainer<BuildTypeT>"
            "org.gradle.api.NamedDomainObjectContainer<com.android.build.gradle.internal.dsl.ProductFlavor>" ->
                "org.gradle.api.NamedDomainObjectContainer<ProductFlavorT>"
            "org.gradle.api.NamedDomainObjectContainer<com.android.build.gradle.internal.dsl.SigningConfig>" ->
                "org.gradle.api.NamedDomainObjectContainer<? extends com.android.build.api.dsl.ApkSigningConfig>"

            else -> typeName
        }

    private fun validate(extensionClass : Class<*>, implClass: Class<*>) {

        val actualOverrides = extensionClass.methods
            .filter { it.parameters.singleOrNull()?.type == Action::class.java }
            .associate { it.name to it.gradleBlockType }

        val requiredOverrides = extensionClass.methods
            .filter { it.parameters.singleOrNull()?.type == Function1::class.java }
            .associate { it.name to it.gradleBlockType }

        assertWithMessage("All blocks defined in the AGP DSL " +
                extensionClass.simpleName.removePrefix("Internal") +
                " need corresponding methods for groovy in " +
                extensionClass.simpleName +
                "\n" +
                "e.g. CommonExtension has\n" +
                "    fun androidResources(action: AndroidResources.() -> Unit)\n" +
                "so internalCommonExtension has\n" +
                "     fun androidResources(action: Action<AndroidResources>)")
            .that(actualOverrides.keys)
            .named("Methods with Action<> parameter for Groovy DSL")
            .containsExactlyElementsIn(requiredOverrides.keys)


        assertWithMessage("All action methods should have the same block type as the block method")
            .that(actualOverrides.mapValues { it.value.normalizedTypeName })
            .named("Map from method name to action receiver type")
            .containsExactlyEntriesIn(requiredOverrides.mapValues { it.value.typeName })

        // Call all the methods to make sure they are implemented
        val instance = androidPluginDslDecorator.decorate(implClass)
        actualOverrides.forEach { (name, blockType) ->
            expect.that(Modifier.isAbstract(instance.getMethod(name, Action::class.java).modifiers))
                .named("Method $name on $implClass is abstract")
                .isFalse()
        }

        val expectedSetters = extensionClass.methods.filter { isCollectionGetter(it) }
            .map {
                val name = it.name.removePrefix("get")
                "set$name(${name.usLocaleDecapitalize()}: ${it.genericReturnType})" }
        val actualSetters = extensionClass.methods.filter { isCollectionSetter(it) }
            .map { val name = it.name.removePrefix("set")
                "set$name(${name.usLocaleDecapitalize()}: ${it.genericParameterTypes[0]})"
             }

        assertWithMessage(
            "All collections defined in the AGP DSL " +
                    extensionClass.simpleName.removePrefix("Internal") +
                    " need corresponding setters for groovy in " +
                    extensionClass.simpleName +
                    "\n" +
                    "e.g. CommonExtension has\n" +
                    "    val flavorDimensions: MutableList<String>\n\n" +
                    "so internalCommonExtension has\n" +
                    "    fun setFlavorDimensions(flavorDimensions: List<String>)\n"
        )
            .that(actualSetters)
            .named("Setters for Groovy DSL")
            .containsExactlyElementsIn(expectedSetters)
    }

    private fun isCollectionGetter(it: Method) =
        it.parameterCount == 0 && it.name.startsWith("get") && isDslCollectionType(it.returnType)

    private fun isCollectionSetter(it: Method) =
        it.parameterCount == 1 && it.name.startsWith("set") && isDslCollectionType(it.parameterTypes[0])

    private fun isDslCollectionType(type: Class<*>) =
        Collection::class.java.isAssignableFrom(type) &&
                !DomainObjectCollection::class.java.isAssignableFrom(type)
}
