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

package com.android.tools.idea.wizard.template.impl.activities.tabbedActivity.src.app_package.ui.main

import com.android.tools.idea.wizard.template.getMaterialComponentName

fun pageViewModelKt(
  packageName: String,
  useAndroidX: Boolean) =

  """package ${packageName}.ui.main

import ${getMaterialComponentName("android.arch.lifecycle.LiveData", useAndroidX)}
import ${getMaterialComponentName("android.arch.lifecycle.MutableLiveData", useAndroidX)}
import ${getMaterialComponentName("android.arch.lifecycle.Transformations", useAndroidX)}
import ${getMaterialComponentName("android.arch.lifecycle.ViewModel", useAndroidX)}
import ${getMaterialComponentName("android.arch.lifecycle.ViewModelProvider", useAndroidX)}

class PageViewModel : ViewModel() {

    private val _index = MutableLiveData<Int>()
    val text: LiveData<String> = Transformations.map(_index) {
        "Hello world from section: ${"$"}it"
    }

    fun setIndex(index: Int) {
        _index.value = index
    }
}"""
