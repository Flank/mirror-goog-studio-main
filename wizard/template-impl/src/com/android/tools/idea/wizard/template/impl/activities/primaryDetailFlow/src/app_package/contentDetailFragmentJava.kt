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
import com.android.tools.idea.wizard.template.impl.activities.common.findViewById
import com.android.tools.idea.wizard.template.impl.activities.common.importViewBindingClass
import com.android.tools.idea.wizard.template.impl.activities.common.layoutToViewBindingClass
import com.android.tools.idea.wizard.template.renderIf

fun contentDetailFragmentJava(
  collection: String,
  collectionName: String,
  applicationPackage: String?,
  detailNameLayout: String,
  objectKind: String,
  packageName: String,
  useAndroidX: Boolean,
  isViewBindingSupported: Boolean
): String {

  val layoutName = "fragment_${detailNameLayout}"
  val onCreateViewBlock = if (isViewBindingSupported) """
      binding = ${layoutToViewBindingClass(layoutName)}.inflate(inflater, container, false);
      View rootView = binding.getRoot();
  """ else "View rootView = inflater.inflate(R.layout.fragment_${detailNameLayout}, container, false);"

  return """
package ${packageName};

import android.content.ClipData;
import android.os.Bundle;
import android.view.DragEvent;

import ${getMaterialComponentName("android.support.v4.app.Fragment", useAndroidX)};

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import ${getMaterialComponentName("android.support.design.widget.CollapsingToolbarLayout", useAndroidX)};
${renderIf(applicationPackage != null) { "import ${applicationPackage}.R;" }}
import ${packageName}.placeholder.PlaceholderContent;
${importViewBindingClass(isViewBindingSupported, packageName, layoutName, Language.Java)}

/**
 * A fragment representing a single ${objectKind} detail screen.
 * This fragment is either contained in a {@link ${collectionName}Fragment}
 * in two-pane mode (on larger screen devices) or self-contained
 * on handsets.
 */
public class ${collection}DetailFragment extends Fragment {

    /**
     * The fragment argument representing the item ID that this fragment
     * represents.
     */
    public static final String ARG_ITEM_ID = "item_id";

    /**
     * The placeholder content this fragment is presenting.
     */
    private PlaceholderContent.PlaceholderItem mItem;
    private CollapsingToolbarLayout mToolbarLayout;
    private TextView mTextView;

    private final View.OnDragListener dragListener = (v, event) -> {
        if (event.getAction() == DragEvent.ACTION_DROP) {
            ClipData.Item clipDataItem = event.getClipData().getItemAt(0);
            mItem = PlaceholderContent.ITEM_MAP.get(clipDataItem.getText().toString());
            updateContent();
        }
        return true;
    };

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ${collection}DetailFragment() {
    }

${renderIf(isViewBindingSupported) {"""
    private ${layoutToViewBindingClass(layoutName)} binding;
"""}}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments().containsKey(ARG_ITEM_ID)) {
            // Load the placeholder content specified by the fragment
            // arguments. In a real-world scenario, use a Loader
            // to load content from a content provider.
            mItem = PlaceholderContent.ITEM_MAP.get(getArguments().getString(ARG_ITEM_ID));
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        $onCreateViewBlock
        mToolbarLayout = rootView.findViewById(R.id.toolbar_layout);
        mTextView = ${findViewById(
            Language.Java,
            isViewBindingSupported = isViewBindingSupported,
            id = detailNameLayout)};

        // Show the placeholder content as text in a TextView & in the toolbar if available.
        updateContent();
        rootView.setOnDragListener(dragListener);
        return rootView;
    }
${renderIf(isViewBindingSupported) {"""
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
"""}}

    private void updateContent() {
        if (mItem != null) {
            mTextView.setText(mItem.details);
            if (mToolbarLayout != null) {
                mToolbarLayout.setTitle(mItem.content);
            }
        }
    }
}
"""
}
