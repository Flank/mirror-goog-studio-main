/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.adblib.tools.debugging.utils

/**
 * Provides and guarantees access to a [ReferenceCountedResource] resource [value],
 * [decrementing][ReferenceCountedResource.release] its reference count on [close].
 *
 * Usage:
 *
 *     val ref: ReferenceCountedResource<T>
 *     ref.retained().use {
 *        (...)
 *        it.value...
 *        (...)
 *     }
 */
internal class RetainedResource<out T : AutoCloseable>(
  private val ref: ReferenceCountedResource<T>,
  val value: T
) : AutoCloseable {

    override fun close() {
        ref.release()
    }
}

internal suspend fun <T: AutoCloseable> ReferenceCountedResource<T>.retained(): RetainedResource<T> {
    return RetainedResource(this, this.retain())
}
