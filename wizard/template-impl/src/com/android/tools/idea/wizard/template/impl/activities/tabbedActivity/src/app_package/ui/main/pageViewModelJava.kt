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

import com.android.tools.idea.wizard.template.getMaterialComponentName

fun pageViewModelJava(
  packageName: String,
  useAndroidX: Boolean) =

  """package ${packageName}.ui.main;

import ${getMaterialComponentName("android.arch.core.util.Function", useAndroidX)};
import ${getMaterialComponentName("android.arch.lifecycle.LiveData", useAndroidX)};
import ${getMaterialComponentName("android.arch.lifecycle.MutableLiveData", useAndroidX)};
import ${getMaterialComponentName("android.arch.lifecycle.Transformations", useAndroidX)};
import ${getMaterialComponentName("android.arch.lifecycle.ViewModel", useAndroidX)};

public class PageViewModel extends ViewModel {

    private MutableLiveData<Integer> mIndex = new MutableLiveData<>();
    private LiveData<String> mText = Transformations.map(mIndex, new Function<Integer, String>() {
        @Override
        public String apply(Integer input) {
            return "Hello world from section: " + input;
        }
    });

    public void setIndex(int index) {
        mIndex.setValue(index);
    }

    public LiveData<String> getText() {
        return mText;
    }
}"""
