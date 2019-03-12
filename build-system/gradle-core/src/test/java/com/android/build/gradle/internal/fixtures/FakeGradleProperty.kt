/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.fixtures

import org.gradle.api.Transformer
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

class FakeGradleProperty<T>(private var value: T? = null): Property<T> {

    override fun <S : Any?> flatMap(p0: Transformer<out Provider<out S>, in T>): Provider<S> {
        TODO("not yet implemented")
    }

    override fun isPresent() = value != null

    override fun getOrElse(p0: T) = if (isPresent) value else p0

    override fun <S : Any?> map(p0: Transformer<out S, in T>?): Provider<S> {
        TODO("not yet implemented")
    }

    override fun get() = value ?: throw IllegalStateException("Value not set")

    override fun getOrNull() = value

    override fun value(value: T?): Property<T> {
        this.value = value
        return this
    }

    override fun set(value: T?) {
        this.value = value
    }

    override fun set(value: Provider<out T>) {
        TODO("not yet implemented")
    }

    override fun convention(convention: T): Property<T> {
        TODO("not yet implemented")
    }

    override fun convention(convention: Provider<out T>): Property<T> {
        TODO("not yet implemented")
    }

    override fun finalizeValue() {
        TODO("not yet implemented")
    }
}