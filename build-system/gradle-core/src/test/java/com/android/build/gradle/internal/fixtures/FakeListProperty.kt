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

package com.android.build.gradle.internal.fixtures

import org.gradle.api.Transformer
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import java.util.function.BiFunction

class FakeListProperty<T>(
    val values: List<T>? = null
): ListProperty<T> {

    override fun get(): List<T> = values ?: listOf()


    override fun getOrNull(): List<T>? = values

    override fun getOrElse(p0: List<T>): List<T> = values ?: p0

    override fun <S : Any?> map(p0: Transformer<out S, in List<T>>): Provider<S> {
        return FakeGradleProvider<S> {
            p0.transform(get())
        }
    }

    override fun <S : Any?> flatMap(p0: Transformer<out Provider<out S>, in MutableList<T>>): Provider<S> {
        TODO("Not yet implemented")
    }

    override fun isPresent(): Boolean = true

    override fun orElse(p0: MutableList<T>): Provider<MutableList<T>> {
        TODO("Not yet implemented")
    }

    override fun orElse(p0: Provider<out MutableList<T>>): Provider<MutableList<T>> {
        TODO("Not yet implemented")
    }

    override fun forUseAtConfigurationTime(): Provider<MutableList<T>> {
        TODO("Not yet implemented")
    }

    override fun <B : Any?, R : Any?> zip(
        p0: Provider<B>,
        p1: BiFunction<MutableList<T>, B, R>
    ): Provider<R> {
        TODO("Not yet implemented")
    }

    override fun finalizeValue() {
        TODO("Not yet implemented")
    }

    override fun finalizeValueOnRead() {
        TODO("Not yet implemented")
    }

    override fun disallowChanges() {
        TODO("Not yet implemented")
    }

    override fun disallowUnsafeRead() {
        TODO("Not yet implemented")
    }

    override fun set(p0: MutableIterable<T>?) {
        TODO("Not yet implemented")
    }

    override fun set(p0: Provider<out MutableIterable<T>>) {
        TODO("Not yet implemented")
    }

    override fun value(p0: MutableIterable<T>?): ListProperty<T> {
        TODO("Not yet implemented")
    }

    override fun value(p0: Provider<out MutableIterable<T>>): ListProperty<T> {
        TODO("Not yet implemented")
    }

    override fun empty(): ListProperty<T> {
        TODO("Not yet implemented")
    }

    override fun add(p0: T) {
        TODO("Not yet implemented")
    }

    override fun add(p0: Provider<out T>) {
        TODO("Not yet implemented")
    }

    override fun addAll(vararg p0: T) {
        TODO("Not yet implemented")
    }

    override fun addAll(p0: MutableIterable<T>) {
        TODO("Not yet implemented")
    }

    override fun addAll(p0: Provider<out MutableIterable<T>>) {
        TODO("Not yet implemented")
    }

    override fun convention(p0: MutableIterable<T>?): ListProperty<T> {
        TODO("Not yet implemented")
    }

    override fun convention(p0: Provider<out MutableIterable<T>>): ListProperty<T> {
        TODO("Not yet implemented")
    }
}
