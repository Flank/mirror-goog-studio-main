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

import org.junit.rules.ExternalResource
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.MockSettings
import org.mockito.MockedStatic
import org.mockito.MockedStatic.Verification
import org.mockito.Mockito
import org.mockito.Mockito.withSettings
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.OngoingStubbing
import org.mockito.stubbing.Stubber
import kotlin.reflect.KMutableProperty

object MockitoKt {
  /**
   * Wrapper around [Mockito.any] that doesn't return null.
   * If used with Kotlin functions that do not accept nullable types it causes a "must not be null" exception.
   *
   * Using the not-null assertion operator (!!) doesn't work because the result of the method call is recorded internally by Mockito.
   * @see Mockito.any
   */
  fun <T> any(type: Class<T>): T = Mockito.any(type)

  /**
   * Wrapper around [Mockito.any] for generic types.
   */
  inline fun <reified T> any() = any(T::class.java)

  /**
   * Wrapper around [Mockito.argThat] that doesn't return null.
   * If used with Kotlin functions that do not accept nullable types it causes a "must not be null" exception.
   *
   * Using the not-null assertion operator (!!) doesn't work because the result of the method call is recorded internally by Mockito.
   * @see Mockito.argThat
   */
  fun <T> argThat(arg: (T) -> Boolean): T = Mockito.argThat(arg)

  /**
   * Convenience wrapper around [Mockito.mock] that allows the type to be inferred.
   */
  inline fun <reified T> mock(mockSettings: MockSettings = withSettings()): T = Mockito.mock(T::class.java, mockSettings)

  /**
   * Convenience wrapper around [Mockito.mockStatic] that allows the type to be inferred.
   */
  inline fun <reified T> mockStatic(mockSettings: MockSettings = withSettings()): MockedStatic<T> =
      Mockito.mockStatic(T::class.java, mockSettings)

  /**
   * Convenience wrapper around [InvocationOnMock.getArgument] that allows the type to be inferred.
   */
  inline fun <reified T> InvocationOnMock.getTypedArgument(i: Int): T = getArgument(i, T::class.java)

  /**
   * Convenience wrapper around [ArgumentCaptor] that allows the type to be inferred.
   */
  inline fun <reified T> argumentCaptor(): ArgumentCaptor<T> = ArgumentCaptor.forClass(T::class.java)

  /**
   * Wrapper around [Mockito.eq] that doesn't return null.
   * If used with Kotlin functions that do not accept nullable types it causes a "must not be null" exception.
   *
   * Using the not-null assertion operator (!!) doesn't work because the result of the method call is recorded internally by Mockito.
   * @see Mockito.eq
   */
  fun <T> eq(arg: T): T {
      Mockito.eq(arg)
      return arg
  }

  /**
   * Wrapper around [Mockito.refEq] that doesn't return null.
   * If used with Kotlin functions that do not accept nullable types it causes a "must not be null" exception.
   *
   * Using the not-null assertion operator (!!) doesn't work because the result of the method call is recorded internally by Mockito.
   * @see Mockito.refEq
   */
  fun <T> refEq(arg: T): T = ArgumentMatchers.refEq(arg)

  /**
   * Wrapper around [ArgumentCaptor.capture] that doesn't return null.
   * If used with Kotlin functions that do not accept nullable types it causes a "must not be null" exception.
   *
   * Using the not-null assertion operator (!!) doesn't work because the result of the method call is recorded internally by Mockito.
   * @see ArgumentCaptor.capture
   */
   fun <T> capture(argumentCaptor: ArgumentCaptor<T>): T = argumentCaptor.capture()

  /**
   * Wrapper around [Mockito.same] that doesn't return null.
   * If used with Kotlin functions that do not accept nullable types it causes a "must not be null" exception.
   *
   * Using the not-null assertion operator (!!) doesn't work because the result of the method call is recorded internally by Mockito.
   * @see Mockito.same
   */
   fun <T> same(value: T): T = Mockito.same(value)

   /**
    * Wrapper around [Mockito.when] that isn't called "when", which is a reserved word in Kotlin.
    *
    * @see Mockito.when
    */
   fun <T> whenever(methodCall: T): OngoingStubbing<T> = Mockito.`when`(methodCall)

   /**
    * Wrapper around [Stubber.when] that isn't called "when", which is a reserved word in Kotlin.
    *
    * @See Stubber.when
    */
    fun <T> Stubber.whenever(mock: T): T = `when`(mock)

   /**
    * Wrapper around [MockedStatic.when] that isn't called "when", which is a reserved word in Kotlin.
    *
    * @See MockedStatic.when
    */
    fun <T> MockedStatic<*>.whenever(verification: Verification): OngoingStubbing<T> = `when`(verification)
}

/**
 * Sets the given [property] to [newValue] before the test, and puts the original value back after.
 */
class PropertySetterRule<T: Any>(val newValue: T, val property: KMutableProperty<T>) : ExternalResource() {
    var oldValue: T? = null

    override fun before() {
      oldValue = property.getter.call()
      property.setter.call(newValue)
    }

    override fun after() {
      property.setter.call(oldValue)
      oldValue = null
    }
}
