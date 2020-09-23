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

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.escapeKotlinIdentifier
import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.impl.activities.common.findViewById
import com.android.tools.idea.wizard.template.impl.activities.common.importViewBindingClass
import com.android.tools.idea.wizard.template.impl.activities.common.layoutToViewBindingClass
import com.android.tools.idea.wizard.template.renderIf

fun firstFragmentJava(
  packageName: String,
  firstFragmentClass: String,
  navFragmentPrefix: String,
  navViewModelClass: String,
  useAndroidX: Boolean,
  isViewBindingSupported: Boolean
): String {
  val viewModelInitializationBlock = if (useAndroidX) "new ViewModelProvider(this).get(${navViewModelClass}.class);"
  else "new ViewModelProvider(this, new ViewModelProvider.NewInstanceFactory()).get(${navViewModelClass}.class);"

  val layoutName = "fragment_${navFragmentPrefix}"
  val onCreateViewBlock = if (isViewBindingSupported) """
    binding = ${layoutToViewBindingClass(layoutName)}.inflate(inflater, container, false);
    View root = binding.getRoot();
  """
  else "View root = inflater.inflate(R.layout.$layoutName, container, false);"

  return """
package ${packageName}.ui.${navFragmentPrefix};

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import ${getMaterialComponentName("android.support.annotation.NonNull", useAndroidX)};
import ${getMaterialComponentName("android.support.annotation.Nullable", useAndroidX)};
import ${getMaterialComponentName("android.support.v4.app.Fragment", useAndroidX)};
import ${getMaterialComponentName("android.arch.lifecycle.Observer", useAndroidX)};
import ${getMaterialComponentName("android.arch.lifecycle.ViewModelProvider", useAndroidX)};
import ${packageName}.R;
${importViewBindingClass(isViewBindingSupported, packageName, layoutName, Language.Java)}

public class ${firstFragmentClass} extends Fragment {

    private ${navViewModelClass} ${navFragmentPrefix}ViewModel;
${renderIf(isViewBindingSupported) {"""
    private ${layoutToViewBindingClass(layoutName)} binding;
"""}}

    public View onCreateView(@NonNull LayoutInflater inflater,
            ViewGroup container, Bundle savedInstanceState) {
        ${navFragmentPrefix}ViewModel =
                $viewModelInitializationBlock
        $onCreateViewBlock
        final TextView textView = ${findViewById(
          Language.Java,
          isViewBindingSupported = isViewBindingSupported,
          id = "text_${navFragmentPrefix}",
          parentView = "root")};
        ${navFragmentPrefix}ViewModel.getText().observe(getViewLifecycleOwner(), new Observer<String>() {
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
}
"""
}
