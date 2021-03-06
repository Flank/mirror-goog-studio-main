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
package com.android.ddmlib.internal;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.ddmlib.AdbHelper;
import com.android.ddmlib.internal.commands.CommandResult;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import kotlin.text.Charsets;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CommandServiceTest {

    private final int TEST_TIMEOUT_MS = 15 * 1000;
    CommandService service;
    SocketChannel channel;

    @Before
    public void setup() throws IOException, InterruptedException {
        service = new CommandService(0);
        service.start();
        for (int i = 10; i >= 0 && service.getBoundPort() == -1; i--) {
            // Sleep until we bind our service port.
            Thread.sleep(100);
        }
        if (service.getBoundPort() == -1) {
            throw new IOException("Failed to bind server");
        }
        channel = SocketChannel.open(new InetSocketAddress("localhost", service.getBoundPort()));
        assertThat(channel.isConnected()).isTrue();
    }

    @After
    public void teardown() throws IOException {
        channel.close();
        service.stop();
    }

    @Test(expected = Test.None.class /* no exception expected */)
    public void serverProcessesCommandWithOutRegistration() throws IOException {
        channel.write(createCommandString("test", null));
    }

    @Test
    public void serverProcessesCommandWithRegistration() throws Exception {
        CountDownLatch commandLatch = new CountDownLatch(1);
        service.addCommand(
                "test",
                argsString -> {
                    commandLatch.countDown();
                    return new CommandResult();
                });
        channel.write(createCommandString("test", null));
        assertThat(commandLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        validateResponse(channel, true, "");
    }

    @Test
    public void serverProcessesCommandWithRegistrationAndArgs() throws Exception {
        final String[] actualArgs = new String[1];
        final String ExpectedArgs = "Some:Arguments";
        CountDownLatch commandLatch = new CountDownLatch(1);
        service.addCommand(
                "test",
                argsString -> {
                    actualArgs[0] = argsString;
                    commandLatch.countDown();
                    return new CommandResult();
                });
        channel.write(createCommandString("test", ExpectedArgs));
        assertThat(commandLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(actualArgs[0]).isEqualTo(ExpectedArgs);
        validateResponse(channel, true, "");
    }

    @Test
    public void commandResultFailHandled() throws Exception {
        CountDownLatch commandLatch = new CountDownLatch(1);
        service.addCommand(
                "test",
                argsString -> {
                    commandLatch.countDown();
                    return new CommandResult("InvalidTest");
                });
        channel.write(createCommandString("test", null));
        assertThat(commandLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        validateResponse(channel, false, "InvalidTest");
    }

    private void validateResponse(SocketChannel channel, boolean result, String message)
            throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(128);
        channel.read(buffer);
        String data = new String(buffer.array(), UTF_8);
        if (result) {
            assertThat(data).contains("OKAY");
        } else {
            assertThat(data).containsMatch(String.format("FAIL%04x%s", message.length(), message));
        }
    }

    private ByteBuffer createCommandString(String command, String args) {
        if (args == null) {
            return ByteBuffer.wrap(AdbHelper.formAdbRequest(command));
        } else {
            return ByteBuffer.wrap(
                    (AdbHelper.formAdbRequest(String.format("%s:%s", command, args))));
        }
    }
}
