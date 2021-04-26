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
import com.android.tools.idea.wizard.template.escapeKotlinIdentifier
import com.android.tools.idea.wizard.template.impl.activities.common.findViewById
import com.android.tools.idea.wizard.template.impl.activities.common.importViewBindingClass
import com.android.tools.idea.wizard.template.impl.activities.common.layoutToViewBindingClass
import com.android.tools.idea.wizard.template.impl.fragments.googleAdMobAdsFragment.AdFormat
import com.android.tools.idea.wizard.template.renderIf

fun simpleActivityKt(
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
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
    """
    AdFormat.Interstitial -> """
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import android.widget.Button
import android.widget.TextView
    """
  }

  val interstitialVariablesBlock = renderIf(adFormat == AdFormat.Interstitial) {
    """
    private var currentLevel: Int = 0
    private var interstitialAd: InterstitialAd? = null
    private lateinit var nextLevelButton: Button
    private lateinit var levelTextView: TextView
    private val TAG = "${activityClass.take(23)}"
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
        MobileAds.initialize(
          this
        ) { }
        // Load the InterstitialAd and set the adUnitId (defined in values/strings.xml).
        loadInterstitialAd()

        // Create the next level button, which tries to show an interstitial when clicked.
        nextLevelButton = ${findViewById(
          Language.Kotlin,
          isViewBindingSupported = isViewBindingSupported,
          id = "next_level_button")}
        nextLevelButton.isEnabled = false
        nextLevelButton.setOnClickListener { showInterstitial() }

        levelTextView = ${findViewById(
          Language.Kotlin,
          isViewBindingSupported = isViewBindingSupported,
          id = "level")}
        // Create the text view to show the level number.
        currentLevel = START_LEVEL
    """
  }

  val interstitialSpecificBlock = renderIf(adFormat == AdFormat.Interstitial) {
    """
    private fun loadInterstitialAd() {
      val adRequest = AdRequest.Builder().build()
      InterstitialAd.load(this, getString(R.string.interstitial_ad_unit_id), adRequest,
        object : InterstitialAdLoadCallback() {
          override fun onAdLoaded(ad: InterstitialAd) {
            // The interstitialAd reference will be null until
            // an ad is loaded.
            interstitialAd = ad
            nextLevelButton.setEnabled(true)
            Toast.makeText(this@${activityClass}, "onAdLoaded()", Toast.LENGTH_SHORT)
              .show()
            ad.setFullScreenContentCallback(
              object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                  // Called when fullscreen content is dismissed.
                  // Make sure to set your reference to null so you don't
                  // show it a second time.
                  interstitialAd = null
                  Log.d(TAG, "The ad was dismissed.")
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                  // Called when fullscreen content failed to show.
                  // Make sure to set your reference to null so you don't
                  // show it a second time.
                  interstitialAd = null
                  Log.d(TAG, "The ad failed to show.")
                }

                override fun onAdShowedFullScreenContent() {
                  // Called when fullscreen content is shown.
                  Log.d(TAG, "The ad was shown.")
                }
              })
          }

          override fun onAdFailedToLoad(loadAdError: LoadAdError) {
            // Handle the error
            Log.i(TAG, loadAdError.message)
            interstitialAd = null
            nextLevelButton.setEnabled(true)
            val error: String = String.format(
              Locale.ENGLISH,
              "domain: %s, code: %d, message: %s",
              loadAdError.domain,
              loadAdError.code,
              loadAdError.message
            )
            Toast.makeText(
              this@${activityClass},
              "onAdFailedToLoad() with error: ${'$'}error", Toast.LENGTH_SHORT
            )
              .show()
          }
        })
    }

    private fun showInterstitial() {
        // Show the ad if it"s ready. Otherwise toast and reload the ad.
        if (interstitialAd != null) {
          interstitialAd!!.show(this)
        } else {
            Toast.makeText(this, "Ad did not load", Toast.LENGTH_SHORT).show()
            goToNextLevel()
        }
    }

    private fun goToNextLevel() {
        // Show the next level and reload the ad to prepare for the level after.
        levelTextView.text = "Level " + (++currentLevel)
        if (interstitialAd == null) {
          loadInterstitialAd()
        }
    }
  """
  }

  val contentViewBlock = if (isViewBindingSupported) """
     binding = ${layoutToViewBindingClass(layoutName)}.inflate(layoutInflater)
     setContentView(binding.root)
  """ else "setContentView(R.layout.$layoutName)"

  return """
package ${escapeKotlinIdentifier(packageName)}

$importBlock

import android.os.Bundle
import android.util.Log
import ${superClassFqcn}
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
${importViewBindingClass(isViewBindingSupported, packageName, layoutName, Language.Kotlin)}
${renderIf(applicationPackage != null) { "import ${applicationPackage}.R" }}

import java.util.Locale

// Remove the line below after defining your own ad unit ID.
private const val TOAST_TEXT = "Test ads are being shown. " +
        "To show live ads, replace the ad unit ID in res/values/strings.xml " +
        "with your own ad unit ID."
private const val START_LEVEL = 1

class ${activityClass} : AppCompatActivity() {

    $interstitialVariablesBlock
${renderIf(isViewBindingSupported) {"""
    private lateinit var binding: ${layoutToViewBindingClass(layoutName)}
"""}}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        $contentViewBlock
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
