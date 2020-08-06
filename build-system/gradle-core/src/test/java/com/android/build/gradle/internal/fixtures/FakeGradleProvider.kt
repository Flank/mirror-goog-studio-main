/*
 * Copyright (C) 2018 The Android Open Source Project
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
import org.gradle.api.provider.Provider
import java.util.function.BiFunction

class FakeGradleProvider<T>(private val v: (()-> T)?): Provider<T> {

    constructor(v: T): this({v})

    override fun <S : Any?> flatMap(transformer: Transformer<out Provider<out S>, in T>): Provider<S> {
        @Suppress("UNCHECKED_CAST")
        return transformer.transform(v!!.invoke()) as Provider<S>
    }

    override fun isPresent() = v != null

    override fun getOrElse(p0: T) = if (isPresent) orNull else p0

    override fun <S : Any> map(transformer: Transformer<out S, in T>): Provider<S> {
        return FakeGradleProvider { transformer.transform(get()) }
    }

    override fun get() = orNull!!

    override fun getOrNull() = v?.invoke()

    override fun orElse(p0: T): Provider<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun orElse(p0: Provider<out T>): Provider<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun forUseAtConfigurationTime(): Provider<T> {
        TODO("Not yet implemented")
    }

    override fun <B : Any?, R : Any?> zip(p0: Provider<B>, p1: BiFunction<T, B, R>): Provider<R> {
        TODO("Not yet implemented")
    }
}