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
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import java.util.function.BiFunction

class FakeMapProperty<K, V>(
    private val values: MutableMap<K, V>? = null
): MapProperty<K, V> {

    override fun get(): Map<K, V> = values ?: mapOf()

    override fun getOrNull(): Map<K, V>? = values

    override fun getOrElse(p0: Map<K, V>): Map<K, V> = values ?: p0

    override fun <S : Any?> map(p0: Transformer<out S, in MutableMap<K, V>>): Provider<S> {
        TODO("Not yet implemented")
    }

    override fun <S : Any?> flatMap(p0: Transformer<out Provider<out S>, in MutableMap<K, V>>): Provider<S> {
        TODO("Not yet implemented")
    }

    override fun isPresent(): Boolean {
        TODO("Not yet implemented")
    }

    override fun orElse(p0: MutableMap<K, V>): Provider<MutableMap<K, V>> {
        TODO("Not yet implemented")
    }

    override fun orElse(p0: Provider<out MutableMap<K, V>>): Provider<MutableMap<K, V>> {
        TODO("Not yet implemented")
    }

    override fun forUseAtConfigurationTime(): Provider<MutableMap<K, V>> {
        TODO("Not yet implemented")
    }

    override fun <U : Any?, R : Any?> zip(
        p0: Provider<U>,
        p1: BiFunction<in MutableMap<K, V>, in U, out R>
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

    override fun empty(): MapProperty<K, V> {
        values?.clear()
        return this
    }

    override fun getting(p0: K): Provider<V> {
        TODO("Not yet implemented")
    }

    override fun set(p0: MutableMap<out K, out V>?) {
        TODO("Not yet implemented")
    }

    override fun set(p0: Provider<out MutableMap<out K, out V>>) {
        TODO("Not yet implemented")
    }

    override fun value(p0: MutableMap<out K, out V>?): MapProperty<K, V> {
        TODO("Not yet implemented")
    }

    override fun value(p0: Provider<out MutableMap<out K, out V>>): MapProperty<K, V> {
        TODO("Not yet implemented")
    }

    override fun put(p0: K, p1: V) {
        values?.set(p0, p1)
    }

    override fun put(p0: K, p1: Provider<out V>) {
        TODO("Not yet implemented")
    }

    override fun putAll(p0: MutableMap<out K, out V>) {
        p0.entries.forEach {
            values?.put(it.key, it.value)
        }
    }

    override fun putAll(p0: Provider<out MutableMap<out K, out V>>) {
        TODO("Not yet implemented")
    }

    override fun keySet(): Provider<MutableSet<K>> {
        TODO("Not yet implemented")
    }

    override fun convention(p0: MutableMap<out K, out V>?): MapProperty<K, V> {
        TODO("Not yet implemented")
    }

    override fun convention(p0: Provider<out MutableMap<out K, out V>>): MapProperty<K, V> {
        TODO("Not yet implemented")
    }
}
