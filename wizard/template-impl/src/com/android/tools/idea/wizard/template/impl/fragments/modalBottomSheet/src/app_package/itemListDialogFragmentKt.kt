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

package com.android.tools.idea.wizard.template.impl.fragments.modalBottomSheet.src.app_package

import com.android.tools.idea.wizard.template.escapeKotlinIdentifier
import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.renderIf

fun itemListDialogFragmentKt(
  applicationPackage: String?,
  columnCount: Int,
  fragmentClass: String,
  itemLayout: String,
  listLayout: String,
  objectKind: String,
  packageName: String,
  useAndroidX: Boolean,
  useMaterial2: Boolean
): String {
  val layoutManagerImport =
    if (columnCount == 1) "import ${getMaterialComponentName("android.support.v7.widget.LinearLayoutManager", useMaterial2)}"
    else "import ${getMaterialComponentName("android.support.v7.widget.GridLayoutManager", useAndroidX)}"

  val layoutManagerInstantiation =
    if (columnCount == 1) "list.layoutManager = LinearLayoutManager(context)"
    else "list.layoutManager = GridLayoutManager(context, ${columnCount})"

  return """
package ${escapeKotlinIdentifier(packageName)}

import android.os.Bundle
import ${getMaterialComponentName("android.support.design.widget.BottomSheetDialog", useMaterial2)}Fragment
$layoutManagerImport
import ${getMaterialComponentName("android.support.v7.widget.RecyclerView", useAndroidX)}
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
${renderIf(applicationPackage != null) { "import ${applicationPackage}.R" }}
import kotlinx.android.synthetic.main.${listLayout}.*
import kotlinx.android.synthetic.main.${itemLayout}.view.*

// TODO: Customize parameter argument names
const val ARG_ITEM_COUNT = "item_count"

/**
 *
 * A fragment that shows a list of items as a modal bottom sheet.
 *
 * You can show this modal bottom sheet from your activity like this:
 * <pre>
 *    ${fragmentClass}.newInstance(30).show(supportFragmentManager, "dialog")
 * </pre>
 */
class ${fragmentClass} : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.${listLayout}, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        $layoutManagerInstantiation
        list.adapter = arguments?.getInt(ARG_ITEM_COUNT)?.let { ItemAdapter(it) }
    }

    private inner class ViewHolder internal constructor(inflater: LayoutInflater, parent: ViewGroup)
        : RecyclerView.ViewHolder(inflater.inflate(R.layout.${itemLayout}, parent, false)) {

        internal val text: TextView = itemView.text
    }

    private inner class ${objectKind}Adapter internal constructor(private val mItemCount: Int) : RecyclerView.Adapter<ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(parent.context), parent)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.text.text = position.toString()
        }

        override fun getItemCount(): Int {
            return mItemCount
        }
    }

    companion object {

        // TODO: Customize parameters
        fun newInstance(itemCount: Int): ${fragmentClass} =
                ${fragmentClass}().apply {
                    arguments = Bundle().apply {
                        putInt(ARG_ITEM_COUNT, itemCount)
                    }
                }

    }
}
"""
}
