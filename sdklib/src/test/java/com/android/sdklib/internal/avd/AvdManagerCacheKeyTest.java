/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.sdklib.internal.avd;

import static com.android.sdklib.internal.avd.AvdManager.AvdManagerCacheKey;

import com.android.annotations.NonNull;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.common.testing.EqualsTester;
import java.nio.file.FileSystem;
import org.junit.Test;

public class AvdManagerCacheKeyTest {
    private final @NonNull FileSystem myFileSystem = Jimfs.newFileSystem(Configuration.unix());

    @Test
    public void testEquals() {
        new EqualsTester()
                .addEqualityGroup(
                        new AvdManagerCacheKey(
                                myFileSystem.getPath("/path/to/sdk"),
                                myFileSystem.getPath("/path/to/avd")),
                        new AvdManagerCacheKey(
                                myFileSystem.getPath("/path/to/sdk"),
                                myFileSystem.getPath("/path/to/avd")))
                .addEqualityGroup(
                        new AvdManagerCacheKey(
                                myFileSystem.getPath("/path/to/other/sdk"),
                                myFileSystem.getPath("/path/to/avd")))
                .addEqualityGroup(
                        new AvdManagerCacheKey(
                                myFileSystem.getPath("/path/to/sdk"),
                                myFileSystem.getPath("/path/to/other/avd")))
                .addEqualityGroup(
                        new AvdManagerCacheKey(
                                myFileSystem.getPath("/path/to/other/sdk"),
                                myFileSystem.getPath("/path/to/other/avd")))
                .testEquals();
    }
}
