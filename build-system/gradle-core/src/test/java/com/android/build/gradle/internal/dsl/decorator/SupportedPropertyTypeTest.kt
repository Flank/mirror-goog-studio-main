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

package com.android.build.gradle.internal.dsl.decorator

import com.android.build.gradle.internal.dsl.Lockable
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.objectweb.asm.Type
import javax.inject.Inject

/** Test to validate the invariants of the supported property types */
class SupportedPropertyTypeTest {

    @Test
    fun checkValTypes() {
        val objects = SupportedPropertyType.Collection::class.sealedSubclasses.mapNotNull { it.objectInstance }
        objects.forEach { propertyType ->
                // Check type hierarchy
                val type = load(propertyType.type)
                val implementationType = load(propertyType.implementationType)
                assertWithMessage("Implementation type must be a subtype of property type %s", propertyType)
                    .that(implementationType)
                    .isAssignableTo(type)
                assertWithMessage("Implementation type must implement Lockable in property type %s", propertyType)
                    .that(implementationType)
                    .isAssignableTo(Lockable::class.java)

                assertWithMessage("Bridge types should be distinct %s", propertyType)
                    .that(propertyType.bridgeTypes).containsNoDuplicates()

                propertyType.bridgeTypes.forEach { bridgeType ->
                    assertWithMessage("Bridge types should be distinct from property type %s", propertyType)
                        .that(bridgeType).isNotEqualTo(propertyType.type)
                    assertWithMessage("Bridge types must be supertypes of the property type %s", propertyType)
                        .that(type)
                        .isAssignableTo(load(bridgeType))
                }
                // Check constructor of implementation type
                val constructor =
                    implementationType.getDeclaredConstructor(String::class.java)
                assertWithMessage("Constructor is annotated with @Inject")
                    .that(constructor.getAnnotation(Inject::class.java))
                    .isNotNull()
                (constructor.newInstance("testName") as Lockable).lock()
            }
    }

    @Test
    fun checkAgpBlockTypes() {
        AGP_SUPPORTED_PROPERTY_TYPES
            .filterIsInstance<SupportedPropertyType.Block>()
            .forEach { propertyType ->
                val type = load(propertyType.type)
                val implementationType = load(propertyType.implementationType)
                assertWithMessage("Implementation type must be a subtype of property type %s", propertyType)
                    .that(implementationType)
                    .isAssignableTo(type)
                if (implementationType.name != "com.android.build.gradle.internal.CompileOptions") {
                    assertWithMessage("Implementation type is in gradle-core package")
                        .that(implementationType.name)
                        .startsWith("com.android.build.gradle.internal.dsl.")
                }
                // Smoke test that the class decorates without error.
                val decorated = try {
                     androidPluginDslDecorator.decorate(implementationType)
                } catch (e: Exception) {
                    throw AssertionError("Could not decorated supported property type $propertyType", e)
                }
                assertThat(decorated.name).isNotEqualTo(implementationType.name)
            }
    }

    private fun load(type: Type): Class<*> {
        return this::class.java.classLoader.loadClass(type.className)
    }
}
