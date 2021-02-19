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
package com.android.tools.deployer;

import static com.android.tools.deployer.InstallStatus.INSTALL_FAILED_PERMISSION_MODEL_DOWNGRADE;
import static com.android.tools.deployer.InstallStatus.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;

import com.android.ddmlib.InstallReceiver;
import java.util.ArrayList;
import org.junit.Assert;
import org.junit.Test;

public class ApkInstallerTest {

    @Test
    public void handleNumericErrorCode() {
        InstallReceiver receiver = new InstallReceiver();
        receiver.processNewLines(
                new String[] {
                    "Failure [-26: Package blah blah bah but the old target SDK 28 does.]"
                });
        receiver.flush();
        AdbClient.InstallResult result = ApkInstaller.toInstallerResult(receiver);
        Assert.assertEquals(INSTALL_FAILED_PERMISSION_MODEL_DOWNGRADE, result.status);
        Assert.assertEquals(
                "-26: Package blah blah bah but the old target SDK 28 does.", result.reason);
    }

    @Test
    public void handleAndroidSManifestRestrictions() {
        InstallReceiver receiver = new InstallReceiver();
        receiver.processNewLines(
                new String[] {
                    "Failure [INSTALL_PARSE_FAILED_MANIFEST_MALFORMED: Failed parse during installPackageLI: /data/app/vmdl395250143.tmp/base.apk (at Binary XML file line #21): com.example.myapplication.MainActivity: Targeting S+ (version 10000 and above) requires that an explicit value for android:exported be defined when intent filters are present]"
                });
        receiver.flush();
        AdbClient.InstallResult result = ApkInstaller.toInstallerResult(receiver);
        Assert.assertEquals(INSTALL_PARSE_FAILED_MANIFEST_MALFORMED, result.status);
        Assert.assertEquals(
                "INSTALL_PARSE_FAILED_MANIFEST_MALFORMED: Failed parse during installPackageLI: /data/app/vmdl395250143.tmp/base.apk (at Binary XML file line #21): com.example.myapplication.MainActivity: Targeting S+ (version 10000 and above) requires that an explicit value for android:exported be defined when intent filters are present",
                result.reason);
    }

    @Test
    public void forceInstallOnNoDeltaShouldTurnOffInheritance() {
        boolean inherit =
                ApkInstaller.canInherit(15, new ArrayList<>(), Deployer.InstallMode.DELTA_NO_SKIP);
        Assert.assertFalse(inherit);

        inherit = ApkInstaller.canInherit(15, new ArrayList<>(), Deployer.InstallMode.DELTA);
        Assert.assertTrue(inherit);
    }
}
