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

import com.google.common.base.Charsets;
import com.google.common.primitives.UnsignedInteger;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;

public class StreamConnectionTest {
    private ByteArrayOutputStream commandStream;
    private StreamConnection conn;
    private byte[] responseData;

    @Before
    public void setUp() {
        commandStream = new ByteArrayOutputStream();
    }

    @Test
    public void isOk_nominal() throws IOException {
        responseData = "OKAY".getBytes(Charsets.UTF_8);
        conn = new StreamConnection(new ByteArrayInputStream(responseData), commandStream);
        assertThat(conn.isOk()).isTrue();
    }

    @Test
    public void isOk_Failure() throws IOException {
        responseData = "FAIL".getBytes(Charsets.UTF_8);
        conn = new StreamConnection(new ByteArrayInputStream(responseData), commandStream);
        assertThat(conn.isOk()).isFalse();
    }

    @Test
    public void isOk_NotEnoughData() {
        responseData = "OK".getBytes(Charsets.UTF_8);
        conn = new StreamConnection(new ByteArrayInputStream(responseData), commandStream);
        try {
            conn.isOk();
            fail("Expected exception since there wasn't enough data");
        } catch (IOException e) {
            assertThat(e.getMessage()).isEqualTo("End of Stream before fully reading 4 bytes");
        }
    }

    @Test
    public void readUnsignedHexInt() throws IOException {
        responseData = "f123".getBytes(Charsets.UTF_8);
        conn = new StreamConnection(new ByteArrayInputStream(responseData), commandStream);
        assertThat(conn.readUnsignedHexInt()).isEqualTo(UnsignedInteger.valueOf(0xf123));
    }
}
