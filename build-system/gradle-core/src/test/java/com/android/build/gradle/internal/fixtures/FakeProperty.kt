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

class FakeProperty<T> : Property<T> {

    private var value: T? = null
    private var valueProvider: Provider<out T>? = null
    private var convention: T? = null

    override fun getOrElse(defaultValue: T): T {
        return value ?: valueProvider?.get() ?: convention ?: defaultValue
    }

    override fun disallowChanges() {
    }

    override fun value(p0: T?): Property<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun value(p0: Provider<out T>): Property<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getOrNull(): T? {
        return value ?: valueProvider?.get() ?: convention
    }

    override fun set(value: T?) {
        this.value = value
        this.valueProvider = null
    }

    override fun set(provider: Provider<out T>) {
        this.value = null
        this.valueProvider = provider
    }

    override fun isPresent(): Boolean {
        return value != null || valueProvider != null
    }

    override fun convention(value: T): Property<T> {
        convention = value
        return this
    }

    override fun convention(p0: Provider<out T>): Property<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <S : Any?> map(p0: Transformer<out S, in T>): Provider<S> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun finalizeValue() {
    }

    override fun get(): T {
        return value ?: valueProvider?.get() ?: convention ?: throw RuntimeException("no value")
    }

    override fun <S : Any?> flatMap(p0: Transformer<out Provider<out S>, in T>): Provider<S> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun orElse(p0: T): Provider<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun orElse(p0: Provider<out T>): Provider<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}