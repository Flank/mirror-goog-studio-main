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

package com.android.tools.idea.wizard.template.impl.activities.primaryDetailFlow.src.app_package

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.escapeKotlinIdentifier
import com.android.tools.idea.wizard.template.impl.activities.common.findViewById
import com.android.tools.idea.wizard.template.impl.activities.common.importViewBindingClass
import com.android.tools.idea.wizard.template.impl.activities.common.layoutToViewBindingClass
import com.android.tools.idea.wizard.template.renderIf

fun contentDetailFragmentKt(
  collectionName: String,
  detailName: String,
  applicationPackage: String?,
  detailNameLayout: String,
  objectKind: String,
  packageName: String,
  useAndroidX: Boolean,
  isViewBindingSupported: Boolean
): String {
  val layoutName = "fragment_${detailNameLayout}"
  val onCreateViewBlock = if (isViewBindingSupported) """
      _binding = ${layoutToViewBindingClass(layoutName)}.inflate(inflater, container, false)
      val rootView = binding.root
  """ else "val rootView = inflater.inflate(R.layout.$layoutName, container, false)"

  return """
package ${escapeKotlinIdentifier(packageName)}

import android.content.ClipData
import android.os.Bundle
import android.view.DragEvent
import ${getMaterialComponentName("android.support.v4.app.Fragment", useAndroidX)}
import ${getMaterialComponentName("android.support.design.widget.CollapsingToolbarLayout", true)}
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
${renderIf(applicationPackage != null) { "import ${applicationPackage}.R" }}
import ${packageName}.placeholder.PlaceholderContent
${importViewBindingClass(isViewBindingSupported, packageName, layoutName, Language.Kotlin)}

/**
 * A fragment representing a single $objectKind detail screen.
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
    private var toolbarLayout: CollapsingToolbarLayout? = null

${renderIf(isViewBindingSupported) {"""
    private var _binding: ${layoutToViewBindingClass(layoutName)}? = null
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
"""}}

    private val dragListener = View.OnDragListener { v, event ->
        if (event.action == DragEvent.ACTION_DROP) {
            val clipDataItem: ClipData.Item = event.clipData.getItemAt(0)
            val dragData = clipDataItem.text
            item = PlaceholderContent.ITEM_MAP[dragData]
            updateContent()
        }
        true
    }

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
        $onCreateViewBlock

        toolbarLayout = ${findViewById(
           Language.Kotlin,
           isViewBindingSupported = isViewBindingSupported,
           id = "toolbar_layout",
           parentView = "rootView",
           className = "CollapsingToolbarLayout")}
        itemDetailTextView = ${findViewById(
          Language.Kotlin,
          isViewBindingSupported = isViewBindingSupported,
          id = detailNameLayout,
          parentView = "rootView")}

        updateContent()
        rootView.setOnDragListener(dragListener)

        return rootView
    }

    private fun updateContent() {
        toolbarLayout?.title = item?.content

        // Show the placeholder content as text in a TextView.
        item?.let {
            itemDetailTextView.text = it.details
        }
    }


    companion object {
        /**
         * The fragment argument representing the item ID that this fragment
         * represents.
         */
        const val ARG_ITEM_ID = "item_id"
    }

${renderIf(isViewBindingSupported) {"""
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
"""}}
}
"""
}
