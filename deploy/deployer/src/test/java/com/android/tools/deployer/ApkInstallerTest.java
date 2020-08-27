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
        String errorCode = receiver.getErrorCode();
        AdbClient.InstallResult result = ApkInstaller.parseInstallerResultErrorCode(errorCode);
        Assert.assertEquals(INSTALL_FAILED_PERMISSION_MODEL_DOWNGRADE, result.status);
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
