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
package com.android.ddmlib;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.mockito.internal.util.StringUtil;

public class IotInstallCheckerTest {
    private static final String[] DUMPSYS_RESULT_WITHOUT_THIRD_PARTY_LAUNCHERS =
            new String[] {
                "Activity Resolver Table:",
                "Non-Data Actions:",
                "android.intent.action.MAIN:",
                "a59037c com.android.iotlauncher/.IoTHome filter adae581",
                "Action: \"android.intent.action.MAIN\"",
                "Category: \"android.intent.category.HOME\"",
                "Category: \"android.intent.category.DEFAULT\"",
                "4de7405 com.android.iotlauncher/.DefaultIoTLauncher filter 3e73626",
                "Action: \"android.intent.action.MAIN\"",
                "Category: \"android.intent.category.IOT_LAUNCHER\"",
                "Category: \"android.intent.category.DEFAULT\"",
                "Category: \"android.intent.category.MONKEY\"",
                "mPriority=-1000, mHasPartialTypes=false",
                "2ac4614 com.anhtnguyen.twinkle/.MainActivity filter 15a0d29",
                "Action: \"android.intent.action.MAIN\"",
                "Category: \"android.intent.category.LAUNCHER\"",
                "2ac4614 com.anhtnguyen.twinkle/.MainActivity filter a1048ae",
                "Action: \"android.intent.action.MAIN\"",
                "Category: \"android.intent.category.IOT_LAUNCHER\"",
                "Category: \"android.intent.category.DEFAULT\"",
                "Packages:",
                "Package [com.android.iotlauncher] (8cd1023):",
                "userId=10034",
                "flags=[ SYSTEM DEBUGGABLE HAS_CODE ALLOW_CLEAR_USER_DATA TEST_ONLY ALLOW_BACKUP ]",
                "Package [com.anhtnguyen.twinkle] (8cd1023):",
                "userId=10034",
                "pkg=Package{84cb920 com.anhtnguyen.twinkle}",
                "codePath=/oem/app/app-debug.apk",
                "resourcePath=/oem/app/app-debug.apk",
                "legacyNativeLibraryDir=/oem/lib/app-debug",
                "primaryCpuAbi=null",
                "secondaryCpuAbi=null",
                "versionCode=1 minSdk=26 targetSdk=26",
                "versionName=1.0",
                "splits=[base]",
                "apkSigningVersion=2",
                "applicationInfo=ApplicationInfo{7d2c2d9 com.anhtnguyen.twinkle}",
                "flags=[ SYSTEM DEBUGGABLE HAS_CODE ALLOW_CLEAR_USER_DATA TEST_ONLY ALLOW_BACKUP ]",
                "privateFlags=[ PRIVATE_FLAG_ACTIVITIES_RESIZE_MODE_RESIZEABLE_VIA_SDK_VERSION ]",
                "dataDir=/data/user/0/com.anhtnguyen.twinkle",
            };
    private static final String[] DUMPSYS_RESULT_WITH_THIRD_PARTY_LAUNCHERS =
            new String[] {
                "Activity Resolver Table:",
                "Non-Data Actions:",
                "android.intent.action.MAIN:",
                "a59037c com.android.iotlauncher/.IoTHome filter adae581",
                "Action: \"android.intent.action.MAIN\"",
                "Category: \"android.intent.category.HOME\"",
                "Category: \"android.intent.category.DEFAULT\"",
                "4de7405 com.android.iotlauncher/.DefaultIoTLauncher filter 3e73626",
                "Action: \"android.intent.action.MAIN\"",
                "Category: \"android.intent.category.IOT_LAUNCHER\"",
                "Category: \"android.intent.category.DEFAULT\"",
                "Category: \"android.intent.category.MONKEY\"",
                "mPriority=-1000, mHasPartialTypes=false",
                "2ac4614 com.anhtnguyen.twinkle/.MainActivity filter 15a0d29",
                "Action: \"android.intent.action.MAIN\"",
                "Category: \"android.intent.category.LAUNCHER\"",
                "2ac4614 com.anhtnguyen.twinkle/.MainActivity filter a1048ae",
                "Action: \"android.intent.action.MAIN\"",
                "Category: \"android.intent.category.IOT_LAUNCHER\"",
                "Category: \"android.intent.category.DEFAULT\"",
                "9b25933 com.example.anhtnguyen.dummyapp/.MainActivity filter a8f9fef",
                "Action: \"android.intent.action.MAIN\"",
                "Category: \"android.intent.category.LAUNCHER\"",
                "9b25933 com.example.anhtnguyen.dummyapp/.MainActivity filter 3bc1afc",
                "Action: \"android.intent.action.MAIN\"",
                "Category: \"android.intent.category.IOT_LAUNCHER\"",
                "Category: \"android.intent.category.DEFAULT\"",
                "Packages:",
                "Package [com.android.iotlauncher] (8cd1023):",
                "userId=10034",
                "flags=[ SYSTEM DEBUGGABLE HAS_CODE ALLOW_CLEAR_USER_DATA TEST_ONLY ALLOW_BACKUP ]",
                "Package [com.anhtnguyen.twinkle] (8cd1023):",
                "userId=10034",
                "pkg=Package{84cb920 com.anhtnguyen.twinkle}",
                "codePath=/oem/app/app-debug.apk",
                "resourcePath=/oem/app/app-debug.apk",
                "legacyNativeLibraryDir=/oem/lib/app-debug",
                "primaryCpuAbi=null",
                "secondaryCpuAbi=null",
                "versionCode=1 minSdk=26 targetSdk=26",
                "versionName=1.0",
                "splits=[base]",
                "apkSigningVersion=2",
                "applicationInfo=ApplicationInfo{7d2c2d9 com.anhtnguyen.twinkle}",
                "flags=[ DEBUGGABLE HAS_CODE ALLOW_CLEAR_USER_DATA TEST_ONLY ALLOW_BACKUP ]",
                "privateFlags=[ PRIVATE_FLAG_ACTIVITIES_RESIZE_MODE_RESIZEABLE_VIA_SDK_VERSION ]",
                "dataDir=/data/user/0/com.anhtnguyen.twinkle",
            };
    private static final String IOT_LAUNCHER = "com.android.iotlauncher";
    private static final String PACKAGE_TO_BE_INSTALLED = "com.anhtnguyen.twinkle";
    private static final String DUMMY_THIRD_PARTY_PACKAGE = "com.example.anhtnguyen.dummyapp";

    @Test
    public void testIntentListReceiver() {
        System.out.println(StringUtil.join(DUMPSYS_RESULT_WITH_THIRD_PARTY_LAUNCHERS));
        IotInstallChecker.LauncherPackagesReceiver receiver =
                new IotInstallChecker.LauncherPackagesReceiver();
        receiver.processNewLines(DUMPSYS_RESULT_WITH_THIRD_PARTY_LAUNCHERS);
        assertThat(receiver.getMatchingPackages())
                .containsExactly(IOT_LAUNCHER, PACKAGE_TO_BE_INSTALLED, DUMMY_THIRD_PARTY_PACKAGE);
    }

    @Test
    public void testSystemPackagesReceiver() {
        IotInstallChecker.SystemPackagesReceiver receiver =
                new IotInstallChecker.SystemPackagesReceiver();
        receiver.processNewLines(DUMPSYS_RESULT_WITH_THIRD_PARTY_LAUNCHERS);
        assertThat(receiver.getMatchingPackages()).containsExactly(IOT_LAUNCHER);
    }

    @Test
    public void testCheckNotOnEmbeddedDevice() {
        IDevice device = createMockDevice(false, false);
        Set<String> packages = (new IotInstallChecker()).getInstalledIotLauncherApps(device);
        assertThat(packages).isEmpty();
    }

    @Test
    public void testCheckNoIotPackageInstalled() {
        IDevice device = createMockDevice(true, false);
        Set<String> packages = (new IotInstallChecker()).getInstalledIotLauncherApps(device);
        // Should not contain IOT_LAUNCHER nor PACKAGE_TO_BE_INSTALLED since they are system apps.
        assertThat(packages).isEmpty();
    }

    @Test
    public void testCheckIotPackageAlreadyInstalled() {
        IDevice device = createMockDevice(true, true);
        Set<String> packages = (new IotInstallChecker()).getInstalledIotLauncherApps(device);
        // Should not contain IOT_LAUNCHER since it is a system app.
        assertThat(packages).containsExactly(PACKAGE_TO_BE_INSTALLED, DUMMY_THIRD_PARTY_PACKAGE);
    }

    /** Helper method that creates a mock device. */
    static IDevice createMockDevice(
            boolean supportsEmbedded, boolean isThirdPartyLauncherPackageAlreadyInstalled) {
        IDevice mockDevice = mock(IDevice.class);
        when(mockDevice.supportsFeature(IDevice.HardwareFeature.EMBEDDED))
                .thenReturn(supportsEmbedded);
        // Only expect a call to executeShellCommand if testing an embedded device. Otherwise, throw an AssertionError.
        if (supportsEmbedded) {
            try {
                doAnswer(
                                invocation -> {
                                    IShellOutputReceiver receiver =
                                            (IShellOutputReceiver) invocation.getArguments()[1];
                                    String[] output =
                                            isThirdPartyLauncherPackageAlreadyInstalled
                                                    ? DUMPSYS_RESULT_WITH_THIRD_PARTY_LAUNCHERS
                                                    : DUMPSYS_RESULT_WITHOUT_THIRD_PARTY_LAUNCHERS;
                                    byte[] bytes = String.join("\n", output).getBytes();
                                    receiver.addOutput(bytes, 0, bytes.length);
                                    return null;
                                })
                        .when(mockDevice)
                        .executeShellCommand(
                                any(String.class),
                                any(IShellOutputReceiver.class),
                                any(Long.class),
                                any(TimeUnit.class));
            } catch (TimeoutException e) {
                e.printStackTrace();
            } catch (AdbCommandRejectedException e) {
                e.printStackTrace();
            } catch (ShellCommandUnresponsiveException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return mockDevice;
    }
}
