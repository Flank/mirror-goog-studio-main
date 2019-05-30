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
package com.android.testutils

import org.mockito.ArgumentMatchers
import org.mockito.Mockito

object MockitoKt {
  /**
   * Wrapper around [Mockito.any] that doesn't return null.
   * If used with Kotlin functions that do not accept nullable types it causes a "must not be null" exception.
   *
   * Using the not-null assertion operator (!!) doesn't work because the result of the method call is recorded internally by Mockito.
   * @see Mockito.any
   */
  fun <T> any(type: Class<T>): T = Mockito.any<T>(type)

  /**
   * Wrapper around [Mockito.eq] that doesn't return null.
   * If used with Kotlin functions that do not accept nullable types it causes a "must not be null" exception.
   *
   * Using the not-null assertion operator (!!) doesn't work because the result of the method call is recorded internally by Mockito.
   * @see Mockito.eq
   */
  fun <T> eq(arg: T): T = Mockito.eq(arg)

  /**
   * Wrapper around [Mockito.refEq] that doesn't return null.
   * If used with Kotlin functions that do not accept nullable types it causes a "must not be null" exception.
   *
   * Using the not-null assertion operator (!!) doesn't work because the result of the method call is recorded internally by Mockito.
   * @see Mockito.refEq
   */
  fun <T> refEq(arg: T): T = ArgumentMatchers.refEq(arg)
}