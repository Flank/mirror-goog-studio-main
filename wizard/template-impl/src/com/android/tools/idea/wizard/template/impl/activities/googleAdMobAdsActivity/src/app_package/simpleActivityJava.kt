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

package com.android.tools.idea.wizard.template.impl.activities.googleAdMobAdsActivity.src.app_package

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.impl.activities.common.findViewById
import com.android.tools.idea.wizard.template.impl.activities.common.importViewBindingClass
import com.android.tools.idea.wizard.template.impl.activities.common.layoutToViewBindingClass
import com.android.tools.idea.wizard.template.impl.fragments.googleAdMobAdsFragment.AdFormat
import com.android.tools.idea.wizard.template.renderIf

fun simpleActivityJava(
  activityClass: String,
  adFormat: AdFormat,
  applicationPackage: String?,
  layoutName: String,
  menuName: String,
  packageName: String,
  superClassFqcn: String,
  isViewBindingSupported: Boolean
): String {
  val importBlock = when (adFormat) {
    AdFormat.Banner -> """
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
    """
    AdFormat.Interstitial -> """
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;
    """
  }

  val interstitialVariablesBlock = renderIf(adFormat == AdFormat.Interstitial) {
    """
    private static final int START_LEVEL = 1;
    private int mLevel;
    private Button mNextLevelButton;
    private InterstitialAd mInterstitialAd;
    private TextView mLevelTextView;
  """
  }

  val onCreateBlock = when (adFormat) {
    AdFormat.Banner -> """
        // Load an ad into the AdMob banner view.
        AdView adView = ${findViewById(
          Language.Java,
          isViewBindingSupported = isViewBindingSupported,
          id = "ad_view")};
        AdRequest adRequest = new AdRequest.Builder()
                .setRequestAgent("android_studio:ad_template").build();
        adView.loadAd(adRequest);
    """
    AdFormat.Interstitial -> """
        // Create the next level button, which tries to show an interstitial when clicked.
        mNextLevelButton = ${findViewById(
          Language.Java,
          isViewBindingSupported = isViewBindingSupported,
          id = "next_level_button")};
        mNextLevelButton.setEnabled(false);
        mNextLevelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showInterstitial();
            }
        });

        // Create the text view to show the level number.
        mLevelTextView = ${findViewById(
          Language.Java,
          isViewBindingSupported = isViewBindingSupported,
          id = "level")};
        mLevel = START_LEVEL;

        // Create the InterstitialAd and set the adUnitId (defined in values/strings.xml).
        mInterstitialAd = newInterstitialAd();
        loadInterstitial();
    """
  }

  val interstitialSpecificBlock = renderIf(adFormat == AdFormat.Interstitial) {
    """
    private InterstitialAd newInterstitialAd() {
        InterstitialAd interstitialAd = new InterstitialAd(this);
        interstitialAd.setAdUnitId(getString(R.string.interstitial_ad_unit_id));
        interstitialAd.setAdListener(new AdListener() {
            @Override
            public void onAdLoaded() {
                mNextLevelButton.setEnabled(true);
            }

            @Override
            public void onAdFailedToLoad(int errorCode) {
                mNextLevelButton.setEnabled(true);
            }

            @Override
            public void onAdClosed() {
                // Proceed to the next level.
                goToNextLevel();
            }
        });
        return interstitialAd;
    }

    private void showInterstitial() {
        // Show the ad if it"s ready. Otherwise toast and reload the ad.
        if (mInterstitialAd != null && mInterstitialAd.isLoaded()) {
            mInterstitialAd.show();
        } else {
            Toast.makeText(this, "Ad did not load", Toast.LENGTH_SHORT).show();
            goToNextLevel();
        }
    }

    private void loadInterstitial() {
        // Disable the next level button and load the ad.
        mNextLevelButton.setEnabled(false);
        AdRequest adRequest = new AdRequest.Builder()
                .setRequestAgent("android_studio:ad_template").build();
        mInterstitialAd.loadAd(adRequest);
    }

    private void goToNextLevel() {
        // Show the next level and reload the ad to prepare for the level after.
        mLevelTextView.setText("Level " + (++mLevel));
        mInterstitialAd = newInterstitialAd();
        loadInterstitial();
    }
"""
  }

  val contentViewBlock = if (isViewBindingSupported) """
     binding = ${layoutToViewBindingClass(layoutName)}.inflate(getLayoutInflater());
     setContentView(binding.getRoot());
  """ else "setContentView(R.layout.$layoutName);"

  return """
package ${packageName};

$importBlock

import android.os.Bundle;
import ${superClassFqcn};
import android.view.Menu;
import android.view.MenuItem;
${renderIf(adFormat == AdFormat.Interstitial) {"""
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
"""}}

import android.widget.Toast;
${importViewBindingClass(isViewBindingSupported, packageName, layoutName, Language.Java)}
${renderIf(applicationPackage != null) { "import ${applicationPackage}.R;" }}

public class ${activityClass} extends AppCompatActivity {
    // Remove the below line after defining your own ad unit ID.
    private static final String TOAST_TEXT = "Test ads are being shown. "
            + "To show live ads, replace the ad unit ID in res/values/strings.xml with your own ad unit ID.";

    $interstitialVariablesBlock
${renderIf(isViewBindingSupported) {"""
    private ${layoutToViewBindingClass(layoutName)} binding;
"""}}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        $contentViewBlock
        $onCreateBlock

        // Toasts the test ad message on the screen. Remove this after defining your own ad unit ID.
        Toast.makeText(this, TOAST_TEXT, Toast.LENGTH_LONG).show();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.${menuName}, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

$interstitialSpecificBlock
}
"""
}
