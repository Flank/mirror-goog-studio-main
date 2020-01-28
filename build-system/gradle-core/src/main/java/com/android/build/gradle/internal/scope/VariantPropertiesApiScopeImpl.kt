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

package com.android.build.gradle.internal.scope

import com.android.build.api.variant.impl.GradleProperty
import com.android.build.gradle.options.BooleanOption
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.io.File
import java.util.concurrent.Callable

class VariantPropertiesApiScopeImpl(
    projectScope: ProjectScope
): BaseScopeImpl(projectScope), VariantPropertiesApiScope {
    // list of properties to lock when [.lockProperties] is called.
    private val properties = mutableListOf<Property<*>>()
    // whether the properties have been locked already
    private var propertiesLockStatus = false

    // flag to know whether to disable memoization of properties that back old API returning the
    // direct value.
    private val disableMemoization = projectScope.projectOptions[BooleanOption.DISABLE_MEMOIZATION]

    override fun <T> propertyOf(type: Class<T>, value: T, id: String): Property<T> {
        return initializeProperty(type, id).also {
            it.set(value)
            it.finalizeValueOnRead()

            // FIXME when Gradle supports this
            // it.preventGet()

            delayedLock(it)
        }
    }

    override fun <T> propertyOf(type: Class<T>, value: Provider<T>, id: String): Property<T> {
        return initializeProperty(type, id).also {
            it.set(value)
            it.finalizeValueOnRead()

            // FIXME when Gradle supports this
            // it.preventGet()

            delayedLock(it)
        }
    }

    override fun <T> propertyOf(type: Class<T>, value: () -> T, id: String): Property<T> {
        return initializeProperty(type, id).also {
            it.set(projectScope.providerFactory.provider(value))
            it.finalizeValueOnRead()

            // FIXME when Gradle supports this
            // it.preventGet()

            delayedLock(it)
        }
    }

    override fun <T> propertyOf(type: Class<T>, value: Callable<T>, id: String): Property<T> {
        return initializeProperty(type, id).also {
            it.set(projectScope.providerFactory.provider(value))
            it.finalizeValueOnRead()

            // FIXME when Gradle supports this
            // it.preventGet()

            delayedLock(it)
        }
    }

    override fun <T> newPropertyBackingDeprecatedApi(type: Class<T>, value: T, id: String): Property<T> {
        return initializeProperty(type, id).also {
            it.set(value)
            if (!disableMemoization) {
                it.finalizeValueOnRead()
            }

            // FIXME when Gradle supports this
            // it.preventGet()

            delayedLock(it)
        }
    }

    override fun <T> newPropertyBackingDeprecatedApi(type: Class<T>, value: Callable<T>, id: String): Property<T> {
        return initializeProperty(type, id).also {
            it.set(projectScope.providerFactory.provider(value))
            if (!disableMemoization) {
                it.finalizeValueOnRead()
            }

            // FIXME when Gradle supports this
            // it.preventGet()

            delayedLock(it)
        }
    }

    override fun <T> providerOf(type: Class<T>, value: Provider<T>, id: String): Provider<T> {
        return initializeProperty(type, id).also {
            it.set(value)
            it.disallowChanges()
            it.finalizeValueOnRead()

            // FIXME when Gradle supports this
            // it.preventGet()
        }
    }

    override fun <T> setProviderOf(type: Class<T>, value: Provider<out Iterable<T>?>): Provider<Set<T>?> {
        return projectScope.objectFactory.setProperty(type).also {
            it.set(value)
            it.disallowChanges()
            it.finalizeValueOnRead()

            // FIXME when Gradle supports this
            // it.preventGet()
        }
    }

    override fun <T> setProviderOf(type: Class<T>, value: Iterable<T>?): Provider<Set<T>?> {
        return projectScope.objectFactory.setProperty(type).also {
            it.set(value)
            it.disallowChanges()
            it.finalizeValueOnRead()

            // FIXME when Gradle supports this
            // it.preventGet()
        }
    }

    override fun file(file: Any): File = projectScope.fileResolver.invoke(file)

    override fun lockProperties() {
        for (property in properties) {
            property.disallowChanges()
        }
        properties.clear()
        propertiesLockStatus = true
    }

    // register a property to be locked later.
    // if the properties have already been locked, the property is locked right away.
    // (this can happen for objects that are lazily created)
    private fun delayedLock(property: Property<*>) {
        if (propertiesLockStatus) {
            property.disallowChanges()
        } else {
            properties.add(property)
        }
    }

    private fun <T> initializeProperty(type: Class<T>, id: String): Property<T> {
        return if (projectOptions[BooleanOption.USE_SAFE_PROPERTIES]) {
            GradleProperty.safeReadingBeforeExecution(
                id,
                projectScope.objectFactory.property(type)
            )
        } else {
            projectScope.objectFactory.property(type)
        }
    }
}