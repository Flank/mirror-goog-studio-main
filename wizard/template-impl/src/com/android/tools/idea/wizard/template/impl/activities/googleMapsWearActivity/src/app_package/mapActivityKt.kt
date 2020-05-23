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

package com.android.tools.idea.wizard.template.impl.activities.googleMapsWearActivity.src.app_package

import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.escapeKotlinIdentifier

fun mapActivityKt(
  activityClass: String,
  layoutName: String,
  packageName: String,
  useAndroidX: Boolean
) = """
package ${escapeKotlinIdentifier(packageName)}

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapFragment
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

import android.os.Bundle
import ${getMaterialComponentName("android.support.wear.widget.SwipeDismissFrameLayout", useAndroidX)}
import android.support.wearable.activity.WearableActivity
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast

class ${activityClass} : WearableActivity(), OnMapReadyCallback {

    /**
     * Map is initialized when it"s fully loaded and ready to be used.
     * See [onMapReady]
     */
    private lateinit var mMap: GoogleMap
    private lateinit var swipeDismissRootContainer: SwipeDismissFrameLayout

    public override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        // Enables always on.
        setAmbientEnabled()

        setContentView(R.layout.${layoutName})

        // Enables the Swipe-To-Dismiss Gesture via the root layout (SwipeDismissFrameLayout).
        // Swipe-To-Dismiss is a standard pattern in Wear for closing an app and needs to be
        // manually enabled for any Google Maps Activity. For more information, review our docs:
        // https://developer.android.com/training/wearables/ui/exit.html
        swipeDismissRootContainer = findViewById(R.id.swipe_dismiss_root_container)
        swipeDismissRootContainer.addCallback(object : SwipeDismissFrameLayout.Callback() {
            override fun onDismissed(layout: SwipeDismissFrameLayout?) {
                // Hides view before exit to avoid stutter.
                layout?.visibility = View.GONE
                finish()
            }
        })

        // Adjusts margins to account for the system window insets when they become available.
        swipeDismissRootContainer.setOnApplyWindowInsetsListener { _, insetsArg ->
            val insets = swipeDismissRootContainer.onApplyWindowInsets(insetsArg)
            val mapContainer = findViewById<FrameLayout>(R.id.map_container)
            val params = mapContainer.layoutParams as FrameLayout.LayoutParams

            // Add Wearable insets to FrameLayout container holding map as margins
            params.setMargins(
                    insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom)
            mapContainer.layoutParams = params

            insets
        }

        // Obtain the MapFragment and set the async listener to be notified when the map is ready.
        @Suppress("Deprecation")
        // Suppressing deprecation since WearableActivity doesn't extend FragmentManager, thus
        // getSupportFragmentManager can't be used
        val mapFragment = fragmentManager.findFragmentById(R.id.map) as MapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        // Map is ready to be used.
        mMap = googleMap

        // Inform user how to close app (Swipe-To-Close).
        val duration = Toast.LENGTH_LONG
        val toast = Toast.makeText(getApplicationContext(), R.string.intro_text, duration)
        toast.setGravity(Gravity.CENTER, 0, 0)
        toast.show()

        // Adds a marker in Sydney, Australia and moves the camera.
        val sydney = LatLng(-34.0, 151.0)
        mMap.addMarker(MarkerOptions().position(sydney).title("Marker in Sydney"))
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney))
    }
}
"""
