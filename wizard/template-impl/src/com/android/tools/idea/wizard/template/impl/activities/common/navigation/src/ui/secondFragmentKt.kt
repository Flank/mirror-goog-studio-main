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

import com.android.tools.idea.wizard.template.getMaterialComponentName

fun secondFragmentKt(
  packageName: String,
  firstFragmentClass: String,
  secondFragmentClass: String,
  secondFragmentLayoutName: String,
  navFragmentPrefix: String,
  useAndroidX: Boolean
) = """
package ${packageName}.ui.${navFragmentPrefix}

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView

import ${getMaterialComponentName("android.support.v4.app.Fragment", useAndroidX)}
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs

import ${packageName}.R

class ${secondFragmentClass} : Fragment() {

    private val args: ${secondFragmentClass}Args by navArgs()

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.${secondFragmentLayoutName}, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.textview_${navFragmentPrefix}_second).text =
            getString(R.string.hello_${navFragmentPrefix}_second, args.myArg)

        view.findViewById<Button>(R.id.button_${navFragmentPrefix}_second).setOnClickListener {
            findNavController().navigate(R.id.action_${secondFragmentClass}_to_${firstFragmentClass})
        }
    }
} 
"""