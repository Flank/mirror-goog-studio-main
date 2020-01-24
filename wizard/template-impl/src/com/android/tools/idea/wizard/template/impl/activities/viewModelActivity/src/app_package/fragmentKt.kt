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

package com.android.tools.idea.wizard.template.impl.activities.viewModelActivity.src.app_package

import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.escapeKotlinIdentifier

fun fragmentKt(
  fragmentClass: String,
  fragmentLayout: String,
  fragmentPackage: String,
  packageName: String,
  useAndroidX: Boolean,
  viewModelClass: String): String {

  val viewModelInitializationBlock = if (useAndroidX) "ViewModelProvider(this).get(${viewModelClass}::class.java)"
  else "ViewModelProvider(this, ViewModelProvider.NewInstanceFactory()).get(${viewModelClass}::class.java)"

  return """package ${escapeKotlinIdentifier(packageName)}.${escapeKotlinIdentifier(fragmentPackage)}

import ${getMaterialComponentName("android.arch.lifecycle.ViewModelProvider", useAndroidX)}
import android.os.Bundle
import ${getMaterialComponentName("android.support.v4.app.Fragment", useAndroidX)}
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import ${escapeKotlinIdentifier(packageName)}.R

class ${fragmentClass} : Fragment() {

    companion object {
        fun newInstance() = ${fragmentClass}()
    }

    private lateinit var viewModel: ${viewModelClass}

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.${fragmentLayout}, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = $viewModelInitializationBlock
        // TODO: Use the ViewModel
    }

}
"""
}
