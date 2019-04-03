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

package com.android.build.gradle.integration.common.utils;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.ndk.DefaultNdkInfo;
import com.android.build.gradle.internal.ndk.NdkInfo;
import java.io.File;
import java.util.Collection;

/**
 * Ndk related helper functions.
 */
public class NdkHelper {

    @NonNull
    public static NdkInfo getNdkInfo() {
        return new DefaultNdkInfo(new File(SdkHelper.findSdkDir(), SdkConstants.FD_NDK));
    }

    @NonNull
    public static NdkInfo getNdkInfo(GradleTestProject project) {
        return new DefaultNdkInfo(project.getAndroidNdkHome());
    }

    @NonNull
    public static Collection<Abi> getAbiList(GradleTestProject project) {
        NdkInfo info = getNdkInfo(project);
        return info.getDefaultAbis();
    }
}
