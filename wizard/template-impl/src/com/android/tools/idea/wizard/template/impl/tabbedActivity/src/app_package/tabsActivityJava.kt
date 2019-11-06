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

package com.android.tools.idea.wizard.template.impl.tabbedActivity.src.app_package

import com.android.tools.idea.wizard.template.getMaterialComponentName

fun tabsActivityJava(
  activityClass: String,
  layoutName: String,
  packageName: String,
  useAndroidX: Boolean,
  useMaterial2: Boolean) =

  """package ${packageName};

import android.os.Bundle;
import ${getMaterialComponentName("android.support.design.widget.FloatingActionButton", useMaterial2)};
import ${getMaterialComponentName("android.support.design.widget.Snackbar", useMaterial2)};
import ${getMaterialComponentName("android.support.design.widget.TabLayout", useMaterial2)};
import ${getMaterialComponentName("android.support.v4.view.ViewPager", useAndroidX)};
import ${getMaterialComponentName("android.support.v7.app.AppCompatActivity", useAndroidX)};
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import ${packageName}.ui.main.SectionsPagerAdapter;

public class ${activityClass} extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.${layoutName});
        SectionsPagerAdapter sectionsPagerAdapter = new SectionsPagerAdapter(this, getSupportFragmentManager());
        ViewPager viewPager = findViewById(R.id.view_pager);
        viewPager.setAdapter(sectionsPagerAdapter);
        TabLayout tabs = findViewById(R.id.tabs);
        tabs.setupWithViewPager(viewPager);
        FloatingActionButton fab = findViewById(R.id.fab);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
    }
}"""
