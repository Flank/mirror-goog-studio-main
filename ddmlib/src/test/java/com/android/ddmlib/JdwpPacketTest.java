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
package com.android.ddmlib;

import static com.google.common.truth.Truth.assertThat;

import com.android.ddmlib.jdwp.JdwpCommands;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import junit.framework.TestCase;

public class JdwpPacketTest extends TestCase {

    private LogCapture logCapture = new LogCapture();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Log.addLogger(logCapture);
    }

    @Override
    protected void tearDown() throws Exception {
        Log.setLevel(DdmPreferences.getLogLevel());
        super.tearDown();
    }

    public void testLoggingDisabled() throws Exception {
        // Prepare
        Log.setLevel(Log.LogLevel.WARN);
        JdwpPacket packet = createTestPacket(JdwpCommands.SET_VM, JdwpCommands.CMD_VM_VERSION);

        // Act
        packet.log("Test Title");

        // Assert
        assertThat(logCapture.contains(x -> x.getMessage().contains("Test Title"))).isFalse();
    }

    public void testLoggingEnabled() throws Exception {
        // Prepare
        Log.setLevel(Log.LogLevel.DEBUG);
        JdwpPacket packet = createTestPacket(JdwpCommands.SET_VM, JdwpCommands.CMD_VM_VERSION);

        // Act
        packet.log("Test Title");

        // Assert
        assertThat(logCapture.contains(x -> x.getMessage().contains("Test Title"))).isTrue();
    }

    public void testLoggingCommand() throws Exception {
        // Prepare
        Log.setLevel(Log.LogLevel.DEBUG);
        JdwpPacket packet = createTestPacket(JdwpCommands.SET_VM, JdwpCommands.CMD_VM_VERSION);

        // Act
        packet.log("Test Title");

        // Assert
        assertThat(logCapture.contains(x -> x.getMessage().contains("Test Title"))).isTrue();
        assertThat(logCapture.contains(x -> x.getMessage().contains("CMD_VM_VERSION"))).isTrue();
        assertThat(
                        logCapture.contains(
                                x -> x.getMessage().contains(Integer.toString(packet.getId()))))
                .isTrue();
    }

    private static JdwpPacket createTestPacket(int cmdSet, int cmd) throws Exception {
        ByteBuffer buf = ByteBuffer.allocate(100);
        buf.order(ByteOrder.BIG_ENDIAN);
        JdwpPacket packet = new JdwpPacket(buf);
        packet.finishPacket(cmdSet, cmd, 0);
        return packet;
    }

    private static class LogEntry {
        private final Log.LogLevel logLevel;
        private final String tag;
        private final String message;

        public LogEntry(Log.LogLevel logLevel, String tag, String message) {
            this.logLevel = logLevel;
            this.tag = tag;
            this.message = message;
        }

        public Log.LogLevel getLogLevel() {
            return logLevel;
        }

        public String getTag() {
            return tag;
        }

        public String getMessage() {
            return message;
        }
    }

    private static class LogCapture implements Log.ILogOutput {
        private final List<LogEntry> entries = new ArrayList<>();

        @Override
        public void printLog(Log.LogLevel logLevel, String tag, String message) {
            addLogEntry(logLevel, tag, message);
        }

        @Override
        public void printAndPromptLog(Log.LogLevel logLevel, String tag, String message) {
            addLogEntry(logLevel, tag, message);
        }

        private void addLogEntry(Log.LogLevel logLevel, String tag, String message) {
            entries.add(new LogEntry(logLevel, tag, message));
        }

        public List<LogEntry> getEntries() {
            return entries;
        }

        public boolean contains(Predicate<? super LogEntry> predicate) {
            return entries.stream().anyMatch(predicate);
        }
    }
}
