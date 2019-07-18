package ${escapeKotlinIdentifiers(packageName)}

import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.InterstitialAd

import ${getMaterialComponentName('android.support.v4.app.Fragment', useAndroidX)}

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import android.content.Context
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
<#if applicationPackage??>
import ${applicationPackage}.R
</#if>

class ${fragmentClass} : Fragment() {

    private var level: Int = 0
    private var nextLevelButton: Button? = null
    private var interstitialAd1: InterstitialAd? = null
    private var levelTextView: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.${layoutName}, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Create the next level button, which tries to show an interstitial when clicked.
        nextLevelButton = view.findViewById(R.id.next_level_button)

        // Create the text view to show the level number.
        levelTextView = view.findViewById(R.id.level)
        level = START_LEVEL
    }

    override fun onDestroy() {
        super.onDestroy()
        nextLevelButton = null
        interstitialAd1 = null
        levelTextView = null
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val appContext = activity?.applicationContext ?: return

        nextLevelButton?.let {
            it.isEnabled = false
            it.setOnClickListener { showInterstitial(appContext) }
            // Create the InterstitialAd and set the adUnitId (defined in values/strings.xml).
        }
        interstitialAd1 = newInterstitialAd(appContext)
        loadInterstitial()
        Toast.makeText(appContext, TOAST_TEXT, Toast.LENGTH_LONG).show()
    }

    private fun newInterstitialAd(context: Context): InterstitialAd {
        val interstitialAd = InterstitialAd(context)
        interstitialAd.adUnitId = getString(R.string.interstitial_ad_unit_id)
        interstitialAd.adListener = object : AdListener() {
            override fun onAdLoaded() {
                nextLevelButton?.isEnabled = true
            }

            override fun onAdFailedToLoad(errorCode: Int) {
                nextLevelButton?.isEnabled = true
            }

            override fun onAdClosed() {
                // Proceed to the next level.
                goToNextLevel(context)
            }
        }
        return interstitialAd
    }

    private fun showInterstitial(context: Context) {
        // Show the ad if it's ready. Otherwise toast and reload the ad.
        val interstitialAd = interstitialAd1 ?: return
        if (interstitialAd.isLoaded) {
            interstitialAd.show()
        } else {
            Toast.makeText(context, "Ad did not load", Toast.LENGTH_SHORT).show()
            goToNextLevel(context)
        }
    }

    private fun loadInterstitial() {
        // Disable the next level button and load the ad.
        nextLevelButton?.isEnabled = false
        val adRequest = AdRequest.Builder()
            .setRequestAgent("android_studio:ad_template").build()
        interstitialAd1?.loadAd(adRequest)
    }

    private fun goToNextLevel(context: Context) {
        // Show the next level and reload the ad to prepare for the level after.
        levelTextView?.text = context.getString(R.string.level_text, ++level)
        interstitialAd1 = newInterstitialAd(context)
        loadInterstitial()
    }

    companion object {
        // Remove the below line after defining your own ad unit ID.
        private const val TOAST_TEXT =
            "Test ads are being shown. " + "To show live ads, replace the ad unit ID in res/values/strings.xml with your own ad unit ID."
        private const val START_LEVEL = 1
    }
}
