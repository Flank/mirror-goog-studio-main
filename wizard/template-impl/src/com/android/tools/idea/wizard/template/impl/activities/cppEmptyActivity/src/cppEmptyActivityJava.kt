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
package com.android.tools.idea.wizard.template.impl.activities.cppEmptyActivity.src

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.impl.activities.common.findViewById
import com.android.tools.idea.wizard.template.impl.activities.common.importViewBindingClass
import com.android.tools.idea.wizard.template.impl.activities.common.layoutToViewBindingClass
import com.android.tools.idea.wizard.template.renderIf

fun cppEmptyActivityJava(
    packageName: String,
    activityClass: String,
    layoutName: String,
    useAndroidX: Boolean,
    isViewBindingSupported: Boolean,
    libraryName: String
): String {

  val contentViewBlock = if (isViewBindingSupported) """
     binding = ${layoutToViewBindingClass(layoutName)}.inflate(getLayoutInflater());
     setContentView(binding.getRoot());
  """ else "setContentView(R.layout.$layoutName);"

  return """
package ${packageName};

import ${getMaterialComponentName("android.support.v7.app.AppCompatActivity", useAndroidX)};
import android.os.Bundle;
import android.widget.TextView;
${importViewBindingClass(isViewBindingSupported, packageName, layoutName, Language.Java)}

public class ${activityClass} extends AppCompatActivity {

${renderIf(isViewBindingSupported) {"""
    private ${layoutToViewBindingClass(layoutName)} binding;
"""}}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        $contentViewBlock

        // Example of a call to a native method
        TextView tv = ${findViewById(
          Language.Java,
          isViewBindingSupported = isViewBindingSupported,
          id = "sample_text")};
        tv.setText(stringFromJNI());
    }

    /**
     * A native method that is implemented by the '$libraryName' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();

    // Used to load the '$libraryName' library on application startup.
    static {
        System.loadLibrary("$libraryName");
    }
}
"""
}
