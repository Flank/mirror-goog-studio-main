/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.lint.checks;

import static com.android.tools.lint.checks.ServiceCastDetector.getExpectedType;

import com.android.tools.lint.detector.api.Detector;

@SuppressWarnings("javadoc")
public class ServiceCastDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new ServiceCastDetector();
    }

    public void testServiceCast() {
        String expected =
                ""
                        + "src/test/pkg/SystemServiceTest.java:13: Error: Suspicious cast to DisplayManager for a DEVICE_POLICY_SERVICE: expected DevicePolicyManager [ServiceCast]\n"
                        + "        DisplayManager displayServiceWrong = (DisplayManager) getSystemService(\n"
                        + "                                             ^\n"
                        + "src/test/pkg/SystemServiceTest.java:16: Error: Suspicious cast to WallpaperService for a WALLPAPER_SERVICE: expected WallpaperManager [ServiceCast]\n"
                        + "        WallpaperService wallPaperWrong = (WallpaperService) getSystemService(WALLPAPER_SERVICE);\n"
                        + "                                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/SystemServiceTest.java:22: Error: Suspicious cast to DisplayManager for a DEVICE_POLICY_SERVICE: expected DevicePolicyManager [ServiceCast]\n"
                        + "        DisplayManager displayServiceWrong = (DisplayManager) context\n"
                        + "                                             ^\n"
                        + "3 errors, 0 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "import android.content.ClipboardManager;\n"
                                        + "import android.app.Activity;\n"
                                        + "import android.app.WallpaperManager;\n"
                                        + "import android.content.Context;\n"
                                        + "import android.hardware.display.DisplayManager;\n"
                                        + "import android.service.wallpaper.WallpaperService;\n"
                                        + "\n"
                                        + "public class SystemServiceTest extends Activity {\n"
                                        + "\n"
                                        + "    public void test1() {\n"
                                        + "        DisplayManager displayServiceOk = (DisplayManager) getSystemService(DISPLAY_SERVICE);\n"
                                        + "        DisplayManager displayServiceWrong = (DisplayManager) getSystemService(\n"
                                        + "                DEVICE_POLICY_SERVICE);\n"
                                        + "        WallpaperManager wallPaperOk = (WallpaperManager) getSystemService(WALLPAPER_SERVICE);\n"
                                        + "        WallpaperService wallPaperWrong = (WallpaperService) getSystemService(WALLPAPER_SERVICE);\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    public void test2(Context context) {\n"
                                        + "        DisplayManager displayServiceOk = (DisplayManager) context\n"
                                        + "                .getSystemService(DISPLAY_SERVICE);\n"
                                        + "        DisplayManager displayServiceWrong = (DisplayManager) context\n"
                                        + "                .getSystemService(DEVICE_POLICY_SERVICE);\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    public void clipboard(Context context) {\n"
                                        + "      ClipboardManager clipboard = (ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);\n"
                                        + "      android.content.ClipboardManager clipboard1 =  (android.content.ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);\n"
                                        + "      android.text.ClipboardManager clipboard2 =  (android.text.ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);\n"
                                        + "    }\n"
                                        + "}\n"))
                .run()
                .expect(expected);
    }

    @SuppressWarnings("ALL") // sample code with warnings
    public void testWifiManagerLookup() {
        String expected =
                ""
                        + "src/test/pkg/WifiManagerTest.java:14: Error: The WIFI_SERVICE must be looked up on the Application context or memory will leak on devices < Android N. Try changing someActivity to someActivity.getApplicationContext() [WifiManagerLeak]\n"
                        + "        someActivity.getSystemService(Context.WIFI_SERVICE); // ERROR: Activity context\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/WifiManagerTest.java:15: Error: The WIFI_SERVICE must be looked up on the Application context or memory will leak on devices < Android N. Try changing someService to someService.getApplicationContext() [WifiManagerLeak]\n"
                        + "        someService.getSystemService(Context.WIFI_SERVICE);  // ERROR: Service context\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/WifiManagerTest.java:16: Error: The WIFI_SERVICE must be looked up on the Application context or memory will leak on devices < Android N. Try changing fragment.getActivity() to fragment.getActivity().getApplicationContext() [WifiManagerLeak]\n"
                        + "        fragment.getActivity().getSystemService(Context.WIFI_SERVICE); // ERROR: Activity context\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/WifiManagerTest.java:17: Error: The WIFI_SERVICE must be looked up on the Application context or memory will leak on devices < Android N. Try changing fragment.getContext() to fragment.getContext().getApplicationContext() [WifiManagerLeak]\n"
                        + "        fragment.getContext().getSystemService(Context.WIFI_SERVICE); // ERROR: FragmentHost context\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/WifiManagerTest.java:29: Error: The WIFI_SERVICE must be looked up on the Application context or memory will leak on devices < Android N. Try changing context to context.getApplicationContext() [WifiManagerLeak]\n"
                        + "        context.getSystemService(Context.WIFI_SERVICE); // ERROR\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/WifiManagerTest.java:34: Error: The WIFI_SERVICE must be looked up on the Application context or memory will leak on devices < Android N. Try changing mActivity to mActivity.getApplicationContext() [WifiManagerLeak]\n"
                        + "        mActivity.getSystemService(Context.WIFI_SERVICE); // ERROR: activity service\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/WifiManagerTest.java:53: Error: The WIFI_SERVICE must be looked up on the Application context or memory will leak on devices < Android N. Try changing getSystemService to getApplicationContext().getSystemService [WifiManagerLeak]\n"
                        + "            getSystemService(WIFI_SERVICE); // ERROR: Activity context\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/WifiManagerTest.java:54: Error: The WIFI_SERVICE must be looked up on the Application context or memory will leak on devices < Android N. Try changing this to this.getApplicationContext() [WifiManagerLeak]\n"
                        + "            this.getSystemService(Context.WIFI_SERVICE); // ERROR: Activity context\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/WifiManagerTest.java:66: Error: The WIFI_SERVICE must be looked up on the Application context or memory will leak on devices < Android N. Try changing getSystemService to getApplicationContext().getSystemService [WifiManagerLeak]\n"
                        + "            getSystemService(WIFI_SERVICE); // ERROR: Service context\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/WifiManagerTest.java:76: Error: The WIFI_SERVICE must be looked up on the Application context or memory will leak on devices < Android N. Try changing getContext() to getContext().getApplicationContext() [WifiManagerLeak]\n"
                        + "            getContext().getSystemService(Context.WIFI_SERVICE); // ERROR: View context\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/WifiManagerTest.java:32: Warning: The WIFI_SERVICE must be looked up on the Application context or memory will leak on devices < Android N. Try changing foreignContext to foreignContext.getApplicationContext() [WifiManagerPotentialLeak]\n"
                        + "        foreignContext.getSystemService(Context.WIFI_SERVICE); // UNKNOWN\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/WifiManagerTest.java:33: Warning: The WIFI_SERVICE must be looked up on the Application context or memory will leak on devices < Android N. Try changing mContext to mContext.getApplicationContext() [WifiManagerPotentialLeak]\n"
                        + "        mContext.getSystemService(Context.WIFI_SERVICE); // UNKNOWN\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/WifiManagerTest.java:41: Warning: The WIFI_SERVICE must be looked up on the Application context or memory will leak on devices < Android N. Try changing ctx to ctx.getApplicationContext() [WifiManagerPotentialLeak]\n"
                        + "        ctx.getSystemService(Context.WIFI_SERVICE); // UNKNOWN (though likely)\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "10 errors, 3 warnings\n";

        //noinspection all // Sample code
        lint().files(
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.app.Activity;\n"
                                        + "import android.app.Application;\n"
                                        + "import android.app.Fragment;\n"
                                        + "import android.app.Service;\n"
                                        + "import android.content.Context;\n"
                                        + "import android.preference.PreferenceActivity;\n"
                                        + "import android.widget.Button;\n"
                                        + "\n"
                                        + "@SuppressWarnings(\"unused\")\n"
                                        + "public class WifiManagerTest {\n"
                                        + "    public void testErrors(PreferenceActivity someActivity, Service someService, Fragment fragment) {\n"
                                        + "        someActivity.getSystemService(Context.WIFI_SERVICE); // ERROR: Activity context\n"
                                        + "        someService.getSystemService(Context.WIFI_SERVICE);  // ERROR: Service context\n"
                                        + "        fragment.getActivity().getSystemService(Context.WIFI_SERVICE); // ERROR: Activity context\n"
                                        + "        fragment.getContext().getSystemService(Context.WIFI_SERVICE); // ERROR: FragmentHost context\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    private Context mContext;\n"
                                        + "    private Application mApplication;\n"
                                        + "    private Activity mActivity;\n"
                                        + "\n"
                                        + "    public void testFlow(Activity activity, Context foreignContext) {\n"
                                        + "        @SuppressWarnings(\"UnnecessaryLocalVariable\")\n"
                                        + "        Context c2;\n"
                                        + "        c2 = activity;\n"
                                        + "        Context context = c2;\n"
                                        + "        context.getSystemService(Context.WIFI_SERVICE); // ERROR\n"
                                        + "\n"
                                        + "        // Consider calling foreignContext.getApplicationContext() here\n"
                                        + "        foreignContext.getSystemService(Context.WIFI_SERVICE); // UNKNOWN\n"
                                        + "        mContext.getSystemService(Context.WIFI_SERVICE); // UNKNOWN\n"
                                        + "        mActivity.getSystemService(Context.WIFI_SERVICE); // ERROR: activity service\n"
                                        + "        mApplication.getSystemService(Context.WIFI_SERVICE); // OK\n"
                                        + "        activity.getApplicationContext().getSystemService(Context.WIFI_SERVICE); // OK\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    public void test(Context ctx) {\n"
                                        + "        mContext = ctx.getApplicationContext();\n"
                                        // Here we *could* determine that ctx is most likely NOT an
                                        // application context since
                                        // we're calling getApplicationContext on it above
                                        + "        ctx.getSystemService(Context.WIFI_SERVICE); // UNKNOWN (though likely)\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    public void testOk(Application application) {\n"
                                        + "        application.getSystemService(Context.WIFI_SERVICE); // OK\n"
                                        + "\n"
                                        + "        Context applicationContext = application.getApplicationContext();\n"
                                        + "        applicationContext.getSystemService(Context.WIFI_SERVICE); // OK\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    public static class MyActivity extends Activity {\n"
                                        + "        public void test() {\n"
                                        + "            getSystemService(WIFI_SERVICE); // ERROR: Activity context\n"
                                        + "            this.getSystemService(Context.WIFI_SERVICE); // ERROR: Activity context\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    public abstract static class MyApplication extends Application {\n"
                                        + "        public void test() {\n"
                                        + "            getSystemService(WIFI_SERVICE); // OK: Application context\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    public abstract static class MyService extends Service {\n"
                                        + "        public void test() {\n"
                                        + "            getSystemService(WIFI_SERVICE); // ERROR: Service context\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    public abstract class MyCustomView extends Button {\n"
                                        + "        public MyCustomView(Context context) {\n"
                                        + "            super(context);\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        public void test() {\n"
                                        + "            getContext().getSystemService(Context.WIFI_SERVICE); // ERROR: View context\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "}\n"))
                .run()
                .expect(expected)
                .expectFixDiffs(
                        ""
                                + "Fix for src/test/pkg/WifiManagerTest.java line 13: Add getApplicationContext():\n"
                                + "@@ -14 +14\n"
                                + "-         someActivity.getSystemService(Context.WIFI_SERVICE); // ERROR: Activity context\n"
                                + "+         someActivity.getApplicationContext().getSystemService(Context.WIFI_SERVICE); // ERROR: Activity context\n"
                                + "Fix for src/test/pkg/WifiManagerTest.java line 14: Add getApplicationContext():\n"
                                + "@@ -15 +15\n"
                                + "-         someService.getSystemService(Context.WIFI_SERVICE);  // ERROR: Service context\n"
                                + "+         someService.getApplicationContext().getSystemService(Context.WIFI_SERVICE);  // ERROR: Service context\n"
                                + "Fix for src/test/pkg/WifiManagerTest.java line 15: Add getApplicationContext():\n"
                                + "@@ -16 +16\n"
                                + "-         fragment.getActivity().getSystemService(Context.WIFI_SERVICE); // ERROR: Activity context\n"
                                + "+         fragment.getActivity().getApplicationContext().getSystemService(Context.WIFI_SERVICE); // ERROR: Activity context\n"
                                + "Fix for src/test/pkg/WifiManagerTest.java line 16: Add getApplicationContext():\n"
                                + "@@ -17 +17\n"
                                + "-         fragment.getContext().getSystemService(Context.WIFI_SERVICE); // ERROR: FragmentHost context\n"
                                + "+         fragment.getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE); // ERROR: FragmentHost context\n"
                                + "Fix for src/test/pkg/WifiManagerTest.java line 28: Add getApplicationContext():\n"
                                + "@@ -29 +29\n"
                                + "-         context.getSystemService(Context.WIFI_SERVICE); // ERROR\n"
                                + "+         context.getApplicationContext().getSystemService(Context.WIFI_SERVICE); // ERROR\n"
                                + "Fix for src/test/pkg/WifiManagerTest.java line 33: Add getApplicationContext():\n"
                                + "@@ -34 +34\n"
                                + "-         mActivity.getSystemService(Context.WIFI_SERVICE); // ERROR: activity service\n"
                                + "+         mActivity.getApplicationContext().getSystemService(Context.WIFI_SERVICE); // ERROR: activity service\n"
                                + "Fix for src/test/pkg/WifiManagerTest.java line 52: Add getApplicationContext():\n"
                                + "@@ -53 +53\n"
                                + "-             getSystemService(WIFI_SERVICE); // ERROR: Activity context\n"
                                + "+             getApplicationContext().getSystemService(WIFI_SERVICE); // ERROR: Activity context\n"
                                + "Fix for src/test/pkg/WifiManagerTest.java line 53: Add getApplicationContext():\n"
                                + "@@ -54 +54\n"
                                + "-             this.getSystemService(Context.WIFI_SERVICE); // ERROR: Activity context\n"
                                + "+             this.getApplicationContext().getSystemService(Context.WIFI_SERVICE); // ERROR: Activity context\n"
                                + "Fix for src/test/pkg/WifiManagerTest.java line 65: Add getApplicationContext():\n"
                                + "@@ -66 +66\n"
                                + "-             getSystemService(WIFI_SERVICE); // ERROR: Service context\n"
                                + "+             getApplicationContext().getSystemService(WIFI_SERVICE); // ERROR: Service context\n"
                                + "Fix for src/test/pkg/WifiManagerTest.java line 75: Add getApplicationContext():\n"
                                + "@@ -76 +76\n"
                                + "-             getContext().getSystemService(Context.WIFI_SERVICE); // ERROR: View context\n"
                                + "+             getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE); // ERROR: View context\n"
                                + "Fix for src/test/pkg/WifiManagerTest.java line 31: Add getApplicationContext():\n"
                                + "@@ -32 +32\n"
                                + "-         foreignContext.getSystemService(Context.WIFI_SERVICE); // UNKNOWN\n"
                                + "+         foreignContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE); // UNKNOWN\n"
                                + "Fix for src/test/pkg/WifiManagerTest.java line 32: Add getApplicationContext():\n"
                                + "@@ -33 +33\n"
                                + "-         mContext.getSystemService(Context.WIFI_SERVICE); // UNKNOWN\n"
                                + "+         mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE); // UNKNOWN\n"
                                + "Fix for src/test/pkg/WifiManagerTest.java line 40: Add getApplicationContext():\n"
                                + "@@ -41 +41\n"
                                + "-         ctx.getSystemService(Context.WIFI_SERVICE); // UNKNOWN (though likely)\n"
                                + "+         ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE); // UNKNOWN (though likely)\n");
    }

    public void testWifiManagerLookupOnNougat() {
        //noinspection all // Sample code
        lint().files(
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.app.Activity;\n"
                                        + "import android.content.Context;\n"
                                        + "import android.preference.PreferenceActivity;\n"
                                        + "\n"
                                        + "public class WifiManagerTest {\n"
                                        + "    public void testErrors(PreferenceActivity someActivity) {\n"
                                        + "        someActivity.getSystemService(Context.WIFI_SERVICE); // ERROR: Activity context\n"
                                        + "    }\n"
                                        + "}\n"),
                        // Android N:
                        manifest().minSdk(24))
                .run()
                .expectClean();
    }

    public void testLookup() {
        assertEquals(
                "android.view.accessibility.AccessibilityManager",
                getExpectedType("ACCESSIBILITY_SERVICE"));
        assertEquals("android.accounts.AccountManager", getExpectedType("ACCOUNT_SERVICE"));
        assertEquals("android.app.ActivityManager", getExpectedType("ACTIVITY_SERVICE"));
        assertEquals("android.app.AlarmManager", getExpectedType("ALARM_SERVICE"));
        assertEquals("android.appwidget.AppWidgetManager", getExpectedType("APPWIDGET_SERVICE"));
        assertEquals("android.app.AppOpsManager", getExpectedType("APP_OPS_SERVICE"));
        assertEquals("android.media.AudioManager", getExpectedType("AUDIO_SERVICE"));
        assertEquals("android.os.BatteryManager", getExpectedType("BATTERY_SERVICE"));
        assertEquals("android.bluetooth.BluetoothManager", getExpectedType("BLUETOOTH_SERVICE"));
        assertEquals("android.hardware.camera2.CameraManager", getExpectedType("CAMERA_SERVICE"));
        assertEquals(
                "android.view.accessibility.CaptioningManager",
                getExpectedType("CAPTIONING_SERVICE"));
        assertEquals(
                "android.telephony.CarrierConfigManager",
                getExpectedType("CARRIER_CONFIG_SERVICE"));
        assertEquals("android.text.ClipboardManager", getExpectedType("CLIPBOARD_SERVICE"));
        assertEquals(
                "android.companion.CompanionDeviceManager",
                getExpectedType("COMPANION_DEVICE_SERVICE"));
        assertEquals("android.net.ConnectivityManager", getExpectedType("CONNECTIVITY_SERVICE"));
        assertEquals("android.hardware.ConsumerIrManager", getExpectedType("CONSUMER_IR_SERVICE"));
        assertEquals("android.telephony.euicc.EuiccManager", getExpectedType("EUICC_SERVICE"));
        assertEquals(
                "android.app.admin.DevicePolicyManager", getExpectedType("DEVICE_POLICY_SERVICE"));
        assertEquals("android.hardware.display.DisplayManager", getExpectedType("DISPLAY_SERVICE"));
        assertEquals("android.app.DownloadManager", getExpectedType("DOWNLOAD_SERVICE"));
        assertEquals("android.os.DropBoxManager", getExpectedType("DROPBOX_SERVICE"));
        assertEquals(
                "android.hardware.fingerprint.FingerprintManager",
                getExpectedType("FINGERPRINT_SERVICE"));
        assertEquals(
                "android.os.HardwarePropertiesManager",
                getExpectedType("HARDWARE_PROPERTIES_SERVICE"));
        assertEquals(
                "android.view.inputmethod.InputMethodManager",
                getExpectedType("INPUT_METHOD_SERVICE"));
        assertEquals("android.hardware.input.InputManager", getExpectedType("INPUT_SERVICE"));
        assertEquals("android.net.IpSecManager", getExpectedType("IPSEC_SERVICE"));
        assertEquals("android.app.job.JobScheduler", getExpectedType("JOB_SCHEDULER_SERVICE"));
        assertEquals("android.app.KeyguardManager", getExpectedType("KEYGUARD_SERVICE"));
        assertEquals("android.content.pm.LauncherApps", getExpectedType("LAUNCHER_APPS_SERVICE"));
        assertEquals("android.view.LayoutInflater", getExpectedType("LAYOUT_INFLATER_SERVICE"));
        assertEquals("android.location.LocationManager", getExpectedType("LOCATION_SERVICE"));
        assertEquals(
                "android.media.projection.MediaProjectionManager",
                getExpectedType("MEDIA_PROJECTION_SERVICE"));
        assertEquals("android.media.MediaRouter", getExpectedType("MEDIA_ROUTER_SERVICE"));
        assertEquals(
                "android.media.session.MediaSessionManager",
                getExpectedType("MEDIA_SESSION_SERVICE"));
        assertEquals("android.media.midi.MidiManager", getExpectedType("MIDI_SERVICE"));
        assertEquals(
                "android.app.usage.NetworkStatsManager", getExpectedType("NETWORK_STATS_SERVICE"));
        assertEquals("android.nfc.NfcManager", getExpectedType("NFC_SERVICE"));
        assertEquals("android.app.NotificationManager", getExpectedType("NOTIFICATION_SERVICE"));
        assertEquals("android.net.nsd.NsdManager", getExpectedType("NSD_SERVICE"));
        assertEquals("android.os.PowerManager", getExpectedType("POWER_SERVICE"));
        assertEquals("android.print.PrintManager", getExpectedType("PRINT_SERVICE"));
        assertEquals(
                "android.content.RestrictionsManager", getExpectedType("RESTRICTIONS_SERVICE"));
        assertEquals("android.app.SearchManager", getExpectedType("SEARCH_SERVICE"));
        assertEquals("android.hardware.SensorManager", getExpectedType("SENSOR_SERVICE"));
        assertEquals("android.content.pm.ShortcutManager", getExpectedType("SHORTCUT_SERVICE"));
        assertEquals("android.os.storage.StorageManager", getExpectedType("STORAGE_SERVICE"));
        assertEquals(
                "android.app.usage.StorageStatsManager", getExpectedType("STORAGE_STATS_SERVICE"));
        assertEquals(
                "android.os.health.SystemHealthManager", getExpectedType("SYSTEM_HEALTH_SERVICE"));
        assertEquals("android.telecom.TelecomManager", getExpectedType("TELECOM_SERVICE"));
        assertEquals("android.telephony.TelephonyManager", getExpectedType("TELEPHONY_SERVICE"));
        assertEquals(
                "android.telephony.SubscriptionManager",
                getExpectedType("TELEPHONY_SUBSCRIPTION_SERVICE"));
        assertEquals(
                "android.view.textclassifier.TextClassificationManager",
                getExpectedType("TEXT_CLASSIFICATION_SERVICE"));
        assertEquals(
                "android.view.textservice.TextServicesManager",
                getExpectedType("TEXT_SERVICES_MANAGER_SERVICE"));
        assertEquals("android.media.tv.TvInputManager", getExpectedType("TV_INPUT_SERVICE"));
        assertEquals("android.app.UiModeManager", getExpectedType("UI_MODE_SERVICE"));
        assertEquals("android.app.usage.UsageStatsManager", getExpectedType("USAGE_STATS_SERVICE"));
        assertEquals("android.hardware.usb.UsbManager", getExpectedType("USB_SERVICE"));
        assertEquals("android.os.UserManager", getExpectedType("USER_SERVICE"));
        assertEquals("android.os.Vibrator", getExpectedType("VIBRATOR_SERVICE"));
        assertEquals("android.app.WallpaperManager", getExpectedType("WALLPAPER_SERVICE"));
        assertEquals(
                "android.net.wifi.aware.WifiAwareManager", getExpectedType("WIFI_AWARE_SERVICE"));
        assertEquals("android.net.wifi.p2p.WifiP2pManager", getExpectedType("WIFI_P2P_SERVICE"));
        assertEquals("android.net.wifi.WifiManager", getExpectedType("WIFI_SERVICE"));
        assertEquals("android.view.WindowManager", getExpectedType("WINDOW_SERVICE"));
    }
}
