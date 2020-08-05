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

package com.android.tools.idea.wizard.template.impl.activities.masterDetailFlow.src.app_package

import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.escapeKotlinIdentifier
import com.android.tools.idea.wizard.template.renderIf

fun contentDetailFragmentKt(
  collectionName: String,
  detailName: String,
  applicationPackage: String?,
  detailNameLayout: String,
  objectKind: String,
  packageName: String,
  useAndroidX: Boolean
) = """
package ${escapeKotlinIdentifier(packageName)}

import android.os.Bundle
import ${getMaterialComponentName("android.support.v4.app.Fragment", useAndroidX)}
import ${getMaterialComponentName("android.support.design.widget.CollapsingToolbarLayout", true)}
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
${renderIf(applicationPackage != null) { "import ${applicationPackage}.R" }}
import ${packageName}.placeholder.PlaceholderContent

/**
 * A fragment representing a single ${objectKind} detail screen.
 * This fragment is either contained in a [${collectionName}Fragment]
 * in two-pane mode (on larger screen devices) or self-contained
 * on handsets.
 */
class ${detailName}Fragment : Fragment() {

    /**
     * The placeholder content this fragment is presenting.
     */
    private var item: PlaceholderContent.PlaceholderItem? = null

    lateinit var itemDetailTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            if (it.containsKey(ARG_ITEM_ID)) {
                // Load the placeholder content specified by the fragment
                // arguments. In a real-world scenario, use a Loader
                // to load content from a content provider.
                item = PlaceholderContent.ITEM_MAP[it.getString(ARG_ITEM_ID)]
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_${detailNameLayout}, container, false)

        rootView.findViewById<CollapsingToolbarLayout>(R.id.toolbar_layout)?.title = item?.content

        itemDetailTextView = rootView.findViewById(R.id.${detailNameLayout})
        // Show the placeholder content as text in a TextView.
        item?.let {
            itemDetailTextView.text = it.details
        }

        return rootView
    }

    companion object {
        /**
         * The fragment argument representing the item ID that this fragment
         * represents.
         */
        const val ARG_ITEM_ID = "item_id"
    }
}
"""
