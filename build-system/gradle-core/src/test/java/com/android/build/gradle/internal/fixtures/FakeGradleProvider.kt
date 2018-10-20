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

class FakeGradleProvider<T>(private val v: T?): Provider<T> {
    override fun isPresent() = v != null

    override fun getOrElse(p0: T) = if (isPresent) v else p0

    override fun <S : Any?> map(p0: Transformer<out S, in T>?): Provider<S> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun get() = v!!

    override fun getOrNull() = v
}