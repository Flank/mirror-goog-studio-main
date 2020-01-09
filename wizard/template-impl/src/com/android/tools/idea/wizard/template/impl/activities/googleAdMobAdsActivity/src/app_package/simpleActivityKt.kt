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

import com.android.tools.idea.wizard.template.escapeKotlinIdentifier
import com.android.tools.idea.wizard.template.impl.fragments.googleAdMobAdsFragment.AdFormat
import com.android.tools.idea.wizard.template.renderIf

fun simpleActivityKt(
  activityClass: String,
  adFormat: AdFormat,
  applicationPackage: String?,
  layoutName: String,
  menuName: String,
  packageName: String,
  superClassFqcn: String
): String {
  val importBlock = when (adFormat) {
    AdFormat.Banner -> """
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
    """
    AdFormat.Interstitial -> """
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.InterstitialAd
    """
  }

  val interstitialVariablesBlock = renderIf(adFormat == AdFormat.Interstitial) {
    """
    private var currentLevel: Int = 0
    private var interstitialAd: InterstitialAd? = null
  """
  }

  val onCreateBlock = when (adFormat) {
    AdFormat.Banner -> """
        // Load an ad into the AdMob banner view.
        val adRequest = AdRequest.Builder()
                .setRequestAgent("android_studio:ad_template")
                .build()
        adView.loadAd(adRequest)
    """
    AdFormat.Interstitial -> """
        // Create the next level button, which tries to show an interstitial when clicked.
        next_level_button.isEnabled = false
        next_level_button.setOnClickListener { showInterstitial() }

        // Create the text view to show the level number.
        currentLevel = START_LEVEL

        // Create the InterstitialAd and set the adUnitId (defined in values/strings.xml).
        interstitialAd = newInterstitialAd()
        loadInterstitial()
    """
  }

  val interstitialSpecificBlock = renderIf(adFormat == AdFormat.Interstitial) {
    """
    private fun newInterstitialAd(): InterstitialAd {
        return InterstitialAd(this).apply {
            adUnitId = getString(R.string.interstitial_ad_unit_id)
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    next_level_button.isEnabled = true
                }

                override fun onAdFailedToLoad(errorCode: Int) {
                    next_level_button.isEnabled = true
                }

                override fun onAdClosed() {
                    // Proceed to the next level.
                    goToNextLevel()
                }
            }
        }
    }

    private fun showInterstitial() {
        // Show the ad if it"s ready. Otherwise toast and reload the ad.
        if (interstitialAd?.isLoaded == true) {
            interstitialAd?.show()
        } else {
            Toast.makeText(this, "Ad did not load", Toast.LENGTH_SHORT).show()
            goToNextLevel()
        }
    }

    private fun loadInterstitial() {
        // Disable the next level button and load the ad.
        next_level_button.isEnabled = false
        val adRequest = AdRequest.Builder()
                .setRequestAgent("android_studio:ad_template")
                .build()
        interstitialAd?.loadAd(adRequest)
    }

    private fun goToNextLevel() {
        // Show the next level and reload the ad to prepare for the level after.
        level.text = "Level " + (++currentLevel)
        interstitialAd = newInterstitialAd()
        loadInterstitial()
    }
  """
  }

  return """
package ${escapeKotlinIdentifier(packageName)}

$importBlock

import android.os.Bundle
import ${superClassFqcn}
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
${renderIf(applicationPackage != null) { "import ${applicationPackage}.R" }}

import kotlinx.android.synthetic.main.${layoutName}.*

// Remove the line below after defining your own ad unit ID.
private const val TOAST_TEXT = "Test ads are being shown. " +
        "To show live ads, replace the ad unit ID in res/values/strings.xml " +
        "with your own ad unit ID."
private const val START_LEVEL = 1

class ${activityClass} : AppCompatActivity() {

$interstitialVariablesBlock

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.${layoutName})

$onCreateBlock

        // Toasts the test ad message on the screen. Remove this after defining your own ad unit ID.
        Toast.makeText(this, TOAST_TEXT, Toast.LENGTH_LONG).show()
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.${menuName}, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) =
            when (item.itemId) {
                R.id.action_settings -> true
                else -> super.onOptionsItemSelected(item)
            }

$interstitialSpecificBlock
}
"""
}
