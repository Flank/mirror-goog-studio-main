package android.com.java.profilertester.taskcategory;

import android.content.Intent;

/**
 * Global request codes for {@link android.app.Activity} and {@link android.content.Intent}. Request
 * codes are params to {@link android.app.Activity#startActivityForResult(Intent, int)}, which are
 * returned in the result for the caller to match up against (hence the need for it to be global).
 * Note these request codes are completely arbitrary, and is app-defined.
 */
public enum ActivityRequestCodes {
    NO_REQUEST_CODE, // Reserved for 0.
    REQUEST_ENABLE_BT, // Request code for enabling Bluetooth.
    ACTION_REQUEST_SCAN_ALWAYS_AVAILABLE, // Request code for making Wifi scanning always available.
    LOCATION, // Request code for enabling location services.
    WRITE_SETTINGS, // Request code to allow writing to system settings.
    CAMERA, // Request code for accessing the camera.
    MICROPHONE, // Request code for accessing the microphone to record sounds.
}
