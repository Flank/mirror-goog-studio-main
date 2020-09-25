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

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.impl.activities.common.findViewById
import com.android.tools.idea.wizard.template.impl.activities.common.importViewBindingClass
import com.android.tools.idea.wizard.template.impl.activities.common.layoutToViewBindingClass
import com.android.tools.idea.wizard.template.renderIf

fun placeholderFragmentJava(
  fragmentLayoutName: String,
  packageName: String,
  useAndroidX: Boolean,
  isViewBindingSupported: Boolean
): String {

  val viewModelInitializationBlock = if (useAndroidX) {
    "pageViewModel = new ViewModelProvider(this).get(PageViewModel.class);"
  } else {
    """pageViewModel = new ViewModelProvider(this,
               new ViewModelProvider.NewInstanceFactory()).get(PageViewModel.class);"""
  }

  val onCreateViewBlock = if (isViewBindingSupported) """
      binding = ${layoutToViewBindingClass(fragmentLayoutName)}.inflate(inflater, container, false);
      View root = binding.getRoot();
  """ else "View root = inflater.inflate(R.layout.$fragmentLayoutName, container, false);"

  return """package ${packageName}.ui.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import ${getMaterialComponentName("android.support.annotation.Nullable", useAndroidX)};
import ${getMaterialComponentName("android.support.annotation.NonNull", useAndroidX)};
import ${getMaterialComponentName("android.support.v4.app.Fragment", useAndroidX)};
import ${getMaterialComponentName("android.arch.lifecycle.Observer", useAndroidX)};
import ${getMaterialComponentName("android.arch.lifecycle.ViewModelProvider", useAndroidX)};
import ${packageName}.R;
${importViewBindingClass(isViewBindingSupported, packageName, fragmentLayoutName, Language.Java)}

/**
 * A placeholder fragment containing a simple view.
 */
public class PlaceholderFragment extends Fragment {

    private static final String ARG_SECTION_NUMBER = "section_number";

    private PageViewModel pageViewModel;
${renderIf(isViewBindingSupported) {"""
    private ${layoutToViewBindingClass(fragmentLayoutName)} binding;
"""}}

    public static PlaceholderFragment newInstance(int index) {
        PlaceholderFragment fragment = new PlaceholderFragment();
        Bundle bundle = new Bundle();
        bundle.putInt(ARG_SECTION_NUMBER, index);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        $viewModelInitializationBlock
        int index = 1;
        if (getArguments() != null) {
            index = getArguments().getInt(ARG_SECTION_NUMBER);
        }
        pageViewModel.setIndex(index);
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        $onCreateViewBlock
        final TextView textView = ${findViewById(
          Language.Java,
          isViewBindingSupported = isViewBindingSupported,
          id = "section_label",
          parentView = "root")};
        pageViewModel.getText().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                textView.setText(s);
            }
        });
        return root;
    }

${renderIf(isViewBindingSupported) {"""
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
"""}}
}"""
}
