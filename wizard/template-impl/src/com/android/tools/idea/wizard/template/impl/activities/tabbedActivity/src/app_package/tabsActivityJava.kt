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

package com.android.tools.idea.wizard.template.impl.activities.tabbedActivity.src.app_package

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.impl.activities.common.findViewById
import com.android.tools.idea.wizard.template.impl.activities.common.importViewBindingClass
import com.android.tools.idea.wizard.template.impl.activities.common.layoutToViewBindingClass
import com.android.tools.idea.wizard.template.renderIf

fun tabsActivityJava(
  activityClass: String,
  layoutName: String,
  packageName: String,
  useAndroidX: Boolean,
  isViewBindingSupported: Boolean
): String {

  val contentViewBlock = if (isViewBindingSupported) """
     binding = ${layoutToViewBindingClass(layoutName)}.inflate(getLayoutInflater());
     setContentView(binding.getRoot());
  """ else "setContentView(R.layout.$layoutName);"

  return """package ${packageName};

import android.os.Bundle;
import ${getMaterialComponentName("android.support.design.widget.FloatingActionButton", useAndroidX)};
import ${getMaterialComponentName("android.support.design.widget.Snackbar", useAndroidX)};
import ${getMaterialComponentName("android.support.design.widget.TabLayout", useAndroidX)};
import ${getMaterialComponentName("android.support.v4.view.ViewPager", useAndroidX)};
import ${getMaterialComponentName("android.support.v7.app.AppCompatActivity", useAndroidX)};
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import ${packageName}.ui.main.SectionsPagerAdapter;
${importViewBindingClass(isViewBindingSupported, packageName, layoutName, Language.Java)}

public class ${activityClass} extends AppCompatActivity {

${renderIf(isViewBindingSupported) {"""
    private ${layoutToViewBindingClass(layoutName)} binding;
"""}}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        $contentViewBlock
        SectionsPagerAdapter sectionsPagerAdapter = new SectionsPagerAdapter(this, getSupportFragmentManager());
        ViewPager viewPager = ${findViewById(
          Language.Java,
          isViewBindingSupported = isViewBindingSupported,
          id = "view_pager")};
        viewPager.setAdapter(sectionsPagerAdapter);
        TabLayout tabs = ${findViewById(
          Language.Java,
          isViewBindingSupported = isViewBindingSupported,
          id = "tabs")};
        tabs.setupWithViewPager(viewPager);
        FloatingActionButton fab = ${findViewById(
          Language.Java,
          isViewBindingSupported = isViewBindingSupported,
          id = "fab")};

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
    }
}"""
}
