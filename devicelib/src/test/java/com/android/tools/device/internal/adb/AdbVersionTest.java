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

package com.android.tools.device.internal.adb;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.android.testutils.truth.PathSubject;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class AdbVersionTest {

    @Test
    public void parseFrom_nominal() {
        AdbVersion version = AdbVersion.parseFrom("Android Debug Bridge version 1.0.32");
        assertThat(version.toString()).isEqualTo("1.0.32");
    }

    @Test
    public void parseFrom_versionWithRevision() {
        AdbVersion version =
                AdbVersion.parseFrom("Android Debug Bridge version 1.0.32 8b22c293a0e5-android");
        assertThat(version).isEqualTo(AdbVersion.parseFrom("1.0.32"));
    }

    @Test
    public void parseFrom_notAdbOutput() {
        AdbVersion version = AdbVersion.parseFrom("1.0.foo");
        assertThat(version).isEqualTo(AdbVersion.UNKNOWN);
    }

    @Test
    public void parseFrom_components() {
        AdbVersion version =
                AdbVersion.parseFrom("Android Debug Bridge version 1.23.32 8b22c293a0e5-android");
        assertThat(version.major).isEqualTo(1);
        assertThat(version.minor).isEqualTo(23);
        assertThat(version.micro).isEqualTo(32);
    }

    @Test
    public void adbVersion_comparisons() {
        AdbVersion min = AdbVersion.parseFrom("1.0.20");
        AdbVersion now = AdbVersion.parseFrom("1.0.32");
        AdbVersion future = AdbVersion.parseFrom("2.0.32");

        assertThat(now).isGreaterThan(min);
        assertThat(min).isLessThan(now);
        assertThat(now).isEquivalentAccordingToCompareTo(now);
        assertThat(min).isGreaterThan(AdbVersion.UNKNOWN);
        assertThat(future).isGreaterThan(now);
    }

    @Test
    public void adbVersion_equals() {
        EqualsVerifier.forClass(AdbVersion.class).verify();
    }

    @Test
    public void adbVersion_fromAdb() throws IOException {
        Path adb = AdbTestUtils.getPathToAdb();
        PathSubject.assertThat(adb).isExecutable();

        AdbVersion version = AdbVersion.get(adb);
        assertThat(version).isAtLeast(AdbVersion.parseFrom("1.0.20"));

        try {
            version = AdbVersion.get(Paths.get("/does/not/exist/adb"));
            fail("Got version " + version + " from non existent adb");
        } catch (IOException expected) {
            // pass
        }
    }
}
