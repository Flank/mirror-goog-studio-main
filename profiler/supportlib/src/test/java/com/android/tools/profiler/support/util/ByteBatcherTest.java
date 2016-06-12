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

package com.android.tools.profiler.support.util;

import static org.hamcrest.CoreMatchers.equalTo;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class ByteBatcherTest {
    @Test
    public void flush_triggersReceiver() throws Exception {
        TestFlushReceiver r = new TestFlushReceiver();
        ByteBatcher b = new ByteBatcher(r, 10);
        b.addByte(123);
        Assert.assertThat(r.getReceived().size(), equalTo(0));
        b.flush();
        Assert.assertThat(r.getReceived().size(), equalTo(1));
        Assert.assertThat(r.getReceived().get(0), equalTo(Arrays.asList((byte)123)));
    }

    @Test
    public void flush_noopIfBatcherIsEmpty() throws Exception {
        TestFlushReceiver r = new TestFlushReceiver();
        ByteBatcher b = new ByteBatcher(r, 10);
        Assert.assertThat(r.getReceived().size(), equalTo(0));
        b.flush();
        Assert.assertThat(r.getReceived().size(), equalTo(0));
    }

    @Test
    public void addByte_triggersReceiverExactlyWhenThresholdIsCrossed() throws Exception {
        TestFlushReceiver r = new TestFlushReceiver();
        ByteBatcher b = new ByteBatcher(r, 3);
        b.addByte(123);
        b.addByte(456);
        Assert.assertThat(r.getReceived().size(), equalTo(0));
        b.addByte(789);
        Assert.assertThat(r.getReceived().size(), equalTo(1));
        Assert.assertThat(r.getReceived().get(0),
                equalTo(Arrays.asList((byte) 123, (byte) 456, (byte) 789)));
    }

    @Test
    public void addByte_triggersReceiverEveryTimeThresholdIsCrossed() throws Exception {
        TestFlushReceiver r = new TestFlushReceiver();
        ByteBatcher b = new ByteBatcher(r, 2);
        b.addByte(12);
        b.addByte(34); // flushed
        b.addByte(56);
        b.addByte(78); // flushed
        b.addByte(90);

        Assert.assertThat(r.getReceived().size(), equalTo(2));
        Assert.assertThat(r.getReceived().get(0),
                equalTo(Arrays.asList((byte) 12, (byte) 34)));
        Assert.assertThat(r.getReceived().get(1),
                equalTo(Arrays.asList((byte) 56, (byte) 78)));
    }

    @Test
    public void addBytes_flushesAutomaticallyIfLargerThanCapacity() throws Exception {
        TestFlushReceiver r = new TestFlushReceiver();
        ByteBatcher b = new ByteBatcher(r, 2);

        byte[] bytes = new byte[] { 12, 34, 56, 78, 90 };
        b.addBytes(bytes, 0, bytes.length);

        Assert.assertThat(r.getReceived().size(), equalTo(2));
        Assert.assertThat(r.getReceived().get(0),
                equalTo(Arrays.asList((byte) 12, (byte) 34)));
        Assert.assertThat(r.getReceived().get(1),
                equalTo(Arrays.asList((byte) 56, (byte) 78)));

        b.flush();
        Assert.assertThat(r.getReceived().size(), equalTo(3));
        Assert.assertThat(r.getReceived().get(2),
                equalTo(Arrays.asList((byte) 90)));
    }

    @Test
    public void addBytes_customOffsetAndLengthWorks() throws Exception {
        TestFlushReceiver r = new TestFlushReceiver();
        ByteBatcher b = new ByteBatcher(r, 2);

        byte[] bytes = new byte[] { 12, 34, 56, 78, 90 };
        b.addBytes(bytes, 1, 3);

        Assert.assertThat(r.getReceived().size(), equalTo(1));
        Assert.assertThat(r.getReceived().get(0),
                equalTo(Arrays.asList((byte) 34, (byte) 56)));

        b.flush();
        Assert.assertThat(r.getReceived().size(), equalTo(2));
        Assert.assertThat(r.getReceived().get(1),
                equalTo(Arrays.asList((byte) 78)));
    }

    private static class TestFlushReceiver implements ByteBatcher.FlushReceiver {

        // Note: List<Byte> is easier to test against than byte[]
        private List<List<Byte>> mReceived = new ArrayList<List<Byte>>();

        @Override
        public void receive(byte[] bytes) {
            ArrayList<Byte> byteList = new ArrayList<Byte>(bytes.length);
            for (byte b : bytes) {
                byteList.add(b);
            }
            mReceived.add(byteList);
        }

        public List<List<Byte>> getReceived() {
            return mReceived;
        }
    }
}
