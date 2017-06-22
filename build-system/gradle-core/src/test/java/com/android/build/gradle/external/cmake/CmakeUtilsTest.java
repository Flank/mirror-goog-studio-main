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

package com.android.build.gradle.external.cmake;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.repository.Revision;
import org.junit.Test;

public class CmakeUtilsTest {
    @Test
    public void testValidCmakeVersion() {
        assertThat(CheckCmakeVersionEquality("3.8.0", new Revision(3, 8, 0))).isTrue();
        assertThat(CheckCmakeVersionEquality("3.8.123", new Revision(3, 8, 123))).isTrue();
        assertThat(CheckCmakeVersionEquality("3.7.0-rc2", new Revision(3, 7, 0, 2))).isTrue();
        assertThat(CheckCmakeVersionEquality("3.6.123-rc12", new Revision(3, 6, 123, 12))).isTrue();
    }

    /**
     * Creates a Revision object from version string and compares with the expected cmake version.
     *
     * @param versionString - Cmake version as a string
     * @param expectedCmakeVersion - expected Revision to check against
     * @return true if actual Revision is same as expectedCmakeVersion
     */
    private boolean CheckCmakeVersionEquality(
            @NonNull String versionString, @NonNull Revision expectedCmakeVersion) {
        Revision actualVersion = CmakeUtils.getVersion(versionString);
        return expectedCmakeVersion.equals(actualVersion);
    }
}
