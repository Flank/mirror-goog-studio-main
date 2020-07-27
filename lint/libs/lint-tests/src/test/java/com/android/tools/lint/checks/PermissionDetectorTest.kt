/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.SdkConstants.TAG_USES_PERMISSION_SDK_23
import com.android.SdkConstants.TAG_USES_PERMISSION_SDK_M
import com.android.tools.lint.detector.api.Detector

class PermissionDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector = PermissionDetector()

    private val mLocationManagerStub = java(
        "src/android/location/LocationManager.java",
        """
                package android.location;

                import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
                import static android.Manifest.permission.ACCESS_FINE_LOCATION;

                import android.support.annotation.RequiresPermission;

                @SuppressWarnings({"UnusedDeclaration", "ClassNameDiffersFromFileName"})
                public abstract class LocationManager {
                    @RequiresPermission(anyOf = {ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION})
                    public abstract Location myMethod(String provider);
                    public static class Location {
                    }
                }
                """
    ).indented()

    private val mPermissionTest = java(
        "src/test/pkg/PermissionTest.java",
        """
                package test.pkg;

                import android.location.LocationManager;
                @SuppressWarnings({"UnusedDeclaration", "ClassNameDiffersFromFileName"})
                public class PermissionTest {
                    public static void test(LocationManager locationManager, String provider) {
                        LocationManager.Location location = locationManager.myMethod(provider);
                    }
                }
                """
    ).indented()

    private fun getManifestWithPermissions(
        targetSdk: Int,
        vararg permissions: String
    ): TestFile {
        return getManifestWithPermissions(1, targetSdk, *permissions)
    }

    private fun getThingsManifestWithPermissions(
        targetSdk: Int,
        isRequired: Boolean?,
        vararg permissions: String
    ): TestFile {
        val applicationBlock = StringBuilder()
        applicationBlock.append("<uses-library android:name=\"com.google.android.things\"")
        if (isRequired != null) {
            applicationBlock.append(" android:required=")
            if (isRequired) {
                applicationBlock.append("\"true\"")
            } else {
                applicationBlock.append("\"false\"")
            }
        }
        applicationBlock.append("/>\n")
        return getManifestWithPermissions(
            applicationBlock.toString(),
            1,
            targetSdk,
            *permissions
        )
    }

    private fun getManifestWithPermissions(
        minSdk: Int,
        targetSdk: Int,
        vararg permissions: String
    ): TestFile {
        return getManifestWithPermissions(null, minSdk, targetSdk, *permissions)
    }

    private fun getManifestWithPermissions(
        applicationBlock: String?,
        minSdk: Int,
        targetSdk: Int,
        vararg permissions: String
    ): TestFile {
        val permissionBlock = StringBuilder()
        for (permission in permissions) {
            permissionBlock.append("    <uses-permission android:name=\"").append(permission)
                .append("\" />\n")
        }
        return xml(
            "AndroidManifest.xml",
            "" +
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    package=\"foo.bar2\"\n" +
                "    android:versionCode=\"1\"\n" +
                "    android:versionName=\"1.0\" >\n" +
                "\n" +
                "    <uses-sdk android:minSdkVersion=\"" + minSdk + "\" android:targetSdkVersion=\"" +
                targetSdk + "\" />\n" +
                "\n" +
                permissionBlock.toString() +
                "\n" +
                "    <application\n" +
                "        android:icon=\"@drawable/ic_launcher\"\n" +
                "        android:label=\"@string/app_name\" >\n" +
                (applicationBlock ?: "") +
                "    </application>\n" +
                "\n" +
                "</manifest>"
        )
    }

    private val mRevokeTest = java(
        "src/test/pkg/RevokeTest.java",
        """
                package test.pkg;

                import android.content.Context;
                import android.content.pm.PackageManager;
                import android.location.LocationManager;
                import java.io.IOException;
                import java.security.AccessControlException;
                @SuppressWarnings({"ClassNameDiffersFromFileName", "TryWithIdenticalCatches", "RedundantThrows"})
                public class RevokeTest {
                    public static void test1(LocationManager locationManager, String provider) {
                        try {
                            // Ok: Security exception caught in one of the branches
                            locationManager.myMethod(provider); // OK
                        } catch (IllegalArgumentException ignored) {
                        } catch (SecurityException ignored) {
                        }

                        try {
                            // You have to catch SecurityException explicitly, not parent
                            locationManager.myMethod(provider); // ERROR
                        } catch (RuntimeException e) { // includes Security Exception
                        }

                        try {
                            // Ok: Caught in outer statement
                            try {
                                locationManager.myMethod(provider); // OK
                            } catch (IllegalArgumentException e) {
                                // inner
                            }
                        } catch (SecurityException ignored) {
                        }

                        try {
                            // You have to catch SecurityException explicitly, not parent
                            locationManager.myMethod(provider); // ERROR
                        } catch (Exception e) { // includes Security Exception
                        }

                        // NOT OK: Catching security exception subclass (except for dedicated ones?)

                        try {
                            // Error: catching security exception, but not all of them
                            locationManager.myMethod(provider); // ERROR
                        } catch (AccessControlException e) { // security exception but specific one
                        }
                    }

                    public static void test2(LocationManager locationManager, String provider) {
                        locationManager.myMethod(provider); // ERROR: not caught
                    }

                    public static void test3(LocationManager locationManager, String provider)
                            throws IllegalArgumentException {
                        locationManager.myMethod(provider); // ERROR: not caught by right type
                    }

                    public static void test4(LocationManager locationManager, String provider)
                            throws AccessControlException {  // Security exception but specific one
                        locationManager.myMethod(provider); // ERROR
                    }

                    public static void test5(LocationManager locationManager, String provider)
                            throws SecurityException {
                        locationManager.myMethod(provider); // OK
                    }

                    public static void test6(LocationManager locationManager, String provider)
                            throws Exception { // includes Security Exception
                        // You have to throw SecurityException explicitly, not parent
                        locationManager.myMethod(provider); // ERROR
                    }

                    public static void test7(LocationManager locationManager, String provider, Context context)
                            throws IllegalArgumentException {
                        if (context.getPackageManager().checkPermission(android.Manifest.permission.ACCESS_FINE_LOCATION, context.getPackageName()) != PackageManager.PERMISSION_GRANTED) {
                            return;
                        }
                        locationManager.myMethod(provider); // OK: permission checked
                    }

                    public void test8(LocationManager locationManager, String provider) {
                          // Regression test for http://b.android.com/187204
                        try {
                            locationManager.myMethod(provider); // ERROR
                            mightThrow();
                        } catch (SecurityException | IOException se) { // OK: Checked in multi catch
                        }
                        try {
                            locationManager.myMethod(provider); // ERROR
                            mightThrow();
                        } catch (IOException | SecurityException se) { // OK: Checked in multi catch
                        }
                    }

                    public void mightThrow() throws IOException {
                    }

                }
                """
    ).indented()

    fun testMissingPermissions() {
        val expected =
            "src/test/pkg/PermissionTest.java:7: Error: Missing permissions required by LocationManager.myMethod: android.permission.ACCESS_FINE_LOCATION or android.permission.ACCESS_COARSE_LOCATION [MissingPermission]\n" +
                "        LocationManager.Location location = locationManager.myMethod(provider);\n" +
                "                                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "1 errors, 0 warnings\n"

        lint().files(
            getManifestWithPermissions(14),
            mPermissionTest,
            mLocationManagerStub,
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(expected)
    }

    fun testHasPermission() {
        lint().files(
            getManifestWithPermissions(14, "android.permission.ACCESS_FINE_LOCATION"),
            mPermissionTest,
            mLocationManagerStub,
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testRevokePermissions() {
        val expected = "" +
            "src/test/pkg/RevokeTest.java:20: Error: Call requires permission which may be rejected by user: code should explicitly check to see if permission is available (with checkPermission) or explicitly handle a potential SecurityException [MissingPermission]\n" +
            "            locationManager.myMethod(provider); // ERROR\n" +
            "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/RevokeTest.java:36: Error: Call requires permission which may be rejected by user: code should explicitly check to see if permission is available (with checkPermission) or explicitly handle a potential SecurityException [MissingPermission]\n" +
            "            locationManager.myMethod(provider); // ERROR\n" +
            "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/RevokeTest.java:44: Error: Call requires permission which may be rejected by user: code should explicitly check to see if permission is available (with checkPermission) or explicitly handle a potential SecurityException [MissingPermission]\n" +
            "            locationManager.myMethod(provider); // ERROR\n" +
            "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/RevokeTest.java:50: Error: Call requires permission which may be rejected by user: code should explicitly check to see if permission is available (with checkPermission) or explicitly handle a potential SecurityException [MissingPermission]\n" +
            "        locationManager.myMethod(provider); // ERROR: not caught\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/RevokeTest.java:55: Error: Call requires permission which may be rejected by user: code should explicitly check to see if permission is available (with checkPermission) or explicitly handle a potential SecurityException [MissingPermission]\n" +
            "        locationManager.myMethod(provider); // ERROR: not caught by right type\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/RevokeTest.java:60: Error: Call requires permission which may be rejected by user: code should explicitly check to see if permission is available (with checkPermission) or explicitly handle a potential SecurityException [MissingPermission]\n" +
            "        locationManager.myMethod(provider); // ERROR\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/RevokeTest.java:71: Error: Call requires permission which may be rejected by user: code should explicitly check to see if permission is available (with checkPermission) or explicitly handle a potential SecurityException [MissingPermission]\n" +
            "        locationManager.myMethod(provider); // ERROR\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "7 errors, 0 warnings\n"

        lint().files(
            getManifestWithPermissions(23, "android.permission.ACCESS_FINE_LOCATION"),
            mLocationManagerStub,
            mRevokeTest,
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(expected)
    }

    fun testImpliedPermissions() {
        // Regression test for
        //   https://code.google.com/p/android/issues/detail?id=177381
        val expected = "" +
            "src/test/pkg/PermissionTest2.java:11: Error: Missing permissions required by PermissionTest2.method1: my.permission.PERM2 [MissingPermission]\n" +
            "        method1(); // ERROR\n" +
            "        ~~~~~~~~~\n" +
            "1 errors, 0 warnings\n"
        lint().files(
            getManifestWithPermissions(
                14,
                14,
                "android.permission.ACCESS_FINE_LOCATION"
            ),
            java(
                "src/test/pkg/PermissionTest2.java",
                "" +
                    "package test.pkg;\n" +
                    "import android.support.annotation.RequiresPermission;\n" +
                    "\n" +
                    "public class PermissionTest2 {\n" +
                    "    @RequiresPermission(allOf = {\"my.permission.PERM1\",\"my.permission.PERM2\"})\n" +
                    "    public void method1() {\n" +
                    "    }\n" +
                    "\n" +
                    "    @RequiresPermission(\"my.permission.PERM1\")\n" +
                    "    public void method2() {\n" +
                    "        method1(); // ERROR\n" +
                    "    }\n" +
                    "\n" +
                    "    @RequiresPermission(allOf = {\"my.permission.PERM1\",\"my.permission.PERM2\"})\n" +
                    "    public void method3() {\n" +
                    "        // The above @RequiresPermission implies that we are holding these\n" +
                    "        // permissions here, so the call to method1() should not be flagged as\n" +
                    "        // missing a permission!\n" +
                    "        method1(); // OK\n" +
                    "    }\n" +
                    "}\n"
            ),
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(expected)
    }

    fun testRevokePermissionsPre23() {
        lint().files(
            getManifestWithPermissions(14, "android.permission.ACCESS_FINE_LOCATION"),
            mLocationManagerStub,
            mRevokeTest,
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testUsesPermissionSdk23() {
        val manifest = getManifestWithPermissions(
            14,
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.BLUETOOTH"
        )
        val contents = manifest.getContents()
        assertNotNull(contents)
        val s = contents!!.replace(TAG_USES_PERMISSION, TAG_USES_PERMISSION_SDK_23)
        manifest.withSource(s)
        lint().files(
            manifest,
            mPermissionTest,
            mLocationManagerStub,
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testUsesPermissionSdkM() {
        val manifest = getManifestWithPermissions(
            14,
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.BLUETOOTH"
        )
        val contents = manifest.getContents()
        assertNotNull(contents)
        val s = contents!!.replace(TAG_USES_PERMISSION, TAG_USES_PERMISSION_SDK_M)
        manifest.withSource(s)
        lint().files(
            manifest,
            mPermissionTest,
            mLocationManagerStub,
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testPermissionAnnotation() {
        val expected = "" +
            "src/test/pkg/LocationManager.java:24: Error: Missing permissions required by LocationManager.getLastKnownLocation: android.permission.ACCESS_FINE_LOCATION or android.permission.ACCESS_COARSE_LOCATION [MissingPermission]\n" +
            "        Location location = manager.getLastKnownLocation(\"provider\");\n" +
            "                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "1 errors, 0 warnings\n"
        lint().files(
            java(
                "src/test/pkg/LocationManager.java",
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import android.support.annotation.RequiresPermission;\n" +
                    "\n" +
                    "import java.lang.annotation.Retention;\n" +
                    "import java.lang.annotation.RetentionPolicy;\n" +
                    "\n" +
                    "import static android.Manifest.permission.ACCESS_COARSE_LOCATION;\n" +
                    "import static android.Manifest.permission.ACCESS_FINE_LOCATION;\n" +
                    "\n" +
                    "@SuppressWarnings(\"UnusedDeclaration\")\n" +
                    "public abstract class LocationManager {\n" +
                    "    @RequiresPermission(anyOf = {ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION})\n" +
                    "    @Retention(RetentionPolicy.SOURCE)\n" +
                    "    @interface AnyLocationPermission {\n" +
                    "    }\n" +
                    "\n" +
                    "    @AnyLocationPermission\n" +
                    "    public abstract Location getLastKnownLocation(String provider);\n" +
                    "    public static class Location {\n" +
                    "    }\n" +
                    "    \n" +
                    "    public static void test(LocationManager manager) {\n" +
                    "        Location location = manager.getLastKnownLocation(\"provider\");\n" +
                    "    }\n" +
                    "}\n"
            ),
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(expected)
    }

    fun testMissingManifestLevelPermissionsWithAndroidThings() {
        val expected = "" +
            "src/test/pkg/PermissionTest.java:7: Error: Missing permissions required by LocationManager.myMethod: android.permission.ACCESS_FINE_LOCATION or android.permission.ACCESS_COARSE_LOCATION [MissingPermission]\n" +
            "        LocationManager.Location location = locationManager.myMethod(provider);\n" +
            "                                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "1 errors, 0 warnings\n"

        lint().files(
            getThingsManifestWithPermissions(24, null),
            mPermissionTest,
            mLocationManagerStub,
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(expected)
    }

    fun testHasManifestLevelPermissionsWithAndroidThings() {
        lint().files(
            getThingsManifestWithPermissions(
                24, null, "android.permission.ACCESS_FINE_LOCATION"
            ),
            mPermissionTest,
            mLocationManagerStub,
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testMissingRuntimePermissionsWithOptionalAndroidThings() {
        val expected = "" +
            "src/test/pkg/PermissionTest.java:7: Error: Call requires permission which may be rejected by user: code should explicitly check to see if permission is available (with checkPermission) or explicitly handle a potential SecurityException [MissingPermission]\n" +
            "        LocationManager.Location location = locationManager.myMethod(provider);\n" +
            "                                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "1 errors, 0 warnings\n"

        lint().files(
            getThingsManifestWithPermissions(
                24,
                false,
                "android.permission.ACCESS_FINE_LOCATION"
            ),
            mPermissionTest,
            mLocationManagerStub,
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(expected)
    }

    fun testIntentPermission() {
        val expected = "" +
            "src/test/pkg/ActionTest.java:36: Error: Missing permissions required by intent ActionTest.ACTION_CALL: android.permission.CALL_PHONE [MissingPermission]\n" +
            "        activity.startActivity(intent);\n" +
            "                 ~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/ActionTest.java:42: Error: Missing permissions required by intent ActionTest.ACTION_CALL: android.permission.CALL_PHONE [MissingPermission]\n" +
            "        activity.startActivity(intent);\n" +
            "                 ~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/ActionTest.java:43: Error: Missing permissions required by intent ActionTest.ACTION_CALL: android.permission.CALL_PHONE [MissingPermission]\n" +
            "        activity.startActivity(intent, null);\n" +
            "                 ~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/ActionTest.java:44: Error: Missing permissions required by intent ActionTest.ACTION_CALL: android.permission.CALL_PHONE [MissingPermission]\n" +
            "        activity.startActivityForResult(intent, 0);\n" +
            "                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/ActionTest.java:45: Error: Missing permissions required by intent ActionTest.ACTION_CALL: android.permission.CALL_PHONE [MissingPermission]\n" +
            "        activity.startActivityFromChild(activity, intent, 0);\n" +
            "                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/ActionTest.java:46: Error: Missing permissions required by intent ActionTest.ACTION_CALL: android.permission.CALL_PHONE [MissingPermission]\n" +
            "        activity.startActivityIfNeeded(intent, 0);\n" +
            "                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/ActionTest.java:47: Error: Missing permissions required by intent ActionTest.ACTION_CALL: android.permission.CALL_PHONE [MissingPermission]\n" +
            "        activity.startActivityFromFragment(null, intent, 0);\n" +
            "                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/ActionTest.java:48: Error: Missing permissions required by intent ActionTest.ACTION_CALL: android.permission.CALL_PHONE [MissingPermission]\n" +
            "        activity.startNextMatchingActivity(intent);\n" +
            "                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/ActionTest.java:54: Error: Missing permissions required by intent ActionTest.ACTION_CALL: android.permission.CALL_PHONE [MissingPermission]\n" +
            "        context.sendBroadcast(intent);\n" +
            "                ~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/ActionTest.java:55: Error: Missing permissions required by intent ActionTest.ACTION_CALL: android.permission.CALL_PHONE [MissingPermission]\n" +
            "        context.sendBroadcast(intent, \"\");\n" +
            "                ~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/ActionTest.java:56: Error: Missing permissions required by Context.sendBroadcastAsUser: android.permission.INTERACT_ACROSS_USERS [MissingPermission]\n" +
            "        context.sendBroadcastAsUser(intent, null);\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/ActionTest.java:56: Error: Missing permissions required by intent ActionTest.ACTION_CALL: android.permission.CALL_PHONE [MissingPermission]\n" +
            "        context.sendBroadcastAsUser(intent, null);\n" +
            "                ~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/ActionTest.java:57: Error: Missing permissions required by Context.sendStickyBroadcast: android.permission.BROADCAST_STICKY [MissingPermission]\n" +
            "        context.sendStickyBroadcast(intent);\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/ActionTest.java:57: Error: Missing permissions required by intent ActionTest.ACTION_CALL: android.permission.CALL_PHONE [MissingPermission]\n" +
            "        context.sendStickyBroadcast(intent);\n" +
            "                ~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/ActionTest.java:62: Error: Missing permissions required to read ActionTest.BOOKMARKS_URI: com.android.browser.permission.READ_HISTORY_BOOKMARKS [MissingPermission]\n" +
            "        resolver.query(BOOKMARKS_URI, null, null, null, null);\n" +
            "                 ~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/ActionTest.java:65: Error: Missing permissions required to write ActionTest.BOOKMARKS_URI: com.android.browser.permission.WRITE_HISTORY_BOOKMARKS [MissingPermission]\n" +
            "        resolver.insert(BOOKMARKS_URI, null);\n" +
            "                 ~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/ActionTest.java:66: Error: Missing permissions required to write ActionTest.BOOKMARKS_URI: com.android.browser.permission.WRITE_HISTORY_BOOKMARKS [MissingPermission]\n" +
            "        resolver.delete(BOOKMARKS_URI, null, null);\n" +
            "                 ~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/ActionTest.java:67: Error: Missing permissions required to write ActionTest.BOOKMARKS_URI: com.android.browser.permission.WRITE_HISTORY_BOOKMARKS [MissingPermission]\n" +
            "        resolver.update(BOOKMARKS_URI, null, null, null);\n" +
            "                 ~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/ActionTest.java:86: Error: Missing permissions required by intent ActionTest.ACTION_CALL: android.permission.CALL_PHONE [MissingPermission]\n" +
            "        myStartActivity(\"\", null, new Intent(ACTION_CALL));\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/ActionTest.java:87: Error: Missing permissions required to read ActionTest.BOOKMARKS_URI: com.android.browser.permission.READ_HISTORY_BOOKMARKS [MissingPermission]\n" +
            "        myReadResolverMethod(\"\", BOOKMARKS_URI);\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "src/test/pkg/ActionTest.java:88: Error: Missing permissions required to read ActionTest.BOOKMARKS_URI: com.android.browser.permission.READ_HISTORY_BOOKMARKS [MissingPermission]\n" +
            "        myWriteResolverMethod(BOOKMARKS_URI);\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "21 errors, 0 warnings\n"

        lint().files(
            getManifestWithPermissions(14, 23),
            java(
                "src/test/pkg/ActionTest.java",
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import android.Manifest;\n" +
                    "import android.app.Activity;\n" +
                    "import android.content.ContentResolver;\n" +
                    "import android.content.Context;\n" +
                    "import android.content.Intent;\n" +
                    "import android.database.Cursor;\n" +
                    "import android.net.Uri;\n" +
                    "import android.support.annotation.RequiresPermission;\n" +
                    "\n" +
                    "\n" +
                    "@SuppressWarnings({\"deprecation\", \"unused\"})\n" +
                    "public class ActionTest {\n" +
                    "     public static final String READ_HISTORY_BOOKMARKS=\"com.android.browser.permission.READ_HISTORY_BOOKMARKS\";\n" +
                    "     public static final String WRITE_HISTORY_BOOKMARKS=\"com.android.browser.permission.WRITE_HISTORY_BOOKMARKS\";\n" +
                    "    @RequiresPermission(Manifest.permission.CALL_PHONE)\n" +
                    "    public static final String ACTION_CALL = \"android.intent.action.CALL\";\n" +
                    "\n" +
                    "    @RequiresPermission.Read(@RequiresPermission(READ_HISTORY_BOOKMARKS))\n" +
                    "    @RequiresPermission.Write(@RequiresPermission(WRITE_HISTORY_BOOKMARKS))\n" +
                    "    public static final Uri BOOKMARKS_URI = Uri.parse(\"content://browser/bookmarks\");\n" +
                    "\n" +
                    "    public static final Uri COMBINED_URI = Uri.withAppendedPath(BOOKMARKS_URI, \"bookmarks\");\n" +
                    "    \n" +
                    "    public static void activities1(Activity activity) {\n" +
                    "        Intent intent = new Intent(Intent.ACTION_CALL);\n" +
                    "        intent.setData(Uri.parse(\"tel:1234567890\"));\n" +
                    "        // This one will only be flagged if we have framework metadata on Intent.ACTION_CALL\n" +
                    // Too flaky +
                    "        //activity.startActivity(intent);\n" +
                    "    }\n" +
                    "\n" +
                    "    public static void activities2(Activity activity) {\n" +
                    "        Intent intent = new Intent(ACTION_CALL);\n" +
                    "        intent.setData(Uri.parse(\"tel:1234567890\"));\n" +
                    "        activity.startActivity(intent);\n" +
                    "    }\n" +
                    "    public static void activities3(Activity activity) {\n" +
                    "        Intent intent;\n" +
                    "        intent = new Intent(ACTION_CALL);\n" +
                    "        intent.setData(Uri.parse(\"tel:1234567890\"));\n" +
                    "        activity.startActivity(intent);\n" +
                    "        activity.startActivity(intent, null);\n" +
                    "        activity.startActivityForResult(intent, 0);\n" +
                    "        activity.startActivityFromChild(activity, intent, 0);\n" +
                    "        activity.startActivityIfNeeded(intent, 0);\n" +
                    "        activity.startActivityFromFragment(null, intent, 0);\n" +
                    "        activity.startNextMatchingActivity(intent);\n" +
                    "    }\n" +
                    "\n" +
                    "    public static void broadcasts(Context context) {\n" +
                    "        Intent intent;\n" +
                    "        intent = new Intent(ACTION_CALL);\n" +
                    "        context.sendBroadcast(intent);\n" +
                    "        context.sendBroadcast(intent, \"\");\n" +
                    "        context.sendBroadcastAsUser(intent, null);\n" +
                    "        context.sendStickyBroadcast(intent);\n" +
                    "    }\n" +
                    "\n" +
                    "    public static void contentResolvers(Context context, ContentResolver resolver) {\n" +
                    "        // read\n" +
                    "        resolver.query(BOOKMARKS_URI, null, null, null, null);\n" +
                    "\n" +
                    "        // write\n" +
                    "        resolver.insert(BOOKMARKS_URI, null);\n" +
                    "        resolver.delete(BOOKMARKS_URI, null, null);\n" +
                    "        resolver.update(BOOKMARKS_URI, null, null, null);\n" +
                    "\n" +
                    "        // Framework (external) annotation\n" +
                    "//REMOVED        resolver.query(android.provider.Browser.BOOKMARKS_URI, null, null, null, null);\n" +
                    "\n" +
                    "        // TODO: Look for more complex URI manipulations\n" +
                    "    }\n" +
                    "\n" +
                    "    public static void myStartActivity(String s1, String s2, \n" +
                    "                                       @RequiresPermission Intent intent) {\n" +
                    "    }\n" +
                    "\n" +
                    "    public static void myReadResolverMethod(String s1, @RequiresPermission.Read(@RequiresPermission) Uri uri) {\n" +
                    "    }\n" +
                    "\n" +
                    "    public static void myWriteResolverMethod(@RequiresPermission.Read(@RequiresPermission) Uri uri) {\n" +
                    "    }\n" +
                    "    \n" +
                    "    public static void testCustomMethods() {\n" +
                    "        myStartActivity(\"\", null, new Intent(ACTION_CALL));\n" +
                    "        myReadResolverMethod(\"\", BOOKMARKS_URI);\n" +
                    "        myWriteResolverMethod(BOOKMARKS_URI);\n" +
                    "    }\n" +
                    "}\n"
            ),
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        )
            .run().expect(expected)
    }

    fun testRequiresPermissionWithinRequires() {
        lint().files(
            java(
                "" +
                    "package com.example.mylibrary1;\n" +
                    "\n" +
                    "import android.Manifest;\n" +
                    "import android.content.Context;\n" +
                    "import android.net.wifi.WifiInfo;\n" +
                    "import android.net.wifi.WifiManager;\n" +
                    "import android.support.annotation.RequiresPermission;\n" +
                    "\n" +
                    "public class WifiInfoUtil {\n" +
                    "    @RequiresPermission(Manifest.permission.ACCESS_WIFI_STATE)\n" +
                    "    public static WifiInfo getWifiInfo(Context context) {\n" +
                    "        WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);\n" +
                    "        return wm.getConnectionInfo();\n" +
                    "    }\n" +
                    "}"
            ),
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testMissingPermission() {

        lint().files(
            java(
                "" +
                    "package test.pkg;\n" +
                    "import android.Manifest;\n" +
                    "import android.content.Context;\n" +
                    "import android.content.pm.PackageManager;\n" +
                    "import android.graphics.Bitmap;\n" +
                    "import android.support.annotation.RequiresPermission;\n" +
                    "\n" +
                    "import static android.Manifest.permission.ACCESS_COARSE_LOCATION;\n" +
                    "import static android.Manifest.permission.ACCESS_FINE_LOCATION;\n" +
                    "\n" +
                    "public class X {\n" +
                    "    private static void foo(Context context, LocationManager manager) {\n" +
                    "        /*Missing permissions required by LocationManager.myMethod: android.permission.ACCESS_FINE_LOCATION or android.permission.ACCESS_COARSE_LOCATION*/manager.myMethod(\"myprovider\")/**/;\n" +
                    "    }\n" +
                    "\n" +
                    "    @SuppressWarnings(\"UnusedDeclaration\")\n" +
                    "    public abstract class LocationManager {\n" +
                    "        @RequiresPermission(anyOf = {ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION})\n" +
                    "        public abstract Location myMethod(String provider);\n" +
                    "        public class Location {\n" +
                    "        }\n" +
                    "    }\n" +
                    "}"
            ),
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectInlinedMessages()
    }

    fun testImpliedPermission() {
        // Regression test for
        //   https://code.google.com/p/android/issues/detail?id=177381

        lint().files(
            java(
                "" +
                    "package test.pkg;\n" +
                    "import android.support.annotation.RequiresPermission;\n" +
                    "\n" +
                    "public class X {\n" +
                    "    @RequiresPermission(allOf = {\"my.permission.PERM1\",\"my.permission.PERM2\"})\n" +
                    "    public void method1() {\n" +
                    "    }\n" +
                    "\n" +
                    "    @RequiresPermission(\"my.permission.PERM1\")\n" +
                    "    public void method2() {\n" +
                    "        /*Missing permissions required by X.method1: my.permission.PERM2*/method1()/**/;\n" +
                    "    }\n" +
                    "\n" +
                    "    @RequiresPermission(allOf = {\"my.permission.PERM1\",\"my.permission.PERM2\"})\n" +
                    "    public void method3() {\n" +
                    "        // The above @RequiresPermission implies that we are holding these\n" +
                    "        // permissions here, so the call to method1() should not be flagged as\n" +
                    "        // missing a permission!\n" +
                    "        method1();\n" +
                    "    }\n" +
                    "}\n"
            ),
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectInlinedMessages()
    }

    fun testLibraryRevocablePermission() {
        lint().files(
            manifest(
                "" +
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "    package=\"test.pkg.permissiontest\">\n" +
                    "\n" +
                    "    <uses-sdk android:minSdkVersion=\"17\" android:targetSdkVersion=\"23\" />\n" +
                    "\n" +
                    "    <permission\n" +
                    "        android:name=\"my.normal.P1\"\n" +
                    "        android:protectionLevel=\"normal\" />\n" +
                    "\n" +
                    "    <permission\n" +
                    "        android:name=\"my.dangerous.P2\"\n" +
                    "        android:protectionLevel=\"dangerous\" />\n" +
                    "\n" +
                    "    <uses-permission android:name=\"my.normal.P1\" />\n" +
                    "    <uses-permission android:name=\"my.dangerous.P2\" />\n" +
                    "\n" +
                    "</manifest>\n"
            ),
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import android.support.annotation.RequiresPermission;\n" +
                    "\n" +
                    "public class X {\n" +
                    "    public void something() {\n" +
                    "        /*Call requires permission which may be rejected by user: code should explicitly check to see if permission is available (with checkPermission) or explicitly handle a potential SecurityException*/methodRequiresDangerous()/**/;\n" +
                    "        methodRequiresNormal();\n" +
                    "    }\n" +
                    "\n" +
                    "    @RequiresPermission(\"my.normal.P1\")\n" +
                    "    public void methodRequiresNormal() {\n" +
                    "    }\n" +
                    "\n" +
                    "    @RequiresPermission(\"my.dangerous.P2\")\n" +
                    "    public void methodRequiresDangerous() {\n" +
                    "    }\n" +
                    "}\n"
            ),
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectInlinedMessages()
    }

    fun testHandledPermission() {

        lint().files(
            manifest(
                "" +
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "    package=\"test.pkg.permissiontest\">\n" +
                    "\n" +
                    "    <uses-sdk android:minSdkVersion=\"17\" android:targetSdkVersion=\"23\" />\n" +
                    "\n" +
                    "    <permission\n" +
                    "        android:name=\"my.normal.P1\"\n" +
                    "        android:protectionLevel=\"normal\" />\n" +
                    "\n" +
                    "    <permission\n" +
                    "        android:name=\"my.dangerous.P2\"\n" +
                    "        android:protectionLevel=\"dangerous\" />\n" +
                    "\n" +
                    "    <uses-permission android:name=\"my.normal.P1\" />\n" +
                    "    <uses-permission android:name=\"my.dangerous.P2\" />\n" +
                    "\n" +
                    "</manifest>\n"
            ),
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import android.content.Context;\n" +
                    "import android.content.pm.PackageManager;\n" +
                    "import android.location.LocationManager;\n" +
                    "import android.support.annotation.RequiresPermission;\n" +
                    "\n" +
                    "import java.io.IOException;\n" +
                    "import java.security.AccessControlException;\n" +
                    "\n" +
                    "public class X {\n" +
                    "    public static void test1() {\n" +
                    "        try {\n" +
                    "            // Ok: Security exception caught in one of the branches\n" +
                    "            methodRequiresDangerous(); // OK\n" +
                    "        } catch (IllegalArgumentException ignored) {\n" +
                    "        } catch (SecurityException ignored) {\n" +
                    "        }\n" +
                    "\n" +
                    "        try {\n" +
                    "            // You have to catch SecurityException explicitly, not parent\n" +
                    "            /*Call requires permission which may be rejected by user: code should explicitly check to see if permission is available (with checkPermission) or explicitly handle a potential SecurityException*/methodRequiresDangerous()/**/; // ERROR\n" +
                    "        } catch (RuntimeException e) { // includes Security Exception\n" +
                    "        }\n" +
                    "\n" +
                    "        try {\n" +
                    "            // Ok: Caught in outer statement\n" +
                    "            try {\n" +
                    "                methodRequiresDangerous(); // OK\n" +
                    "            } catch (IllegalArgumentException e) {\n" +
                    "                // inner\n" +
                    "            }\n" +
                    "        } catch (SecurityException ignored) {\n" +
                    "        }\n" +
                    "\n" +
                    "        try {\n" +
                    "            // You have to catch SecurityException explicitly, not parent\n" +
                    "            /*Call requires permission which may be rejected by user: code should explicitly check to see if permission is available (with checkPermission) or explicitly handle a potential SecurityException*/methodRequiresDangerous()/**/; // ERROR\n" +
                    "        } catch (Exception e) { // includes Security Exception\n" +
                    "        }\n" +
                    "\n" +
                    "        // NOT OK: Catching security exception subclass (except for dedicated ones?)\n" +
                    "\n" +
                    "        try {\n" +
                    "            // Error: catching security exception, but not all of them\n" +
                    "            /*Call requires permission which may be rejected by user: code should explicitly check to see if permission is available (with checkPermission) or explicitly handle a potential SecurityException*/methodRequiresDangerous()/**/; // ERROR\n" +
                    "        } catch (AccessControlException e) { // security exception but specific one\n" +
                    "        }\n" +
                    "    }\n" +
                    "\n" +
                    "    public static void test2() {\n" +
                    "        /*Call requires permission which may be rejected by user: code should explicitly check to see if permission is available (with checkPermission) or explicitly handle a potential SecurityException*/methodRequiresDangerous()/**/; // ERROR: not caught\n" +
                    "    }\n" +
                    "\n" +
                    "    public static void test3()\n" +
                    "            throws IllegalArgumentException {\n" +
                    "        /*Call requires permission which may be rejected by user: code should explicitly check to see if permission is available (with checkPermission) or explicitly handle a potential SecurityException*/methodRequiresDangerous()/**/; // ERROR: not caught by right type\n" +
                    "    }\n" +
                    "\n" +
                    "    public static void test4()\n" +
                    "            throws AccessControlException {  // Security exception but specific one\n" +
                    "        /*Call requires permission which may be rejected by user: code should explicitly check to see if permission is available (with checkPermission) or explicitly handle a potential SecurityException*/methodRequiresDangerous()/**/; // ERROR\n" +
                    "    }\n" +
                    "\n" +
                    "    public static void test5()\n" +
                    "            throws SecurityException {\n" +
                    "        methodRequiresDangerous(); // OK\n" +
                    "    }\n" +
                    "\n" +
                    "    public static void test6()\n" +
                    "            throws Exception { // includes Security Exception\n" +
                    "        // You have to throw SecurityException explicitly, not parent\n" +
                    "        /*Call requires permission which may be rejected by user: code should explicitly check to see if permission is available (with checkPermission) or explicitly handle a potential SecurityException*/methodRequiresDangerous()/**/; // ERROR\n" +
                    "    }\n" +
                    "\n" +
                    "    public static void test7(Context context)\n" +
                    "            throws IllegalArgumentException {\n" +
                    "        if (context.getPackageManager().checkPermission(android.Manifest.permission.ACCESS_FINE_LOCATION, context.getPackageName()) != PackageManager.PERMISSION_GRANTED) {\n" +
                    "            return;\n" +
                    "        }\n" +
                    "        methodRequiresDangerous(); // OK: permission checked\n" +
                    "    }\n" +
                    "\n" +
                    "    @RequiresPermission(\"my.dangerous.P2\")\n" +
                    "    public static void methodRequiresDangerous() {\n" +
                    "    }\n" +
                    "\n" +
                    "    public void test8() { // Regression test for http://b.android.com/187204\n" +
                    "        try {\n" +
                    "            methodRequiresDangerous();\n" +
                    "            mightThrow();\n" +
                    "        } catch (SecurityException | IOException se) { // OK: Checked in multi catch\n" +
                    "        }\n" +
                    "    }\n" +
                    "\n" +
                    "    public void mightThrow() throws IOException {\n" +
                    "    }\n" +
                    "\n" +
                    "}\n"
            ),
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectInlinedMessages()
    }

    fun testIntentsAndContentResolvers() {
        lint().files(
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import android.Manifest;\n" +
                    "import android.app.Activity;\n" +
                    "import android.content.ContentResolver;\n" +
                    "import android.content.Context;\n" +
                    "import android.content.Intent;\n" +
                    "import android.net.Uri;\n" +
                    "import android.support.annotation.RequiresPermission;\n" +
                    "\n" +
                    "@SuppressWarnings({\"deprecation\", \"unused\"})\n" +
                    "public class X {\n" +
                    "    @RequiresPermission(Manifest.permission.CALL_PHONE)\n" +
                    "    public static final String ACTION_CALL = \"android.intent.action.CALL\";\n" +
                    "\n" +
                    "    public static final Uri BOOKMARKS_URI = Uri.parse(\"content://browser/bookmarks\");\n" +
                    "\n" +
                    "    public static final Uri COMBINED_URI = Uri.withAppendedPath(BOOKMARKS_URI, \"bookmarks\");\n" +
                    "\n" +
                    "    public static void activities1(Activity activity) {\n" +
                    "        Intent intent = new Intent(Intent.ACTION_CALL);\n" +
                    "        intent.setData(Uri.parse(\"tel:1234567890\"));\n" +
                    "        // This one will only be flagged if we have framework metadata on Intent.ACTION_CALL\n" +
                    // This relies on the attached SDK having external annotations on Intent.ACTION_CALL;
                    // it looks like this is not available on the SDK we're currently using:
                    // "        activity./*Missing permissions required by intent Intent.ACTION_CALL: android.permission.CALL_PHONE*/startActivity(intent/**/);\n" +
                    "        activity.startActivity(intent);\n" +
                    "    }\n" +
                    "\n" +
                    "    public static void activities2(Activity activity) {\n" +
                    "        Intent intent = new Intent(ACTION_CALL);\n" +
                    "        intent.setData(Uri.parse(\"tel:1234567890\"));\n" +
                    "        activity./*Missing permissions required by intent X.ACTION_CALL: android.permission.CALL_PHONE*/startActivity(intent/**/);\n" +
                    "    }\n" +
                    "\n" +
                    "    public static void activities3(Activity activity) {\n" +
                    "        Intent intent;\n" +
                    "        intent = new Intent(ACTION_CALL);\n" +
                    "        intent.setData(Uri.parse(\"tel:1234567890\"));\n" +
                    "        activity./*Missing permissions required by intent X.ACTION_CALL: android.permission.CALL_PHONE*/startActivity(intent/**/);\n" +
                    "        activity./*Missing permissions required by intent X.ACTION_CALL: android.permission.CALL_PHONE*/startActivity(intent/**/, null);\n" +
                    "        activity./*Missing permissions required by intent X.ACTION_CALL: android.permission.CALL_PHONE*/startActivityForResult(intent/**/, 0);\n" +
                    "        activity./*Missing permissions required by intent X.ACTION_CALL: android.permission.CALL_PHONE*/startActivityFromChild(activity, intent/**/, 0);\n" +
                    "        activity./*Missing permissions required by intent X.ACTION_CALL: android.permission.CALL_PHONE*/startActivityIfNeeded(intent/**/, 0);\n" +
                    "        activity./*Missing permissions required by intent X.ACTION_CALL: android.permission.CALL_PHONE*/startActivityFromFragment(null, intent/**/, 0);\n" +
                    "        activity./*Missing permissions required by intent X.ACTION_CALL: android.permission.CALL_PHONE*/startNextMatchingActivity(intent/**/);\n" +
                    "        startActivity(\"\"); // Not an error!\n" +
                    "    }\n" +
                    "\n" +
                    "    public static void broadcasts(Context context) {\n" +
                    "        Intent intent;\n" +
                    "        intent = new Intent(ACTION_CALL);\n" +
                    "        context./*Missing permissions required by intent X.ACTION_CALL: android.permission.CALL_PHONE*/sendBroadcast(intent/**/);\n" +
                    "        context./*Missing permissions required by intent X.ACTION_CALL: android.permission.CALL_PHONE*/sendBroadcast(intent/**/, \"\");\n" +
                    "    }\n" +
                    "\n" +
                    "    public static void contentResolvers(Context context, ContentResolver resolver) {\n" +
                    "        // read\n" +
                    "        resolver.query(BOOKMARKS_URI, null, null, null, null);\n" +
                    "\n" +
                    "        // write\n" +
                    "        resolver.insert(BOOKMARKS_URI, null);\n" +
                    "        resolver.delete(BOOKMARKS_URI, null, null);\n" +
                    "        resolver.update(BOOKMARKS_URI, null, null, null);\n" +
                    "\n" +
                    "        // URI manipulations\n" +
                    "        resolver.insert(COMBINED_URI, null);\n" +
                    "    }\n" +
                    "\n" +
                    "    public static void startActivity(Object other) {\n" +
                    "        // Unrelated\n" +
                    "    }\n" +
                    "}\n"
            ),
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectInlinedMessages()
    }

    fun testSetPersisted() {
        // Regression test for
        // 68767657: Lint Warning on Valid setPersisted(false) Call
        lint().files(
            java(
                """
                package test.pkg;

                import android.app.job.JobInfo;
                import android.content.ComponentName;
                import android.os.Build;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class PermissionTest {
                    public void test(ComponentName componentName) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            JobInfo.Builder builder = new JobInfo.Builder(5, componentName);
                            builder.setPersisted(false); // Does not require permission
                            builder.setPersisted(true); // Requires permission
                        }
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/PermissionTest.java:13: Error: Missing permissions required by Builder.setPersisted: android.permission.RECEIVE_BOOT_COMPLETED [MissingPermission]
                        builder.setPersisted(true); // Requires permission
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun test72967236() {
        lint().files(
            manifest(
                "" +
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "    package=\"test.pkg.permissiontest\">\n" +
                    "\n" +
                    "    <uses-sdk android:minSdkVersion=\"17\" android:targetSdkVersion=\"23\" />\n" +
                    "\n" +
                    "    <permission\n" +
                    "        android:name=\"my.dangerous.P2\"\n" +
                    "        android:protectionLevel=\"dangerous\" />\n" +
                    "\n" +
                    "</manifest>\n"
            ),
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import android.support.annotation.RequiresPermission;\n" +
                    "\n" +
                    "public class X {\n" +
                    "    public void something() {\n" +
                    "        methodRequiresCarrierOrP2();\n" +
                    "    }\n" +
                    "\n" +
                    "    @RequiresPermission(anyOf = {\"my.dangerous.P2\", \"carrier privileges\"})\n" +
                    "    public void methodRequiresCarrierOrP2() {\n" +
                    "    }\n" +
                    "}\n"
            ),
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/X.java:7: Error: Missing permissions required by X.methodRequiresCarrierOrP2: my.dangerous.P2 or carrier privileges (see TelephonyManager#hasCarrierPrivileges) [MissingPermission]
                    methodRequiresCarrierOrP2();
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun test113159124() {
        lint().files(
            manifest(
                "" +
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "    package=\"test.pkg.permissiontest\">\n" +
                    "\n" +
                    "    <uses-sdk android:minSdkVersion=\"17\" android:targetSdkVersion=\"23\" />\n" +
                    "\n" +
                    "    <permission\n" +
                    "        android:name=\"my.normal.P1\"\n" +
                    "        android:protectionLevel=\"normal\" />\n" +
                    "\n" +
                    "    <permission\n" +
                    "        android:name=\"my.dangerous.P2\"\n" +
                    "        android:protectionLevel=\"dangerous\" />\n" +
                    "\n" +
                    "    <uses-permission android:name=\"my.normal.P1\" />\n" +
                    "    <uses-permission android:name=\"my.dangerous.P2\" />\n" +
                    "\n" +
                    "</manifest>\n"
            ),
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import android.support.annotation.RequiresPermission;\n" +
                    "\n" +
                    "public class X {\n" +
                    "    @RequiresPermission(\"my.dangerous.P2\")\n" +
                    "    public void something() {\n" +
                    "        methodRequiresNormal();\n" +
                    "        methodRequiresDangerous();\n" +
                    "    }\n" +
                    "\n" +
                    "    @RequiresPermission(\"my.normal.P1\")\n" +
                    "    public void methodRequiresNormal() {\n" +
                    "    }\n" +
                    "\n" +
                    "    @RequiresPermission(\"my.dangerous.P2\")\n" +
                    "    public void methodRequiresDangerous() {\n" +
                    "    }\n" +
                    "}\n"
            ),
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun test63962416() {
        // Regression test for
        // 63962416: Android Lint incorrectly reports missing location permission
        lint().files(
            manifest(
                "" +
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "    package=\"test.pkg.permissiontest\">\n" +
                    "\n" +
                    "    <uses-sdk android:minSdkVersion=\"17\" android:targetSdkVersion=\"23\" />\n" +
                    "\n" +
                    "    <uses-permission android:name=\"android.permission.ACCESS_FINE_LOCATION\"/>\n" +
                    "\n" +
                    "</manifest>\n"
            ),
            java(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import android.Manifest;\n" +
                    "import android.annotation.TargetApi;\n" +
                    "import android.app.Activity;\n" +
                    "import android.content.pm.PackageManager;\n" +
                    "import android.os.Build;\n" +
                    "import android.support.v4.app.ActivityCompat;\n" +
                    "import android.telephony.TelephonyManager;\n" +
                    "\n" +
                    "public class CellInfoTest extends Activity {\n" +
                    "    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)\n" +
                    "    public void tet(TelephonyManager manager) {\n" +
                    "        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {\n" +
                    "            return;\n" +
                    "        }\n" +
                    "        manager.getAllCellInfo();\n" +
                    "    }\n" +
                    "}\n"
            ),
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testPermissionAnnotationsInCompiledJars() {
        val libJarPath = "jar/jar/annotation_test.jar"
        val base64JarData = "" +
            "H4sIAAAAAAAAAAvwZmbhYmDgYAACRRsGJMDJwMLg6xriqOvp56b/7xQDQwCK" +
            "0h/FzxlqgSwQFgFiuFJfRz9PN9fgED1fN9/EvMy01OIS3bDUouLM/DwrBUM9" +
            "A14u56LUxJLUFF2nSiuFpMSq1BxerpDEovTUEl2fxKTUHCsFff3UioLUoszc" +
            "1LySxBz90mKgdv3EosTs1OIM/azEskQgUQTCVol5efkliSVAs+NzMvNK4nm5" +
            "FEqAFvJy8XIF4PQZCxCDDMCtggOqAqZKhIGDg4OBEU2VHJIqR7hDip1zEouL" +
            "9ZJBZGugb76woUDt5PNqvF+6naJ3fGlYILNp+r2uCdtWh7hEpa5Wedvq/FSl" +
            "zerWjcS3vVfmH+VcyV7HYM9leM8ttyUq8aTxvJnf5x1/fL8+r57R4lidcOqe" +
            "NY9DZatbbrkvnfrbRWO//S6VECXjiS29EZk3T6crz+Q9qvH0TFnOMaW9IjM/" +
            "fTSKLLx+Sojv0coTTPud1/YK+0nmLg6JqHayKLm067LJ9HMS5bV6m2dumsbI" +
            "H3Dodd26ubNOrovTWB1R5doucMTJ32ixWuPZ58WTdzrcmd6878+81T632BP3" +
            "bd4lUKP48OykG/Puh99e/tE1fYbK4fMt2/dtDbivEv/38Z47j9+Gth4XuGer" +
            "JO4WmG6ww+r1N6vwQqHWw3mXlGVse3smbbCPOGrGrnb+2Cefn53v/lkf/Bai" +
            "48ItGsJZ7d87c8PLREetL9sVJH2f3ngooPovff8phfPp0Y81EvzfrJava6r7" +
            "sLDF6nbUxDXJvbmv+RrYZr2RjnhevFLh/eM8UZcoRbMZLGxC8z7W5W08uPLs" +
            "/89RrPnZJ2//app0dNtRPnb9rntl/5kCvNk5Djy1aZnEyMCwgwmUqBmZuBhw" +
            "5wBUgJYfULWi5wgE0MaRP/BZjmr1LXiSxa2DA0XHH5QkzMgkwoBIxMgBIIei" +
            "S4mRUJIO8GZlA6lkBUJDoGoGZhAPALGIOx5HBAAA"

        val customLib = base64gzip(libJarPath, base64JarData)
        val classPath = classpath(SUPPORT_JAR_PATH, libJarPath)

        lint().files(
            java(
                "package test.pkg;\n" +
                    "\n" +
                    "import jar.jar.AnnotationsClass;\n" +
                    "import android.Manifest;\n" +
                    "import android.support.annotation.RequiresPermission;\n" +
                    "\n" +
                    "public class TestClass {\n" +
                    "\n" +
                    "    @RequiresPermission(Manifest.permission.BLUETOOTH)\n" +
                    "    public static void inClassBluetoothAnnotation() {}\n" +
                    "\n" +
                    "    public static void callMethod() {\n" +
                    "        inClassBluetoothAnnotation(); // Missing Bluetooth Permission\n" +

                    "        AnnotationsClass.testBluetoothPermissionAnnotation(); // Missing Bluetooth Permission\n" +
                    "        AnnotationsClass.testAnyOfLocationPermissionAnnotation(); // Missing Location Permission\n" +
                    "    }\n" +
                    "}"
            ),
            classPath,
            SUPPORT_ANNOTATIONS_JAR,
            customLib
        )
            .run()
            .expect(
                "src/test/pkg/TestClass.java:13: Error: Missing permissions required by TestClass.inClassBluetoothAnnotation: android.permission.BLUETOOTH [MissingPermission]\n" +
                    "        inClassBluetoothAnnotation(); // Missing Bluetooth Permission\n" +
                    "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                    "src/test/pkg/TestClass.java:14: Error: Missing permissions required by AnnotationsClass.testBluetoothPermissionAnnotation: android.permission.BLUETOOTH [MissingPermission]\n" +
                    "        AnnotationsClass.testBluetoothPermissionAnnotation(); // Missing Bluetooth Permission\n" +
                    "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                    "src/test/pkg/TestClass.java:15: Error: Missing permissions required by AnnotationsClass.testAnyOfLocationPermissionAnnotation: android.permission.ACCESS_FINE_LOCATION or android.permission.ACCESS_COARSE_LOCATION [MissingPermission]\n" +
                    "        AnnotationsClass.testAnyOfLocationPermissionAnnotation(); // Missing Location Permission\n" +
                    "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                    "3 errors, 0 warnings"
            )
    }

    fun testGms() {
        // Regression test for some false positives found in g3 in the chromecast app sources
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="com.google.android.apps.chromecast.app"
                    android:versionCode="990000002"
                    android:versionName="DO.NOT.CHANGE" >
                  <uses-sdk
                      android:minSdkVersion="21"
                      android:targetSdkVersion="28" />

                  <permission android:name="com.google.android.apps.chromecast.app.permission.DISCOVER_DEVICES"
                      android:protectionLevel="signature" />

                  <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
                  <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
                  <uses-permission android:name="android.permission.CAMERA" />
                  <uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
                  <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
                  <uses-permission android:name="android.permission.GET_ACCOUNTS" />
                  <uses-permission android:name="android.permission.MANAGE_ACCOUNTS" />
                  <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
                  <uses-permission android:name="android.permission.INTERNET" />
                  <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
                  <uses-permission android:name="android.permission.RECORD_AUDIO" />
                  <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
                  <uses-permission android:name="android.permission.BLUETOOTH"/>
                  <uses-permission android:name="android.permission.BLUETOOTH_ADMIN"/>
                  <uses-permission android:name="android.permission.WAKE_LOCK"/>
                  <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
                  <uses-permission android:name="com.google.android.c2dm.permission.RECEIVE" />
                  <uses-permission android:name="android.permission.CALL_PHONE" flag="e911_enabled_compile_time"/>
                  <uses-permission android:name="com.google.android.providers.gsf.permission.READ_GSERVICES" />
                </manifest>
                """
            ).indented(),
            kotlin(
                """
                package com.google.android.apps.chromecast.app.widget.addressedit
                import android.content.Context
                import android.app.Fragment
                import com.google.android.apps.chromecast.app.util.LocationUtil
                import com.google.android.gms.maps.GoogleMap
                class AddressMapFragment : Fragment() {
                    private var googleMap: GoogleMap? = null
                    fun requireContext(): Context {
                        return this.context
                            ?: throw IllegalStateException("Fragment $this not attached to a context.")
                    }
                    /** Enable my location if it is not already enabled. */
                    fun enableMyLocation() {
                        if (
                            !LocationUtil.missingLocationPermissionOrServices(requireContext()) &&
                            googleMap?.isMyLocationEnabled == false
                        ) {
                            try {
                                googleMap?.isMyLocationEnabled = true
                            } catch (exception: SecurityException) {
                                //logger.at(Logger.WTF).withCause(exception).log("Unable to set my location enabled to true.")
                            }
                        }
                    }
                }
                """
            ).indented(),
            java(
                """
                package com.google.android.apps.chromecast.app.util;
                import android.content.Context;
                import android.location.LocationManager;
                import android.os.Build;
                import android.provider.Settings;
                import android.Manifest.permission;
                public class LocationUtil {
                    public static boolean missingLocationPermissionOrServices(Context context) {
                        return missingLocationPermission(context) || missingLocationServicesForScanning(context);
                    }
                    public static boolean missingLocationPermission(Context context) {
                        return !PermissionUtil.permissionGranted(context, permission.ACCESS_FINE_LOCATION);
                    }
                    public static boolean missingLocationServicesForScanning(Context context) {
                        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && missingLocationServices(context);
                    }
                    public static boolean missingLocationServices(Context context) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            LocationManager locationManager =
                                    (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
                            return locationManager == null || !locationManager.isLocationEnabled();
                        } else {
                            int locationMode = Settings.Secure.LOCATION_MODE_OFF;
                            try {
                                locationMode =
                                        Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);
                            } catch (Settings.SettingNotFoundException e) {
                                //logger.atSevere().log("Location mode setting wasn't found");
                            }
                            return locationMode == Settings.Secure.LOCATION_MODE_OFF;
                        }
                    }
                }
                """
            ).indented(),
            kotlin(
                """
                package com.google.android.apps.chromecast.app.util
                import android.content.Context
                import android.content.pm.PackageManager
                object PermissionUtil {
                    @JvmStatic fun permissionGranted(context: Context, permission: String): Boolean {
                        //return androidx.core.content.ContextCompat.checkSelfPermission(context.applicationContext, permission) == PackageManager.PERMISSION_GRANTED
                        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
                    }
                }
                """
            ).indented(),
            java(
                """
                package com.google.android.gms.maps;
                import android.Manifest;
                import android.support.annotation.RequiresPermission;
                public class GoogleMap {
                    public final boolean isMyLocationEnabled() {
                        return true;
                    }
                    @RequiresPermission(
                            anyOf = {
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                            })
                    public final void setMyLocationEnabled(boolean enabled) {
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_CLASS_PATH,
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }
}
