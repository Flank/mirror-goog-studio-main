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
package com.android.sdklib.deviceprovisioner

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

sealed interface SetChange<T> {
  class Add<T>(val value: T) : SetChange<T>

  class Remove<T>(val value: T) : SetChange<T>
}

/**
 * Given a Flow<Set<T>>, returns a Flow of Change<T>, identifying elements which have been added or
 * removed from the set.
 */
fun <T> Flow<Set<T>>.trackSetChanges(): Flow<SetChange<T>> = flow {
  var current = emptySet<T>()
  collect { new ->
    (current - new).forEach { emit(SetChange.Remove(it)) }
    (new - current).forEach { emit(SetChange.Add(it)) }
    current = new
  }
}
