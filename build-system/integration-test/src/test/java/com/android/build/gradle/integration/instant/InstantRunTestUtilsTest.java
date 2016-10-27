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

package com.android.build.gradle.integration.instant;

import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.builder.testing.api.DeviceException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.InstallException;
import com.android.sdklib.AndroidVersion;
import com.android.tools.fd.client.InstantRunArtifact;
import com.android.tools.fd.client.InstantRunArtifactType;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class InstantRunTestUtilsTest {

    @Mock public IDevice device;

    @Test
    public void testDoInstallForMultidex() throws DeviceException, InstallException {
        InstantRunTestUtils.doInstall(
                device,
                ImmutableList.of(
                        new InstantRunArtifact(
                                InstantRunArtifactType.MAIN, new File("test"), "1")));
        verify(device).installPackage(any(), anyBoolean());
        verifyNoMoreInteractions(device);
    }

    @Test
    public void testDoInstallForMultiApk() throws DeviceException, InstallException {
        File apkSplit = new File("test3.apk");
        checkMultiApkInstall(
                ImmutableList.of(
                        new InstantRunArtifact(InstantRunArtifactType.SPLIT, apkSplit, "8")),
                ImmutableList.of(apkSplit));
    }

    @Test
    public void testDoInstallForMultiApkIncremental() throws DeviceException, InstallException {
        File apkSplitMain = new File("testMain.apk");
        File apkSplit1 = new File("testSplit1.apk");
        File apkSplit2 = new File("testSplit2.apk");

        // NB: the ordering is important, the split apk installer needs the main split to be first.
        checkMultiApkInstall(
                ImmutableList.of(
                        new InstantRunArtifact(InstantRunArtifactType.SPLIT, apkSplit1, "1"),
                        new InstantRunArtifact(
                                InstantRunArtifactType.SPLIT_MAIN, apkSplitMain, "1"),
                        new InstantRunArtifact(InstantRunArtifactType.SPLIT, apkSplit2, "1")),
                ImmutableList.of(apkSplitMain, apkSplit1, apkSplit2));
    }

    private void checkMultiApkInstall(
            @NonNull List<InstantRunArtifact> artifactList, @NonNull List<File> expectedFiles)
            throws DeviceException, InstallException {
        when(device.getVersion()).thenReturn(new AndroidVersion(21, null));

        InstantRunTestUtils.doInstall(device, artifactList);
        verify(device).getVersion();
        verify(device)
                .installPackages(
                        eq(expectedFiles), eq(true), anyListOf(String.class), anyLong(), any());
        verifyNoMoreInteractions(device);
    }
}
