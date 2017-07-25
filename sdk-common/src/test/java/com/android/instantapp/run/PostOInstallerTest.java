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

package com.android.instantapp.run;

import static com.android.instantapp.run.PostOInstaller.getAdbInstallCommand;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.InstallException;
import com.android.instantapp.utils.InstantAppTests;
import com.google.common.collect.Lists;
import java.io.File;
import org.junit.Test;

/** Unit tests for {@link PostOInstaller}. */
public class PostOInstallerTest {
    @Test
    public void testGetAdbInstallCommand() {
        File apk1 = new File("apk1");
        File apk2 = new File("path/to/apk2");
        File apk3 = new File("apk3");
        assertEquals(
                "$ adb install-multiple -r --option1 --option2 apk1 path/to/apk2 ",
                getAdbInstallCommand(
                        Lists.newArrayList(apk1, apk2),
                        Lists.newArrayList("--option1", "--option2")));
        assertEquals(
                "$ adb install-multiple -r -t --ephemeral apk1 path/to/apk2 apk3 ",
                getAdbInstallCommand(
                        Lists.newArrayList(apk1, apk2, apk3),
                        Lists.newArrayList("-t", "--ephemeral")));
    }

    @Test
    public void testInstallSucceeds() throws Throwable {
        IDevice device = new InstantAppTests.DeviceGenerator().getDevice();
        File apk1 = new File("apk1.apk");
        File apk2 = new File("apk2.apk");
        PostOInstaller installer =
                new PostOInstaller(Lists.newArrayList(apk1, apk2), new RunListener.NullListener());
        installer.install(device);
        verify(device, times(1))
                .installPackages(
                        eq(Lists.newArrayList(apk1, apk2)),
                        eq(true),
                        eq(Lists.newArrayList("-t", "--ephemeral")),
                        anyLong(),
                        any());
    }

    @Test
    public void testExceptionWhenInstallFails() throws Throwable {
        IDevice device = new InstantAppTests.DeviceGenerator().getDevice();
        File apk1 = new File("apk1.apk");
        File apk2 = new File("apk2.apk");
        doThrow(InstallException.class)
                .when(device)
                .installPackages(any(), anyBoolean(), any(), anyLong(), any());
        PostOInstaller installer =
                new PostOInstaller(Lists.newArrayList(apk1, apk2), new RunListener.NullListener());

        try {
            installer.install(device);
            fail("No exception thrown.");
        } catch (InstantAppRunException e) {
            assertEquals(InstantAppRunException.ErrorType.INSTALL_FAILED, e.getErrorType());
        }
    }
}
