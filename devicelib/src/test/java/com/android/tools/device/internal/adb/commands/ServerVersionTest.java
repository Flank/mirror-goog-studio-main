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

package com.android.tools.device.internal.adb.commands;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.android.tools.device.internal.adb.StreamConnection;
import com.google.common.base.Charsets;
import com.google.common.primitives.UnsignedInteger;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ServerVersionTest {
    private byte[] responseData;
    private ByteArrayOutputStream commandStream;
    private StreamConnection connection;
    private ServerVersion versionCommand;

    @Before
    public void setup() {
        commandStream = new ByteArrayOutputStream();
        versionCommand = new ServerVersion();
    }

    @After
    public void tearDown() throws IOException {
        connection.close();
    }

    @Test
    public void version_nominal() throws IOException {
        responseData = "OKAY00040024".getBytes(Charsets.UTF_8);
        connection = new StreamConnection(new ByteArrayInputStream(responseData), commandStream);
        assertThat(versionCommand.execute(connection)).isEqualTo(UnsignedInteger.valueOf(0x24));
    }

    @Test
    public void version_failure() {
        responseData = "FAIL0003Err".getBytes(Charsets.UTF_8);
        connection = new StreamConnection(new ByteArrayInputStream(responseData), commandStream);
        try {
            versionCommand.execute(connection);
            fail("Shouldn't succeed on an error response");
        } catch (IOException e) {
            assertThat(e.getMessage()).isEqualTo("Err");
        }
    }
}
