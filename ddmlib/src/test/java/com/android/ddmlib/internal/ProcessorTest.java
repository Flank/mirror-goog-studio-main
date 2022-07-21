/*
 * Copyright (C) 2022 The Android Open Source Project
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import junit.framework.Assert;
import org.junit.Test;

public class ProcessorTest {

    private byte[] makeMessage(String s) {
        String header = String.format("%04X", s.length());
        return (header + s).getBytes(StandardCharsets.UTF_8);
    }

    @Test
    public void bytesOneAtAtimeTest() throws IOException {
        String hello = "HelloWorld";
        TestableProcessor tp = new TestableProcessor(null);

        byte[] msg = makeMessage(hello);
        for (int i = 0; i < msg.length; i++) {
            tp.onBytesReceived(ByteBuffer.wrap(msg, i, 1));
        }

        Assert.assertEquals("Wrong number of messages", tp.messages.size(), 1);
        String actual = tp.messages.get(0);
        Assert.assertEquals("Unexpected message", hello, actual);
        Assert.assertEquals("Unexpected remaining stream", tp.mStream.size(), 0);
    }

    @Test
    public void twoMessageInARowOneByteAtaTime() throws IOException {
        String hello = "Hello";
        String world = "World";
        TestableProcessor tp = new TestableProcessor(null);

        byte[] msg;

        msg = makeMessage(hello);
        for (int i = 0; i < msg.length; i++) {
            tp.onBytesReceived(ByteBuffer.wrap(msg, i, 1));
        }

        msg = makeMessage(world);
        for (int i = 0; i < msg.length; i++) {
            tp.onBytesReceived(ByteBuffer.wrap(msg, i, 1));
        }

        Assert.assertEquals("Wrong number of messages\"", tp.messages.size(), 2);
        Assert.assertEquals("Unexpected message", hello, tp.messages.get(0));
        Assert.assertEquals("Unexpected message", world, tp.messages.get(1));
        Assert.assertEquals("Unexpected remaining stream", tp.mStream.size(), 0);
    }

    @Test
    public void headerFirstPayloadNextTest() throws IOException {
        String hello = "HelloWorld";
        TestableProcessor tp = new TestableProcessor(null);

        byte[] msg = makeMessage(hello);
        tp.onBytesReceived(ByteBuffer.wrap(msg, 0, 4));
        tp.onBytesReceived(ByteBuffer.wrap(msg, 4, hello.length()));

        Assert.assertEquals("Wrong number of messages", tp.messages.size(), 1);
        String actual = tp.messages.get(0);
        Assert.assertEquals("Unexpected message", hello, actual);
        Assert.assertEquals("Unexpected remaining stream", tp.mStream.size(), 0);
    }

    @Test
    public void messageThenHeader() throws IOException {
        String hello = "HelloWorld";
        TestableProcessor tp = new TestableProcessor(null);

        byte[] msg = makeMessage(hello);
        tp.onBytesReceived(ByteBuffer.wrap(msg, 0, msg.length));
        tp.onBytesReceived(ByteBuffer.wrap(msg, 0, 4));

        Assert.assertEquals("Wrong number of messages", tp.messages.size(), 1);
        String actual = tp.messages.get(0);
        Assert.assertEquals("Unexpected message", hello, actual);
        Assert.assertEquals("Unexpected remaining stream", tp.mStream.size(), 4);
    }

    @Test
    public void headerThenPartialPayload() throws IOException {
        String hello = "HelloWorld";
        TestableProcessor tp = new TestableProcessor(null);

        byte[] msg = makeMessage(hello);
        tp.onBytesReceived(ByteBuffer.wrap(msg, 0, 4));
        tp.onBytesReceived(ByteBuffer.wrap(msg, 4, hello.length() - 1));

        Assert.assertEquals("Wrong number of messages", tp.messages.size(), 0);
        Assert.assertEquals(
                "Unexpected remaining stream", tp.mStream.size(), 4 + hello.length() - 1);
    }
}
