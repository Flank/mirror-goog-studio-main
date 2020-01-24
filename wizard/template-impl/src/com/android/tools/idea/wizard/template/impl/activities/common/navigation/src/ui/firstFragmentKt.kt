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
package com.android.tools.idea.wizard.template.impl.activities.common.navigation.src.ui

import com.android.tools.idea.wizard.template.escapeKotlinIdentifier
import com.android.tools.idea.wizard.template.getMaterialComponentName


fun firstFragmentKt(
  packageName: String,
  firstFragmentClass: String,
  navFragmentPrefix: String,
  navViewModelClass: String,
  useAndroidX: Boolean
): String {
  val viewModelInitializationBlock = if (useAndroidX) "ViewModelProvider(this).get(${navViewModelClass}::class.java)"
  else "ViewModelProvider(this, ViewModelProvider.NewInstanceFactory()).get(${navViewModelClass}::class.java)"

  return """
package ${packageName}.ui.${navFragmentPrefix}

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import ${getMaterialComponentName("android.support.v4.app.Fragment", useAndroidX)}
import ${getMaterialComponentName("android.arch.lifecycle.Observer", useAndroidX)}
import ${getMaterialComponentName("android.arch.lifecycle.ViewModelProvider", useAndroidX)}
import ${escapeKotlinIdentifier(packageName)}.R

class ${firstFragmentClass} : Fragment() {

  private lateinit var ${navFragmentPrefix}ViewModel: ${navViewModelClass}

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    ${navFragmentPrefix}ViewModel =
            $viewModelInitializationBlock
    val root = inflater.inflate(R.layout.fragment_${navFragmentPrefix}, container, false)
    val textView: TextView = root.findViewById(R.id.text_${navFragmentPrefix})
    ${navFragmentPrefix}ViewModel.text.observe(viewLifecycleOwner, Observer {
      textView.text = it
    })
    return root
  }
}
"""
}