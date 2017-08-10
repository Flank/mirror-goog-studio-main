package ${escapeKotlinIdentifiers(packageName)}

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapFragment
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

import android.os.Bundle
import android.support.wearable.activity.WearableActivity
import android.widget.FrameLayout

import kotlinx.android.synthetic.main.${layoutName}.*

class ${activityClass} : WearableActivity(), OnMapReadyCallback, GoogleMap.OnMapLongClickListener {

    /**
     * The map. It is initialized when the map has been fully loaded and is ready to be used.
     * See [onMapReady]
     */
    private lateinit var mMap: GoogleMap

    public override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        setAmbientEnabled()

        // Set the layout. It only contains a MapFragment and a DismissOverlay.
        setContentView(R.layout.${layoutName})

        // Retrieve the containers for the root of the layout and the map. Margins will need to be
        // set on them to account for the system window insets.

        // Set the system view insets on the containers when they become available.
        root_container.setOnApplyWindowInsetsListener { _, insetsArg ->
            // Call through to super implementation and apply insets
            val insets = root_container.onApplyWindowInsets(insetsArg)

            val params = map_container.layoutParams as FrameLayout.LayoutParams

            // Add Wearable insets to FrameLayout container holding map as margins
            params.setMargins(
                    insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom)
            map_container.layoutParams = params

            insets
        }

        // Obtain the DismissOverlayView and display the introductory help text.
        dismiss_overlay.setIntroText(R.string.intro_text)
        dismiss_overlay.showIntroIfNecessary()

        // Obtain the MapFragment and set the async listener to be notified when the map is ready.
        val mapFragment = map as MapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        // Map is ready to be used.
        mMap = googleMap

        // Set the long click listener as a way to exit the map.
        mMap.setOnMapLongClickListener(this)

        // Add a marker in Sydney, Australia and move the camera.
        val sydney = LatLng(-34.0, 151.0)
        mMap.addMarker(MarkerOptions().position(sydney).title("Marker in Sydney"))
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney))
    }

    override fun onMapLongClick(latLng: LatLng) {
        // Display the dismiss overlay with a button to exit this activity.
        dismiss_overlay.show()
    }
}
