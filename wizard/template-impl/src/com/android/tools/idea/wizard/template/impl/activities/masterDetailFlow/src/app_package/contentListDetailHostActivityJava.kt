/*
 * Copyright (C) 2020 The Android Open Source Project
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

fun contentListDetailHostActivityJava(
  packageName: String,
  collection: String,
  activityLayout: String,
  navHostFragmentId: String,
  useAndroidX: Boolean
) = """
package ${packageName};

import android.os.Bundle;
import ${getMaterialComponentName("android.support.v7.app.AppCompatActivity", useAndroidX)};
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

public class ${collection}DetailHostActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_${activityLayout});

        NavController navController = Navigation.findNavController(this, R.id.${navHostFragmentId});
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.
                Builder(navController.getGraph())
                .build();

        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.${navHostFragmentId});
        return navController.navigateUp() || super.onSupportNavigateUp();
    }
}
  """