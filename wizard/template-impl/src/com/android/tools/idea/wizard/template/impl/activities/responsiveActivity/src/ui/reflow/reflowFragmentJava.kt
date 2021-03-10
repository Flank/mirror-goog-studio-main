/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.wizard.template.impl.activities.responsiveActivity.src.ui.reflow

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.impl.activities.common.importViewBindingClass
import com.android.tools.idea.wizard.template.impl.activities.common.layoutToViewBindingClass

fun reflowFragmentJava(
  packageName: String,
  fragmentClassName: String,
  navFragmentPrefix: String,
  isViewBindingSupported: Boolean
): String {
  val layoutName = "fragment_reflow"
  val bindingName = layoutToViewBindingClass(layoutName)

  return """
package ${packageName}.ui.${navFragmentPrefix};

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

${importViewBindingClass(isViewBindingSupported, packageName, layoutName, Language.Java)}

/**
 * Fragment that demonstrates a responsive layout pattern where the content reflows adaptive to the
 * size of the screen. Specifically images and texts stack vertically in a small screen, but stack
 * horizontally in a large screen.
 */
public class $fragmentClassName extends Fragment {

    private $bindingName binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = ${bindingName}.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
"""
}