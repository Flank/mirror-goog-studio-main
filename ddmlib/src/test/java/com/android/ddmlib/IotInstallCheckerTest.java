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

public class IotInstallCheckerTest {
    private static final String[] DUMPSYS_RESULT_WITHOUT_IOT_PACKAGE =
            new String[] {
                "android.intent.action.MAIN:",
                "abc49e0 com.android.settings/.Settings$WifiCallingSuggestionActivity filter 7470c66",
                "Action: \"android.intent.action.MAIN\"",
                "Category: \"com.android.settings.suggested.category.SETTINGS_ONLY\"",
                "AutoVerify=false"
            };
    private static final String[] DUMPSYS_RESULT_WITH_IOT_PACKAGE =
            new String[] {
                "android.intent.action.MAIN:",
                "abc49e0 com.android.settings/.Settings$WifiCallingSuggestionActivity filter 7470c66",
                "Action: \"android.intent.action.MAIN\"",
                "Category: \"com.android.settings.suggested.category.SETTINGS_ONLY\"",
                "AutoVerify=false",
                "3ede499 com.anhtnguyen.twinkle/.MainActivity filter b91ee63",
                "Action: \"android.intent.action.MAIN\"",
                "Category: \"android.intent.category.IOT_LAUNCHER\"",
                "Category: \"android.intent.category.DEFAULT\"",
                "AutoVerify=false"
            };
    private static final String PACKAGE_TO_BE_INSTALLED = "com.anhtnguyen.twinkle";

    @Test
    public void testCustomReceiver() {
        IotInstallChecker.IntentListReceiver receiver = new IotInstallChecker.IntentListReceiver();
        receiver.processNewLines(DUMPSYS_RESULT_WITH_IOT_PACKAGE);
        assertThat(receiver.getPackagesWithIotLauncher()).containsExactly("com.anhtnguyen.twinkle");
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
        assertThat(packages).isEmpty();
    }

    @Test
    public void testCheckIotPackageAlreadyInstalled() {
        IDevice device = createMockDevice(true, true);
        Set<String> packages = (new IotInstallChecker()).getInstalledIotLauncherApps(device);
        assertThat(packages).containsExactly(PACKAGE_TO_BE_INSTALLED);
    }

    /** Helper method that creates a mock device. */
    static IDevice createMockDevice(
            boolean supportsEmbedded, boolean isIotPackageAlreadyInstalled) {
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
                                            isIotPackageAlreadyInstalled
                                                    ? DUMPSYS_RESULT_WITH_IOT_PACKAGE
                                                    : DUMPSYS_RESULT_WITHOUT_IOT_PACKAGE;
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
