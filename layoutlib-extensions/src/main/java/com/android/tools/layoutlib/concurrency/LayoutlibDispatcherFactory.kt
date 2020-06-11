/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.layoutlib.concurrency

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.internal.MainDispatcherFactory
import java.util.concurrent.ExecutorService
import kotlin.coroutines.CoroutineContext

/**
 * A factory to intercept main coroutine dispatcher for user code executed by layoutlib.
 *
 * We would like to control the code execution in layoutlib and do not depend on kotlinx-coroutin-android implementation (which provides
 * [MainDispatcherFactory] in Android). This way we can pass an executor that will execute the code in a desired way (in the thread we want
 * at a time we want),
 */
@InternalCoroutinesApi
internal class LayoutlibDispatcherFactory : MainDispatcherFactory {
  /**
   * We are using the highest priority to override all other [MainDispatcherFactory] implementations.
   */
  override val loadPriority = Int.MAX_VALUE

  override fun createDispatcher(allFactories: List<MainDispatcherFactory>): MainCoroutineDispatcher = mainDispatcher
}

private val mainDispatcher = object : MainCoroutineDispatcher() {
  var executor: ExecutorService? = null
  override val immediate: MainCoroutineDispatcher
    get() = this

  override fun dispatch(context: CoroutineContext, block: Runnable) {
    executor?.asCoroutineDispatcher()?.dispatch(context, block)
  }
}

/**
 * Allows so set any desired executor to execute intercepted user code.
 */
fun setExecutor(executor: ExecutorService) {
  mainDispatcher.executor = executor
}