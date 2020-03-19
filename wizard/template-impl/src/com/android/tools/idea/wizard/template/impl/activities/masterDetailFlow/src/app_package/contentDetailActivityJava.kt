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

package com.android.tools.idea.wizard.template.impl.activities.masterDetailFlow.src.app_package

import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.renderIf

fun contentDetailActivityJava(
  collectionName: String,
  detailName: String,
  applicationPackage: String?,
  detailNameLayout: String,
  objectKind: String,
  packageName: String,
  useAndroidX: Boolean,
  useMaterial2: Boolean
): String = """
package ${packageName};

import android.content.Intent;
import android.os.Bundle;
import ${getMaterialComponentName("android.support.design.widget.FloatingActionButton", useMaterial2)};
import ${getMaterialComponentName("android.support.design.widget.Snackbar", useMaterial2)};
import ${getMaterialComponentName("android.support.v7.widget.Toolbar", useAndroidX)};
import android.view.View;
import ${getMaterialComponentName("android.support.v7.app.AppCompatActivity", useAndroidX)};
import ${getMaterialComponentName("android.support.v7.app.ActionBar", useAndroidX)};
import android.view.MenuItem;
${renderIf(applicationPackage != null) { "import ${applicationPackage}.R;" }}

/**
* An activity representing a single ${objectKind} detail screen. This
* activity is only used on narrow width devices. On tablet-size devices,
* item details are presented side-by-side with a list of items
* in a {@link ${collectionName}Activity}.
*/
public class ${detailName}Activity extends AppCompatActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.activity_${detailNameLayout});
      Toolbar toolbar = (Toolbar) findViewById(R.id.detail_toolbar);
      setSupportActionBar(toolbar);

      FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
      fab.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View view) {
              Snackbar.make(view, "Replace with your own detail action", Snackbar.LENGTH_LONG)
                      .setAction("Action", null).show();
          }
      });

      // Show the Up button in the action bar.
      ActionBar actionBar = getSupportActionBar();
      if (actionBar != null) {
          actionBar.setDisplayHomeAsUpEnabled(true);
      }

      // savedInstanceState is non-null when there is fragment state
      // saved from previous configurations of this activity
      // (e.g. when rotating the screen from portrait to landscape).
      // In this case, the fragment will automatically be re-added
      // to its container so we don"t need to manually add it.
      // For more information, see the Fragments API guide at:
      //
      // http://developer.android.com/guide/components/fragments.html
      //
      if (savedInstanceState == null) {
          // Create the detail fragment and add it to the activity
          // using a fragment transaction.
          Bundle arguments = new Bundle();
          arguments.putString(${detailName}Fragment.ARG_ITEM_ID,
                  getIntent().getStringExtra(${detailName}Fragment.ARG_ITEM_ID));
          ${detailName}Fragment fragment = new ${detailName}Fragment();
          fragment.setArguments(arguments);
          getSupportFragmentManager().beginTransaction()
                  .add(R.id.${detailNameLayout}_container, fragment)
                  .commit();
      }
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
      int id = item.getItemId();
      if (id == android.R.id.home) {
          // This ID represents the Home or Up button. In the case of this
          // activity, the Up button is shown. For
          // more details, see the Navigation pattern on Android Design:
          //
          // http://developer.android.com/design/patterns/navigation.html#up-vs-back
          //
          navigateUpTo(new Intent(this, ${collectionName}Activity.class));
          return true;
      }
      return super.onOptionsItemSelected(item);
  }
}
"""
