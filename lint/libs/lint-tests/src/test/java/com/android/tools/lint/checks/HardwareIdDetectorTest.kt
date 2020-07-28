/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Detector

class HardwareIdDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return HardwareIdDetector()
    }

    fun testBluetoothAdapterGetAddressCall() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.bluetooth.BluetoothAdapter;

                public class AppUtils {
                    public String getBAddress() {
                        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                        return adapter.getAddress();
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/AppUtils.java:8: Warning: Using getAddress to get device identifiers is not recommended [HardwareIds]
                    return adapter.getAddress();
                           ~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testGetAddressCallInCatchBlock() {
        lint().files(
            java(
                """
                package com.google.android.gms.common;
                public class GooglePlayServicesNotAvailableException extends Exception {
                }
                """
            ).indented(),
            java(
                """
                package com.google.android.gms;
                import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
                public class GmsSampleClient {
                    public static String getId() throws GooglePlayServicesNotAvailableException {
                        return "sampleId";
                    }
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;

                import android.bluetooth.BluetoothAdapter;
                import android.content.Context;

                import com.google.android.gms.GmsSampleClient;
                import com.google.android.gms.common.GooglePlayServicesNotAvailableException;

                import java.io.IOException;

                public class AppUtils {

                    public String getAdvertisingId(Context context) {
                        try {
                            return GmsSampleClient.getId();
                        } catch (RuntimeException | GooglePlayServicesNotAvailableException e) {
                            // not available so get one of the ids.
                            return BluetoothAdapter.getDefaultAdapter().getAddress();
                        }
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testGetAndroidId() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.Context;
                import android.provider.Settings;

                public class AppUtils {
                    public String getAndroidId(Context context) {
                        String androidId = Settings.Secure.ANDROID_ID;
                        return Settings.Secure.getString(context.getContentResolver(), androidId);
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/AppUtils.java:9: Warning: Using getString to get device identifiers is not recommended [HardwareIds]
                    return Settings.Secure.getString(context.getContentResolver(), androidId);
                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testWifiInfoGetMacAddress() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.Context;
                import android.net.wifi.WifiInfo;

                public class AppUtils {
                    public String getMacAddress(WifiInfo info) {
                        return info.getMacAddress();
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/AppUtils.java:8: Warning: Using getMacAddress to get device identifiers is not recommended [HardwareIds]
                    return info.getMacAddress();
                           ~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testTelephoneManagerIdentifierCalls() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.Context;
                import android.telephony.TelephonyManager;

                public class AppUtils {
                    public String getDeviceId(TelephonyManager info) {
                        return info.getDeviceId();
                    }
                    public String getLine1Number(TelephonyManager info) {
                        return info.getLine1Number();
                    }
                    public String getSerial(TelephonyManager info) {
                        return info.getSimSerialNumber();
                    }
                    public String getSubscriberId(TelephonyManager info) {
                        return info.getSubscriberId();
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/AppUtils.java:8: Warning: Using getDeviceId to get device identifiers is not recommended [HardwareIds]
                    return info.getDeviceId();
                           ~~~~~~~~~~~~~~~~~~
            src/test/pkg/AppUtils.java:11: Warning: Using getLine1Number to get device identifiers is not recommended [HardwareIds]
                    return info.getLine1Number();
                           ~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/AppUtils.java:14: Warning: Using getSimSerialNumber to get device identifiers is not recommended [HardwareIds]
                    return info.getSimSerialNumber();
                           ~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/AppUtils.java:17: Warning: Using getSubscriberId to get device identifiers is not recommended [HardwareIds]
                    return info.getSubscriberId();
                           ~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 4 warnings
            """
        )
    }

    fun testBuildSerialUsage() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.Context;

                import java.lang.reflect.Method;

                import static android.os.Build.*;

                public class HardwareIdDetectorTestData {

                    // Fails because of the use of `ro.serialno` using reflection
                    // and Build.SERIAL static field access to hardware Id.
                    public static String getSerialNumber(Context context) {
                        String serial = null;
                        if (VERSION.SDK_INT >= VERSION_CODES.GINGERBREAD) {
                            serial = SERIAL;
                        } else {
                            try {
                                Class<?> c = Class.forName("android.os.SystemProperties");
                                Method get = c.getMethod("get", String.class);
                                serial = (String) get.invoke(null, "ro.serialno");
                            } catch (Exception ig) {
                            }
                        }
                        return serial;
                    }

                    public static String getSerialNumber2() {
                        return android.os.Build.SERIAL;
                    }
                    public static String getSerialNumber3() {
                        try {
                            Class<?> c;
                            Method get;
                            c = Class.forName("android.os.SystemProperties");
                            get = c.getMethod("get", String.class);
                            return (String) get.invoke(null, "ro.serialno");
                        } catch (Exception ig) {
                            return null;        }
                    }

                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/HardwareIdDetectorTestData.java:16: Warning: Using SERIAL to get device identifiers is not recommended [HardwareIds]
                        serial = SERIAL;
                                 ~~~~~~
            src/test/pkg/HardwareIdDetectorTestData.java:21: Warning: Using ro.serialno to get device identifiers is not recommended [HardwareIds]
                            serial = (String) get.invoke(null, "ro.serialno");
                                                               ~~~~~~~~~~~~~
            src/test/pkg/HardwareIdDetectorTestData.java:29: Warning: Using SERIAL to get device identifiers is not recommended [HardwareIds]
                    return android.os.Build.SERIAL;
                                            ~~~~~~
            src/test/pkg/HardwareIdDetectorTestData.java:37: Warning: Using ro.serialno to get device identifiers is not recommended [HardwareIds]
                        return (String) get.invoke(null, "ro.serialno");
                                                         ~~~~~~~~~~~~~
            0 errors, 4 warnings
            """
        )
    }

    fun testRoSerialUsage() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.Context;
                import java.lang.reflect.Method;

                public class AppUtils {

                    public static String getSystemProperty(Context context, String key) throws Exception {

                        Class<?> c = context.getClassLoader()
                                .loadClass("android.os.SystemProperties");
                        Method get = c.getMethod("get", String.class);
                        return (String) get.invoke(null, key);
                    }

                    public static String getSerialProperty(Context context) throws Exception {
                        return getSystemProperty(context, "ro.serialno");
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/AppUtils.java:17: Warning: Using ro.serialno to get device identifiers is not recommended [HardwareIds]
                    return getSystemProperty(context, "ro.serialno");
                                                      ~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun testMultipleRoSerialUsages() {
        lint().files(
            java(
                """
                package com.google.android.gms.common;
                public class GooglePlayServicesNotAvailableException extends Exception {
                }
                """
            ).indented(),
            java(
                """
                package com.google.android.gms;
                import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
                public class GmsSampleClient {
                    public static String getId() throws GooglePlayServicesNotAvailableException {
                        return "sampleId";
                    }
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;

                import android.content.Context;

                import com.google.android.gms.GmsSampleClient;
                import com.google.android.gms.common.GooglePlayServicesNotAvailableException;

                import java.lang.reflect.Method;

                public class AppUtils {

                    public static final String RO_SERIAL = "ro.serialno";

                    public static String getSysProperty(String key, String defaultValue) throws Exception {
                       Class<?> s = Class.forName("android.os.SystemProperties");
                       Method getDefault = s.getMethod("get", String.class, String.class);
                       return (String) getDefault.invoke(s, key, defaultValue);
                    }    public static String getSerial2() throws Exception {
                        return getSysProperty(RO_SERIAL, "default");
                    }

                    public static String getSystemProperty(Context context, String key1, String key2) throws Exception {

                        Class<?> c = context.getClassLoader()
                                .loadClass("android.os.SystemProperties");
                        Class<?> s = Class.forName("android.os.SystemProperties");
                        Method get = c.getMethod("get", String.class);
                        Method getDefault = s.getMethod("get", String.class, String.class);
                        String x = (String)getDefault.invoke(s, key2, "def");
                        return (String) get.invoke(null, key1);
                    }

                    public static String getSerialProperty(Context context) throws Exception {
                        String def = getSystemProperty(context, null, "ro.serialno");
                        return getSystemProperty(context, "ro.serialno", null);
                    }

                    // Should not result in a warning since it's called within the catch block
                    public static String doPlayServicesCall(Context context) throws Exception {
                        try {
                            return GmsSampleClient.getId();
                        } catch (RuntimeException | GooglePlayServicesNotAvailableException e) {
                            // not available so get one of the ids.
                            return getSystemProperty(context, "ro.serialno", "ID");
                        }
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/AppUtils.java:19: Warning: Using ro.serialno to get device identifiers is not recommended [HardwareIds]
                    return getSysProperty(RO_SERIAL, "default");
                                          ~~~~~~~~~
            src/test/pkg/AppUtils.java:34: Warning: Using ro.serialno to get device identifiers is not recommended [HardwareIds]
                    String def = getSystemProperty(context, null, "ro.serialno");
                                                                  ~~~~~~~~~~~~~
            src/test/pkg/AppUtils.java:35: Warning: Using ro.serialno to get device identifiers is not recommended [HardwareIds]
                    return getSystemProperty(context, "ro.serialno", null);
                                                      ~~~~~~~~~~~~~
            0 errors, 3 warnings
            """
        )
    }

    fun testCrash() {
        // Regression test for https://issuetracker.google.com/121341637
        lint().files(
            kotlin(
                """
                package com.example.linterror33rc3

                import android.support.annotation.StringRes
                import android.text.TextUtils
                import android.view.View
                import android.widget.AutoCompleteTextView

                class BillingAddressRib(val view: View, val activity: MainActivity) {

                    internal lateinit var addressField: AutoCompleteTextView


                    fun didBecomeActive() {
                        initViews(view)

                        addressField.apply {
                            setOnFocusChangeListener { _, hasFocus ->
                                if (!hasFocus) {
                                    if (!validateAddress(text.toString())) {
                                        error = getString(R.string.ck_field_error_street_address)
                                    } else {
                                        error = null
                                    }
                                }
                            }
                        }

                    }

                    fun initViews(view: View) {
                        addressField = view.findViewById(R.id.address_field) as AutoCompleteTextView
                    }

                    internal fun getString(@StringRes stringRes: Int): String {
                        return activity.getResources().getString(stringRes)
                    }

                    internal fun validateAddress(address: String): Boolean {
                        return !TextUtils.isEmpty(address)
                    }


                }
                """
            ).indented()
        ).run().expectClean()
    }
}
