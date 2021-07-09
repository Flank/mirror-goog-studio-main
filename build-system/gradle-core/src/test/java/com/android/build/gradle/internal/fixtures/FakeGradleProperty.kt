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
import java.util.function.BiFunction

class FakeGradleProperty<T>(private var value: T? = null): Property<T> {

    private var valueProvider: Provider<out T>? = null
    private var convention: T? = null

    override fun <S : Any?> flatMap(p0: Transformer<out Provider<out S>, in T>): Provider<S> {
        TODO("not yet implemented")
    }

    override fun isPresent() = value != null || valueProvider != null

    override fun getOrElse(defaultValue: T) = value ?: valueProvider?.get() ?: convention ?: defaultValue

    override fun <S : Any?> map(p0: Transformer<out S, in T>?): Provider<S> {
        TODO("not yet implemented")
    }

    override fun get() = value ?: valueProvider?.get() ?: convention ?: throw IllegalStateException("Value not set")

    override fun getOrNull() = value ?: valueProvider?.get() ?: convention

    override fun value(value: T?): Property<T> {
        this.value = value
        return this
    }

    override fun set(value: T?) {
        this.value = value
        this.valueProvider = null
    }

    override fun set(provider: Provider<out T>) {
        this.value = null
        this.valueProvider = provider
    }

    override fun convention(convention: T?): Property<T> {
        this.convention = convention
        return this
    }

    override fun convention(convention: Provider<out T>): Property<T> {
        TODO("not yet implemented")
    }

    override fun finalizeValue() {
        TODO("not yet implemented")
    }

    override fun finalizeValueOnRead() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun value(p0: Provider<out T>): Property<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun disallowChanges() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun orElse(p0: T): Provider<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun orElse(p0: Provider<out T>): Provider<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun disallowUnsafeRead() {
        TODO("Not yet implemented")
    }

    override fun forUseAtConfigurationTime(): Provider<T> {
        TODO("Not yet implemented")
    }

    override fun <B : Any?, R : Any?> zip(p0: Provider<B>, p1: BiFunction<T, B, R>): Provider<R> {
        TODO("Not yet implemented")
    }
}
