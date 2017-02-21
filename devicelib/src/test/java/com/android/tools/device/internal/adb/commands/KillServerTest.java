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

import com.android.tools.device.internal.adb.StreamConnection;
import com.google.common.base.Charsets;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class KillServerTest {
    private ByteArrayOutputStream commandStream;
    private StreamConnection connection;
    private KillServer killCommand;

    @Before
    public void setup() {
        commandStream = new ByteArrayOutputStream();
        connection = new StreamConnection(new ByteArrayInputStream(new byte[0]), commandStream);
        killCommand = new KillServer();
    }

    @After
    public void tearDown() throws IOException {
        connection.close();
    }

    @Test
    public void execute_killCommand() throws IOException {
        killCommand.execute(connection);
        assertThat(commandStream.toString(Charsets.UTF_8.name())).isEqualTo("0009host:kill");
    }
}
